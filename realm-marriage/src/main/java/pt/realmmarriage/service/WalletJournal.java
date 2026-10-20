package pt.realmmarriage.service;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import pt.realmmarriage.compat.WalletBridge;
import pt.realmmarriage.data.MarriageData;
import pt.realmmarriage.domain.MarriageLedger.*;
import static pt.realmmarriage.domain.MarriageLedger.require;

/** Intent -> atomic player proof (both LAN owner files) -> registry commit. */
public final class WalletJournal {
    public static final String RECEIPT="realm_marriage_receipt";
    private WalletJournal(){}
    public static List<ItemStack> items(ServerPlayer p){return p.getInventory().items.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));}
    public static ListTag serialize(ServerPlayer p,List<ItemStack> items){ListTag list=new ListTag();for(int i=0;i<36;i++)if(!items.get(i).isEmpty()){CompoundTag tag=new CompoundTag();tag.putInt("slot",i);tag.put("item",items.get(i).save(p.registryAccess()));list.add(tag);}return list;}
    private static ListTag inventory(ServerPlayer p){return serialize(p,items(p));}
    private static void inventory(ServerPlayer p,ListTag list){List<ItemStack> after=new ArrayList<>(Collections.nCopies(36,ItemStack.EMPTY));Set<Integer> seen=new HashSet<>();for(var row:list){CompoundTag tag=(CompoundTag)row;int slot=tag.getInt("slot");require(slot>=0&&slot<36&&seen.add(slot),"storage");ItemStack stack=ItemStack.parse(p.registryAccess(),tag.getCompound("item")).orElseThrow();require(!stack.isEmpty()&&stack.getCount()<=stack.getMaxStackSize(),"storage");after.set(slot,stack);}for(int i=0;i<36;i++)p.getInventory().items.set(i,after.get(i));p.getInventory().setChanged();}
    public static CompoundTag intent(ServerPlayer p,UUID id,long delta,List<ItemStack> after){CompoundTag tag=new CompoundTag();tag.putUUID("id",id);tag.putUUID("player",p.getUUID());tag.put("inventoryBefore",inventory(p));tag.put("inventoryAfter",after==null?inventory(p):serialize(p,after));if(delta!=0){tag.put("profileBefore",WalletBridge.current(p));tag.put("profileAfter",WalletBridge.changed(p,delta));}return tag;}
    public static void begin(ServerPlayer p,Operation o,CompoundTag intent){MarriageData data=MarriageData.get(p.getServer());data.available();require(!data.journals.containsKey(p.getUUID())&&o.actor.equals(p.getUUID())&&o.id.equals(intent.getUUID("id")),"pending");data.journals.put(p.getUUID(),intent);data.persist(p.getServer());recover(p);}
    private static CompoundTag proof(CompoundTag intent){CompoundTag tag=new CompoundTag();tag.putUUID("id",intent.getUUID("id"));tag.putUUID("player",intent.getUUID("player"));return tag;}
    public static void recover(ServerPlayer p){MarriageData data=MarriageData.get(p.getServer());data.available();CompoundTag intent=data.journals.get(p.getUUID());if(intent==null)return;Operation o=data.ledger.operations.get(intent.getUUID("id"));require(o!=null&&o.actor.equals(p.getUUID())&&o.state==OpState.PREPARED,"storage");
        CompoundTag proof=proof(intent);boolean applied=p.getPersistentData().get(RECEIPT)instanceof CompoundTag r&&r.equals(proof);
        if(!applied){require(inventory(p).equals(intent.getList("inventoryBefore",Tag.TAG_COMPOUND)),"ambiguous");if(intent.contains("profileBefore"))WalletBridge.install(p,intent.getCompound("profileBefore"),intent.getCompound("profileAfter"));inventory(p,intent.getList("inventoryAfter",Tag.TAG_COMPOUND));p.getPersistentData().put(RECEIPT,proof);}
        savePlayer(p);require(savedReceipt(p,proof,false),"storage");if(p.getServer().isSingleplayerOwner(p.getGameProfile())){saveHost(p);require(savedReceipt(p,proof,true),"storage");}
        data.ledger.settled(o,System.currentTimeMillis());data.journals.remove(p.getUUID());data.persist(p.getServer());WalletBridge.sync(p);p.inventoryMenu.broadcastChanges();p.containerMenu.broadcastChanges();
    }
    public static boolean savedReceipt(ServerPlayer p,CompoundTag proof,boolean host){try{CompoundTag root=NbtIo.readCompressed(p.getServer().getWorldPath(host?LevelResource.LEVEL_DATA_FILE:LevelResource.PLAYER_DATA_DIR).resolve(host?"":p.getUUID()+".dat"),NbtAccounter.create(104857600));if(host)root=root.getCompound("Data").getCompound("Player");if(!root.hasUUID("UUID")||!root.getUUID("UUID").equals(p.getUUID()))return false;for(String key:List.of("NeoForgeData","ForgeData"))if(root.getCompound(key).get(RECEIPT)instanceof CompoundTag r&&r.equals(proof))return true;return false;}catch(java.io.IOException|RuntimeException e){return false;}}
    private static void savePlayer(ServerPlayer p){try{var method=net.minecraft.server.players.PlayerList.class.getDeclaredMethod("save",ServerPlayer.class);method.setAccessible(true);method.invoke(p.getServer().getPlayerList(),p);Path file=p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat");MarriageData.force(file);MarriageData.directory(file.getParent());}catch(ReflectiveOperationException|java.io.IOException e){throw new IllegalStateException("marriage.error.storage",e);}}
    private static void saveHost(ServerPlayer p){try{var server=p.getServer();var field=net.minecraft.server.MinecraftServer.class.getDeclaredField("storageSource");field.setAccessible(true);var access=(LevelStorageSource.LevelStorageAccess)field.get(server);access.saveDataTag(server.registryAccess(),server.getWorldData(),p.saveWithoutId(new CompoundTag()));Path file=server.getWorldPath(LevelResource.LEVEL_DATA_FILE);MarriageData.force(file);MarriageData.directory(file.getParent());}catch(ReflectiveOperationException|java.io.IOException e){throw new IllegalStateException("marriage.error.storage",e);}}
}
