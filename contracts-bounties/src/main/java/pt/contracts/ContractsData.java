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
        if (tag.getInt("version") != 1) throw new IllegalStateException("Unsupported contracts ledger version; preserve the world backup.");
        for (String key : List.of("contracts", "credits", "deliveries")) {
            if (!(tag.get(key) instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
                throw new IllegalStateException("Invalid contracts ledger list: " + key);
        }
        List<Contract> contracts = new ArrayList<>();
        ListTag entries = tag.getList("contracts", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag c = entries.getCompound(i);
            contracts.add(new Contract(c.getUUID("id"), c.getUUID("author"), c.getString("authorName"),
                    c.getString("title"), c.getString("item"), c.getInt("quantity"), c.getLong("reward"),
                    Currency.valueOf(c.getString("currency")), Status.valueOf(c.getString("status")),
                    c.hasUUID("worker") ? c.getUUID("worker") : null));
        }
        Map<UUID, Map<Currency, Long>> credits = new LinkedHashMap<>();
        entries = tag.getList("credits", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag c = entries.getCompound(i);
            var balances = credits.computeIfAbsent(c.getUUID("player"), id -> new EnumMap<>(Currency.class));
            if (balances.put(Currency.valueOf(c.getString("currency")), c.getLong("amount")) != null)
                throw new IllegalStateException("Duplicate credit in contracts ledger.");
        }
        Map<UUID, List<Delivery>> deliveries = new LinkedHashMap<>();
        entries = tag.getList("deliveries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag c = entries.getCompound(i);
            deliveries.computeIfAbsent(c.getUUID("player"), id -> new ArrayList<>()).add(new Delivery(c.getString("item"), c.getInt("quantity")));
        }
        ContractsData data = new ContractsData();
        data.ledger = new Ledger(contracts, credits, deliveries);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("version", 1);
        ListTag contracts = new ListTag();
        for (Contract c : ledger.list()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", c.id()); t.putUUID("author", c.author()); t.putString("authorName", c.authorName());
            t.putString("title", c.title()); t.putString("item", c.item()); t.putInt("quantity", c.quantity());
            t.putLong("reward", c.reward()); t.putString("currency", c.currency().name()); t.putString("status", c.status().name());
            if (c.worker() != null) t.putUUID("worker", c.worker());
            contracts.add(t);
        }
        tag.put("contracts", contracts);
        ListTag credits = new ListTag();
        ledger.creditsSnapshot().forEach((id, amounts) -> amounts.forEach((currency, amount) -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id); t.putString("currency", currency.name()); t.putLong("amount", amount); credits.add(t);
        }));
        tag.put("credits", credits);
        ListTag deliveries = new ListTag();
        ledger.deliveriesSnapshot().forEach((id, items) -> items.forEach(item -> {
            CompoundTag t = new CompoundTag(); t.putUUID("player", id); t.putString("item", item.item()); t.putInt("quantity", item.quantity()); deliveries.add(t);
        }));
        tag.put("deliveries", deliveries);
        return tag;
    }
}
