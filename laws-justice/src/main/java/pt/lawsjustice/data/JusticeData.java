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
import pt.lawsjustice.domain.CourtLedger;
import pt.lawsjustice.domain.WorkLedger;
import pt.lawsjustice.domain.FinanceLedger;

/** World-owned custody. The wall clock never determines time served. */
public final class JusticeData {
    private static final int MAX_LEDGER_BYTES=12_000_000;
    private static final WeakHashMap<MinecraftServer,JusticeData> LOADED=new WeakHashMap<>();
    private static final Gson JSON=new Gson();
    private Path file;
    private JusticeLedger ledger=JusticeLedger.empty();
    private CourtLedger court=CourtLedger.empty();
    private WorkLedger work=WorkLedger.empty();
    private FinanceLedger finance=FinanceLedger.empty();
    private Map<UUID,ReleaseTarget> pendingReleases=Map.of();
    private long version;
    private boolean dirty;

    public record ReleaseTarget(String dimension,Point point) {
        public ReleaseTarget { new JusticeLedger.Area(dimension,point,point); }
    }
    private record Stored(Map<UUID,JusticeLedger.Prison> prisons,Map<UUID,JusticeLedger.Sentence> active,
                          java.util.List<JusticeLedger.Decision> history,Map<UUID,ReleaseTarget> pendingReleases) {}
    private record StoredV2(Map<UUID,JusticeLedger.Prison> prisons,Map<UUID,JusticeLedger.Sentence> active,
                            java.util.List<JusticeLedger.Decision> history,Map<UUID,ReleaseTarget> pendingReleases,CourtLedger.State court) {}
    private record StoredV3(Map<UUID,JusticeLedger.Prison> prisons,Map<UUID,JusticeLedger.Sentence> active,
                            java.util.List<JusticeLedger.Decision> history,Map<UUID,ReleaseTarget> pendingReleases,CourtLedger.State court,WorkLedger.State work) {}
    private record StoredV4(Map<UUID,JusticeLedger.Prison> prisons,Map<UUID,JusticeLedger.Sentence> active,
                            java.util.List<JusticeLedger.Decision> history,Map<UUID,ReleaseTarget> pendingReleases,CourtLedger.State court,WorkLedger.State work,FinanceLedger.State finance) {}
    private record Loaded(JusticeLedger ledger,CourtLedger court,WorkLedger work,FinanceLedger finance,Map<UUID,ReleaseTarget> releases,long revision) {}

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
    public CourtLedger court(){return court;}
    public WorkLedger work(){return work;}
    public FinanceLedger finance(){return finance;}
    public long version(){return version;}
    public Map<UUID,ReleaseTarget> pendingReleases(){return pendingReleases;}
    public static JusticeLedger read(Path path){return load(path).ledger();}
    /** Reopens the complete durable state, including undelivered offline releases. */
    public static JusticeData readState(Path path){
        Loaded loaded=load(path);JusticeData data=new JusticeData();data.file=path;
        data.ledger=loaded.ledger();data.court=loaded.court();data.work=loaded.work();data.finance=loaded.finance();data.pendingReleases=loaded.releases();data.version=loaded.revision();return data;
    }

