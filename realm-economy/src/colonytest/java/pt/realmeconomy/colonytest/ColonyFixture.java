package pt.realmeconomy.colonytest;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import pt.realmeconomy.block.EconomyBlocks;
import pt.realmeconomy.colony.*;
import pt.realmeconomy.data.EconomyData;
final class ColonyFixture implements AutoCloseable {
 static int sequence;
 final GameTestHelper h;final ColonyActors actors;final ColonyActors.Actor owner,friend,outsider;final IColony colony;final BlockPos pos;
 EconomyData data;final EconomyData previous;final Path directory;final Map<MinecraftServer,EconomyData> loaded;
 @SuppressWarnings("unchecked") ColonyFixture(GameTestHelper helper)throws Exception{
  h=helper;actors=new ColonyActors(h);owner=actors.actor("ColonyOwner",100000);friend=actors.actor("ColonyMember",200000);outsider=actors.actor("ColonyStranger",200000);var server=h.getLevel().getServer();previous=EconomyData.get(server);
  var f=EconomyData.class.getDeclaredField("LOADED");f.setAccessible(true);loaded=(Map<MinecraftServer,EconomyData>)f.get(null);directory=server.getWorldPath(LevelResource.ROOT).resolve("colony-native/"+UUID.randomUUID());Files.createDirectories(directory);var fresh=EconomyData.class.getDeclaredMethod("fresh");fresh.setAccessible(true);var ctor=EconomyData.class.getDeclaredConstructor(Path.class,CompoundTag.class);ctor.setAccessible(true);data=ctor.newInstance(directory.resolve("economy.dat"),fresh.invoke(null));loaded.put(server,data);
  pos=new BlockPos(100000+(++sequence)*512,90,100000);h.getLevel().getChunkAt(pos);colony=Objects.requireNonNull(IColonyManager.getInstance().createColony(h.getLevel(),pos,owner,"Native colony "+sequence,"medievaloak"));
  colony.getPermissions().addPlayer(friend.getUUID(),friend.getGameProfile().getName(),colony.getPermissions().getRankFriend());
  h.getLevel().setBlock(pos,EconomyBlocks.COLONY_CHEST.get().defaultBlockState(),3);owner.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1);friend.setPos(owner.position());outsider.setPos(owner.position());ColonyAccess.ensure(colony);
 }
 String key(){return ColonyAccess.key(colony);}
 void open(){ColonyService.open(owner,pos);}
 JsonObject request(ColonyActors.Actor p){var body=new JsonObject();body.add("nonce",ColonyService.snapshot(p).get("nonce"));return body;}
 void resetLimit(ColonyActors.Actor p)throws Exception{var f=ColonyService.class.getDeclaredField("SESSIONS");f.setAccessible(true);var session=((Map<?,?>)f.get(null)).get(p.getUUID());var a=session.getClass().getDeclaredField("actionAt");a.setAccessible(true);a.setLong(session,0);}
 void transfer(ColonyActors.Actor p,boolean deposit,long cents)throws Exception{resetLimit(p);var body=request(p);body.addProperty("amount",cents);ColonyService.action(p,deposit?"clnDeposit":"clnWithdraw",body.toString());}
 void pay(String id)throws Exception{resetLimit(owner);var body=request(owner);body.addProperty("invoice",id);ColonyService.action(owner,"clnPay",body.toString());}
 WorkOrderBuilding order(WorkOrderType type,int level)throws Exception{
  var cls=WorkOrderBuilding.class;var ctor=Arrays.stream(cls.getDeclaredConstructors()).filter(c->c.getParameterCount()==8).findFirst().orElseThrow();ctor.setAccessible(true);Object rotation=Class.forName("com.ldtteam.structurize.api.RotationMirror").getField("NONE").get(null);var o=(WorkOrderBuilding)ctor.newInstance("medievaloak","builder1.blueprint","block.minecolonies.blockhutbuilder",type,pos.offset(4,0,4),rotation,Math.max(0,level-1),level);
  for(String name:List.of("customName","customParentName","parentTranslationKey")){var f=cls.getDeclaredField(name);f.setAccessible(true);f.set(o,"");}
  o.setID(sequence*100+colony.getWorkManager().getWorkOrders().size()+1);o.setColony(colony);colony.getWorkManager().getWorkOrders().put(o.getID(),o);o.onAdded(colony,false);return o;
 }
 com.minecolonies.core.colony.buildings.workerbuildings.BuildingAlchemist alchemist(boolean built,boolean staffed)throws Exception{
  BlockPos hut=pos.offset(5,0,0);h.getLevel().setBlock(hut,com.minecolonies.api.colony.buildings.ModBuildings.alchemist.get().getBuildingBlock().defaultBlockState(),3);
  var b=(com.minecolonies.core.colony.buildings.workerbuildings.BuildingAlchemist)com.minecolonies.api.colony.buildings.ModBuildings.alchemist.get().produceBuilding(hut,colony);
  var tile=(com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding)h.getLevel().getBlockEntity(hut);tile.setColony(colony);tile.setBuilding(b);b.setBuildingLevel(built?1:0);
  var manager=colony.getServerBuildingManager();var f=manager.getClass().getDeclaredField("buildings");f.setAccessible(true);var buildings=new HashMap<net.minecraft.core.BlockPos,com.minecolonies.api.colony.buildings.IBuilding>(manager.getBuildings());buildings.put(hut,b);f.set(manager,com.google.common.collect.ImmutableMap.copyOf(buildings));
  if(staffed){var citizen=(com.minecolonies.api.colony.ICitizenData)colony.getCitizenManager().createAndRegisterCivilianData();if(!b.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(citizen))throw new IllegalStateException("Native alchemist assignment failed");}
  ColonyBrewing.clear();return b;
 }
 BuildingBuilder builder(){return new BuildingBuilder(colony,pos.offset(3,0,3));}
 void reload()throws Exception{var ctor=EconomyData.class.getDeclaredConstructor(Path.class,CompoundTag.class);ctor.setAccessible(true);data=ctor.newInstance(data.path(),net.minecraft.nbt.NbtIo.readCompressed(data.path(),net.minecraft.nbt.NbtAccounter.unlimitedHeap()));loaded.put(h.getLevel().getServer(),data);}
 public void close(){try{owner.savedHook=null;ColonyBrewing.clear();owner.closeContainer();friend.closeContainer();outsider.closeContainer();loaded.put(h.getLevel().getServer(),previous);IColonyManager.getInstance().deleteColonyByWorld(colony.getID(),false,h.getLevel());}finally{loaded.put(h.getLevel().getServer(),previous);actors.close();}}
}
