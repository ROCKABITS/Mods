package pt.lawsjustice.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import pt.lawsjustice.domain.JusticeLedger;
import pt.lawsjustice.domain.JusticeLedger.Point;

/** World-owned custody. The wall clock never determines time served. */
public final class JusticeData {
    private static final int MAX_LEDGER_BYTES=12_000_000;
    private static final WeakHashMap<MinecraftServer,JusticeData> LOADED=new WeakHashMap<>();
    private static final Gson JSON=new Gson();
    private Path file;
    private JusticeLedger ledger=JusticeLedger.empty();
    private Map<UUID,ReleaseTarget> pendingReleases=Map.of();
    private long version;
    private boolean dirty;

    public record ReleaseTarget(String dimension,Point point) {
        public ReleaseTarget { new JusticeLedger.Area(dimension,point,point); }
    }
    private record Stored(Map<UUID,JusticeLedger.Prison> prisons,Map<UUID,JusticeLedger.Sentence> active,
                          java.util.List<JusticeLedger.Decision> history,Map<UUID,ReleaseTarget> pendingReleases) {}
    private record Loaded(JusticeLedger ledger,Map<UUID,ReleaseTarget> releases,long revision) {}

    public JusticeData() {}
    public static JusticeData get(MinecraftServer server) {
        if(!server.isSameThread())throw error("thread",null);
        return LOADED.computeIfAbsent(server,s->{
            Path path=s.getWorldPath(LevelResource.ROOT).resolve("data/laws_justice.dat");
            // An inaccessible path is not proof that no custody has ever been saved.
            if(!Files.notExists(path))return readState(path);
            JusticeData data=new JusticeData();data.file=path;
            return data;
        });
    }
    public static void drop(MinecraftServer server) { LOADED.remove(server); }
    public JusticeLedger ledger(){return ledger;}
    public long version(){return version;}
    public Map<UUID,ReleaseTarget> pendingReleases(){return pendingReleases;}
    public static JusticeLedger read(Path path){return load(path).ledger();}
    /** Reopens the complete durable state, including undelivered offline releases. */
    public static JusticeData readState(Path path){
        Loaded loaded=load(path);JusticeData data=new JusticeData();data.file=path;
        data.ledger=loaded.ledger();data.pendingReleases=loaded.releases();data.version=loaded.revision();return data;
    }

    /** Administrative changes are durable before they take effect in the world. */
    public void replace(JusticeLedger next) { commit(next,pendingReleases); }
    public void replaceAndQueue(JusticeLedger next,UUID player,ReleaseTarget target) {
        Map<UUID,ReleaseTarget> changed=new HashMap<>(pendingReleases);changed.put(player,target);commit(next,changed);
    }
    public void completeRelease(UUID player) {
        if(!pendingReleases.containsKey(player))return;
        Map<UUID,ReleaseTarget> changed=new HashMap<>(pendingReleases);changed.remove(player);commit(ledger,changed);
    }
    public void replaceProgress(JusticeLedger next) {
        java.util.Objects.requireNonNull(next);validateReleases(next,pendingReleases);ledger=next;dirty=true;
    }
    /** A storage failure must not keep an already completed player imprisoned. Retry the checkpoint. */
    public void completedInMemory(JusticeLedger next,UUID player,ReleaseTarget target) {
        java.util.Objects.requireNonNull(next);Map<UUID,ReleaseTarget> changed=new HashMap<>(pendingReleases);changed.put(player,target);
        Map<UUID,ReleaseTarget> copy=Map.copyOf(changed);validateReleases(next,copy);
        long revision=nextVersion();ledger=next;pendingReleases=copy;version=revision;dirty=true;
    }
    private void commit(JusticeLedger next,Map<UUID,ReleaseTarget> releases) {
        java.util.Objects.requireNonNull(next);long revision=nextVersion();
        Map<UUID,ReleaseTarget> copy=Map.copyOf(releases);validateReleases(next,copy);
        write(next,copy,revision);ledger=next;pendingReleases=copy;version=revision;dirty=false;
    }
    public void flush() { if(dirty){write(ledger,pendingReleases,version);dirty=false;} }

