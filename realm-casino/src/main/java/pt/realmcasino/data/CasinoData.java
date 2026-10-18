package pt.realmcasino.data;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import pt.realmcasino.domain.Blackjack;
import pt.realmcasino.domain.CasinoLedger;
import pt.realmcasino.domain.CasinoLedger.History;
import pt.realmcasino.domain.CasinoLedger.Kind;
import pt.realmcasino.domain.CasinoLedger.Machine;
import pt.realmcasino.domain.CasinoLedger.Round;
import pt.realmcasino.domain.Money;

/**
 * Casino-only world ledger with an explicit durable journal. SavedData's regular save path logs
 * IO failures, so financial commits use a verified atomic replacement and report errors to callers.
 */
public final class CasinoData extends SavedData {
    public static final String FILE_ID = "realm_casino";
    public static final int MAX_STATES = 4296;
    private static final Map<MinecraftServer,CasinoData> LOADED = new WeakHashMap<>();
    private CasinoLedger ledger = new CasinoLedger();
    private Map<UUID,GameState> states = Map.of();
    private Journal journal;
    private Path file;
    private boolean frozen;

    public record Journal(UUID id, UUID player, long before, long after, String kind,
                          CasinoLedger ledger, Map<UUID,GameState> states) {
        public Journal {
            Objects.requireNonNull(id); Objects.requireNonNull(player); Objects.requireNonNull(kind);
            Objects.requireNonNull(ledger); states = prune(states);
            Money.checked(before); Money.checked(after);
            if (before == after || !Set.of("deposit","bet","claim").contains(kind)) throw corrupt(null);
            validate(ledger, states);
        }
    }
    public static CasinoData get(MinecraftServer server) {
        thread(server);
        return LOADED.computeIfAbsent(server, key -> {
            Path path = key.getWorldPath(LevelResource.ROOT).resolve("data/"+FILE_ID+".dat");
            CasinoData value;
            if (Files.notExists(path)) value = new CasinoData();
            else try { value = load(NbtIo.readCompressed(path,NbtAccounter.create(64L*1024*1024)), key.registryAccess()); }
            catch (IOException | RuntimeException failure) { throw corrupt(failure); }
            value.file = path; return value;
        });
    }
    public static void forget(MinecraftServer server) { LOADED.remove(server); }
    public CasinoLedger ledger() { return ledger; }
    public Map<UUID,GameState> states() { return states; }
    public Journal journal() { return journal; }
    public boolean frozen() { return frozen; }
    public Path path() { return file; }

