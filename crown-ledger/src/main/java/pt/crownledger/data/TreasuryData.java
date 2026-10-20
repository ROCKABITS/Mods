package pt.crownledger.data;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import pt.crownledger.domain.Policy;
import pt.crownledger.domain.IncomeLedger;
import pt.crownledger.domain.Treasury;
import pt.crownledger.domain.Treasury.Activity;
import pt.crownledger.domain.Treasury.Currency;
import pt.crownledger.domain.Treasury.History;
import pt.crownledger.domain.Treasury.Kind;
import pt.crownledger.domain.Treasury.PendingBatch;

/** One overworld treasury shared by every dimension and physical station. */
public final class TreasuryData extends SavedData {
    public static final String STORAGE_KEY = "crown_ledger";
    public Treasury treasury = new Treasury();
    public IncomeLedger income = new IncomeLedger();

    public static TreasuryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> {
            var file = server.getWorldPath(LevelResource.ROOT).resolve("data/" + STORAGE_KEY + ".dat");
            if (!Files.notExists(file))
                throw new IllegalStateException("Cannot read crown_ledger.dat; preserve the file and recover a world backup.");
            return new TreasuryData();
        }, TreasuryData::load), STORAGE_KEY);
    }

    public static TreasuryData load(CompoundTag tag, HolderLookup.Provider lookup) {
        type(tag, "version", Tag.TAG_INT);
        int version = tag.getInt("version");
        if (version != 1 && version != 2 && version != 3 && version != 4 && version != 5 && version != 6) throw new IllegalStateException("Unsupported Crown Ledger storage version");
        type(tag, "policy", Tag.TAG_COMPOUND);
        type(tag, "periodStartedAt", Tag.TAG_LONG);
        type(tag, "nextCycleAt", Tag.TAG_LONG);
        if (tag.contains("standaloneManager")) uuid(tag, "standaloneManager");
        Policy policy = readPolicy(tag.getCompound("policy"), version);
        Map<Currency, Long> balances = new EnumMap<>(Currency.class);
        for (Tag raw : list(tag, "balances")) {
            CompoundTag entry = (CompoundTag) raw;
            type(entry, "currency", Tag.TAG_STRING); type(entry, "amount", Tag.TAG_LONG);
            if (balances.putIfAbsent(Currency.valueOf(entry.getString("currency")), entry.getLong("amount")) != null)
                throw new IllegalStateException("Duplicate treasury balance");
        }
        Map<UUID, Activity> activity = new LinkedHashMap<>();
        for (Tag raw : list(tag, "activity")) {
            CompoundTag entry = (CompoundTag) raw;
            uuid(entry, "player"); type(entry, "name", Tag.TAG_STRING); type(entry, "minutes", Tag.TAG_INT);
            if (activity.putIfAbsent(entry.getUUID("player"), new Activity(entry.getString("name"), entry.getInt("minutes"))) != null)
                throw new IllegalStateException("Duplicate activity record");
        }
        List<History> history = new ArrayList<>();
        for (Tag raw : list(tag, "history")) {
            CompoundTag entry = (CompoundTag) raw;
            type(entry, "time", Tag.TAG_LONG); type(entry, "amount", Tag.TAG_LONG);
            for (String key : List.of("kind", "details", "currency")) type(entry, key, Tag.TAG_STRING);
            if (entry.contains("actor")) uuid(entry, "actor");
            history.add(new History(entry.getLong("time"), Kind.valueOf(entry.getString("kind")),
                    entry.hasUUID("actor") ? entry.getUUID("actor") : null, entry.getString("details"),
                    Currency.valueOf(entry.getString("currency")), entry.getLong("amount")));
        }
        PendingBatch pending = null;
        if (tag.contains("pending")) {
            type(tag, "pending", Tag.TAG_COMPOUND);
            CompoundTag entry = tag.getCompound("pending");
            uuid(entry, "id"); type(entry, "closedAt", Tag.TAG_LONG); type(entry, "currency", Tag.TAG_STRING);
            type(entry, "salary", Tag.TAG_LONG); type(entry, "fixedTax", Tag.TAG_LONG);
            Map<UUID, String> eligible = new LinkedHashMap<>();
            for (Tag raw : list(entry, "eligible")) {
                CompoundTag citizen = (CompoundTag) raw;
                uuid(citizen, "player"); type(citizen, "name", Tag.TAG_STRING);
                if (eligible.putIfAbsent(citizen.getUUID("player"), citizen.getString("name")) != null)
                    throw new IllegalStateException("Duplicate frozen payroll recipient");
            }
            pending = new PendingBatch(entry.getUUID("id"), entry.getLong("closedAt"),
                    Currency.valueOf(entry.getString("currency")), entry.getLong("salary"), entry.getLong("fixedTax"), eligible);
        }
        TreasuryData result = new TreasuryData();
        result.treasury = new Treasury(new Treasury.Snapshot(policy, balances, readAmounts(tag, "claims"),
                readAmounts(tag, "taxDue"), activity, history, pending, tag.getLong("periodStartedAt"),
                tag.getLong("nextCycleAt"), tag.hasUUID("standaloneManager") ? tag.getUUID("standaloneManager") : null));
        if(version>=3){
            Map<java.util.UUID,IncomeLedger.Receipt> receipts=new LinkedHashMap<>();
            for(Tag raw:list(tag,"incomeReceipts")){
                CompoundTag entry=(CompoundTag)raw;
                if(!entry.getAllKeys().equals(java.util.Set.of("id","currency","amount","payer","memo")))throw new IllegalStateException("Unknown income receipt field");
                uuid(entry,"id");uuid(entry,"payer");type(entry,"currency",Tag.TAG_STRING);type(entry,"amount",Tag.TAG_LONG);type(entry,"memo",Tag.TAG_STRING);
                if(version<6&&IncomeLedger.validEconomicPolicy(entry.getString("memo")))throw new IllegalStateException("Economic audit receipts require format 6");
                if(version<5&&entry.getLong("amount")==0)throw new IllegalStateException("Fiscal audit receipts require treasury format 5");
                var receipt=new IncomeLedger.Receipt(entry.getUUID("id"),Currency.valueOf(entry.getString("currency")),entry.getLong("amount"),entry.getUUID("payer"),entry.getString("memo"));
                if(receipts.putIfAbsent(receipt.id(),receipt)!=null||receipts.size()>IncomeLedger.MAX_RECEIPTS)throw new IllegalStateException("Duplicate or excessive income receipts");
            }
            result.income=new IncomeLedger(receipts);
        }else if(tag.contains("incomeReceipts"))throw new IllegalStateException("Income receipts cannot be loaded as an older treasury format");
        if (version < 6) result.setDirty();
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        Treasury.Snapshot saved = treasury.snapshot();
        tag.putInt("version", 6);
        ListTag receipts=new ListTag();
        income.receipts().values().forEach(receipt->{CompoundTag entry=new CompoundTag();entry.putUUID("id",receipt.id());entry.putString("currency",receipt.currency().name());entry.putLong("amount",receipt.amount());entry.putUUID("payer",receipt.payer());entry.putString("memo",receipt.memo());receipts.add(entry);});
        tag.put("incomeReceipts",receipts);
        tag.put("policy", writePolicy(saved.policy()));
        tag.putLong("periodStartedAt", saved.periodStartedAt()); tag.putLong("nextCycleAt", saved.nextCycleAt());
        if (saved.standaloneManager() == null) tag.remove("standaloneManager");
        else tag.putUUID("standaloneManager", saved.standaloneManager());
        ListTag balances = new ListTag();
        saved.balances().forEach((currency, amount) -> {
            CompoundTag entry = new CompoundTag(); entry.putString("currency", currency.name()); entry.putLong("amount", amount); balances.add(entry);
        });
        tag.put("balances", balances);
        tag.put("claims", writeAmounts(saved.claims())); tag.put("taxDue", writeAmounts(saved.taxDue()));
        ListTag activity = new ListTag();
        saved.activity().forEach((id, record) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("player", id); entry.putString("name", record.name()); entry.putInt("minutes", record.minutes()); activity.add(entry);
        });
        tag.put("activity", activity);
        ListTag history = new ListTag();
        saved.history().forEach(record -> {
            CompoundTag entry = new CompoundTag(); entry.putLong("time", record.time()); entry.putString("kind", record.kind().name());
            if (record.actor() != null) entry.putUUID("actor", record.actor());
            entry.putString("details", record.details()); entry.putString("currency", record.currency().name()); entry.putLong("amount", record.amount()); history.add(entry);
        });
        tag.put("history", history);
        if (saved.pending() == null) tag.remove("pending");
        else {
            PendingBatch pending = saved.pending();
            CompoundTag entry = new CompoundTag(); entry.putUUID("id", pending.id()); entry.putLong("closedAt", pending.closedAt());
            entry.putString("currency", pending.currency().name()); entry.putLong("salary", pending.salary()); entry.putLong("fixedTax", pending.fixedTax());
            ListTag eligible = new ListTag();
            pending.eligible().forEach((id, name) -> {
                CompoundTag citizen = new CompoundTag(); citizen.putUUID("player", id); citizen.putString("name", name); eligible.add(citizen);
            });
            entry.put("eligible", eligible); tag.put("pending", entry);
        }
        return tag;
    }

    private static Policy readPolicy(CompoundTag tag, int version) {
        if(version<4&&tag.contains("casinoTaxBasisPoints")){type(tag,"casinoTaxBasisPoints",Tag.TAG_INT);if(tag.getInt("casinoTaxBasisPoints")!=0)throw new IllegalStateException("Casino taxation cannot be loaded as an older policy format");}
        for (String key : List.of("minimumReputation", "taxBasisPoints", "periodHours", "activeMinutes", "reputationPerPublicContract", "dailyReputationLimit")) type(tag, key, Tag.TAG_INT);
        for (String key : List.of("fixedTax", "salary")) type(tag, key, Tag.TAG_LONG);
        for (String key : List.of("taxMode", "currency")) type(tag, key, Tag.TAG_STRING);
        if (version >= 2) type(tag, "marketTaxBasisPoints", Tag.TAG_INT);
        if(version>=4)type(tag,"casinoTaxBasisPoints",Tag.TAG_INT);
        return new Policy(tag.getInt("minimumReputation"), Policy.TaxMode.valueOf(tag.getString("taxMode")),
                tag.getInt("taxBasisPoints"), tag.getLong("fixedTax"), tag.getLong("salary"), tag.getInt("periodHours"),
                tag.getInt("activeMinutes"), Currency.valueOf(tag.getString("currency")),
                tag.getInt("reputationPerPublicContract"), tag.getInt("dailyReputationLimit"),
                version >= 2 ? tag.getInt("marketTaxBasisPoints") : 0, version>=4?tag.getInt("casinoTaxBasisPoints"):0);
    }

    private static CompoundTag writePolicy(Policy policy) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minimumReputation", policy.minimumReputation()); tag.putString("taxMode", policy.taxMode().name());
        tag.putInt("taxBasisPoints", policy.taxBasisPoints()); tag.putLong("fixedTax", policy.fixedTax()); tag.putLong("salary", policy.salary());
        tag.putInt("periodHours", policy.periodHours()); tag.putInt("activeMinutes", policy.activeMinutes()); tag.putString("currency", policy.currency().name());
        tag.putInt("reputationPerPublicContract", policy.reputationPerPublicContract()); tag.putInt("dailyReputationLimit", policy.dailyReputationLimit());
        tag.putInt("marketTaxBasisPoints", policy.marketTaxBasisPoints());tag.putInt("casinoTaxBasisPoints",policy.casinoTaxBasisPoints());
        return tag;
    }

    private static Map<UUID, Map<Currency, Long>> readAmounts(CompoundTag tag, String key) {
        Map<UUID, Map<Currency, Long>> result = new LinkedHashMap<>();
        for (Tag raw : list(tag, key)) {
            CompoundTag entry = (CompoundTag) raw;
            uuid(entry, "player"); type(entry, "currency", Tag.TAG_STRING); type(entry, "amount", Tag.TAG_LONG);
            Map<Currency, Long> amounts = result.computeIfAbsent(entry.getUUID("player"), id -> new EnumMap<>(Currency.class));
            if (amounts.putIfAbsent(Currency.valueOf(entry.getString("currency")), entry.getLong("amount")) != null)
                throw new IllegalStateException("Duplicate treasury amount in " + key);
        }
        return result;
    }

    private static ListTag writeAmounts(Map<UUID, Map<Currency, Long>> amounts) {
        ListTag result = new ListTag();
        amounts.forEach((id, currencies) -> currencies.forEach((currency, amount) -> {
            CompoundTag entry = new CompoundTag(); entry.putUUID("player", id); entry.putString("currency", currency.name()); entry.putLong("amount", amount); result.add(entry);
        }));
        return result;
    }

    private static ListTag list(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
            throw new IllegalStateException("Invalid treasury list: " + key);
        return list;
    }
    private static void type(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalStateException("Invalid or missing treasury field: " + key);
    }
    private static void uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw new IllegalStateException("Invalid treasury UUID: " + key);
    }
}
