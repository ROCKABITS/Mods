package pt.crownledger.service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.*;
import pt.crownledger.compat.*;
import pt.crownledger.data.*;
import pt.crownledger.domain.*;
import pt.crownledger.domain.Treasury.Currency;

/** Treasury intent -> wallet+receipt in both native restart sources -> final public ledger. */
public final class LoanTransactions {
    public static final String RECEIPT_KEY="realm_treasury_loan_receipt";
    public static void commit(ServerPlayer p,UUID loan,boolean credit,long amount,long at,Loans nextLoans,Treasury nextTreasury){
        requirePlayer(p);var data=TreasuryData.get(p.getServer());data.requireBankAvailable();
        var contract=data.loans.get(loan);Currency currency=contract.currency();long before=WalletBridge.balance(p,currency),after=Math.addExact(before,credit?amount:-amount);
        if(after<0)throw failure("balance");if(credit&&WalletBridge.capacity(p,currency)<amount)throw failure("wallet_full");
        var intent=new CompoundTag();intent.putUUID("id",UUID.randomUUID());intent.putUUID("loan",loan);intent.putUUID("player",p.getUUID());intent.putString("name",ChroniclesBridge.publicName(p));intent.putString("currency",currency.name());intent.putBoolean("credit",credit);intent.putLong("amount",amount);intent.putLong("before",before);intent.putLong("after",after);intent.putLong("at",at);intent.put("inventoryBefore",LoanInventory.inventory(p));intent.put("inventoryAfter",LoanInventory.plan(p,currency,amount,credit));intent.putByteArray("loansAfter",LoanCodec.encode(nextLoans.snapshot()));intent.putByteArray("treasuryAfter",LoanCodec.encode(nextTreasury.snapshot()));
        persistBefore(p,intent);data.prepareLoan(p.getServer(),intent);recover(p);
    }
    /** A freshly created character or recently changed wallet must also have a durable starting point. */
    private static void persistBefore(ServerPlayer p,CompoundTag intent){
        try{
            savePlayer(p);var file=p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat");
            if(!before(NbtIo.readCompressed(file,NbtAccounter.create(64L*1024*1024)),p,intent))throw failure("storage");force(file);
            if(p.getServer().isSingleplayerOwner(p.getGameProfile())){saveHost(p);file=p.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE);var root=NbtIo.readCompressed(file,NbtAccounter.create(104857600));if(!root.contains("Data")||!before(root.getCompound("Data").getCompound("Player"),p,intent))throw failure("storage");force(file);}
        }catch(IOException|RuntimeException error){throw new IllegalStateException("realm.treasury.loan.error.storage",error);}
    }
    private static boolean before(CompoundTag root,ServerPlayer p,CompoundTag intent){
        if(!root.hasUUID("UUID")||!root.getUUID("UUID").equals(p.getUUID())||!LoanInventory.saved(p,root).equals(intent.getList("inventoryBefore",Tag.TAG_COMPOUND)))return false;
        if(!intent.getString("currency").equals("CHRONICLES"))return true;
        var profile=root.getCompound("neoforge:attachments").getCompound("cronicasrp:profile");return profile.getBoolean("created")&&profile.getInt("moneyVersion")==1&&profile.contains("balance",Tag.TAG_LONG)&&profile.getLong("balance")==intent.getLong("before");
    }
    public static void recover(ServerPlayer p){
        requirePlayer(p);var data=TreasuryData.get(p.getServer());var intent=data.loanJournal();if(intent==null||!intent.getUUID("player").equals(p.getUUID()))return;
        Currency currency=Currency.valueOf(intent.getString("currency"));
        try{
            if(currency==Currency.CHRONICLES&&!ChroniclesBridge.available(p))throw failure("profile");
            var persistent=p.getPersistentData();boolean receipted=persistent.get(RECEIPT_KEY) instanceof CompoundTag r&&matches(r,intent);
            if(!receipted){
                if(WalletBridge.balance(p,currency)!=intent.getLong("before")||!LoanInventory.inventory(p).equals(intent.getList("inventoryBefore",Tag.TAG_COMPOUND)))throw failure("recovery");
                if(currency==Currency.CHRONICLES)ChroniclesBridge.setBalanceForReceipt(p,intent.getLong("before"),intent.getLong("after"));
                LoanInventory.install(p,intent.getList("inventoryAfter",Tag.TAG_COMPOUND));persistent.put(RECEIPT_KEY,receipt(intent));
            }
            if(WalletBridge.balance(p,currency)!=intent.getLong("after")||!LoanInventory.inventory(p).equals(intent.getList("inventoryAfter",Tag.TAG_COMPOUND)))throw failure("recovery");
            savePlayer(p);if(!savedReceipt(p,intent))throw failure("recovery");force(p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat"));
            if(p.getServer().isSingleplayerOwner(p.getGameProfile())){saveHost(p);if(!savedHostReceipt(p,intent))throw failure("recovery");force(p.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE));}
            data.completeLoan(p.getServer(),intent.getUUID("id"));
        }catch(RuntimeException|LinkageError error){
            com.mojang.logging.LogUtils.getLogger().error("Loan receipt retained; waiting for unambiguous wallet recovery",error);
            try{if(p.connection!=null)p.connection.disconnect(net.minecraft.network.chat.Component.translatable("realm.treasury.loan.error.recovery"));}catch(RuntimeException ignored){}
            throw new IllegalStateException("realm.treasury.loan.error.recovery",error);
        }
        try{if(currency==Currency.CHRONICLES)ChroniclesBridge.syncReceipt(p);p.inventoryMenu.broadcastChanges();p.containerMenu.broadcastChanges();}catch(RuntimeException|LinkageError secondary){com.mojang.logging.LogUtils.getLogger().warn("Loan committed; client synchronization deferred",secondary);}
    }
    public static boolean savedReceipt(ServerPlayer p,CompoundTag intent){var file=p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat");try{return Files.isRegularFile(file)&&matchesSaved(NbtIo.readCompressed(file,NbtAccounter.create(64L*1024*1024)),p,intent);}catch(IOException|RuntimeException error){return false;}}
    public static boolean savedHostReceipt(ServerPlayer p,CompoundTag intent){if(!p.getServer().isSingleplayerOwner(p.getGameProfile()))return false;try{var root=NbtIo.readCompressed(p.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE),NbtAccounter.create(104857600));return root.get("Data") instanceof CompoundTag d&&d.get("Player") instanceof CompoundTag host&&matchesSaved(host,p,intent);}catch(IOException|RuntimeException error){return false;}}
    private static boolean matchesSaved(CompoundTag root,ServerPlayer p,CompoundTag intent){
        if(!root.hasUUID("UUID")||!root.getUUID("UUID").equals(p.getUUID())||!LoanInventory.saved(p,root).equals(intent.getList("inventoryAfter",Tag.TAG_COMPOUND)))return false;
        if(intent.getString("currency").equals("CHRONICLES")){
            if(!(root.get("neoforge:attachments") instanceof CompoundTag a)||!(a.get("cronicasrp:profile") instanceof CompoundTag profile)||!profile.getBoolean("created")||profile.getInt("moneyVersion")!=1||!profile.contains("balance",Tag.TAG_LONG)||profile.getLong("balance")!=intent.getLong("after"))return false;
        }
        for(String key:List.of("ForgeData","NeoForgeData"))if(root.get(key) instanceof CompoundTag d&&d.get(RECEIPT_KEY) instanceof CompoundTag r&&matches(r,intent))return true;
        return false;
    }
    private static CompoundTag receipt(CompoundTag intent){var r=new CompoundTag();for(String k:List.of("id","player","currency","before","after"))r.put(k,Objects.requireNonNull(intent.get(k)).copy());return r;}
    private static boolean matches(CompoundTag r,CompoundTag i){return r.equals(receipt(i));}
    private static void force(Path file){try{try(var ch=FileChannel.open(file,StandardOpenOption.WRITE)){ch.force(true);}if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"))try(var dir=FileChannel.open(file.getParent(),StandardOpenOption.READ)){dir.force(true);}}catch(IOException e){throw new IllegalStateException("realm.treasury.loan.error.recovery",e);}}
    private static void savePlayer(ServerPlayer p){try{var method=net.minecraft.server.players.PlayerList.class.getDeclaredMethod("save",ServerPlayer.class);if(!method.trySetAccessible())throw failure("recovery");method.invoke(p.getServer().getPlayerList(),p);}catch(ReflectiveOperationException|LinkageError e){throw new IllegalStateException("realm.treasury.loan.error.recovery",e);}}
    private static void saveHost(ServerPlayer p){try{var server=p.getServer();var f=net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");if(!f.trySetAccessible()||!(f.get(server) instanceof LevelStorageSource.LevelStorageAccess storage))throw failure("recovery");storage.saveDataTag(server.registryAccess(),server.getWorldData(),p.saveWithoutId(new CompoundTag()));}catch(ReflectiveOperationException|LinkageError e){throw new IllegalStateException("realm.treasury.loan.error.recovery",e);}}
    private static void requirePlayer(ServerPlayer p){if(p==null||p.getServer()==null||!p.getServer().isSameThread())throw failure("invalid");}
    private static IllegalStateException failure(String key){return new IllegalStateException("realm.treasury.loan.error."+key);}
    private LoanTransactions(){}
}
