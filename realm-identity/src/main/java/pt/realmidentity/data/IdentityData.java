package pt.realmidentity.data;

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
import pt.realmidentity.domain.Document;
import pt.realmidentity.domain.IdentityLedger;
import pt.realmidentity.domain.ProfileSnapshot;

/** Every dimension uses the overworld's realm_identity.dat registry. */
public final class IdentityData extends SavedData {
    private static final String FILE_ID = "realm_identity";
    public IdentityLedger ledger = new IdentityLedger();

    public static IdentityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> {
            // DimensionDataStorage catches decoder errors and invokes this factory. A corrupt
            // existing registry must never silently become a new world seal or serial sequence.
            var file = server.getWorldPath(LevelResource.ROOT).resolve("data/" + FILE_ID + ".dat");
            if (!Files.notExists(file)) {
                throw new IllegalStateException("Cannot read realm_identity.dat. Preserve the file and restore a world backup.");
            }
            IdentityData fresh = new IdentityData();
            fresh.setDirty(); // Persist the world seal even before the first document is issued.
            return fresh;
        }, IdentityData::load), FILE_ID);
    }

    public void replace(IdentityLedger replacement) {
        ledger = Objects.requireNonNull(replacement, "replacement");
        setDirty();
    }

    public static IdentityData load(CompoundTag tag, HolderLookup.Provider lookup) {
        requireType(tag, "version", Tag.TAG_INT);
        if (tag.getInt("version") != 1) throw new IllegalStateException("Unsupported identity registry version.");
        requireUuid(tag, "seal");
        requireType(tag, "nextSerial", Tag.TAG_LONG);
        if (!(tag.get("documents") instanceof ListTag documents)
                || (!documents.isEmpty() && documents.getElementType() != Tag.TAG_COMPOUND)
                || documents.size() > IdentityLedger.MAX_OWNERS) {
            throw new IllegalStateException("Invalid identity registry document list.");
        }
        List<Document> records = new ArrayList<>(documents.size());
        try {
            for (int i = 0; i < documents.size(); i++) {
                CompoundTag entry = documents.getCompound(i);
                requireUuid(entry, "id");
                requireUuid(entry, "owner");
                requireType(entry, "serial", Tag.TAG_LONG);
                requireType(entry, "issuedAt", Tag.TAG_LONG);
                requireType(entry, "profile", Tag.TAG_COMPOUND);
                CompoundTag profile = entry.getCompound("profile");
                for (String key : List.of("source", "name", "race", "occupation", "signature", "signatureDrawing")) {
                    requireType(profile, key, Tag.TAG_STRING);
                }
                requireType(profile, "age", Tag.TAG_INT);
                ProfileSnapshot snapshot = new ProfileSnapshot(ProfileSnapshot.Source.valueOf(profile.getString("source")),
                        profile.getString("name"), profile.getInt("age"), profile.getString("race"),
                        profile.getString("occupation"), profile.getString("signature"), profile.getString("signatureDrawing"));
                records.add(new Document(entry.getUUID("id"), entry.getUUID("owner"), entry.getLong("serial"),
                        entry.getLong("issuedAt"), snapshot));
            }
            IdentityData data = new IdentityData();
            data.ledger = new IdentityLedger(tag.getUUID("seal"), tag.getLong("nextSerial"), records);
            return data;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Invalid identity registry; preserve the world backup.", exception);
        }
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        IdentityLedger.Snapshot snapshot = ledger.snapshot();
        tag.putInt("version", 1);
        tag.putUUID("seal", snapshot.seal());
        tag.putLong("nextSerial", snapshot.nextSerial());
        ListTag documents = new ListTag();
        for (Document document : snapshot.documents()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", document.id());
            entry.putUUID("owner", document.owner());
            entry.putLong("serial", document.serial());
            entry.putLong("issuedAt", document.issuedAt());
            ProfileSnapshot profile = document.profile();
            CompoundTag identity = new CompoundTag();
            identity.putString("source", profile.source().name());
            identity.putString("name", profile.name());
            identity.putInt("age", profile.age());
            identity.putString("race", profile.race());
            identity.putString("occupation", profile.occupation());
            identity.putString("signature", profile.signature());
            identity.putString("signatureDrawing", profile.signatureDrawing());
            entry.put("profile", identity);
            documents.add(entry);
        }
        tag.put("documents", documents);
        return tag;
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalStateException("Invalid or missing identity registry field: " + key);
    }

    private static void requireUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw new IllegalStateException("Invalid or missing identity registry UUID: " + key);
    }
}
