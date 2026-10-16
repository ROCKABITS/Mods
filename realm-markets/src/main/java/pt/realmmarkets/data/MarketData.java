package pt.realmmarkets.data;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import pt.realmmarkets.domain.Currency;
import pt.realmmarkets.domain.Listing;
import pt.realmmarkets.domain.MarketLedger;
import pt.realmmarkets.domain.Stall;

/** One server registry across dimensions; no player profile files are read or edited offline. */
public final class MarketData extends SavedData {
    private static final String FILE_ID = "realm_markets";
    public MarketLedger ledger = new MarketLedger();

    public static MarketData get(MinecraftServer server) {
        if (server == null || !server.isSameThread()) throw new IllegalStateException("Market data requires the server thread.");
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> {
            // The storage layer catches decode failures and invokes the factory. Never
            // replace a corrupt existing economy registry with an empty one.
            var path = server.getWorldPath(LevelResource.ROOT).resolve("data/" + FILE_ID + ".dat");
            if (!Files.notExists(path)) {
                throw new IllegalStateException("Cannot read realm_markets.dat. Preserve it and restore a world backup.");
            }
            MarketData fresh = new MarketData();
            fresh.setDirty();
            return fresh;
        }, MarketData::load), FILE_ID);
    }

    public void replace(MarketLedger replacement) {
        ledger = Objects.requireNonNull(replacement, "replacement");
        setDirty();
    }

    public static MarketData load(CompoundTag tag, HolderLookup.Provider lookup) {
        require(tag, "version", Tag.TAG_INT);
        if (tag.getInt("version") != 1) throw new IllegalStateException("Unsupported market registry version.");
        ListTag entries = compounds(tag, "stalls", 10_000);
        List<Stall> stalls = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            uuid(entry, "id"); uuid(entry, "owner");
            require(entry, "dimension", Tag.TAG_STRING); require(entry, "position", Tag.TAG_LONG);
            require(entry, "title", Tag.TAG_STRING); require(entry, "currency", Tag.TAG_STRING);
            require(entry, "earnings", Tag.TAG_LONG);
            ListTag offers = compounds(entry, "listings", 8);
            List<Listing> listings = new ArrayList<>(offers.size());
            for (int slot = 0; slot < offers.size(); slot++) {
                CompoundTag offer = offers.getCompound(slot);
                require(offer, "slot", Tag.TAG_INT); require(offer, "item", Tag.TAG_STRING);
                require(offer, "stock", Tag.TAG_INT); require(offer, "unitPrice", Tag.TAG_LONG);
                listings.add(new Listing(offer.getInt("slot"), offer.getString("item"),
                        offer.getInt("stock"), offer.getLong("unitPrice")));
            }
            stalls.add(new Stall(entry.getUUID("id"), entry.getUUID("owner"), entry.getString("dimension"),
                    entry.getLong("position"), entry.getString("title"), Currency.valueOf(entry.getString("currency")),
                    entry.getLong("earnings"), listings));
        }
        MarketData data = new MarketData();
        data.ledger = new MarketLedger(stalls);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("version", 1);
        ListTag entries = new ListTag();
        for (Stall stall : ledger.stalls()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", stall.id()); entry.putUUID("owner", stall.owner());
            entry.putString("dimension", stall.dimension()); entry.putLong("position", stall.position());
            entry.putString("title", stall.title()); entry.putString("currency", stall.currency().name());
            entry.putLong("earnings", stall.earnings());
            ListTag offers = new ListTag();
            for (Listing offer : stall.listings()) {
                CompoundTag listing = new CompoundTag();
                listing.putInt("slot", offer.slot()); listing.putString("item", offer.item());
                listing.putInt("stock", offer.stock()); listing.putLong("unitPrice", offer.unitPrice());
                offers.add(listing);
            }
            entry.put("listings", offers);
            entries.add(entry);
        }
        tag.put("stalls", entries);
        return tag;
    }

    private static ListTag compounds(CompoundTag tag, String key, int limit) {
        if (!(tag.get(key) instanceof ListTag list) || list.size() > limit
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Invalid market registry list: " + key);
        }
        return list;
    }

    private static void uuid(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_INT_ARRAY);
        if (!tag.hasUUID(key)) throw new IllegalStateException("Invalid market registry UUID: " + key);
    }

    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalStateException("Invalid market registry field: " + key);
    }
}
