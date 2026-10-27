package pt.realmeconomy.colony;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import pt.realmeconomy.api.EconomyPermissions;
import java.util.*;

/** Isolated optional adapter. All identities and rights are read afresh from MineColonies. */
public final class ColonyAccess {
    public static String key(IColony c){return c.getDimension().location()+"/"+c.getID();}
    public static IColony at(ServerPlayer p,BlockPos pos){var c=IColonyManager.getInstance().getColonyByPosFromWorld(p.level(),pos);if(c==null||!c.isCoordInColony(p.level(),pos))throw error("colony_location");return c;}
    public static boolean member(ServerPlayer p,IColony c){return EconomyPermissions.canAdminister(p)||c.getPermissions().isColonyMember(p)&&!c.getPermissions().getRank(p).isHostile();}
    public static boolean manager(ServerPlayer p,IColony c){return EconomyPermissions.canAdminister(p)||c.getPermissions().getOwner().equals(p.getUUID())||c.getPermissions().isColonyMember(p)&&c.getPermissions().getRank(p).isColonyManager()&&!c.getPermissions().getRank(p).isHostile()&&c.getPermissions().hasPermission(p,Action.MANAGE_HUTS);}
    public static IColony require(ServerPlayer p,BlockPos pos,String expected){var c=at(p,pos);if(!key(c).equals(expected)||!member(p,c))throw error("permission");return c;}
    public static void ensure(IColony c){
        if(c.getWorld()==null||c.getWorld().isClientSide||c.getWorld().getServer()==null)return;var data=pt.realmeconomy.data.EconomyData.get(c.getWorld().getServer());data.requireAvailable();var s=data.colonies();if(s.accounts().isEmpty()&&s.highWater()==0)s=s.policy(ColonyCatalog.config().initialPolicy(),0);
        long now=Math.max(s.highWater(),System.currentTimeMillis()),day=Math.floorDiv(now,ColonyLedger.DAY);var old=s.accounts().get(key(c));
        if(old!=null&&!old.retired()&&old.center()==c.getCenter().asLong()&&old.day()==day&&old.name().equals(c.getName()))return;
        var orders=ColonyCatalog.generate(key(c),day,data.colonySalt(),s.policy().orders());data.colonies(ColonyLedger.ensure(s,key(c),c.getName(),c.getCenter().asLong(),now,orders));
    }
    public static Set<String> active(IColony c){
        var ids=new HashSet<String>();for(var o:c.getWorkManager().getWorkOrders().values())if(o instanceof WorkOrderBuilding&&o instanceof ColonyOrderAccess access&&access.realmEconomy$invoice()!=null)ids.add(access.realmEconomy$invoice().toString());
        for(var visitor:c.getVisitorManager().getCivilianDataMap().values())if(visitor instanceof IVisitorData v)ids.add(recruitId(v).toString());return ids;
    }
    public static UUID recruitId(ICitizenData visitor){return UUID.nameUUIDFromBytes(("realm-economy:recruit:"+key(visitor.getColony())+":"+visitor.getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public static void refreshVisitors(IColony c){
        var data=pt.realmeconomy.data.EconomyData.get(c.getWorld().getServer());var state=data.colonies();if(!state.policy().enabled())return;
        for(var visitor:c.getVisitorManager().getCivilianDataMap().values())if(visitor instanceof IVisitorData v){UUID id=recruitId(v);state=ColonyLedger.invoice(state,key(c),new ColonyLedger.Invoice(id.toString(),"RECRUIT",v.getName(),"visitor:"+v.getUUID(),state.policy().recruitCents(),state.policy().recruitCents()==0,System.currentTimeMillis()),System.currentTimeMillis());}
        data.colonies(state);
    }
    public static boolean alchemistStation(net.minecraft.server.level.ServerLevel level,BlockPos pos){
        var c=IColonyManager.getInstance().getColonyByPosFromWorld(level,pos);if(c==null||!c.isCoordInColony(level,pos))return false;
        for(var building:c.getServerBuildingManager().getBuildings().values())if(building instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingAlchemist alchemist&&alchemist.getBuildingLevel()>0&&alchemist.getAllBrewingStandPositions().contains(pos)&&alchemist.getAllAssignedCitizen().stream().anyMatch(worker->worker.getWorkBuilding()==alchemist&&worker.getJob()!=null))return true;
        return false;
    }
    private static IllegalStateException error(String key){return new IllegalStateException("error.realmeconomy."+key);}
}
