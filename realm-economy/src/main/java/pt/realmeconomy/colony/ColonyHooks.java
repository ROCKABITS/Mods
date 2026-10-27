package pt.realmeconomy.colony;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import pt.realmeconomy.data.EconomyData;
import java.util.UUID;

/** Native work orders wait for a separately approved, durable invoice. No tick-driven debits. */
public final class ColonyHooks {
    public static void install(){IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyDeletedModEvent.class,e->{var c=e.getColony();if(c.getWorld()!=null&&!c.getWorld().isClientSide&&c.getWorld().getServer()!=null){var d=EconomyData.get(c.getWorld().getServer());d.colonies(ColonyLedger.retire(d.colonies(),ColonyAccess.key(c),System.currentTimeMillis()));}});}
    public static boolean canDelete(IColony c){
        if(c==null||c.getWorld()==null||c.getWorld().isClientSide||c.getWorld().getServer()==null)return true;
        try{var d=EconomyData.get(c.getWorld().getServer());d.requireAvailable();var a=d.colonies().accounts().get(ColonyAccess.key(c));if(a==null||a.balance()==0)return true;for(var p:c.getWorld().getServer().getPlayerList().getPlayers())if(c.getPermissions().getOwner().equals(p.getUUID())||pt.realmeconomy.api.EconomyPermissions.canAdminister(p))p.displayClientMessage(Component.translatable("error.realmeconomy.colony_not_empty"),false);return false;}catch(RuntimeException failure){return false;}
    }
    public static UUID added(WorkOrderBuilding order,IColony c,boolean reading,UUID id){
        if(!pt.realmeconomy.compat.ChroniclesBridge.loaded()||reading||id==null||c==null||c.getWorld()==null||c.getWorld().isClientSide||c.getWorld().getServer()==null)return id;
        ColonyAccess.ensure(c);var d=EconomyData.get(c.getWorld().getServer());var s=d.colonies();if(!s.policy().enabled())return id;
        if(order.getWorkOrderType()!=WorkOrderType.BUILD&&order.getWorkOrderType()!=WorkOrderType.UPGRADE)return id;
        String title=order.getDisplayName().getString();if(title.length()>160)title=title.substring(0,160);
        String reference=order.getLocation().toShortString()+" · "+order.getTargetLevel()+" | "+order.getTranslationKey();
        var paid=s.account(ColonyAccess.key(c)).invoices().values().stream().filter(i->i.paid()&&i.kind().equals(order.getWorkOrderType().name())&&i.reference().equals(reference)).findFirst();
        if(paid.isPresent())return UUID.fromString(paid.get().id());
        long amount=s.policy().construction(order.getTargetLevel());d.colonies(ColonyLedger.invoice(s,ColonyAccess.key(c),new ColonyLedger.Invoice(id.toString(),order.getWorkOrderType().name(),title,reference,amount,amount==0,System.currentTimeMillis()),System.currentTimeMillis()));
        return id;
    }
    public static boolean allowed(WorkOrderBuilding order,IColony colony,UUID id){
        if(!pt.realmeconomy.compat.ChroniclesBridge.loaded()||id==null||colony==null||colony.getWorld()==null||colony.getWorld().isClientSide||colony.getWorld().getServer()==null)return true;
        try{var d=EconomyData.get(colony.getWorld().getServer());if(!d.colonies().policy().enabled())return true;if(order.getWorkOrderType()!=WorkOrderType.BUILD&&order.getWorkOrderType()!=WorkOrderType.UPGRADE)return true;var a=d.colonies().accounts().get(ColonyAccess.key(colony));var i=a==null?null:a.invoice(id.toString());return i!=null&&i.paid()&&d.journal()==null;}
        catch(RuntimeException|LinkageError failure){return false;}
    }
    public static boolean recruit(ServerPlayer p,ICitizenData data){
        if(!pt.realmeconomy.compat.ChroniclesBridge.loaded()||!(data instanceof IVisitorData))return true;var c=data.getColony();
        try{if(!ColonyAccess.manager(p,c)||c.getVisitorManager().getVisitor(data.getId())!=data||p.distanceToSqr(data.getEntity().orElseThrow())>64)return false;ColonyAccess.ensure(c);var d=EconomyData.get(p.getServer());if(!d.colonies().policy().enabled())return true;ColonyAccess.refreshVisitors(c);var invoice=d.colonies().account(ColonyAccess.key(c)).invoice(ColonyAccess.recruitId(data).toString());if(invoice!=null&&invoice.paid())return true;
            p.displayClientMessage(Component.translatable("gui.realmeconomy.colony_recruit_required"),false);return false;
        }catch(RuntimeException|LinkageError failure){p.displayClientMessage(Component.translatable("error.realmeconomy.colony_unavailable"),false);return false;}
    }
}
