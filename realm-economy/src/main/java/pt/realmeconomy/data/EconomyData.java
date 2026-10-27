package pt.realmeconomy.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** One durable intent binds the wallet, the exact inventory and an authoritative merchant revision. */
public final class EconomyData {
    private static final Map<MinecraftServer,EconomyData> LOADED=new WeakHashMap<>();
    private CompoundTag root;private final Path path;private boolean frozen;private pt.realmeconomy.colony.ColonyLedger.State cachedColonies;private Tag cachedColonyTag;
    private EconomyData(Path path,CompoundTag root){this.path=path;this.root=upgrade(root);validate(this.root);}
    public static EconomyData get(MinecraftServer server){
        if(server==null||!server.isSameThread())throw new IllegalStateException("Server thread required");
        return LOADED.computeIfAbsent(server,s->{var file=s.getWorldPath(LevelResource.ROOT).resolve("data/realm_economy.dat");try{return new EconomyData(file,Files.exists(file)?NbtIo.readCompressed(file,NbtAccounter.create(64L*1024*1024)):Files.exists(file.resolveSibling("realm_villager_economy.dat"))?NbtIo.readCompressed(file.resolveSibling("realm_villager_economy.dat"),NbtAccounter.create(64L*1024*1024)):fresh());}catch(Exception error){throw new IllegalStateException("error.realmeconomy.storage",error);}});
    }
    public static void forget(MinecraftServer server){LOADED.remove(server);}
    private static CompoundTag fresh(){var tag=new CompoundTag();tag.putInt("version",1);tag.putLong("revision",0);tag.putInt("rate",0);tag.put("merchants",new CompoundTag());tag.put("outbox",new CompoundTag());tag.put("history",new ListTag());return upgrade(tag);}
    public static void validate(CompoundTag tag){
        if(!tag.contains("version",Tag.TAG_INT)||tag.getInt("version")!=3||!tag.contains("revision",Tag.TAG_LONG)||tag.getLong("revision")<0||!tag.contains("rate",Tag.TAG_INT)||tag.getInt("rate")<0||tag.getInt("rate")>10000||!tag.contains("merchants",Tag.TAG_COMPOUND)||!tag.contains("outbox",Tag.TAG_COMPOUND)||!tag.contains("history",Tag.TAG_LIST))throw new IllegalStateException("Corrupt villager economy data");
        if(tag.getCompound("merchants").size()>16384||tag.getCompound("outbox").size()>65536||tag.getList("history",Tag.TAG_COMPOUND).size()>512)throw new IllegalStateException("Villager ledger capacity exceeded");
        for(String id:tag.getCompound("merchants").getAllKeys()){UUID.fromString(id);var m=tag.getCompound("merchants").getCompound(id);if(m.getLong("revision")<1||!m.contains("offers",Tag.TAG_LIST)||m.getList("offers",Tag.TAG_COMPOUND).size()>256||m.getInt("xp")<0)throw new IllegalStateException("Invalid merchant revision");}
        for(String id:tag.getCompound("outbox").getAllKeys()){UUID.fromString(id);var e=tag.getCompound("outbox").getCompound(id);if(!e.hasUUID("payer")||e.getLong("amount")<0||e.getLong("amount")==0&&!e.getBoolean("policy")||e.getLong("at")<0||e.getInt("rate")<0||e.getInt("rate")>10000)throw new IllegalStateException("Invalid funded tax receipt");}
        if(!tag.hasUUID("colonySalt"))throw new IllegalStateException("Missing colony generation salt");
        var colonyState=pt.realmeconomy.colony.ColonyCodec.read(tag,"colonies");
        validateCollections(tag);for(var kind:pt.realmeconomy.domain.ServiceKind.values())pt.realmeconomy.domain.EconomyRule.decode(tag.getCompound("rules").getString(kind.name()));
        if(tag.getInt("beaconSeconds")<10||tag.getInt("beaconSeconds")>86400||tag.getCompound("jobs").size()>65536||tag.getCompound("beacons").size()>16384||tag.getCompound("fuel").size()>65536||tag.getList("audit",Tag.TAG_COMPOUND).size()>512)throw new IllegalStateException("Economic state capacity exceeded");
        if(tag.contains("journal")){var j=tag.getCompound("journal");boolean colony=j.contains("colonyOperation");if(colony)pt.realmeconomy.colony.ColonyTransactions.validate(j,colonyState);if(j.contains("seedAfter")&&(!j.contains("seedAfter",Tag.TAG_INT)||!j.contains("seedBefore",Tag.TAG_INT)))throw new IllegalStateException("Incomplete seed mutation");if(j.contains("mutations")){var proposed=tag.copy();applyMutations(proposed,j.getList("mutations",Tag.TAG_COMPOUND));validateCollections(proposed);}

            for(String key:java.util.List.of("before","after","base","tax","at"))if(!j.contains(key,Tag.TAG_LONG))throw new IllegalStateException("Missing trade monetary field");
            if(!colony&&(!j.contains("rate",Tag.TAG_INT)||j.getInt("rate")<0||j.getInt("rate")>10000||j.getLong("base")<0||j.getLong("tax")<0||j.getLong("at")<0||j.getLong("before")!=j.getLong("after")&&Math.subtractExact(j.getLong("before"),j.getLong("after"))!=Math.addExact(j.getLong("base"),j.getLong("tax"))||j.getLong("before")==j.getLong("after")&&(j.getLong("base")!=0||j.getLong("tax")!=0)))throw new IllegalStateException("Invalid trade monetary conservation");
            if(j.getList("inventoryBefore",Tag.TAG_COMPOUND).size()!=36||j.getList("inventoryAfter",Tag.TAG_COMPOUND).size()!=36)throw new IllegalStateException("Incomplete inventory transaction");
            if(!j.contains("mutations")){var overlay=j.getCompound("overlay");if(overlay.getLong("revision")<1||!overlay.contains("offers",Tag.TAG_LIST)||overlay.getInt("xp")<0)throw new IllegalStateException("Invalid proposed stock revision");}
if(!j.hasUUID("id")||!j.hasUUID("player")||!j.contains("mutations")&&!j.hasUUID("merchant")||!j.contains("before",Tag.TAG_LONG)||!j.contains("after",Tag.TAG_LONG)||j.getLong("before")< -1||j.getLong("after")< -1||!colony&&j.getLong("after")>j.getLong("before")||!j.contains("inventoryBefore",Tag.TAG_LIST)||!j.contains("inventoryAfter",Tag.TAG_LIST)||!j.contains("mutations")&&!j.contains("overlay",Tag.TAG_COMPOUND))throw new IllegalStateException("Invalid trade intent");}
    }
    public pt.realmeconomy.colony.ColonyLedger.State colonies(){Tag tag=root.get("colonies");if(cachedColonies==null||tag!=cachedColonyTag){cachedColonies=pt.realmeconomy.colony.ColonyCodec.read(root,"colonies");cachedColonyTag=tag;}return cachedColonies;}
    public UUID colonySalt(){return root.getUUID("colonySalt");}
    public void colonies(pt.realmeconomy.colony.ColonyLedger.State state){requireAvailable();if(state.equals(colonies()))return;var next=root.copy();pt.realmeconomy.colony.ColonyCodec.write(next,"colonies",state);next.putLong("revision",Math.addExact(revision(),1));write(next);}
    public void colonyPolicy(UUID actor,String name,pt.realmeconomy.colony.ColonyLedger.Policy policy){requireAvailable();var state=colonies();if(state.policy().equals(policy))return;var next=root.copy();pt.realmeconomy.colony.ColonyCodec.write(next,"colonies",state.policy(policy,System.currentTimeMillis()));appendAudit(next,auditEvent(actor,name,"COLONY_POLICY",pt.realmeconomy.colony.ColonyLedger.JSON.toJson(state.policy()),pt.realmeconomy.colony.ColonyLedger.JSON.toJson(policy)));next.putLong("revision",Math.addExact(revision(),1));write(next);}
    public Path path(){return path;}
    public CompoundTag snapshot(){return root.copy();}
    public int rate(){return rule(pt.realmeconomy.domain.ServiceKind.VILLAGER).tax();}
    public long revision(){return root.getLong("revision");}
    public CompoundTag journal(){return root.contains("journal")?root.getCompound("journal").copy():null;}
    public CompoundTag merchant(UUID id){return root.getCompound("merchants").getCompound(id.toString()).copy();}
    public CompoundTag outbox(){return root.getCompound("outbox").copy();}
    public ListTag history(){return root.getList("history",Tag.TAG_COMPOUND).copy();}
    public void requireAvailable(){if(frozen||root.contains("journal"))throw new IllegalStateException("error.realmeconomy.frozen");}
    public void freeze(){frozen=true;}
    public void prepare(CompoundTag journal){requireAvailable();var next=root.copy();next.put("journal",journal.copy());write(next);}
    public void complete(UUID receipt){
        var journal=journal();if(journal==null||!journal.getUUID("id").equals(receipt))throw new IllegalStateException("Wrong trade receipt");
        var next=root.copy();if(journal.contains("mutations"))applyMutations(next,journal.getList("mutations",Tag.TAG_COMPOUND));else next.getCompound("merchants").put(journal.getUUID("merchant").toString(),journal.getCompound("overlay").copy());
        if(journal.getLong("tax")>0){var tax=new CompoundTag();tax.putUUID("payer",journal.getUUID("player"));tax.putLong("amount",journal.getLong("tax"));tax.putLong("at",journal.getLong("at"));tax.putInt("rate",journal.getInt("rate"));tax.putBoolean("policy",false);if(journal.contains("service"))tax.putString("service",journal.getString("service"));next.getCompound("outbox").put(receipt.toString(),tax);}
        if(journal.contains("colonyOperation"))next.put("colonies",journal.get("colonyAfter").copy());
        next.remove("journal");write(next);frozen=false;
    }
    public void policy(UUID actor,String name,int rate,boolean treasury){changeRule(actor,name,pt.realmeconomy.domain.ServiceKind.VILLAGER,rule(pt.realmeconomy.domain.ServiceKind.VILLAGER).price(rule(pt.realmeconomy.domain.ServiceKind.VILLAGER).value(),rate));}
    public pt.realmeconomy.domain.EconomyRule rule(pt.realmeconomy.domain.ServiceKind kind){return pt.realmeconomy.domain.EconomyRule.decode(root.getCompound("rules").getString(kind.name()));}
    public CompoundTag collection(String map){if(!java.util.Set.of("jobs","fuel","beacons").contains(map))throw new IllegalArgumentException("Unknown economic collection");return root.getCompound(map).copy();}
    public CompoundTag entry(String map,String key){if(!java.util.Set.of("jobs","fuel","beacons","outbox").contains(map))throw new IllegalArgumentException("Unknown economic collection");return root.getCompound(map).getCompound(key).copy();}
    public CompoundTag rulesSnapshot(){return root.getCompound("rules").copy();}
    public ListTag audit(){return root.getList("audit",Tag.TAG_COMPOUND).copy();}
    public int beaconSeconds(){return root.getInt("beaconSeconds");}
    public void beaconSeconds(UUID actor,String name,int seconds){
        requireAvailable();if(seconds==beaconSeconds())return;if(seconds<10||seconds>86400)throw new IllegalArgumentException("Invalid beacon period");
        var next=root.copy();var event=auditEvent(actor,name,"BEACON_PERIOD",Integer.toString(beaconSeconds()),Integer.toString(seconds));next.putInt("beaconSeconds",seconds);appendAudit(next,event);next.getCompound("outbox").put(UUID.randomUUID().toString(),economicOutbox(event,actor,"BEACON_PERIOD",0));next.putLong("revision",Math.addExact(revision(),1));write(next);
    }
    public void changeRule(UUID actor,String name,pt.realmeconomy.domain.ServiceKind kind,pt.realmeconomy.domain.EconomyRule rule){changeRules(actor,name,java.util.Map.of(kind,rule));}
    public void changeRules(UUID actor,String name,java.util.Map<pt.realmeconomy.domain.ServiceKind,pt.realmeconomy.domain.EconomyRule> changes){
        requireAvailable();var next=root.copy();boolean changed=false;
        for(var change:changes.entrySet()){
            var kind=change.getKey();var rule=change.getValue();var old=rule(kind);if(old.equals(rule))continue;changed=true;
            next.getCompound("rules").putString(kind.name(),rule.encode());if(kind==pt.realmeconomy.domain.ServiceKind.VILLAGER)next.putInt("rate",rule.tax());
            var event=auditEvent(actor,name,kind.name(),old.encode(),rule.encode());appendAudit(next,event);var pending=economicOutbox(event,actor,kind.name(),rule.tax());next.getCompound("outbox").put(UUID.randomUUID().toString(),pending);
            if(kind==pt.realmeconomy.domain.ServiceKind.VILLAGER){var h=next.getList("history",Tag.TAG_COMPOUND);h.add(0,pending.copy());while(h.size()>512)h.remove(h.size()-1);}
        }
        if(changed){next.putLong("revision",Math.addExact(revision(),1));write(next);}
    }
    private static CompoundTag economicOutbox(CompoundTag event,UUID actor,String kind,int rate){var p=event.copy();p.putUUID("payer",actor);p.putLong("amount",0);p.putInt("rate",rate);p.putBoolean("policy",true);p.putBoolean("economic",true);p.putString("service",kind);return p;}
    public void markNotice(String map,String key){requireAvailable();var next=root.copy();if(!next.getCompound(map).contains(key))return;next.getCompound(map).getCompound(key).putBoolean("notified",true);write(next);}
    private static CompoundTag auditEvent(UUID actor,String name,String service,String old,String value){var e=new CompoundTag();e.putUUID("actor",actor);e.putString("name",name);e.putString("service",service);e.putString("old",old);e.putString("next",value);e.putLong("at",System.currentTimeMillis());return e;}
    private static void appendAudit(CompoundTag next,CompoundTag event){var a=next.getList("audit",Tag.TAG_COMPOUND);a.add(0,event);while(a.size()>512)a.remove(a.size()-1);}
    private static CompoundTag upgrade(CompoundTag source){
        var tag=source.copy();int v=tag.getInt("version");if(v<3&&tag.contains("colonies")&&(!pt.realmeconomy.colony.ColonyCodec.read(tag,"colonies").accounts().isEmpty()||tag.getCompound("journal").contains("colonyOperation")))throw new IllegalStateException("Refusing downgraded colony ledger");if(v==3)return tag;if(v==2){pt.realmeconomy.colony.ColonyCodec.write(tag,"colonies",pt.realmeconomy.colony.ColonyLedger.State.empty());tag.putUUID("colonySalt",UUID.randomUUID());tag.putInt("version",3);return tag;}if(v!=1)throw new IllegalStateException("Unsupported Realm Economy data");
        var rules=new CompoundTag();var config=EconomyConfig.get();for(var kind:pt.realmeconomy.domain.ServiceKind.values()){
            long value=kind==pt.realmeconomy.domain.ServiceKind.VILLAGER?config.centsPerEmerald():kind.defaultCents;
            int tax=kind==pt.realmeconomy.domain.ServiceKind.VILLAGER?Math.min(tag.getInt("rate"),config.maximumTaxBasisPoints()):0;
            rules.putString(kind.name(),new pt.realmeconomy.domain.EconomyRule(value,kind==pt.realmeconomy.domain.ServiceKind.VILLAGER?1:0,Math.max(1_000_000L,value),tax,config.maximumTaxBasisPoints(),true).encode());
        }tag.put("rules",rules);tag.put("jobs",new CompoundTag());tag.put("fuel",new CompoundTag());tag.put("beacons",new CompoundTag());tag.put("audit",new ListTag());tag.putInt("beaconSeconds",600);tag.putInt("version",2);return upgrade(tag);
    }
    private static void validateCollections(CompoundTag root){
        for(String map:java.util.List.of("jobs","fuel","beacons"))if(!root.contains(map,Tag.TAG_COMPOUND))throw new IllegalStateException("Missing economic collection");
        if(root.getCompound("jobs").size()>65536||root.getCompound("fuel").size()>65536||root.getCompound("beacons").size()>16384)throw new IllegalStateException("Economic capacity exceeded");
        for(String key:root.getCompound("jobs").getAllKeys()){UUID.fromString(key);var job=root.getCompound("jobs").getCompound(key);if(!job.hasUUID("owner")||!job.contains("readyAt",Tag.TAG_LONG)||job.getLong("readyAt")<0||job.getList("items",Tag.TAG_COMPOUND).size()<1||job.getList("items",Tag.TAG_COMPOUND).size()>3)throw new IllegalStateException("Invalid brewing escrow");}
        for(String key:root.getCompound("fuel").getAllKeys()){UUID.fromString(key);var fuel=root.getCompound("fuel").getCompound(key);if(!fuel.contains("units",Tag.TAG_INT)||fuel.getInt("units")<0||fuel.getInt("units")>19)throw new IllegalStateException("Invalid brewing fuel credit");}
        for(String key:root.getCompound("beacons").getAllKeys()){var lease=root.getCompound("beacons").getCompound(key);if(!lease.hasUUID("owner")||!lease.contains("until",Tag.TAG_LONG)||lease.getLong("until")<0||lease.getInt("primary")<0||lease.getInt("primary")>4||lease.getInt("secondary")<0||lease.getInt("secondary")>2)throw new IllegalStateException("Invalid beacon lease");}
    }
    private static void applyMutations(CompoundTag root,ListTag mutations){
        if(mutations.size()>8)throw new IllegalStateException("Too many economic mutations");
        for(Tag raw:mutations){var m=(CompoundTag)raw;String map=m.getString("map"),key=m.getString("key");if(!java.util.Set.of("jobs","fuel","beacons").contains(map)||key.isBlank()||key.length()>180)throw new IllegalStateException("Invalid economic mutation");
            if(m.getBoolean("remove"))root.getCompound(map).remove(key);else{if(!m.contains("value",Tag.TAG_COMPOUND))throw new IllegalStateException("Missing economic mutation");root.getCompound(map).put(key,m.getCompound("value").copy());}
        }
    }
    public void delivered(UUID receipt){requireAvailable();var next=root.copy();if(!next.getCompound("outbox").contains(receipt.toString()))throw new IllegalStateException("Missing funded receipt");next.getCompound("outbox").remove(receipt.toString());write(next);}
    private void write(CompoundTag next){
        validate(next);Path temp=null;
        try{Files.createDirectories(path.getParent());temp=Files.createTempFile(path.getParent(),"villager-economy-",".tmp");NbtIo.writeCompressed(next,temp);DurableFiles.forceFile(temp);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);temp=null;root=next;DurableFiles.forceDirectory(path.getParent());}
        catch(Exception error){frozen=true;throw new IllegalStateException("error.realmeconomy.storage",error);}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(java.io.IOException ignored){}}
    }
}
