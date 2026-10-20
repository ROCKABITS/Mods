package pt.realmmarriage.data;

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
import pt.realmmarriage.domain.MarriageLedger;

/** Atomic intent ledger; an unreadable existing registry is never recreated as empty. */
public final class MarriageData extends SavedData {
    public static final String KEY="realm_marriage";
    public MarriageLedger ledger=new MarriageLedger();
    public final Map<UUID,CompoundTag> journals=new LinkedHashMap<>();
    private boolean frozen;
    public static MarriageData get(MinecraftServer server){
        if(server==null||!server.isSameThread())throw new IllegalStateException("marriage.error.thread");
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(()->{
            if(!Files.notExists(server.getWorldPath(LevelResource.ROOT).resolve("data/"+KEY+".dat")))throw new IllegalStateException("marriage.error.storage");
            return new MarriageData();
        },MarriageData::load),KEY);
    }
    public static MarriageData load(CompoundTag root,HolderLookup.Provider lookup){
        MarriageLedger.require(root.getInt("version")==1&&root.contains("payload",Tag.TAG_BYTE_ARRAY)&&root.get("journals")instanceof ListTag,"storage");
        byte[] payload=root.getByteArray("payload");MarriageLedger.require(payload.length<=64000000,"storage");MarriageData data=new MarriageData();data.ledger=MarriageLedger.fromJson(new String(payload,StandardCharsets.UTF_8));
        ListTag list=root.getList("journals",Tag.TAG_COMPOUND);MarriageLedger.require(list.size()<=10000,"storage");
        for(var row:list){CompoundTag intent=(CompoundTag)row;MarriageLedger.require(intent.hasUUID("id")&&intent.hasUUID("player")&&intent.get("inventoryBefore")instanceof ListTag&&intent.get("inventoryAfter")instanceof ListTag,"storage");
            UUID player=intent.getUUID("player");var op=data.ledger.operations.get(intent.getUUID("id"));MarriageLedger.require(op!=null&&op.actor.equals(player)&&op.state==MarriageLedger.OpState.PREPARED&&data.journals.putIfAbsent(player,intent.copy())==null,"storage");
            if(op.kind!=MarriageLedger.Kind.DOCUMENTS&&op.amount>0)MarriageLedger.require(intent.contains("profileBefore",Tag.TAG_COMPOUND)&&intent.contains("profileAfter",Tag.TAG_COMPOUND),"storage");
        }
        for(var o:data.ledger.operations.values())if(o.state==MarriageLedger.OpState.PREPARED)MarriageLedger.require(data.journals.containsKey(o.actor)&&data.journals.get(o.actor).getUUID("id").equals(o.id),"storage");return data;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){ledger.validate();byte[] payload=MarriageLedger.JSON.toJson(ledger).getBytes(StandardCharsets.UTF_8);MarriageLedger.require(payload.length<=64000000,"capacity");root.putInt("version",1);root.putByteArray("payload",payload);ListTag list=new ListTag();journals.values().forEach(j->list.add(j.copy()));root.put("journals",list);return root;}
    public void available(){MarriageLedger.require(!frozen,"storage");}
    public void persist(MinecraftServer server){available();try{
        CompoundTag root=new CompoundTag();root.put("data",save(new CompoundTag(),server.registryAccess()));NbtUtils.addCurrentDataVersion(root);
        Path file=server.getWorldPath(LevelResource.ROOT).resolve("data/"+KEY+".dat");AtomicReference<Throwable> failure=new AtomicReference<>();
        IOUtilities.withIOWorker(()->{try{write(file,root);}catch(Throwable e){failure.set(e);}});IOUtilities.waitUntilIOWorkerComplete();if(failure.get()!=null)throw new IllegalStateException(failure.get());setDirty(false);
    }catch(RuntimeException|LinkageError failure){frozen=true;throw new IllegalStateException("marriage.error.storage",failure);}}
    private static void write(Path file,CompoundTag root)throws java.io.IOException{Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"marriage-",".tmp");try{NbtIo.writeCompressed(root,temporary);force(temporary);Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);directory(file.getParent());}finally{Files.deleteIfExists(temporary);}}
    public static void force(Path file)throws java.io.IOException{try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}}
    public static void directory(Path file)throws java.io.IOException{if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))try(FileChannel channel=FileChannel.open(file,StandardOpenOption.READ)){channel.force(true);}}
}
