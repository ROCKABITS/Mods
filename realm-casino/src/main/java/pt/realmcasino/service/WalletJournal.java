package pt.realmcasino.service;

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
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.data.CasinoData;
import pt.realmcasino.data.GameState;
import pt.realmcasino.data.DurableFiles;
import pt.realmcasino.domain.CasinoLedger;

/** One recoverable intent covering every wallet file vanilla can load. Uncertain state freezes. */
public final class WalletJournal {
    public static final String RECEIPT_KEY="realmcasino_receipt";
    private WalletJournal() {}

    public static void transfer(ServerPlayer player,long delta,String kind,CasinoLedger next,Map<UUID,GameState> states) {
        requirePlayer(player);
        CasinoData data=CasinoData.get(player.getServer());data.requireAvailable();
        long before=ChroniclesBridge.balance(player), after;
        try {after=Math.addExact(before,delta);}catch(ArithmeticException failure){throw failure("wallet_full");}
        if(after<0)throw failure("insufficient_funds");
        if(after>ChroniclesBridge.MAX_CENTS)throw failure("wallet_full");
        data.prepare(new CasinoData.Journal(UUID.randomUUID(),player.getUUID(),before,after,kind,next,states));
        recover(player);
    }

    /** Called on login and before a physical session opens. Only the intended online player can resume. */
    public static void recover(ServerPlayer player) {
        requirePlayer(player);
        CasinoData data=CasinoData.get(player.getServer());CasinoData.Journal intent=data.journal();
        if(intent==null||!intent.player().equals(player.getUUID()))return;
        try {
            if(!ChroniclesBridge.available(player))throw failure("chronicles_unavailable");
            CompoundTag persistent=player.getPersistentData();
            Tag raw=persistent.get(RECEIPT_KEY);
            boolean receipted=raw instanceof CompoundTag receipt&&matches(receipt,intent);
            long balance=ChroniclesBridge.balance(player);
            if(!receipted) {
                if(balance!=intent.before())throw failure("wallet_ambiguous");
                // Profile and receipt are serialized in the same player NBT snapshot.
                ChroniclesBridge.setBalance(player,intent.before(),intent.after());
                persistent.put(RECEIPT_KEY,receipt(intent));
            }
            savePlayer(player);
            if(!savedReceipt(player,intent))throw failure("storage_unavailable");
            var playerFile=player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID()+".dat");
            forceSavedFile(playerFile);
            if(player.getServer().isSingleplayerOwner(player.getGameProfile())) {
                // Vanilla prefers level.dat/Data/Player over UUID.dat for the integrated host.
                // The virtual player-list save above refreshes the host's in-memory cache only;
                // persist the same receipt to the file a normal singleplayer/LAN restart loads.
                saveHost(player);
                if(!savedHostReceipt(player,intent))throw failure("storage_unavailable");
                forceSavedFile(player.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE));
            }
            data.complete(intent.id());
            ChroniclesBridge.sync(player);
        } catch(RuntimeException|LinkageError failure) {
            data.freeze();throw new IllegalStateException("error.realmcasino.ledger_frozen",failure);
        }
    }
    public static boolean savedReceipt(ServerPlayer player,CasinoData.Journal intent) {
        var path=player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID()+".dat");
        if(!Files.isRegularFile(path))return false;
        try {
            return matchesSavedPlayer(NbtIo.readCompressed(path,NbtAccounter.create(64L*1024*1024)),player,intent);
        }catch(IOException|RuntimeException failure){return false;}
    }
    /** Verifies the actual restart source for the singleplayer/LAN owner, never a timestamp heuristic. */
    public static boolean savedHostReceipt(ServerPlayer player,CasinoData.Journal intent) {
        if(!player.getServer().isSingleplayerOwner(player.getGameProfile()))return false;
        var path=player.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE);
        if(!Files.isRegularFile(path))return false;
        try {
            CompoundTag root=NbtIo.readCompressed(path,NbtAccounter.create(104857600L));
            return root.get("Data")instanceof CompoundTag data&&data.get("Player")instanceof CompoundTag host
                    &&matchesSavedPlayer(host,player,intent);
        }catch(IOException|RuntimeException failure){return false;}
    }
    private static boolean matchesSavedPlayer(CompoundTag root,ServerPlayer player,CasinoData.Journal intent) {
        if(!root.hasUUID("UUID")||!root.getUUID("UUID").equals(player.getUUID()))return false;
        if(!(root.get("neoforge:attachments")instanceof CompoundTag attachments)
                ||!(attachments.get("cronicasrp:profile")instanceof CompoundTag profile)
                ||!profile.getBoolean("created")||profile.getInt("moneyVersion")!=1
                ||(profile.contains("balance")&&!profile.contains("balance",Tag.TAG_LONG))
                ||profile.getLong("balance")!=ChroniclesBridge.balance(player))return false;
        // NeoForge persists IEntityExtension persistent data at the root NeoForgeData key.
        // Inspect explicit root containers only; a book/item custom tag is never a wallet receipt.
        for(String key:java.util.List.of("ForgeData","NeoForgeData")) {
            if(root.get(key)instanceof CompoundTag data&&data.get(RECEIPT_KEY)instanceof CompoundTag receipt&&matches(receipt,intent))return true;
        }
        return false;
    }
    private static void forceSavedFile(Path path) {
        try {DurableFiles.forceFile(path);DurableFiles.forceDirectory(path.getParent());}
        catch(IOException failure){throw new IllegalStateException("error.realmcasino.storage_unavailable",failure);}
    }
    private static void savePlayer(ServerPlayer player) {
        // Invoke the vanilla virtual hook so IntegratedPlayerList also refreshes the LAN owner's
        // cached level.dat player snapshot. PlayerList.save is protected, not a public mod API.
        try {
            java.lang.reflect.Method save=net.minecraft.server.players.PlayerList.class.getDeclaredMethod("save",ServerPlayer.class);
            if(!save.trySetAccessible())throw failure("storage_unavailable");
            save.invoke(player.getServer().getPlayerList(),player);
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalStateException("error.realmcasino.storage_unavailable",failure);}
    }
    private static void saveHost(ServerPlayer player) {
        // Save only the world metadata using vanilla's atomic replacement and mod save hooks.
        // A full saveEverything here would synchronously save every chunk on every casino action.
        try {
            var server=player.getServer();
            java.lang.reflect.Field field=net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");
            if(!field.trySetAccessible()||!(field.get(server)instanceof LevelStorageSource.LevelStorageAccess storage))throw failure("storage_unavailable");
            storage.saveDataTag(server.registryAccess(),server.getWorldData(),player.saveWithoutId(new CompoundTag()));
        }catch(ReflectiveOperationException|LinkageError failure){throw new IllegalStateException("error.realmcasino.storage_unavailable",failure);}
    }
    private static CompoundTag receipt(CasinoData.Journal intent) {
        CompoundTag tag=new CompoundTag();tag.putUUID("id",intent.id());tag.putUUID("player",intent.player());tag.putLong("before",intent.before());tag.putLong("after",intent.after());return tag;
    }
    private static boolean matches(CompoundTag receipt,CasinoData.Journal intent) {
        return receipt.getAllKeys().equals(java.util.Set.of("id","player","before","after"))&&receipt.hasUUID("id")&&receipt.hasUUID("player")
                &&receipt.contains("before",Tag.TAG_LONG)&&receipt.contains("after",Tag.TAG_LONG)
                &&receipt.getUUID("id").equals(intent.id())&&receipt.getUUID("player").equals(intent.player())
                &&receipt.getLong("before")==intent.before()&&receipt.getLong("after")==intent.after();
    }
    private static void requirePlayer(ServerPlayer player) {
        if(player==null||player.getServer()==null||!player.getServer().isSameThread())throw failure("invalid_request");
    }
    private static IllegalStateException failure(String key){return new IllegalStateException("error.realmcasino."+key);}
}
