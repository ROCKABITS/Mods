package pt.contracts;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import pt.contracts.domain.Ledger;
import pt.contracts.domain.Ledger.Contract;
import pt.contracts.domain.Ledger.Currency;
import pt.contracts.domain.Ledger.Delivery;
import pt.contracts.domain.Ledger.DailyProgress;
import pt.contracts.domain.Ledger.Funding;
import pt.contracts.domain.Ledger.Progress;
import pt.contracts.domain.Ledger.Status;

/** All dimensions and all boards share a single overworld escrow ledger. */
public final class ContractsData extends SavedData {
    public Ledger ledger = new Ledger();

    public static ContractsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> {
                    // DimensionDataStorage catches load exceptions. Never let its fallback replace
                    // an existing but unreadable/corrupt ledger with an empty one.
                    var file = server.getWorldPath(LevelResource.ROOT).resolve("data/contracts_ledger.dat");
                    if (!Files.notExists(file)) throw new IllegalStateException("Não foi possível ler contracts_ledger.dat. Preserva o ficheiro e recupera uma cópia de segurança.");
                    return new ContractsData();
                }, ContractsData::load), "contracts_ledger");
    }

    public static ContractsData load(CompoundTag tag, HolderLookup.Provider lookup) {
        requireType(tag, "version", Tag.TAG_INT);
        int version = tag.getInt("version");
        if (version != 1 && version != 2) throw new IllegalStateException("Unsupported contracts ledger version; preserve the world backup.");
        for (String key : version == 1 ? List.of("contracts", "credits", "deliveries")
                : List.of("contracts", "credits", "deliveries", "progress", "daily")) {
            if (!(tag.get(key) instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
                throw new IllegalStateException("Invalid contracts ledger list: " + key);
        }
        List<Contract> contracts = new ArrayList<>();
        ListTag entries = tag.getList("contracts", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag c = entries.getCompound(i);
            for (String key : List.of("id", "author")) requireUuid(c, key);
            for (String key : List.of("authorName", "title", "item", "currency", "status")) requireType(c, key, Tag.TAG_STRING);
            requireType(c, "quantity", Tag.TAG_INT);
            requireType(c, "reward", Tag.TAG_LONG);
            if (c.contains("worker")) requireUuid(c, "worker");
            UUID author = c.getUUID("author");
            Funding funding = Funding.PLAYER;
            UUID issuer = author;
            int tax = 0, reputation = 0, limit = 0;
            if (version == 2) {
                requireType(c, "funding", Tag.TAG_STRING);
                requireUuid(c, "issuer");
                for (String key : List.of("taxBasisPoints", "reputationReward", "dailyReputationLimit")) requireType(c, key, Tag.TAG_INT);
                funding = Funding.valueOf(c.getString("funding"));
                issuer = c.getUUID("issuer");
                tax = c.getInt("taxBasisPoints");
                reputation = c.getInt("reputationReward");
                limit = c.getInt("dailyReputationLimit");
            }
            contracts.add(new Contract(c.getUUID("id"), author, c.getString("authorName"),
                    c.getString("title"), c.getString("item"), c.getInt("quantity"), c.getLong("reward"),
                    Currency.valueOf(c.getString("currency")), Status.valueOf(c.getString("status")),
                    c.hasUUID("worker") ? c.getUUID("worker") : null, funding, issuer, tax, reputation, limit));
        }
        Map<UUID, Map<Currency, Long>> credits = new LinkedHashMap<>();
        entries = tag.getList("credits", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag c = entries.getCompound(i);
            requireUuid(c, "player");
            requireType(c, "currency", Tag.TAG_STRING);
            requireType(c, "amount", Tag.TAG_LONG);
            var balances = credits.computeIfAbsent(c.getUUID("player"), id -> new EnumMap<>(Currency.class));
            if (balances.put(Currency.valueOf(c.getString("currency")), c.getLong("amount")) != null)
                throw new IllegalStateException("Duplicate credit in contracts ledger.");
        }
        Map<UUID, List<Delivery>> deliveries = new LinkedHashMap<>();
        entries = tag.getList("deliveries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag c = entries.getCompound(i);
            requireUuid(c, "player");
            requireType(c, "item", Tag.TAG_STRING);
            requireType(c, "quantity", Tag.TAG_INT);
            deliveries.computeIfAbsent(c.getUUID("player"), id -> new ArrayList<>()).add(new Delivery(c.getString("item"), c.getInt("quantity")));
        }
        Map<UUID, Progress> progress = new LinkedHashMap<>();
        Map<UUID, DailyProgress> daily = new LinkedHashMap<>();
        if (version == 2) {
            entries = tag.getList("progress", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag c = entries.getCompound(i);
                requireUuid(c, "player");
                requireType(c, "reputation", Tag.TAG_INT);
                requireType(c, "missions", Tag.TAG_INT);
                if (progress.putIfAbsent(c.getUUID("player"), new Progress(c.getInt("reputation"), c.getInt("missions"))) != null)
                    throw new IllegalStateException("Duplicate pending progress in contracts ledger.");
            }
            entries = tag.getList("daily", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag c = entries.getCompound(i);
                requireUuid(c, "player");
                requireType(c, "day", Tag.TAG_LONG);
                requireType(c, "reputation", Tag.TAG_INT);
                if (daily.putIfAbsent(c.getUUID("player"), new DailyProgress(c.getLong("day"), c.getInt("reputation"))) != null)
                    throw new IllegalStateException("Duplicate daily progress in contracts ledger.");
            }
        }
        ContractsData data = new ContractsData();
        data.ledger = new Ledger(contracts, credits, deliveries, progress, daily);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        Ledger snapshot = ledger.copy();
        tag.putInt("version", 2);
        ListTag contracts = new ListTag();
        for (Contract c : snapshot.list()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", c.id()); t.putUUID("author", c.author()); t.putString("authorName", c.authorName());
            t.putString("title", c.title()); t.putString("item", c.item()); t.putInt("quantity", c.quantity());
            t.putLong("reward", c.reward()); t.putString("currency", c.currency().name()); t.putString("status", c.status().name());
            t.putString("funding", c.funding().name()); t.putUUID("issuer", c.issuer());
            t.putInt("taxBasisPoints", c.taxBasisPoints()); t.putInt("reputationReward", c.reputationReward());
            t.putInt("dailyReputationLimit", c.dailyReputationLimit());
            if (c.worker() != null) t.putUUID("worker", c.worker());
            contracts.add(t);
        }
        tag.put("contracts", contracts);
        ListTag credits = new ListTag();
        snapshot.creditsSnapshot().forEach((id, amounts) -> amounts.forEach((currency, amount) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id); t.putString("currency", currency.name()); t.putLong("amount", amount); credits.add(t);
        }));
        tag.put("credits", credits);
        ListTag deliveries = new ListTag();
        snapshot.deliveriesSnapshot().forEach((id, items) -> items.forEach(item -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id); t.putString("item", item.item()); t.putInt("quantity", item.quantity()); deliveries.add(t);
        }));
        tag.put("deliveries", deliveries);
        ListTag progress = new ListTag();
        snapshot.progressSnapshot().forEach((id, pending) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id);
            t.putInt("reputation", pending.reputation()); t.putInt("missions", pending.missions()); progress.add(t);
        });
        tag.put("progress", progress);
        ListTag daily = new ListTag();
        snapshot.dailySnapshot().forEach((id, earned) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id);
            t.putLong("day", earned.day()); t.putInt("reputation", earned.reputation()); daily.add(t);
        });
        tag.put("daily", daily);
        return tag;
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalStateException("Invalid or missing contracts ledger field: " + key);
    }

    private static void requireUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw new IllegalStateException("Invalid or missing contracts ledger UUID: " + key);
    }
}
