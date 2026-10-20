package pt.contracts.daily;

import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.*;

/** Recoverable receipt covers the wallet, progress, consumed deliveries and both LAN host files. */
public final class DailyRewardJournal {
    public static final String RECEIPT="contracts_daily_receipt";
    private DailyRewardJournal(){}
    public static ListTag inventory(ServerPlayer player){ListTag list=new ListTag();for(int i=0;i<player.getInventory().items.size();i++){ItemStack stack=player.getInventory().items.get(i);if(!stack.isEmpty()){CompoundTag slot=new CompoundTag();slot.putInt("slot",i);slot.put("item",stack.save(player.registryAccess()));list.add(slot);}}return list;}
    public static List<ItemStack> items(ServerPlayer p){List<ItemStack> list=new ArrayList<>();p.getInventory().items.forEach(s->list.add(s.copy()));return list;}
    public static ListTag serialize(ServerPlayer p,List<ItemStack> items){ListTag list=new ListTag();for(int i=0;i<items.size();i++)if(!items.get(i).isEmpty()){CompoundTag slot=new CompoundTag();slot.putInt("slot",i);slot.put("item",items.get(i).save(p.registryAccess()));list.add(slot);}return list;}
    private static void installInventory(ServerPlayer player,ListTag list){
        List<ItemStack> parsed=new ArrayList<>(Collections.nCopies(36,ItemStack.EMPTY));Set<Integer> seen=new HashSet<>();
        for(int i=0;i<list.size();i++){CompoundTag slot=list.getCompound(i);int index=slot.getInt("slot");if(index<0||index>=36||!seen.add(index))throw new IllegalStateException("Invalid inventory journal");ItemStack stack=ItemStack.parse(player.registryAccess(),slot.getCompound("item")).orElseThrow();if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalStateException("Invalid journal stack");parsed.set(index,stack);}
        for(int i=0;i<36;i++)player.getInventory().items.set(i,parsed.get(i));player.getInventory().setChanged();
    }
    public static void begin(ServerPlayer player,DailyLedger.Reward reward,List<ItemStack> deliveryAfter){
        DailyData data=DailyData.get(player.getServer());data.available();if(data.journal(player.getUUID())!=null)throw new IllegalStateException("daily.contracts.payment_pending");
        boolean delivery=deliveryAfter!=null;CompoundTag intent=new CompoundTag();intent.putUUID("id",reward.id);intent.putUUID("player",player.getUUID());intent.putBoolean("delivery",delivery);
        intent.put("inventoryBefore",inventory(player));
        List<ItemStack> after=delivery?deliveryAfter:items(player);
        if(!delivery){DailyTreasury.preflight(player.getServer(),reward);if(reward.currency.equals("CHRONICLES")){
            CompoundTag before=DailyWallet.current(player),awarded=DailyWallet.awarded(player,reward);
            reward.reputation=Math.max(0,awarded.getCompound("progress").getInt("reputation")-before.getCompound("progress").getInt("reputation"));
            intent.put("profileBefore",before);intent.put("profileAfter",awarded);}
            else insertEmeralds(after,reward.net/100);}
        intent.put("inventoryAfter",serialize(player,after));data.prepare(player.getServer(),intent);recover(player);
    }
    private static void insertEmeralds(List<ItemStack> items,long count){
        long left=count;for(var stack:items)if(stack.is(Items.EMERALD)&&ItemStack.isSameItemSameComponents(stack,new ItemStack(Items.EMERALD))){int n=(int)Math.min(left,stack.getMaxStackSize()-stack.getCount());stack.grow(n);left-=n;}
        for(int i=0;i<items.size()&&left>0;i++)if(items.get(i).isEmpty()){int n=(int)Math.min(left,64);items.set(i,new ItemStack(Items.EMERALD,n));left-=n;}
        if(left!=0)throw new IllegalStateException("daily.contracts.inventory_full");
    }
    private static CompoundTag receipt(CompoundTag intent){CompoundTag tag=new CompoundTag();tag.putUUID("id",intent.getUUID("id"));tag.putUUID("player",intent.getUUID("player"));tag.putBoolean("delivery",intent.getBoolean("delivery"));return tag;}
    public static void recover(ServerPlayer player){
        DailyData data=DailyData.get(player.getServer());data.available();CompoundTag intent=data.journal(player.getUUID());if(intent==null)return;
        DailyLedger.Reward reward=data.ledger.rewards.get(intent.getUUID("id"));
        if(reward==null||!reward.player.equals(player.getUUID())||reward.paid)throw new IllegalStateException("daily.contracts.wallet_ambiguous");
        CompoundTag proof=receipt(intent);boolean receipted=player.getPersistentData().get(RECEIPT)instanceof CompoundTag r&&r.equals(proof);
        if(!receipted){
            if(!inventory(player).equals(intent.getList("inventoryBefore",Tag.TAG_COMPOUND)))throw new IllegalStateException("daily.contracts.wallet_ambiguous");
            if(intent.contains("profileBefore"))DailyWallet.install(player,intent.getCompound("profileBefore"),intent.getCompound("profileAfter"));
            installInventory(player,intent.getList("inventoryAfter",Tag.TAG_COMPOUND));player.getPersistentData().put(RECEIPT,proof);
        }
        // A matching receipt is written in the same atomic player snapshot as the award.
        // Legitimate subsequent profile changes must not cause the award to be applied again.
        savePlayer(player);if(!savedReceipt(player,intent))throw new IllegalStateException("daily.contracts.storage_error");
        if(player.getServer().isSingleplayerOwner(player.getGameProfile())){saveHost(player);if(!savedHostReceipt(player,intent))throw new IllegalStateException("daily.contracts.storage_error");}
        if(!intent.getBoolean("delivery"))DailyTreasury.credit(player.getServer(),reward);
        data.finish(player.getServer(),player.getUUID(),reward.id,intent.getBoolean("delivery"));
        DailyWallet.sync(player);player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
    }
    private static boolean matches(CompoundTag root,ServerPlayer p,CompoundTag intent){
        if(!root.hasUUID("UUID")||!root.getUUID("UUID").equals(p.getUUID()))return false;
        for(String key:List.of("NeoForgeData","ForgeData"))if(root.get(key)instanceof CompoundTag data&&data.get(RECEIPT)instanceof CompoundTag r&&r.equals(receipt(intent)))return true;return false;
    }
    public static boolean savedReceipt(ServerPlayer p,CompoundTag intent){try{return matches(NbtIo.readCompressed(p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat"),NbtAccounter.create(64_000_000)),p,intent);}catch(java.io.IOException|RuntimeException e){return false;}}
    public static boolean savedHostReceipt(ServerPlayer p,CompoundTag intent){try{return matches(NbtIo.readCompressed(p.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE),NbtAccounter.create(104857600)).getCompound("Data").getCompound("Player"),p,intent);}catch(java.io.IOException|RuntimeException e){return false;}}
    private static void savePlayer(ServerPlayer p){
        try{var method=net.minecraft.server.players.PlayerList.class.getDeclaredMethod("save",ServerPlayer.class);if(!method.trySetAccessible())throw new IllegalAccessException();method.invoke(p.getServer().getPlayerList(),p);Path file=p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat");DailyData.force(file);DailyData.directory(file.getParent());}
        catch(ReflectiveOperationException|java.io.IOException e){throw new IllegalStateException("daily.contracts.storage_error",e);}
    }
    private static void saveHost(ServerPlayer p){
        try{var server=p.getServer();var field=net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");if(!field.trySetAccessible()||!(field.get(server)instanceof LevelStorageSource.LevelStorageAccess storage))throw new IllegalAccessException();storage.saveDataTag(server.registryAccess(),server.getWorldData(),p.saveWithoutId(new CompoundTag()));Path file=server.getWorldPath(LevelResource.LEVEL_DATA_FILE);DailyData.force(file);DailyData.directory(file.getParent());}
        catch(ReflectiveOperationException|java.io.IOException e){throw new IllegalStateException("daily.contracts.storage_error",e);}
    }
}
