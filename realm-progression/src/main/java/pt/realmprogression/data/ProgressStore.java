package pt.realmprogression.data;

import com.google.gson.Gson;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import pt.realmprogression.domain.ProgressLedger.State;

/** Atomic, forced state replacement before any unlock becomes usable or is announced. */
public final class ProgressStore {
    private static final Gson JSON=new Gson();
    private static final Map<MinecraftServer,ProgressStore> LOADED=new WeakHashMap<>();
    private static final Map<MinecraftServer,RuntimeException> FAILED=new WeakHashMap<>();
    private final Path file;private State state;private boolean frozen;
    public ProgressStore(Path file){
        this.file=file;
        try{
            if(!Files.notExists(file)){
                if(!Files.isRegularFile(file)||Files.size(file)>2_000_000)throw new IllegalArgumentException("Oversized or inaccessible progression data");
                var decoder=StandardCharsets.UTF_8.newDecoder();String text=decoder.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file))).toString();
                state=Objects.requireNonNull(JSON.fromJson(text,State.class));
            }else state=State.fresh();
        }catch(Exception failure){throw new IllegalStateException("error.realmprogression.storage",failure);}
    }
    public static ProgressStore get(MinecraftServer server){
        if(server==null||!server.isSameThread())throw new IllegalStateException("Server thread required");
        if(FAILED.containsKey(server))throw FAILED.get(server);
        try{return LOADED.computeIfAbsent(server,s->new ProgressStore(s.getWorldPath(LevelResource.ROOT).resolve("data/realm_progression.json")));}
        catch(RuntimeException failure){FAILED.put(server,failure);throw failure;}
    }
    public State state(){if(frozen)throw new IllegalStateException("error.realmprogression.storage");return state;}
    public Path path(){return file;}
    public void commit(State next){
        if(frozen)throw new IllegalStateException("error.realmprogression.storage");if(next.equals(state))return;
        byte[] data=JSON.toJson(next).getBytes(StandardCharsets.UTF_8);if(data.length>2_000_000)throw new IllegalStateException("error.realmprogression.capacity");
        Path tmp=null;boolean moved=false;
        try{
            Files.createDirectories(file.getParent());tmp=Files.createTempFile(file.getParent(),"realm-progression-",".tmp");
            Files.write(tmp,data);try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);moved=true;state=next;
            if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))try(var dir=FileChannel.open(file.getParent(),StandardOpenOption.READ)){dir.force(true);}
        }catch(Exception failure){if(moved)frozen=true;throw new IllegalStateException("error.realmprogression.storage",failure);}
        finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}}
    }
    public static void forget(MinecraftServer server){LOADED.remove(server);FAILED.remove(server);}
}