    /** Administrative changes are durable before they take effect in the world. */
    public void replace(JusticeLedger next) { commit(next,court,work,finance,pendingReleases); }
    /** Verdict and custody are committed in the same atomic world file. */
    public void replace(JusticeLedger next,CourtLedger nextCourt) { commit(next,nextCourt,work,finance,pendingReleases); }
    public void replaceCourt(CourtLedger nextCourt) { commit(ledger,nextCourt,work,finance,pendingReleases); }
    public void replaceWork(WorkLedger next){commit(ledger,court,next,finance,pendingReleases);}
    public void replaceWork(JusticeLedger next,WorkLedger nextWork){commit(next,court,nextWork,finance,pendingReleases);}
    public void replaceWorkAndQueue(JusticeLedger next,WorkLedger nextWork,UUID player,ReleaseTarget target){Map<UUID,ReleaseTarget> changed=new HashMap<>(pendingReleases);changed.put(player,target);commit(next,court,nextWork,finance,changed);}
    public void replaceFinance(FinanceLedger next){commit(ledger,court,work,next,pendingReleases);}
    public void replaceFinance(JusticeLedger next,CourtLedger nextCourt,FinanceLedger nextFinance){commit(next,nextCourt,work,nextFinance,pendingReleases);}
    public void replaceFinanceAndQueue(JusticeLedger next,FinanceLedger nextFinance,UUID player,ReleaseTarget target){Map<UUID,ReleaseTarget> changed=new HashMap<>(pendingReleases);changed.put(player,target);commit(next,court,work,nextFinance,changed);}
    public void replaceAndQueue(JusticeLedger next,UUID player,ReleaseTarget target) {
        Map<UUID,ReleaseTarget> changed=new HashMap<>(pendingReleases);changed.put(player,target);commit(next,court,work,finance,changed);
    }
    public void completeRelease(UUID player) {
        if(!pendingReleases.containsKey(player))return;
        Map<UUID,ReleaseTarget> changed=new HashMap<>(pendingReleases);changed.remove(player);commit(ledger,court,work,finance,changed);
    }
    public void replaceProgress(JusticeLedger next) {
        java.util.Objects.requireNonNull(next);validateReleases(next,pendingReleases);validateWork(next,work);ledger=next;dirty=true;
    }
    /** A storage failure must not keep an already completed player imprisoned. Retry the checkpoint. */
    public void completedInMemory(JusticeLedger next,UUID player,ReleaseTarget target) {
        java.util.Objects.requireNonNull(next);Map<UUID,ReleaseTarget> changed=new HashMap<>(pendingReleases);changed.put(player,target);
        Map<UUID,ReleaseTarget> copy=Map.copyOf(changed);validateReleases(next,copy);
        long revision=nextVersion();ledger=next;work=work.reconcile(next,Math.max(0,System.currentTimeMillis()));finance=finance.reconcile(next,Math.max(0,System.currentTimeMillis()));pendingReleases=copy;version=revision;dirty=true;
    }
    private void commit(JusticeLedger next,CourtLedger nextCourt,WorkLedger nextWork,FinanceLedger nextFinance,Map<UUID,ReleaseTarget> releases) {
        java.util.Objects.requireNonNull(next);java.util.Objects.requireNonNull(nextCourt);long revision=nextVersion();
        Map<UUID,ReleaseTarget> copy=Map.copyOf(releases);validateReleases(next,copy);
        nextWork=java.util.Objects.requireNonNull(nextWork).reconcile(next,Math.max(0,System.currentTimeMillis()));
        nextFinance=java.util.Objects.requireNonNull(nextFinance).reconcile(next,Math.max(0,System.currentTimeMillis()));
        validateCourt(next,nextCourt);validateNewVerdicts(court,next,nextCourt);validateWork(next,nextWork);validateFinance(next,nextCourt,nextFinance);validateNewFinancialDecisions(court,finance,nextCourt,nextFinance);
        write(next,nextCourt,nextWork,nextFinance,copy,revision);ledger=next;court=nextCourt;work=nextWork;finance=nextFinance;pendingReleases=copy;version=revision;dirty=false;
    }
    public void flush() { if(dirty){write(ledger,court,work,finance,pendingReleases,version);dirty=false;} }

