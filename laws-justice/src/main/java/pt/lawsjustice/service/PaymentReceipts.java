package pt.lawsjustice.service;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.IOUtilities;
import pt.lawsjustice.compat.JusticeWallet;
import pt.lawsjustice.domain.FinanceLedger.Payment;

/** A debit and its stable receipt are saved in the same authoritative Minecraft player snapshot. */
public final class PaymentReceipts {
    private static final String ROOT="lawsjustice_payments";
    private PaymentReceipts(){}
    public static boolean live(ServerPlayer p,Payment payment){thread(p.getServer());if(!p.getUUID().equals(payment.payer()))throw error("finance_payer",null);return marker(p.getPersistentData(),payment);}
    public static boolean seen(MinecraftServer server,Payment payment){thread(server);CompoundTag saved=read(server,payment);return saved!=null&&marker(persistent(saved),payment);}
    /** A positive result includes synchronization of the authoritative file and its directory. */
    public static boolean durable(MinecraftServer server,Payment payment){
        thread(server);CompoundTag saved=read(server,payment);if(saved==null||!marker(persistent(saved),payment))return false;
        Path file=file(server,payment);try{force(file);return true;}catch(IOException failure){throw error("finance_storage",failure);}
    }
    public static boolean applyOnline(ServerPlayer p,Payment payment){
        thread(p.getServer());if(!p.getUUID().equals(payment.payer()))throw error("finance_payer",null);
        if(durable(p.getServer(),payment))return true;
        if(!live(p,payment)){
            CompoundTag data=p.getPersistentData();CompoundTag kept=persisted(data).copy();if(kept.contains(ROOT)&&!kept.contains(ROOT,Tag.TAG_COMPOUND))throw error("payment_receipt",null);
            CompoundTag receipts=kept.getCompound(ROOT).copy();CompoundTag receipt=new CompoundTag();receipt.putUUID("id",payment.id());receipt.putUUID("charge",payment.charge());receipt.putUUID("payer",payment.payer());receipt.putString("currency",payment.currency());receipt.putLong("amount",payment.amount());receipts.put(payment.id().toString(),receipt);
            JusticeWallet.debit(p,payment.currency(),payment.amount());kept.put(ROOT,receipts);data.put(Player.PERSISTED_NBT_TAG,kept);
        }
        // An integrated host reloads Data.Player from level.dat ahead of UUID.dat. Saving only
        // playerdata would permit a stale host balance to return after a LAN-world restart.
        if(owner(p.getServer(),payment))p.getServer().saveEverything(true,true,true);else p.getServer().getPlayerList().saveAll();
        IOUtilities.waitUntilIOWorkerComplete();boolean saved=durable(p.getServer(),payment);if(saved)JusticeWallet.sync(p,payment.currency());return saved;
    }
    private static CompoundTag persistent(CompoundTag saved){if(saved.contains("NeoForgeData")&&!saved.contains("NeoForgeData",Tag.TAG_COMPOUND))throw error("payment_receipt",null);return saved.getCompound("NeoForgeData");}
    private static CompoundTag persisted(CompoundTag data){if(data.contains(Player.PERSISTED_NBT_TAG)&&!data.contains(Player.PERSISTED_NBT_TAG,Tag.TAG_COMPOUND))throw error("payment_receipt",null);return data.getCompound(Player.PERSISTED_NBT_TAG);}
    private static boolean marker(CompoundTag data,Payment payment){
        data=persisted(data);
        if(data.contains(ROOT)&&!data.contains(ROOT,Tag.TAG_COMPOUND))throw error("payment_receipt",null);CompoundTag receipts=data.getCompound(ROOT);String key=payment.id().toString();if(!receipts.contains(key))return false;if(!receipts.contains(key,Tag.TAG_COMPOUND))throw error("payment_receipt",null);CompoundTag receipt=receipts.getCompound(key);
        if(!receipt.getAllKeys().equals(Set.of("id","charge","payer","currency","amount"))||!receipt.hasUUID("id")||!receipt.hasUUID("charge")||!receipt.hasUUID("payer")||!receipt.contains("currency",Tag.TAG_STRING)||!receipt.contains("amount",Tag.TAG_LONG)
                ||!receipt.getUUID("id").equals(payment.id())||!receipt.getUUID("charge").equals(payment.charge())||!receipt.getUUID("payer").equals(payment.payer())||!receipt.getString("currency").equals(payment.currency())||receipt.getLong("amount")!=payment.amount())throw error("payment_receipt",null);
        return true;
    }
    private static boolean owner(MinecraftServer server,Payment payment){
        var online=server.getPlayerList().getPlayer(payment.payer());if(online!=null)return server.isSingleplayerOwner(online.getGameProfile());
        GameProfile owner=server.getSingleplayerProfile();if(owner!=null&&owner.getId()!=null)return owner.getId().equals(payment.payer());
        return server.isSingleplayerOwner(new GameProfile(payment.payer(),payment.accountName()));
    }
    private static Path file(MinecraftServer server,Payment payment){return owner(server,payment)?server.getWorldPath(LevelResource.ROOT).resolve("level.dat"):server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(payment.payer()+".dat");}
    private static CompoundTag read(MinecraftServer server,Payment payment){
        Path path=file(server,payment);if(Files.notExists(path))return null;
        try{if(!Files.isRegularFile(path)||Files.size(path)>32_000_000)throw error("finance_storage",null);CompoundTag root=NbtIo.readCompressed(path,NbtAccounter.create(32_000_000));
            if(owner(server,payment)){if(!root.contains("Data",Tag.TAG_COMPOUND))throw error("finance_storage",null);CompoundTag level=root.getCompound("Data");if(!level.contains("Player"))return null;if(!level.contains("Player",Tag.TAG_COMPOUND))throw error("finance_storage",null);return level.getCompound("Player");}return root;
        }catch(IOException failure){throw error("finance_storage",failure);}
    }
    private static void force(Path file)throws IOException {try(FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"))try(FileChannel directory=FileChannel.open(file.getParent(),StandardOpenOption.READ)){directory.force(true);}}
    private static void thread(MinecraftServer server){if(server==null||!server.isSameThread())throw error("thread",null);}
    private static IllegalStateException error(String key,Throwable cause){return new IllegalStateException("lawsjustice.error."+key,cause);}
}
