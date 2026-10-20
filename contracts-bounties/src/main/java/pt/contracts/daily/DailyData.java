package pt.contracts.daily;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

/** Separate storage leaves the v1/v2 contract, credit and material ledgers untouched. */
public final class DailyData extends SavedData {
    public static final String KEY="contracts_daily_quests";
    public DailyLedger ledger=new DailyLedger();
    private final Map<UUID,CompoundTag> journals=new LinkedHashMap<>();
    private boolean frozen;
    public static DailyData get(MinecraftServer server){
        if(server==null||!server.isSameThread())throw new IllegalStateException("Daily quests require the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(()->{
            if(Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("data/"+KEY+".dat")))throw new IllegalStateException("Unreadable daily quest ledger; preserve the world backup");
            return new DailyData();
        },DailyData::load),KEY);
    }
    public static DailyData load(CompoundTag tag,HolderLookup.Provider lookup){
        if(!tag.contains("version",Tag.TAG_INT)||tag.getInt("version")!=1||!tag.contains("payload",Tag.TAG_BYTE_ARRAY)||!(tag.get("journals") instanceof ListTag))throw new IllegalStateException("Invalid daily quest storage");
        byte[] bytes=tag.getByteArray("payload");if(bytes.length>64_000_000)throw new IllegalStateException("Oversized daily quest storage");
        DailyData data=new DailyData();data.ledger=DailyLedger.fromJson(new String(bytes,StandardCharsets.UTF_8));
        ListTag list=(ListTag)tag.get("journals");if(list.size()>DailyLedger.MAX_PLAYERS||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException("Invalid daily journals");
        for(int i=0;i<list.size();i++){
            CompoundTag intent=list.getCompound(i);if(!intent.hasUUID("player")||!intent.hasUUID("id")||!intent.contains("delivery",Tag.TAG_BYTE))throw new IllegalStateException("Invalid daily journal identity");
            for(String key:List.of("inventoryBefore","inventoryAfter"))if(!(intent.get(key)instanceof ListTag slots)||slots.size()>36||!slots.isEmpty()&&slots.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalStateException("Invalid daily inventory journal");
            UUID player=intent.getUUID("player"),id=intent.getUUID("id");
            DailyLedger.Reward reward=data.ledger.rewards.get(id);
            if(reward==null||!reward.prepared||!reward.player.equals(player)||reward.paid||data.journals.putIfAbsent(player,intent.copy())!=null)throw new IllegalStateException("Invalid daily reward journal");
            if(!intent.getBoolean("delivery")&&reward.currency.equals("CHRONICLES")&&(!intent.contains("profileBefore",Tag.TAG_COMPOUND)||!intent.contains("profileAfter",Tag.TAG_COMPOUND)))throw new IllegalStateException("Missing daily wallet journal");
        }
        for(var reward:data.ledger.rewards.values())if(reward.prepared&&(!data.journals.containsKey(reward.player)||!data.journals.get(reward.player).getUUID("id").equals(reward.id)))throw new IllegalStateException("Daily prepared receipt lost its journal");
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        ledger.validate();byte[] json=DailyLedger.JSON.toJson(ledger).getBytes(StandardCharsets.UTF_8);if(json.length>64_000_000)throw new IllegalStateException("Daily quest ledger capacity reached");
        tag.putInt("version",1);tag.putByteArray("payload",json);ListTag list=new ListTag();journals.values().forEach(j->list.add(j.copy()));tag.put("journals",list);return tag;
    }
    public void available(){if(frozen)throw new IllegalStateException("daily.contracts.storage_error");}
    public CompoundTag journal(UUID player){CompoundTag intent=journals.get(player);return intent==null?null:intent.copy();}
    public void prepare(MinecraftServer server,CompoundTag intent){available();UUID player=intent.getUUID("player");if(journals.putIfAbsent(player,intent.copy())!=null)throw new IllegalStateException("daily.contracts.payment_pending");ledger.rewards.get(intent.getUUID("id")).prepared=true;persist(server);}
    public void finish(MinecraftServer server,UUID player,UUID id,boolean delivery){
        CompoundTag intent=journals.get(player);if(intent==null||!intent.getUUID("id").equals(id))throw new IllegalStateException("Mismatched daily journal");
        ledger.rewards.get(id).prepared=false;if(delivery)ledger.rewards.get(id).deliveryConfirmed=true;else ledger.paid(id);
        journals.remove(player);persist(server);
    }
    public void persist(MinecraftServer server){
        available();
        try {
            CompoundTag root=new CompoundTag();root.put("data",save(new CompoundTag(),server.registryAccess()));NbtUtils.addCurrentDataVersion(root);
            Path target=server.getWorldPath(LevelResource.ROOT).resolve("data/"+KEY+".dat");AtomicReference<Throwable> failure=new AtomicReference<>();
            IOUtilities.withIOWorker(()->{try{write(target,root);}catch(Throwable error){failure.set(error);}});IOUtilities.waitUntilIOWorkerComplete();
            if(failure.get()!=null)throw new IllegalStateException("Daily checkpoint failed",failure.get());setDirty(false);
        } catch(RuntimeException|LinkageError error) {
            frozen=true;throw new IllegalStateException("daily.contracts.storage_error",error);
        }
    }
    private static void write(Path file,CompoundTag root)throws java.io.IOException{
        Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"daily-quest-",".tmp");
        try{NbtIo.writeCompressed(root,temp);force(temp);Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);directory(file.getParent());}finally{Files.deleteIfExists(temp);}
    }
    public static void force(Path file)throws java.io.IOException{try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}}
    public static void directory(Path path)throws java.io.IOException{if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))try(FileChannel dir=FileChannel.open(path,StandardOpenOption.READ)){dir.force(true);}}
}