    private void write(JusticeLedger next,CourtLedger nextCourt,WorkLedger nextWork,FinanceLedger nextFinance,Map<UUID,ReleaseTarget> releases,long revision) {
        if(file==null)throw error("storage",null);
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try {
            Files.createDirectories(file.getParent());
            String serialized=JSON.toJson(new StoredV4(next.prisons(),next.active(),next.history(),releases,nextCourt.state(),nextWork.state(),nextFinance.state()));
            byte[] encoded=serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(encoded.length>MAX_LEDGER_BYTES-nextFinance.preparedCount()*4096L)throw error("capacity",null);
            CompoundTag tag=new CompoundTag();tag.putInt("format",4);tag.putLong("revision",revision);tag.putByteArray("ledger",encoded);
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
                    ||(root.getInt("format")<1||root.getInt("format")>4)||!root.contains("revision",Tag.TAG_LONG)||root.getLong("revision")<0
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
            JsonElement tree=readJson(reader,0);if(reader.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw error("data",null);
            if(root.getInt("format")==2||root.getInt("format")==3)migrateCourtDuration(tree);
            Stored stored;CourtLedger court;WorkLedger work=WorkLedger.empty();FinanceLedger finance=FinanceLedger.empty();
            if(root.getInt("format")==1){
                validateShape(tree,Stored.class);stored=JSON.fromJson(tree,Stored.class);court=CourtLedger.empty();
            }else if(root.getInt("format")==2){
                validateShape(tree,StoredV2.class);StoredV2 v2=JSON.fromJson(tree,StoredV2.class);
                stored=new Stored(v2.prisons(),v2.active(),v2.history(),v2.pendingReleases());court=CourtLedger.restore(v2.court());
            }else if(root.getInt("format")==3){
                validateShape(tree,StoredV3.class);StoredV3 v3=JSON.fromJson(tree,StoredV3.class);
                stored=new Stored(v3.prisons(),v3.active(),v3.history(),v3.pendingReleases());court=CourtLedger.restore(v3.court());work=WorkLedger.restore(v3.work());
            }else{
                validateShape(tree,StoredV4.class);StoredV4 v4=JSON.fromJson(tree,StoredV4.class);
                stored=new Stored(v4.prisons(),v4.active(),v4.history(),v4.pendingReleases());court=CourtLedger.restore(v4.court());work=WorkLedger.restore(v4.work());finance=FinanceLedger.restore(v4.finance());
            }
            JusticeLedger ledger=JusticeLedger.restore(stored.prisons(),stored.active(),stored.history());
            Map<UUID,ReleaseTarget> releases=Map.copyOf(stored.pendingReleases());validateReleases(ledger,releases);
            validateCourt(ledger,court);validateWork(ledger,work);validateFinance(ledger,court,finance);return new Loaded(ledger,court,work,finance,releases,root.getLong("revision"));
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
    /** Older formats did not keep duration in the case itself; recover it from authentic custody records. */
    private static void migrateCourtDuration(JsonElement tree){
        if(!tree.isJsonObject())throw error("data",null);var root=tree.getAsJsonObject();var cases=root.getAsJsonObject("court").getAsJsonObject("cases");
        for(var entry:cases.entrySet()){var row=entry.getValue().getAsJsonObject();if(row.has("originalTicks"))throw error("data",null);long ticks=0;
            if("CONVICTED".equals(row.get("status").getAsString())){String id=row.get("sentence").getAsString();for(var active:root.getAsJsonObject("active").entrySet()){var sentence=active.getValue().getAsJsonObject();if(id.equals(sentence.get("id").getAsString()))ticks=sentence.get("originalTicks").getAsLong();}
                if(ticks==0)for(var raw:root.getAsJsonArray("history")){var decision=raw.getAsJsonObject();if(id.equals(decision.get("sentence").getAsString()))ticks=decision.get("originalTicks").getAsLong();}
            }row.addProperty("originalTicks",ticks);
        }
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
    private static void validateCourt(JusticeLedger custody,CourtLedger court){
        Map<UUID,UUID> players=new HashMap<>();for(var sentence:custody.active().values())players.put(sentence.id(),sentence.player());
        for(var decision:custody.history())players.put(decision.sentence(),decision.player());
        for(var file:court.cases().values())if(CourtLedger.CONVICTED.equals(file.status())){
            UUID player=players.get(UUID.fromString(file.sentence()));if(player!=null&&!player.equals(file.accused()))throw error("case_reference",null);
            for(var sentence:custody.active().values())if(sentence.id().toString().equals(file.sentence())&&file.originalTicks()!=0&&file.originalTicks()!=sentence.originalTicks())throw error("case_reference",null);
            for(var decision:custody.history())if(decision.sentence().toString().equals(file.sentence())&&file.originalTicks()!=0&&file.originalTicks()!=decision.originalTicks())throw error("case_reference",null);
        }
    }
    private static void validateWork(JusticeLedger custody,WorkLedger work){
        for(var zone:work.zones().values()){var prison=custody.prisons().get(zone.prison());if(prison==null||!prison.area().contains(zone.area()))throw error("work_zone",null);}
        for(var sentence:custody.active().values()){var credit=work.credits().get(sentence.id());if(credit==null?sentence.reductionTicks()!=0:!credit.player().equals(sentence.player())||credit.ticks()!=sentence.reductionTicks())throw error("work_credit",null);}
        for(var job:work.jobs().values())if(WorkLedger.ACTIVE.equals(job.status())){var sentence=custody.active().get(job.player());if(sentence==null||!sentence.id().equals(job.sentence())||!sentence.prison().equals(job.prison()))throw error("work_sentence",null);}
    }
    private static void validateFinance(JusticeLedger custody,CourtLedger court,FinanceLedger finance){
        for(var charge:finance.charges().values())if(charge.kind().equals(FinanceLedger.FINE)){
            var file=court.cases().get(UUID.fromString(charge.caseId()));
            if(file==null||!file.status().equals(CourtLedger.FINED)||!file.accused().equals(charge.target())||!file.judge().equals(charge.author().toString())||!file.authorityKind().equals(charge.authority())||!file.verdict().equals(charge.reason()))throw error("finance_case",null);
        }else if(charge.status().equals(FinanceLedger.DUE)&&finance.pending(charge.id())==null){
            var sentence=custody.active().get(charge.target());if(sentence==null||!sentence.id().toString().equals(charge.sentence()))throw error("bail_sentence",null);
        }else if(charge.status().equals(FinanceLedger.PAID)){
            var sentence=custody.active().get(charge.target());if(sentence!=null&&sentence.id().toString().equals(charge.sentence()))throw error("bail_atomic",null);
        }
        for(var file:court.cases().values())if(file.status().equals(CourtLedger.FINED)&&finance.charges().values().stream().noneMatch(c->c.kind().equals(FinanceLedger.FINE)&&c.caseId().equals(file.id().toString())))throw error("fine_atomic",null);
    }
    private static void validateNewFinancialDecisions(CourtLedger previous,FinanceLedger prior,CourtLedger next,FinanceLedger finance){
        for(var payment:finance.payments().values())if(payment.status().equals(FinanceLedger.PAID)){
            var old=prior.payments().get(payment.id());if(old==null)throw error("payment_atomic",null);
            if(old.status().equals(FinanceLedger.CANCELLED)||!old.charge().equals(payment.charge())||!old.payer().equals(payment.payer())||old.amount()!=payment.amount()||!old.currency().equals(payment.currency())||!old.memo().equals(payment.memo()))throw error("payment_atomic",null);
        }
    }
    private static void validateNewVerdicts(CourtLedger previous,JusticeLedger custody,CourtLedger next){
        for(var file:next.cases().values())if(CourtLedger.CONVICTED.equals(file.status())){
            var old=previous.cases().get(file.id());
            if(old==null||!CourtLedger.CONVICTED.equals(old.status())){
                UUID id=UUID.fromString(file.sentence());boolean present=custody.active().values().stream()
                        .anyMatch(sentence->sentence.id().equals(id)&&sentence.player().equals(file.accused()));
                if(!present)throw error("court_atomic",null);
            }
        }
    }
    /** Opaque session token; wrapping invalidates old MAX_VALUE snapshots. */
    private long nextVersion(){return version==Long.MAX_VALUE?0:version+1;}
    private static void force(Path path)throws IOException {try(FileChannel c=FileChannel.open(path,StandardOpenOption.WRITE)){c.force(true);}}
    private static void forceDirectory(Path path)throws IOException {try(FileChannel c=FileChannel.open(path,StandardOpenOption.READ)){c.force(true);}}
    private static IllegalStateException error(String key,Throwable cause){return new IllegalStateException("lawsjustice.error."+key,cause);}
}
