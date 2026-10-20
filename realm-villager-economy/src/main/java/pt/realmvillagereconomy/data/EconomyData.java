package pt.realmvillagereconomy.data;

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
    private CompoundTag root;private final Path path;private boolean frozen;
    private EconomyData(Path path,CompoundTag root){this.path=path;this.root=root;validate(root);}
    public static EconomyData get(MinecraftServer server){
        if(server==null||!server.isSameThread())throw new IllegalStateException("Server thread required");
        return LOADED.computeIfAbsent(server,s->{var file=s.getWorldPath(LevelResource.ROOT).resolve("data/realm_villager_economy.dat");try{return new EconomyData(file,Files.notExists(file)?fresh():NbtIo.readCompressed(file,NbtAccounter.create(64L*1024*1024)));}catch(Exception error){throw new IllegalStateException("error.realmvillagereconomy.storage",error);}});
    }
    public static void forget(MinecraftServer server){LOADED.remove(server);}
    private static CompoundTag fresh(){var tag=new CompoundTag();tag.putInt("version",1);tag.putLong("revision",0);tag.putInt("rate",0);tag.put("merchants",new CompoundTag());tag.put("outbox",new CompoundTag());tag.put("history",new ListTag());return tag;}
    public static void validate(CompoundTag tag){
        if(!tag.contains("version",Tag.TAG_INT)||tag.getInt("version")!=1||!tag.contains("revision",Tag.TAG_LONG)||tag.getLong("revision")<0||!tag.contains("rate",Tag.TAG_INT)||tag.getInt("rate")<0||tag.getInt("rate")>10000||!tag.contains("merchants",Tag.TAG_COMPOUND)||!tag.contains("outbox",Tag.TAG_COMPOUND)||!tag.contains("history",Tag.TAG_LIST))throw new IllegalStateException("Corrupt villager economy data");
        if(tag.getCompound("merchants").size()>16384||tag.getCompound("outbox").size()>65536||tag.getList("history",Tag.TAG_COMPOUND).size()>512)throw new IllegalStateException("Villager ledger capacity exceeded");
        for(String id:tag.getCompound("merchants").getAllKeys()){UUID.fromString(id);var m=tag.getCompound("merchants").getCompound(id);if(m.getLong("revision")<1||!m.contains("offers",Tag.TAG_LIST)||m.getList("offers",Tag.TAG_COMPOUND).size()>256||m.getInt("xp")<0)throw new IllegalStateException("Invalid merchant revision");}
        for(String id:tag.getCompound("outbox").getAllKeys()){UUID.fromString(id);var e=tag.getCompound("outbox").getCompound(id);if(!e.hasUUID("payer")||e.getLong("amount")<0||e.getLong("amount")==0&&!e.getBoolean("policy")||e.getLong("at")<0||e.getInt("rate")<0||e.getInt("rate")>10000)throw new IllegalStateException("Invalid funded tax receipt");}
        if(tag.contains("journal")){var j=tag.getCompound("journal");
            for(String key:java.util.List.of("before","after","base","tax","at"))if(!j.contains(key,Tag.TAG_LONG))throw new IllegalStateException("Missing trade monetary field");
            if(!j.contains("rate",Tag.TAG_INT)||j.getInt("rate")<0||j.getInt("rate")>10000||j.getLong("base")<0||j.getLong("tax")<0||j.getLong("at")<0||j.getLong("before")!=j.getLong("after")&&Math.subtractExact(j.getLong("before"),j.getLong("after"))!=Math.addExact(j.getLong("base"),j.getLong("tax"))||j.getLong("before")==j.getLong("after")&&(j.getLong("base")!=0||j.getLong("tax")!=0))throw new IllegalStateException("Invalid trade monetary conservation");
            if(j.getList("inventoryBefore",Tag.TAG_COMPOUND).size()!=36||j.getList("inventoryAfter",Tag.TAG_COMPOUND).size()!=36)throw new IllegalStateException("Incomplete inventory transaction");
            var overlay=j.getCompound("overlay");if(overlay.getLong("revision")<1||!overlay.contains("offers",Tag.TAG_LIST)||overlay.getInt("xp")<0)throw new IllegalStateException("Invalid proposed stock revision");
if(!j.hasUUID("id")||!j.hasUUID("player")||!j.hasUUID("merchant")||!j.contains("before",Tag.TAG_LONG)||!j.contains("after",Tag.TAG_LONG)||j.getLong("before")< -1||j.getLong("after")< -1||j.getLong("after")>j.getLong("before")||!j.contains("inventoryBefore",Tag.TAG_LIST)||!j.contains("inventoryAfter",Tag.TAG_LIST)||!j.contains("overlay",Tag.TAG_COMPOUND))throw new IllegalStateException("Invalid trade intent");}
    }
    public Path path(){return path;}
    public CompoundTag snapshot(){return root.copy();}
    public int rate(){return root.getInt("rate");}
    public long revision(){return root.getLong("revision");}
    public CompoundTag journal(){return root.contains("journal")?root.getCompound("journal").copy():null;}
    public CompoundTag merchant(UUID id){return root.getCompound("merchants").getCompound(id.toString()).copy();}
    public CompoundTag outbox(){return root.getCompound("outbox").copy();}
    public ListTag history(){return root.getList("history",Tag.TAG_COMPOUND).copy();}
    public void requireAvailable(){if(frozen||root.contains("journal"))throw new IllegalStateException("error.realmvillagereconomy.frozen");}
    public void freeze(){frozen=true;}
    public void prepare(CompoundTag journal){requireAvailable();var next=root.copy();next.put("journal",journal.copy());write(next);}
    public void complete(UUID receipt){
        var journal=journal();if(journal==null||!journal.getUUID("id").equals(receipt))throw new IllegalStateException("Wrong trade receipt");
        var next=root.copy();next.getCompound("merchants").put(journal.getUUID("merchant").toString(),journal.getCompound("overlay").copy());
        if(journal.getLong("tax")>0){var tax=new CompoundTag();tax.putUUID("payer",journal.getUUID("player"));tax.putLong("amount",journal.getLong("tax"));tax.putLong("at",journal.getLong("at"));tax.putInt("rate",journal.getInt("rate"));tax.putBoolean("policy",false);next.getCompound("outbox").put(receipt.toString(),tax);}
        next.remove("journal");write(next);frozen=false;
    }
    public void policy(UUID actor,String name,int rate,boolean treasury){
        requireAvailable();if(rate==this.rate())return;if(rate<0||rate>10000)throw new IllegalArgumentException("Invalid tax percentage");
        var next=root.copy();long at=System.currentTimeMillis();next.putInt("rate",rate);next.putLong("revision",Math.addExact(revision(),1));
        var event=new CompoundTag();event.putUUID("payer",actor);event.putString("name",name);event.putInt("rate",rate);event.putInt("previous",this.rate());event.putLong("at",at);event.putLong("amount",0);event.putBoolean("policy",true);
        var history=next.getList("history",Tag.TAG_COMPOUND);history.add(0,event.copy());while(history.size()>512)history.remove(history.size()-1);
        next.getCompound("outbox").put(UUID.randomUUID().toString(),event.copy());write(next);
    }
    public void delivered(UUID receipt){requireAvailable();var next=root.copy();if(!next.getCompound("outbox").contains(receipt.toString()))throw new IllegalStateException("Missing funded receipt");next.getCompound("outbox").remove(receipt.toString());write(next);}
    private void write(CompoundTag next){
        validate(next);Path temp=null;
        try{Files.createDirectories(path.getParent());temp=Files.createTempFile(path.getParent(),"villager-economy-",".tmp");NbtIo.writeCompressed(next,temp);DurableFiles.forceFile(temp);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);temp=null;root=next;DurableFiles.forceDirectory(path.getParent());}
        catch(Exception error){frozen=true;throw new IllegalStateException("error.realmvillagereconomy.storage",error);}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(java.io.IOException ignored){}}
    }
}