    public void replace(CasinoLedger next, Map<UUID,GameState> games) {
        requireAvailable(); games=prune(games); validate(next,games);
        write(next,games,null);
        ledger=next; states=Map.copyOf(games);
    }
    public void prepare(Journal intent) {
        requireAvailable();
        validateIntent(ledger,intent);
        write(ledger,states,intent);
        journal=intent;
    }
    public void complete(UUID id) {
        if (journal == null || !journal.id().equals(id)) throw new IllegalStateException("error.realmcasino.wallet_ambiguous");
        Journal committing = journal;
        write(committing.ledger(),committing.states(),null);
        ledger=committing.ledger(); states=committing.states(); journal=null; frozen=false;
    }
    public void freeze() { frozen=true; }
    public void requireAvailable() {
        if (frozen || journal != null) throw new IllegalStateException("error.realmcasino.ledger_frozen");
    }
    private void write(CasinoLedger next, Map<UUID,GameState> games, Journal intent) {
        if (file == null) throw new IllegalStateException("An unbound test ledger cannot commit money.");
        CompoundTag encoded = encode(next,games,intent);
        Path temporary=null;
        try {
            Files.createDirectories(file.getParent());
            temporary=Files.createTempFile(file.getParent(),"realm-casino-",".tmp");
            NbtIo.writeCompressed(encoded,temporary);
            DurableFiles.forceFile(temporary);
            // Never fall back to delete-and-rename: a failed atomic move must freeze, not expose a partial ledger.
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            temporary=null;
            DurableFiles.forceDirectory(file.getParent());
        } catch (IOException | RuntimeException failure) {
            frozen=true;
            throw new IllegalStateException("error.realmcasino.storage_unavailable",failure);
        } finally {
            if (temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) {}
        }
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        CompoundTag encoded=encode(ledger,states,journal);
        for (String key:encoded.getAllKeys()) root.put(key,Objects.requireNonNull(encoded.get(key)).copy());
        return root;
    }
    public static CasinoData load(CompoundTag root, HolderLookup.Provider lookup) {
        try {
            keys(root,Set.of("version","ledger","states","journal"),Set.of("version","ledger","states"));
            type(root,"version",Tag.TAG_INT); if(root.getInt("version")!=1)throw corrupt(null);
            CasinoData data=new CasinoData();
            data.ledger=readLedger(compound(root,"ledger")); data.states=readStates(list(root,"states",MAX_STATES));
            validate(data.ledger,data.states);
            if(root.contains("journal")) {
                CompoundTag j=compound(root,"journal"); exact(j,Set.of("id","player","before","after","kind","ledger","states"));
                data.journal=new Journal(uuid(j,"id"),uuid(j,"player"),number(j,"before"),number(j,"after"),string(j,"kind",16),
                        readLedger(compound(j,"ledger")),readStates(list(j,"states",MAX_STATES)));
            }
            if(data.journal!=null) validateIntent(data.ledger,data.journal);
            return data;
        } catch(RuntimeException failure) { throw corrupt(failure); }
    }
    private static CompoundTag encode(CasinoLedger ledger,Map<UUID,GameState> states,Journal journal) {
        validate(ledger,states);
        CompoundTag root=new CompoundTag(); root.putInt("version",1); root.put("ledger",writeLedger(ledger)); root.put("states",writeStates(states));
        if(journal!=null) {
            CompoundTag j=new CompoundTag(); j.putUUID("id",journal.id());j.putUUID("player",journal.player());j.putLong("before",journal.before());j.putLong("after",journal.after());j.putString("kind",journal.kind());
            j.put("ledger",writeLedger(journal.ledger()));j.put("states",writeStates(journal.states()));root.put("journal",j);
        }
        return root;
    }
    private static CompoundTag writeLedger(CasinoLedger ledger) {
        CompoundTag result=new CompoundTag();ListTag machines=new ListTag(),rounds=new ListTag(),credits=new ListTag(),history=new ListTag();
        for(Machine row:ledger.machines().values()) {
            CompoundTag t=new CompoundTag();t.putUUID("id",row.id());t.putUUID("owner",row.owner());t.putString("kind",row.kind().name());t.putString("dimension",row.dimension());t.putLong("position",row.position());t.putString("name",row.name());
            t.putLong("min",row.minBet());t.putLong("max",row.maxBet());t.putLong("bank",row.bank());t.putLong("reserved",row.reserved());t.putLong("version",row.version());machines.add(t);
        }
        for(Round row:ledger.rounds().values()) {
            CompoundTag t=new CompoundTag();t.putUUID("id",row.id());t.putUUID("machine",row.machineId());t.putUUID("player",row.player());t.putLong("stake",row.stake());t.putLong("maximum",row.maxPayout());t.putLong("started",row.startedAt());rounds.add(t);
        }
        ledger.credits().forEach((id,amount)->{CompoundTag t=new CompoundTag();t.putUUID("player",id);t.putLong("amount",amount);credits.add(t);});
        for(History row:ledger.history()) {
            CompoundTag t=new CompoundTag();t.putUUID("id",row.id());t.putUUID("machine",row.machineId());t.putUUID("player",row.player());t.putLong("stake",row.stake());t.putLong("payout",row.payout());t.putLong("settled",row.settledAt());history.add(t);
        }
        result.put("machines",machines);result.put("rounds",rounds);result.put("credits",credits);result.put("history",history);return result;
    }
    private static CasinoLedger readLedger(CompoundTag tag) {
        exact(tag,Set.of("machines","rounds","credits","history"));
        List<Machine> machines=new ArrayList<>();List<Round> rounds=new ArrayList<>();Map<UUID,Long> credits=new LinkedHashMap<>();List<History> history=new ArrayList<>();
        for(Tag raw:list(tag,"machines",100_000)) {
            CompoundTag t=(CompoundTag)raw;exact(t,Set.of("id","owner","kind","dimension","position","name","min","max","bank","reserved","version"));
            machines.add(new Machine(uuid(t,"id"),uuid(t,"owner"),Kind.valueOf(string(t,"kind",16)),string(t,"dimension",256),number(t,"position"),string(t,"name",64),number(t,"min"),number(t,"max"),number(t,"bank"),number(t,"reserved"),number(t,"version")));
        }
        for(Tag raw:list(tag,"rounds",100_000)) {
            CompoundTag t=(CompoundTag)raw;exact(t,Set.of("id","machine","player","stake","maximum","started"));
            rounds.add(new Round(uuid(t,"id"),uuid(t,"machine"),uuid(t,"player"),number(t,"stake"),number(t,"maximum"),number(t,"started")));
        }
        for(Tag raw:list(tag,"credits",100_000)) {
            CompoundTag t=(CompoundTag)raw;exact(t,Set.of("player","amount"));
            if(credits.put(uuid(t,"player"),number(t,"amount"))!=null)throw corrupt(null);
        }
        for(Tag raw:list(tag,"history",10_000)) {
            CompoundTag t=(CompoundTag)raw;exact(t,Set.of("id","machine","player","stake","payout","settled"));
            history.add(new History(uuid(t,"id"),uuid(t,"machine"),uuid(t,"player"),number(t,"stake"),number(t,"payout"),number(t,"settled")));
        }
        return new CasinoLedger(machines,rounds,credits,history);
    }
    private static ListTag writeStates(Map<UUID,GameState> states) {
        ListTag list=new ListTag();
        for(GameState row:states.values()) {
            CompoundTag t=new CompoundTag();t.putUUID("id",row.id());t.putUUID("machine",row.machine());t.putUUID("player",row.player());t.putString("kind",row.kind().name());t.putLong("stake",row.stake());t.putLong("started",row.startedAt());t.putLong("deadline",row.deadline());t.putLong("revision",row.revision());
            t.putIntArray("reels",row.reels());t.putInt("roulette",row.rouletteNumber());t.putString("betType",row.betType());t.putInt("betNumber",row.betNumber());t.putLong("payout",row.payout());t.putBoolean("finished",row.finished());
            if(row.blackjack()!=null) {
                Blackjack.State bj=row.blackjack();CompoundTag b=new CompoundTag();b.putIntArray("deck",bj.deck());b.putInt("cursor",bj.cursor());b.putIntArray("player",bj.player());b.putIntArray("dealer",bj.dealer());b.putString("status",bj.status().name());b.putString("outcome",bj.outcome().name());t.put("blackjack",b);
            }
            list.add(t);
        }
        return list;
    }
    private static Map<UUID,GameState> readStates(ListTag list) {
        Map<UUID,GameState> states=new LinkedHashMap<>();
        for(Tag raw:list) {
            CompoundTag t=(CompoundTag)raw;keys(t,Set.of("id","machine","player","kind","stake","started","deadline","revision","reels","roulette","betType","betNumber","payout","finished","blackjack"),Set.of("id","machine","player","kind","stake","started","deadline","revision","reels","roulette","betType","betNumber","payout","finished"));
            Blackjack.State b=null;
            if(t.contains("blackjack")) {
                CompoundTag a=compound(t,"blackjack");exact(a,Set.of("deck","cursor","player","dealer","status","outcome"));
                b=new Blackjack.State(ints(a,"deck",52),integer(a,"cursor"),ints(a,"player",52),ints(a,"dealer",52),Blackjack.Status.valueOf(string(a,"status",16)),Blackjack.Outcome.valueOf(string(a,"outcome",16)));
            }
            type(t,"finished",Tag.TAG_BYTE);if(t.getByte("finished")!=0&&t.getByte("finished")!=1)throw corrupt(null);
            GameState state=new GameState(uuid(t,"id"),uuid(t,"machine"),uuid(t,"player"),Kind.valueOf(string(t,"kind",16)),number(t,"stake"),number(t,"started"),number(t,"deadline"),number(t,"revision"),b,ints(t,"reels",3),integer(t,"roulette"),string(t,"betType",16),integer(t,"betNumber"),number(t,"payout"),t.getBoolean("finished"));
            if(states.put(state.player(),state)!=null)throw corrupt(null);
        }
        return Map.copyOf(states);
    }
    private static void validate(CasinoLedger ledger,Map<UUID,GameState> states) {
        if(states.size()>MAX_STATES)throw corrupt(null);
        for(var entry:states.entrySet()) {
            GameState state=entry.getValue();if(!entry.getKey().equals(state.player()))throw corrupt(null);
            Round round=ledger.rounds().get(state.id());
            Machine machine=ledger.machines().get(state.machine());
            if(machine!=null&&machine.kind()!=state.kind())throw corrupt(null);
            for(History h:ledger.history())if(h.id().equals(state.id())&&(!h.machineId().equals(state.machine())||!h.player().equals(state.player())||h.stake()!=state.stake()||h.payout()!=state.payout()))throw corrupt(null);
            if(!state.finished()&&(round==null||!round.player().equals(state.player())||!round.machineId().equals(state.machine())||round.stake()!=state.stake()))throw corrupt(null);
            if(state.finished()&&round!=null)throw corrupt(null);
        }
        for(Round round:ledger.rounds().values()) {
            GameState state=states.get(round.player());if(state==null||state.finished()||!state.id().equals(round.id()))throw corrupt(null);
        }
    }
    private static Map<UUID,GameState> prune(Map<UUID,GameState> source) {
        Map<UUID,GameState> kept=new LinkedHashMap<>();
        source.values().stream().filter(s->!s.finished()).forEach(s->kept.put(s.player(),s));
        source.values().stream().filter(GameState::finished).sorted(java.util.Comparator.comparingLong(GameState::startedAt).reversed()).limit(200).forEach(s->kept.put(s.player(),s));
        return Map.copyOf(kept);
    }
    private static void validateIntent(CasinoLedger before, Journal intent) {
        java.math.BigInteger base=total(before), target=total(intent.ledger());
        if(!target.subtract(base).equals(java.math.BigInteger.valueOf(intent.before()).subtract(java.math.BigInteger.valueOf(intent.after()))))throw corrupt(null);
        if((intent.kind().equals("claim")) != (intent.after()>intent.before()))throw corrupt(null);
    }
    private static java.math.BigInteger total(CasinoLedger ledger) {
        java.math.BigInteger value=java.math.BigInteger.ZERO;
        for(Machine machine:ledger.machines().values())value=value.add(java.math.BigInteger.valueOf(machine.bank()));
        for(long credit:ledger.credits().values())value=value.add(java.math.BigInteger.valueOf(credit));
        return value;
    }
    private static CompoundTag compound(CompoundTag t,String key){type(t,key,Tag.TAG_COMPOUND);return t.getCompound(key);}
    private static ListTag list(CompoundTag t,String key,int max){if(!(t.get(key)instanceof ListTag list)||list.size()>max||(list.getElementType()!=Tag.TAG_COMPOUND&&!(list.isEmpty()&&list.getElementType()==Tag.TAG_END)))throw corrupt(null);return list;}
    private static List<Integer> ints(CompoundTag t,String key,int max){type(t,key,Tag.TAG_INT_ARRAY);int[] a=t.getIntArray(key);if(a.length>max)throw corrupt(null);return java.util.Arrays.stream(a).boxed().toList();}
    private static long number(CompoundTag t,String key){type(t,key,Tag.TAG_LONG);return t.getLong(key);}
    private static int integer(CompoundTag t,String key){type(t,key,Tag.TAG_INT);return t.getInt(key);}
    private static UUID uuid(CompoundTag t,String key){type(t,key,Tag.TAG_INT_ARRAY);if(!t.hasUUID(key))throw corrupt(null);return t.getUUID(key);}
    private static String string(CompoundTag t,String key,int max){type(t,key,Tag.TAG_STRING);String s=t.getString(key);if(s.length()>max)throw corrupt(null);return s;}
    private static void type(CompoundTag t,String key,int kind){if(t.getTagType(key)!=kind)throw corrupt(null);}
    private static void exact(CompoundTag t,Set<String> keys){keys(t,keys,keys);}
    private static void keys(CompoundTag t,Set<String> allowed,Set<String> required){if(!allowed.containsAll(t.getAllKeys())||!t.getAllKeys().containsAll(required))throw corrupt(null);}
    private static void thread(MinecraftServer server){if(server==null||!server.isSameThread())throw new IllegalStateException("error.realmcasino.invalid_request");}
    private static IllegalStateException corrupt(Throwable cause){return new IllegalStateException("Cannot read realm_casino.dat. Preserve the file and restore a world backup.",cause);}
}
