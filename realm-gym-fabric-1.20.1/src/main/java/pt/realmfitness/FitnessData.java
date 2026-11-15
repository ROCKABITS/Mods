package pt.realmfitness;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import pt.realmfitness.domain.FitnessLedger;

public final class FitnessData extends SavedData {
    public static final String KEY="realm_fitness";
    public FitnessLedger ledger=new FitnessLedger();
    private boolean frozen;
    public static FitnessData get(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("Fitness requires the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(FitnessData::load,()->{
            if(!Files.notExists(server.getWorldPath(LevelResource.ROOT).resolve("data/"+KEY+".dat")))throw new IllegalStateException("Unreadable athlete data; preserve the world backup");
            return new FitnessData();
        },KEY);
    }
    public static FitnessData load(CompoundTag tag){
        if(tag.getInt("version")!=1||!tag.contains("payload",Tag.TAG_BYTE_ARRAY))throw new IllegalArgumentException("Unsupported fitness storage");
        byte[] bytes=tag.getByteArray("payload");if(bytes.length>64_000_000)throw new IllegalArgumentException("Fitness storage exceeds capacity");
        FitnessData d=new FitnessData();d.ledger=FitnessLedger.fromJson(new String(bytes,StandardCharsets.UTF_8));return d;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ledger.validate();byte[] bytes=FitnessLedger.JSON.toJson(ledger).getBytes(StandardCharsets.UTF_8);if(bytes.length>64_000_000)throw new IllegalStateException("Fitness storage capacity");
        tag.putInt("version",1);tag.putByteArray("payload",bytes);return tag;
    }
    public void available(){if(frozen)throw new IllegalStateException("fitness.storage_error");}
    public void persist(MinecraftServer server){
        available();Path path=server.getWorldPath(LevelResource.ROOT).resolve("data/"+KEY+".dat"),temp=null;
        try{
            CompoundTag root=new CompoundTag();root.put("data",save(new CompoundTag()));NbtUtils.addCurrentDataVersion(root);
            Files.createDirectories(path.getParent());temp=Files.createTempFile(path.getParent(),"fitness-",".tmp");NbtIo.writeCompressed(root,temp.toFile());
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))try(var dir=FileChannel.open(path.getParent(),StandardOpenOption.READ)){dir.force(true);}
            setDirty(false);
        }catch(java.io.IOException|RuntimeException error){frozen=true;throw new IllegalStateException("fitness.storage_error",error);}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(java.io.IOException ignored){}}
    }
}
