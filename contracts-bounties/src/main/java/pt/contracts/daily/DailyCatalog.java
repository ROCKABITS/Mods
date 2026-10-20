package pt.contracts.daily;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import pt.contracts.ContractsMod;
import pt.contracts.daily.DailyRules.*;

/** Datapack path: data/<namespace>/daily_quest/<id>.json; file/world overrides are optional. */
public final class DailyCatalog extends SimpleJsonResourceReloadListener {
    private static Map<String,Template> datapacks=Map.of(),files=Map.of();
    public DailyCatalog(){super(DailyLedger.JSON,"daily_quest");}
    public static Path folder(){return FMLPaths.CONFIGDIR.get().resolve("contracts-daily-quests");}
    public static void register(AddReloadListenerEvent event){event.addListener(new DailyCatalog());}
    @Override protected void apply(Map<ResourceLocation,JsonElement> input,ResourceManager manager,ProfilerFiller profiler){
        Map<String,Template> next=new TreeMap<>();
        for(var entry:input.entrySet())try{
            JsonObject object=entry.getValue().getAsJsonObject().deepCopy();if(!object.has("id"))object.addProperty("id",entry.getKey().toString());
            Template template=DailyLedger.JSON.fromJson(object,Template.class);if(next.putIfAbsent(template.id(),template)!=null)throw new IllegalArgumentException("Duplicate quest template");
        }catch(RuntimeException invalid){ContractsMod.LOG.error("Rejected daily quest datapack {}: {}",entry.getKey(),invalid.getMessage());}
        if(next.size()>1024)throw new IllegalStateException("Daily quest catalogue exceeds1024 templates");datapacks=Map.copyOf(next);reloadFiles();
    }
    public static void reloadFiles(){
        Map<String,Template> next=new TreeMap<>();
        try{
            Files.createDirectories(folder());Path settings=folder().resolve("settings.json");if(!Files.exists(settings))Files.writeString(settings,new GsonBuilder().setPrettyPrinting().create().toJson(new Settings())+"\n");
            try(var paths=Files.list(folder())){for(Path file:paths.filter(p->p.getFileName().toString().endsWith(".json")&&!p.getFileName().toString().equals("settings.json")).sorted().toList()){
                try{if(Files.size(file)>32768)throw new IllegalArgumentException("Template file too large");Template template=DailyLedger.JSON.fromJson(Files.readString(file),Template.class);if(next.putIfAbsent(template.id(),template)!=null)throw new IllegalArgumentException("Duplicate file template");}
                catch(RuntimeException invalid){ContractsMod.LOG.error("Rejected daily quest file {}: {}",file.getFileName(),invalid.getMessage());}
            }}
        }catch(java.io.IOException failed){throw new IllegalStateException("daily.contracts.catalog_error",failed);}
        if(next.size()>1024)throw new IllegalStateException("Daily quest file catalogue exceeds1024 templates");files=Map.copyOf(next);
    }
    public static Settings initialSettings(){try{Path path=folder().resolve("settings.json");if(!Files.isRegularFile(path)||Files.size(path)>32768)throw new IllegalStateException("Invalid daily settings file");Settings settings=DailyLedger.JSON.fromJson(Files.readString(path),Settings.class);settings.validate();return settings;}catch(java.io.IOException error){throw new IllegalStateException("daily.contracts.catalog_error",error);}}
    public static List<Template> all(DailyLedger ledger){Map<String,Template> result=new TreeMap<>(datapacks);result.putAll(files);result.putAll(ledger.overrides);return List.copyOf(result.values());}
    public static List<Template> available(ServerPlayer player,DailyLedger ledger){
        DailyLedger.Account account=ledger.account(player.getUUID());List<Template> result=new ArrayList<>();
        for(Template template:all(ledger)){
            if(!template.enabled()||template.mods().stream().anyMatch(m->!ModList.get().isLoaded(m))||template.dimensions().stream().anyMatch(d->player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(d)))==null))continue;
            if(template.biomes().stream().anyMatch(b->!player.registryAccess().registryOrThrow(Registries.BIOME).containsKey(ResourceLocation.parse(b))))continue;
            if(template.category()==Category.EXPLORATION){
                if(!template.dimensions().isEmpty()&&Collections.disjoint(template.dimensions(),account.dimensions))continue;
                boolean biome=template.validation()==Validation.NEW_BIOME;
                List<String> candidates=exploration(player,biome,template,account);
                if(template.target().startsWith("#")){
                    ResourceLocation tag=ResourceLocation.parse(template.target().substring(1));Set<String> tagged=new HashSet<>();
                    if(biome)player.registryAccess().registryOrThrow(Registries.BIOME).getTag(TagKey.create(Registries.BIOME,tag)).ifPresent(set->set.forEach(holder->holder.unwrapKey().ifPresent(k->tagged.add(k.location().toString()))));
                    else player.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(TagKey.create(Registries.STRUCTURE,tag)).ifPresent(set->set.forEach(holder->holder.unwrapKey().ifPresent(k->tagged.add(k.location().toString()))));
                    candidates.removeIf(id->!tagged.contains(id));
                }
                if(template.target().startsWith("*")||template.target().startsWith("#")){
                    // One deterministic target per template/player/day; the catalogue never rerolls an accepted quest.
                    if(candidates.isEmpty())continue;Collections.sort(candidates);long seed=ledger.salt.getLeastSignificantBits()^player.getUUID().getMostSignificantBits()^ledger.settings.day(Math.max(ledger.highWater,System.currentTimeMillis()))^template.id().hashCode();String target=candidates.get(new Random(seed).nextInt(candidates.size()));
                    result.add(new Template(template.id(),template.title(),template.category(),template.difficulty(),target,template.quantity(),template.reward(),template.weight(),template.mods(),template.dimensions(),template.biomes(),true,template.validation()));
                }else if(candidates.contains(template.target()))result.add(template);
            }else if(supported(player.getServer(),template))result.add(template);
        }return result;
    }
    private static List<String> exploration(ServerPlayer player,boolean biome,Template template,DailyLedger.Account account){
        Set<String> candidates=new HashSet<>();
        for(var level:player.getServer().getAllLevels()){
            String dimension=level.dimension().location().toString();if(!account.dimensions.contains(dimension)||!template.dimensions().isEmpty()&&!template.dimensions().contains(dimension))continue;
            var possible=level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
            if(biome){for(var holder:possible)holder.unwrapKey().ifPresent(k->{String id=k.location().toString();if(!account.biomes.contains(id))candidates.add(id);});}
            else if(player.getServer().getWorldData().worldGenOptions().generateStructures())for(var set:level.getChunkSource().getGeneratorState().possibleStructureSets())for(var selection:set.value().structures()){
                var holder=selection.structure();String id=holder.unwrapKey().orElseThrow().location().toString();if(!account.structures.contains(id)&&holder.value().biomes().stream().anyMatch(possible::contains))candidates.add(id);
            }
        }return new ArrayList<>(candidates);
    }
    public static boolean supported(MinecraftServer server,Template t){
        String target=t.target();if(target.startsWith("*"))return switch(t.category()){case COMBAT->target.equals("*hostile");case FISHING->target.equals("*fish")||target.equals("*treasure");default->false;};
        boolean tag=target.startsWith("#");ResourceLocation id=ResourceLocation.parse(tag?target.substring(1):target);
        return switch(t.category()){
            case MINING,FARMING -> tag?BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK,id)).map(v->v.size()>0).orElse(false):BuiltInRegistries.BLOCK.containsKey(id);
            case COMBAT -> tag?BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE,id)).map(v->v.size()>0).orElse(false):BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            case FISHING,DELIVERY -> tag?BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,id)).map(v->v.size()>0).orElse(false):BuiltInRegistries.ITEM.containsKey(id);
            case EXPLORATION -> false;
        };
    }
}
