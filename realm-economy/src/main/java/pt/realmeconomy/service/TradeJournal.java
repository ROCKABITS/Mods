package pt.realmeconomy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import pt.realmeconomy.compat.ChroniclesBridge;
import pt.realmeconomy.data.EconomyData;
import pt.realmeconomy.data.DurableFiles;

/** One recoverable intent covering every wallet file vanilla can load. Uncertain state freezes. */
public final class TradeJournal {
    public static final String RECEIPT_KEY="realmvillagereconomy_receipt";
    private TradeJournal() {}

    public static void commit(ServerPlayer player,CompoundTag intent) {
        requirePlayer(player);EconomyData data=EconomyData.get(player.getServer());data.requireAvailable();
        if(!intent.getUUID("player").equals(player.getUUID()))throw failure("invalid_request");
        data.prepare(intent);recover(player);
    }

    /** Called on login and before a physical session opens. Only the intended online player can resume. */
    public static void recover(ServerPlayer player) {
        requirePlayer(player);
        EconomyData data=EconomyData.get(player.getServer());CompoundTag intent=data.journal();
        if(intent==null||!intent.getUUID("player").equals(player.getUUID()))return;
        try {
            if(intent.getLong("before")>=0&&!ChroniclesBridge.available(player))throw failure("chronicles_unavailable");
            CompoundTag persistent=player.getPersistentData();
            Tag raw=persistent.get(RECEIPT_KEY);
            boolean receipted=raw instanceof CompoundTag receipt&&matches(receipt,intent);
            long balance=intent.getLong("before")>=0?ChroniclesBridge.balance(player):-1;
            if(!receipted) {
                if(intent.contains("seedBefore")&&player.getEnchantmentSeed()!=intent.getInt("seedBefore"))throw failure("wallet_ambiguous");
                if(balance!=intent.getLong("before")||!TradeItems.inventory(player).equals(intent.getList("inventoryBefore",Tag.TAG_COMPOUND)))throw failure("wallet_ambiguous");
                // Profile and receipt are serialized in the same player NBT snapshot.
                if(intent.getLong("before")>=0)ChroniclesBridge.setBalance(player,intent.getLong("before"),intent.getLong("after"));
                TradeItems.install(player,intent.getList("inventoryAfter",Tag.TAG_COMPOUND));if(intent.contains("seedAfter"))((pt.realmeconomy.mixin.PlayerSeedAccess)player).realmEconomy$seed(intent.getInt("seedAfter"));
                persistent.put(RECEIPT_KEY,receipt(intent));
            }
            if((intent.getLong("after")>=0&&ChroniclesBridge.balance(player)!=intent.getLong("after"))||!TradeItems.inventory(player).equals(intent.getList("inventoryAfter",Tag.TAG_COMPOUND)))throw failure("wallet_ambiguous");
            if(intent.contains("seedAfter")&&player.getEnchantmentSeed()!=intent.getInt("seedAfter"))throw failure("wallet_ambiguous");
            savePlayer(player);
            if(!savedReceipt(player,intent))throw failure("storage");
            var playerFile=player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID()+".dat");
            forceSavedFile(playerFile);
            if(player.getServer().isSingleplayerOwner(player.getGameProfile())) {
                // Vanilla prefers level.dat/Data/Player over UUID.dat for the integrated host.
                // The virtual player-list save above refreshes the host's in-memory cache only;
                // persist the same receipt to the file a normal singleplayer/LAN restart loads.
                saveHost(player);
                if(!savedHostReceipt(player,intent))throw failure("storage");
                forceSavedFile(player.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE));
            }
            data.complete(intent.getUUID("id"));

        } catch(RuntimeException|LinkageError failure) {
            com.mojang.logging.LogUtils.getLogger().error("Villager trade intent could not be reconciled; preserving its receipt",failure);
            data.freeze();try{player.connection.disconnect(net.minecraft.network.chat.Component.translatable("error.realmeconomy.frozen"));}catch(RuntimeException ignored){}throw new IllegalStateException("error.realmeconomy.frozen",failure);
        }
        // Inventory listeners can award recipes/advancements (and optional Realm Rewards money).
        // Run them only after the complete financial receipt is committed, never during an intent.
        try{if(intent.getLong("after")>=0)ChroniclesBridge.sync(player);player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();}
        catch(RuntimeException|LinkageError secondary){com.mojang.logging.LogUtils.getLogger().warn("Trade is committed; an inventory notification failed",secondary);}
    }
    public static boolean savedReceipt(ServerPlayer player,CompoundTag intent) {
        var path=player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID()+".dat");
        if(!Files.isRegularFile(path))return false;
        try {
            return matchesSavedPlayer(NbtIo.readCompressed(path,NbtAccounter.create(64L*1024*1024)),player,intent);
        }catch(IOException|RuntimeException failure){return false;}
    }
    /** Verifies the actual restart source for the singleplayer/LAN owner, never a timestamp heuristic. */
    public static boolean savedHostReceipt(ServerPlayer player,CompoundTag intent) {
        if(!player.getServer().isSingleplayerOwner(player.getGameProfile()))return false;
        var path=player.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE);
        if(!Files.isRegularFile(path))return false;
        try {
            CompoundTag root=NbtIo.readCompressed(path,NbtAccounter.create(104857600L));
            return root.get("Data")instanceof CompoundTag data&&data.get("Player")instanceof CompoundTag host
                    &&matchesSavedPlayer(host,player,intent);
        }catch(IOException|RuntimeException failure){return false;}
    }
    private static boolean matchesSavedPlayer(CompoundTag root,ServerPlayer player,CompoundTag intent) {
        if(intent.contains("seedAfter")&&root.getInt("XpSeed")!=intent.getInt("seedAfter"))return false;
        if(!root.hasUUID("UUID")||!root.getUUID("UUID").equals(player.getUUID()))return false;
        if(intent.getLong("after")>=0){
            if(!(root.get("neoforge:attachments") instanceof CompoundTag attachments)||!(attachments.get("cronicasrp:profile") instanceof CompoundTag profile)||!profile.getBoolean("created")||profile.getInt("moneyVersion")!=1||!profile.contains("balance",Tag.TAG_LONG)||profile.getLong("balance")!=intent.getLong("after"))return false;
        }
        if(!TradeItems.savedInventory(player,root).equals(intent.getList("inventoryAfter",Tag.TAG_COMPOUND)))return false;
        // NeoForge persists IEntityExtension persistent data at the root NeoForgeData key.
        // Inspect explicit root containers only; a book/item custom tag is never a wallet receipt.
        for(String key:java.util.List.of("ForgeData","NeoForgeData")) {
            if(root.get(key)instanceof CompoundTag data&&data.get(RECEIPT_KEY)instanceof CompoundTag receipt&&matches(receipt,intent))return true;
        }
        return false;
    }
    private static void forceSavedFile(Path path) {
        try {DurableFiles.forceFile(path);DurableFiles.forceDirectory(path.getParent());}
        catch(IOException failure){throw new IllegalStateException("error.realmeconomy.storage",failure);}
    }
    private static void savePlayer(ServerPlayer player) {
        // Invoke the vanilla virtual hook so IntegratedPlayerList also refreshes the LAN owner's
        // cached level.dat player snapshot. PlayerList.save is protected, not a public mod API.
        try {
            java.lang.reflect.Method save=net.minecraft.server.players.PlayerList.class.getDeclaredMethod("save",ServerPlayer.class);
            if(!save.trySetAccessible())throw failure("storage");
            save.invoke(player.getServer().getPlayerList(),player);
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalStateException("error.realmeconomy.storage",failure);}
    }
    private static void saveHost(ServerPlayer player) {
        // Save only the world metadata using vanilla's atomic replacement and mod save hooks.
        // A full saveEverything here would synchronously save every chunk on every casino action.
        try {
            var server=player.getServer();
            java.lang.reflect.Field field=net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");
            if(!field.trySetAccessible()||!(field.get(server)instanceof LevelStorageSource.LevelStorageAccess storage))throw failure("storage");
            storage.saveDataTag(server.registryAccess(),server.getWorldData(),player.saveWithoutId(new CompoundTag()));
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalStateException("error.realmeconomy.storage",failure);}
    }
    private static CompoundTag receipt(CompoundTag intent) {
        CompoundTag tag=new CompoundTag();tag.putUUID("id",intent.getUUID("id"));tag.putUUID("player",intent.getUUID("player"));tag.putLong("before",intent.getLong("before"));tag.putLong("after",intent.getLong("after"));return tag;
    }
    private static boolean matches(CompoundTag receipt,CompoundTag intent) {
        return receipt.getAllKeys().equals(java.util.Set.of("id","player","before","after"))&&receipt.hasUUID("id")&&receipt.hasUUID("player")
                &&receipt.contains("before",Tag.TAG_LONG)&&receipt.contains("after",Tag.TAG_LONG)
                &&receipt.getUUID("id").equals(intent.getUUID("id"))&&receipt.getUUID("player").equals(intent.getUUID("player"))
                &&receipt.getLong("before")==intent.getLong("before")&&receipt.getLong("after")==intent.getLong("after");
    }
    private static void requirePlayer(ServerPlayer player) {
        if(player==null||player.getServer()==null||!player.getServer().isSameThread())throw failure("invalid_request");
    }
    private static IllegalStateException failure(String key){return new IllegalStateException("error.realmeconomy."+key);}
}
