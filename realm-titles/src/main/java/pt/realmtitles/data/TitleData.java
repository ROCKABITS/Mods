package pt.realmtitles.data;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import pt.realmtitles.domain.TitleLedger;
import pt.realmtitles.domain.TitleLedger.Citizen;
import pt.realmtitles.domain.TitleLedger.Definition;
import pt.realmtitles.domain.TitleLedger.Grant;

/** One overworld registry; an existing unreadable file can never reset to an empty registry. */
public final class TitleData extends SavedData {
    public static final String FILE_ID = "realm_titles";
    private TitleLedger ledger = new TitleLedger();
    private UUID steward;

    public static TitleData get(MinecraftServer server) {
        requireThread(server);
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> {
            var file = server.getWorldPath(LevelResource.ROOT).resolve("data/" + FILE_ID + ".dat");
            if (!Files.notExists(file)) throw corrupt(null);
            TitleData data = new TitleData();
            data.setDirty();
            return data;
        }, TitleData::load), FILE_ID);
    }

    public TitleLedger ledger() { return ledger; }
    public UUID steward() { return steward; }
    public void replace(TitleLedger replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (steward != null && !replacement.citizens().containsKey(steward)) throw corrupt(null);
        ledger = replacement;
        setDirty();
    }
    public void steward(UUID player) {
        Objects.requireNonNull(player, "player");
        if (!ledger.citizens().containsKey(player)) throw new IllegalStateException("gui.realmtitles.unknown_player");
        steward = player; setDirty();
    }

    public static TitleData load(CompoundTag root, HolderLookup.Provider lookup) {
        try {
            keys(root, Set.of("version", "definitions", "grants", "citizens", "steward"),
                    Set.of("version", "definitions", "grants", "citizens"));
            type(root, "version", Tag.TAG_INT);
            if (root.getInt("version") != 1) throw corrupt(null);
            ListTag definitions = list(root, "definitions", TitleLedger.MAX_DEFINITIONS);
            ListTag grants = list(root, "grants", TitleLedger.MAX_GRANTS);
            ListTag citizens = list(root, "citizens", TitleLedger.MAX_CITIZENS);
            List<Definition> definitionRows = new ArrayList<>(definitions.size());
            List<Grant> grantRows = new ArrayList<>(grants.size());
            List<Citizen> citizenRows = new ArrayList<>(citizens.size());
            for (int index = 0; index < definitions.size(); index++) {
                CompoundTag entry = definitions.getCompound(index);
                exact(entry, Set.of("id", "name", "description"));
                uuid(entry, "id"); type(entry, "name", Tag.TAG_STRING); type(entry, "description", Tag.TAG_STRING);
                definitionRows.add(new Definition(entry.getUUID("id"), entry.getString("name"), entry.getString("description")));
            }
            for (int index = 0; index < grants.size(); index++) {
                CompoundTag entry = grants.getCompound(index);
                exact(entry, Set.of("id", "owner", "titleId", "grantedAt", "expiresAt"));
                uuid(entry, "id"); uuid(entry, "owner"); uuid(entry, "titleId");
                type(entry, "grantedAt", Tag.TAG_LONG); type(entry, "expiresAt", Tag.TAG_LONG);
                grantRows.add(new Grant(entry.getUUID("id"), entry.getUUID("owner"), entry.getUUID("titleId"),
                        entry.getLong("grantedAt"), entry.getLong("expiresAt")));
            }
            for (int index = 0; index < citizens.size(); index++) {
                CompoundTag entry = citizens.getCompound(index);
                exact(entry, Set.of("id", "name", "eligible"));
                uuid(entry, "id"); type(entry, "name", Tag.TAG_STRING); type(entry, "eligible", Tag.TAG_BYTE);
                if (entry.getByte("eligible") != 0 && entry.getByte("eligible") != 1) throw corrupt(null);
                citizenRows.add(new Citizen(entry.getUUID("id"), entry.getString("name"), entry.getBoolean("eligible")));
            }
            TitleData data = new TitleData();
            data.ledger = new TitleLedger(definitionRows, grantRows, citizenRows);
            if (root.contains("steward")) {
                uuid(root, "steward"); data.steward(root.getUUID("steward"));
                data.setDirty(false);
            }
            return data;
        } catch (RuntimeException failure) { throw corrupt(failure); }
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        root.putInt("version", 1);
        if (steward != null) root.putUUID("steward", steward);
        else root.remove("steward");
        ListTag definitions = new ListTag();
        for (Definition row : ledger.definitions().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", row.id()); entry.putString("name", row.name()); entry.putString("description", row.description());
            definitions.add(entry);
        }
        ListTag grants = new ListTag();
        for (Grant row : ledger.grants().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", row.id()); entry.putUUID("owner", row.owner()); entry.putUUID("titleId", row.titleId());
            entry.putLong("grantedAt", row.grantedAt()); entry.putLong("expiresAt", row.expiresAt());
            grants.add(entry);
        }
        ListTag citizens = new ListTag();
        for (Citizen row : ledger.citizens().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", row.id()); entry.putString("name", row.name()); entry.putBoolean("eligible", row.eligible());
            citizens.add(entry);
        }
        root.put("definitions", definitions); root.put("grants", grants); root.put("citizens", citizens);
        return root;
    }

    private static ListTag list(CompoundTag tag, String key, int limit) {
        if (!(tag.get(key) instanceof ListTag rows) || rows.size() > limit
                || (rows.getElementType() != Tag.TAG_COMPOUND
                    && !(rows.isEmpty() && rows.getElementType() == Tag.TAG_END))) throw corrupt(null);
        return rows;
    }
    private static void type(CompoundTag tag, String key, int type) {
        if (tag.getTagType(key) != type) throw corrupt(null);
    }
    private static void uuid(CompoundTag tag, String key) {
        type(tag, key, Tag.TAG_INT_ARRAY);
        if (!tag.hasUUID(key)) throw corrupt(null);
    }
    private static void exact(CompoundTag tag, Set<String> keys) { keys(tag, keys, keys); }
    private static void keys(CompoundTag tag, Set<String> allowed, Set<String> required) {
        if (!allowed.containsAll(tag.getAllKeys()) || !tag.getAllKeys().containsAll(required)) throw corrupt(null);
    }
    private static void requireThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) throw new IllegalStateException("gui.realmtitles.invalid_request");
    }
    private static IllegalStateException corrupt(Throwable failure) {
        return new IllegalStateException("Cannot read realm_titles.dat. Preserve this file and restore a world backup.", failure);
    }
}