    private void write(JusticeLedger next,Map<UUID,ReleaseTarget> releases,long revision) {
        if(file==null)throw error("storage",null);
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try {
            Files.createDirectories(file.getParent());
            String serialized=JSON.toJson(new Stored(next.prisons(),next.active(),next.history(),releases));
            byte[] encoded=serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(encoded.length>MAX_LEDGER_BYTES)throw error("capacity",null);
            CompoundTag tag=new CompoundTag();tag.putInt("format",1);tag.putLong("revision",revision);tag.putByteArray("ledger",encoded);
            NbtIo.writeCompressed(tag,temp);force(temp);
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            if(!System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win"))forceDirectory(file.getParent());
        }catch(IOException|RuntimeException failure){
            try{Files.deleteIfExists(temp);}catch(IOException ignored){}
            throw error("storage",failure);
        }
    }
    private static Loaded load(Path path) {
        try {
            if(!Files.isRegularFile(path)||Files.size(path)>16_777_216)throw error("data",null);
            CompoundTag root=NbtIo.readCompressed(path,NbtAccounter.create(16_777_216));
            if(!root.getAllKeys().equals(Set.of("format","revision","ledger"))||!root.contains("format",Tag.TAG_INT)
                    ||root.getInt("format")!=1||!root.contains("revision",Tag.TAG_LONG)||root.getLong("revision")<0
                    ||!(root.contains("ledger",Tag.TAG_STRING)||root.contains("ledger",Tag.TAG_BYTE_ARRAY)))throw error("data",null);
            String text;
            if(root.contains("ledger",Tag.TAG_BYTE_ARRAY)){
                byte[] encoded=root.getByteArray("ledger");if(encoded.length>MAX_LEDGER_BYTES)throw error("data",null);
                text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(encoded)).toString();
            }else{
                text=root.getString("ledger");if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_LEDGER_BYTES)throw error("data",null);
            }
            com.google.gson.stream.JsonReader reader=new com.google.gson.stream.JsonReader(new java.io.StringReader(text));reader.setLenient(false);
            JsonElement tree=readJson(reader,0);if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw error("data",null);validateShape(tree,Stored.class);
            JsonObject obj=tree.getAsJsonObject();if(!obj.keySet().equals(Set.of("prisons","active","history","pendingReleases")))throw error("data",null);
            Stored stored=JSON.fromJson(tree,Stored.class);
            JusticeLedger ledger=JusticeLedger.restore(stored.prisons(),stored.active(),stored.history());
            Map<UUID,ReleaseTarget> releases=Map.copyOf(stored.pendingReleases());validateReleases(ledger,releases);
            return new Loaded(ledger,releases,root.getLong("revision"));
        }catch(IOException|RuntimeException failure){throw error("data",failure);}
    }
    private static JsonElement readJson(com.google.gson.stream.JsonReader reader,int depth)throws IOException {
        if(depth>32)throw error("data",null);
        return switch(reader.peek()) {
            case BEGIN_OBJECT->{reader.beginObject();JsonObject object=new JsonObject();while(reader.hasNext()){String key=reader.nextName();if(object.has(key))throw error("data",null);object.add(key,readJson(reader,depth+1));}reader.endObject();yield object;}
            case BEGIN_ARRAY->{reader.beginArray();com.google.gson.JsonArray array=new com.google.gson.JsonArray();while(reader.hasNext()){if(array.size()>100_000)throw error("data",null);array.add(readJson(reader,depth+1));}reader.endArray();yield array;}
            case STRING->new com.google.gson.JsonPrimitive(reader.nextString());
            case NUMBER->new com.google.gson.JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN->new com.google.gson.JsonPrimitive(reader.nextBoolean());
            default->throw error("data",null);
        };
    }
    private static void validateShape(JsonElement value,java.lang.reflect.Type type) {
        if(value==null||value.isJsonNull())throw error("data",null);
        if(type instanceof java.lang.reflect.ParameterizedType parameterized) {
            if(parameterized.getRawType()==Map.class) {
                if(!value.isJsonObject()||parameterized.getActualTypeArguments()[0]!=UUID.class)throw error("data",null);
                for(var entry:value.getAsJsonObject().entrySet()){canonicalUuid(entry.getKey());validateShape(entry.getValue(),parameterized.getActualTypeArguments()[1]);}
            }else if(parameterized.getRawType()==java.util.List.class){if(!value.isJsonArray())throw error("data",null);for(var item:value.getAsJsonArray())validateShape(item,parameterized.getActualTypeArguments()[0]);}
            else throw error("data",null);
            return;
        }
        Class<?> kind=(Class<?>)type;
        if(kind==String.class||kind==UUID.class){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw error("data",null);if(kind==UUID.class)canonicalUuid(value.getAsString());return;}
        if(kind==long.class||kind==int.class){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw error("data",null);long number=value.getAsBigDecimal().longValueExact();if(kind==int.class&&(number<Integer.MIN_VALUE||number>Integer.MAX_VALUE))throw error("data",null);return;}
        if(!kind.isRecord()||!value.isJsonObject())throw error("data",null);
        Set<String> fields=new java.util.HashSet<>();for(var component:kind.getRecordComponents()){fields.add(component.getName());validateShape(value.getAsJsonObject().get(component.getName()),component.getGenericType());}
        if(!fields.equals(value.getAsJsonObject().keySet()))throw error("data",null);
    }
    private static void canonicalUuid(String text){if(!UUID.fromString(text).toString().equals(text))throw error("data",null);}
    private static void validateReleases(JusticeLedger ledger,Map<UUID,ReleaseTarget> releases) {
        // Every admission reserves a future release slot. A full offline queue
        // can reject a new admission, but can never trap a completed prisoner.
        if((long)releases.size()+ledger.active().size()>JusticeLedger.MAX_ACTIVE)throw error("capacity",null);
        for(var entry:releases.entrySet())if(entry.getKey()==null||entry.getValue()==null||ledger.active().containsKey(entry.getKey()))throw error("data",null);
    }
    /** Opaque session token; wrapping invalidates old MAX_VALUE snapshots. */
    private long nextVersion(){return version==Long.MAX_VALUE?0:version+1;}
    private static void force(Path path)throws IOException {try(FileChannel c=FileChannel.open(path,StandardOpenOption.WRITE)){c.force(true);}}
    private static void forceDirectory(Path path)throws IOException {try(FileChannel c=FileChannel.open(path,StandardOpenOption.READ)){c.force(true);}}
    private static IllegalStateException error(String key,Throwable cause){return new IllegalStateException("lawsjustice.error."+key,cause);}
}
