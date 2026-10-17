package pt.realmrewards.data;

import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import pt.realmrewards.domain.RewardLedger;

public final class RewardData extends SavedData {
    public RewardLedger ledger = new RewardLedger();
    public static RewardData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(() -> {
            if (!Files.notExists(server.getWorldPath(LevelResource.ROOT).resolve("data/realm_rewards.dat")))
                throw new IllegalStateException("Cannot read realm_rewards.dat; restore a world backup, never clear reward history.");
            var fresh = new RewardData(); fresh.setDirty(); return fresh;
        }, RewardData::load), "realm_rewards");
    }
    public void replace(RewardLedger next) { ledger = Objects.requireNonNull(next); setDirty(); }
    public static RewardData load(CompoundTag root, HolderLookup.Provider lookup) {
        require(root, "version", Tag.TAG_INT);
        if (root.getInt("version") != 1) throw new IllegalStateException("Unsupported reward data version");
        require(root, "defaults", Tag.TAG_COMPOUND);
        EnumMap<RewardLedger.Kind,Long> defaults = new EnumMap<>(RewardLedger.Kind.class);
        CompoundTag rules = root.getCompound("defaults");
        for (var kind : RewardLedger.Kind.values()) { require(rules, kind.name(), Tag.TAG_LONG); defaults.put(kind, rules.getLong(kind.name())); }
        Map<String,Long> overrides = new HashMap<>();
        ListTag entries = list(root, "overrides", Tag.TAG_COMPOUND, RewardLedger.MAX_ENTRIES);
        for (int i=0;i<entries.size();i++) {
            CompoundTag row=entries.getCompound(i); require(row,"id",Tag.TAG_STRING); require(row,"amount",Tag.TAG_LONG);
            if (overrides.put(row.getString("id"), row.getLong("amount")) != null) throw new IllegalStateException("Duplicate reward rule");
        }
        Map<UUID,RewardLedger.Account> accounts = new HashMap<>();
        ListTag players = list(root,"players",Tag.TAG_COMPOUND,RewardLedger.MAX_PLAYERS);
        for (int i=0;i<players.size();i++) {
            CompoundTag row=players.getCompound(i);
            if (!row.hasUUID("id")) throw new IllegalStateException("Invalid reward player UUID");
            require(row,"pending",Tag.TAG_LONG);
            ListTag seen=list(row,"completed",Tag.TAG_STRING,RewardLedger.MAX_ENTRIES);
            Set<String> ids=new HashSet<>();
            for (int j=0;j<seen.size();j++) if (!ids.add(seen.getString(j))) throw new IllegalStateException("Duplicate completed advancement");
            if (accounts.put(row.getUUID("id"),new RewardLedger.Account(ids,row.getLong("pending"))) != null) throw new IllegalStateException("Duplicate reward player");
        }
        var data=new RewardData(); data.ledger=new RewardLedger(defaults,overrides,accounts); return data;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        root.putInt("version",1);
        CompoundTag defaults=new CompoundTag(); ledger.defaults().forEach((k,v)->defaults.putLong(k.name(),v)); root.put("defaults",defaults);
        ListTag overrides=new ListTag();
        ledger.overrides().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            CompoundTag row=new CompoundTag(); row.putString("id",e.getKey()); row.putLong("amount",e.getValue()); overrides.add(row);
        }); root.put("overrides",overrides);
        ListTag players=new ListTag();
        ledger.accounts().forEach((id,account)->{
            CompoundTag row=new CompoundTag(); row.putUUID("id",id); row.putLong("pending",account.pending());
            ListTag seen=new ListTag(); account.completed().stream().sorted().forEach(value->seen.add(StringTag.valueOf(value))); row.put("completed",seen); players.add(row);
        }); root.put("players",players); return root;
    }
    private static void require(CompoundTag tag,String key,int type) {
        if (!tag.contains(key,type)) throw new IllegalStateException("Missing or malformed reward data: "+key);
    }
    private static ListTag list(CompoundTag tag,String key,int type,int cap) {
        if (!(tag.get(key) instanceof ListTag list) || list.size()>cap || (!list.isEmpty() && list.getElementType()!=type)) throw new IllegalStateException("Invalid reward list: "+key);
        return list;
    }
}
