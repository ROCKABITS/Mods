package pt.realmeconomy.colony;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import pt.realmeconomy.api.EconomyPermissions;
import pt.realmeconomy.block.EconomyBlocks;
import pt.realmeconomy.compat.ChroniclesBridge;
import pt.realmeconomy.compat.NotificationsBridge;
import pt.realmeconomy.data.EconomyData;
import pt.realmeconomy.menu.ColonyMenu;
import pt.realmeconomy.network.TradeNetwork;
import pt.realmeconomy.service.TradeJournal;

/** Physical, server-authoritative colonial cashier. No command or remote financial access. */
public final class ColonyService {
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final class Session {
        final BlockPos pos;final String dimension,key;final long opened=System.nanoTime();UUID nonce=UUID.randomUUID();long actionAt,refreshAt;
        Session(ServerPlayer p,BlockPos pos,String key){this.pos=pos.immutable();this.key=key;dimension=p.level().dimension().location().toString();}
    }
    public static boolean loaded(){return ModList.get().isLoaded("minecolonies");}
    public static void install(){if(loaded())ColonyHooks.install();}
    public static void interact(PlayerInteractEvent.RightClickBlock event){
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof ServerPlayer p)||!event.getLevel().getBlockState(event.getPos()).is(EconomyBlocks.COLONY_CHEST.get()))return;
        event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        try{open(p,event.getPos());}catch(RuntimeException|LinkageError failure){p.displayClientMessage(Component.translatable(message(failure)),false);}
    }
    public static void open(ServerPlayer p,BlockPos pos){
        if(!loaded())throw error("colony_missing");if(!p.getServer().isSameThread()||p instanceof FakePlayer||p.isSpectator()||!p.isAlive()||!p.level().hasChunkAt(pos)||p.distanceToSqr(pos.getCenter())>64||!p.level().getBlockState(pos).is(EconomyBlocks.COLONY_CHEST.get()))throw error("distance");
        TradeJournal.recover(p);EconomyData.get(p.getServer()).requireAvailable();var c=ColonyAccess.at(p,pos);if(!ColonyAccess.member(p,c))throw error("permission");ColonyAccess.ensure(c);ColonyAccess.refreshVisitors(c);p.closeContainer();SESSIONS.put(p.getUUID(),new Session(p,pos,ColonyAccess.key(c)));p.openMenu(new SimpleMenuProvider((id,inv,player)->new ColonyMenu(id,inv),tr("title")));send(p,"");
    }
    public static boolean valid(ServerPlayer p){
        var s=SESSIONS.get(p.getUUID());return s!=null&&!(p instanceof FakePlayer)&&p.isAlive()&&!p.isSpectator()&&System.nanoTime()-s.opened<600_000_000_000L&&p.level().dimension().location().toString().equals(s.dimension)&&p.level().hasChunkAt(s.pos)&&p.distanceToSqr(s.pos.getCenter())<=64&&p.level().getBlockState(s.pos).is(EconomyBlocks.COLONY_CHEST.get());
    }
    public static void close(ServerPlayer p){SESSIONS.remove(p.getUUID());}
    public static void stop(){SESSIONS.clear();ColonyCatalog.clear();}
    private static Session session(ServerPlayer p){if(!loaded()||!valid(p)||!(p.containerMenu instanceof ColonyMenu))throw error("distance");return SESSIONS.get(p.getUUID());}
    private static void exact(JsonObject r,String...keys){if(!r.keySet().equals(Set.of(keys)))throw error("invalid_request");}
    private static long number(JsonObject r,String key){if(!r.has(key)||!r.get(key).isJsonPrimitive()||!r.getAsJsonPrimitive(key).isNumber())throw error("invalid_request");return r.get(key).getAsBigDecimal().longValueExact();}
    public static void action(ServerPlayer p,String op,String json){
        try{
            var s=session(p);if(json.length()>2048)throw error("invalid_request");var body=JsonParser.parseString(json).getAsJsonObject();long nano=System.nanoTime();var c=ColonyAccess.require(p,s.pos,s.key);var d=EconomyData.get(p.getServer());d.requireAvailable();
            if(op.equals("clnRefresh")){exact(body);if(nano-s.refreshAt<800_000_000L)return;s.refreshAt=nano;ColonyAccess.ensure(c);ColonyAccess.refreshVisitors(c);send(p,"");return;}
            if(!body.has("nonce")||!body.get("nonce").isJsonPrimitive()||!body.getAsJsonPrimitive("nonce").isString()||!UUID.fromString(body.get("nonce").getAsString()).equals(s.nonce))throw error("stale");
            if(nano-s.actionAt<150_000_000L)throw error("stale");s.actionAt=nano;s.nonce=UUID.randomUUID();ColonyAccess.ensure(c);
            boolean manager=ColonyAccess.manager(p,c);
            switch(op){
                case "clnDeposit","clnWithdraw"->{exact(body,"nonce","amount");if(op.equals("clnWithdraw")&&!manager)throw error("permission");ColonyTransactions.commit(p,s.key,op.equals("clnDeposit")?"DEPOSIT":"WITHDRAW",number(body,"amount"),"");}
                case "clnExport"->{exact(body,"nonce","order");if(!manager||p.isCreative())throw error("permission");String id=UUID.fromString(body.get("order").getAsString()).toString();ColonyTransactions.commit(p,s.key,"EXPORT",0,id);try{NotificationsBridge.send(p,"notification.realmeconomy.colony_export",d.colonies().account(s.key).orders().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow().cents());}catch(RuntimeException secondary){com.mojang.logging.LogUtils.getLogger().warn("Colony export is committed; feedback failed",secondary);}}
                case "clnPay"->{exact(body,"nonce","invoice");if(!ChroniclesBridge.loaded())throw error("chronicles_unavailable");if(!d.colonies().policy().enabled())throw error("permission");if(!manager)throw error("permission");String id=UUID.fromString(body.get("invoice").getAsString()).toString();if(!ColonyAccess.active(c).contains(id))throw error("colony_expired");var before=d.colonies();d.colonies(ColonyLedger.pay(before,s.key,id,p.getUUID(),publicName(p),System.currentTimeMillis(),UUID.randomUUID()));}
                case "clnPolicy"->{
                    exact(body,"nonce","dailyCap","playerCap","orders","buildCents","recruitCents","enabled");if(!EconomyPermissions.canAdminister(p)||!body.getAsJsonPrimitive("enabled").isBoolean())throw error("permission");
                    var policy=new ColonyLedger.Policy(body.get("enabled").getAsBoolean(),number(body,"dailyCap"),number(body,"playerCap"),Math.toIntExact(number(body,"orders")),number(body,"buildCents"),number(body,"recruitCents"));d.colonyPolicy(p.getUUID(),publicName(p),policy);
                }
                default->throw error("invalid_request");
            }
            send(p,"");
        }catch(RuntimeException|LinkageError failed){if(valid(p)&&p.containerMenu instanceof ColonyMenu){try{send(p,message(failed));}catch(RuntimeException secondary){p.closeContainer();p.displayClientMessage(Component.translatable(message(failed)),false);}}else p.closeContainer();}
    }
    public static JsonObject snapshot(ServerPlayer p){
        var s=session(p);var c=ColonyAccess.require(p,s.pos,s.key);var d=EconomyData.get(p.getServer());var state=d.colonies();var a=state.account(s.key);var out=new JsonObject();
        out.addProperty("screen","colony");out.addProperty("container",p.containerMenu.containerId);out.addProperty("nonce",s.nonce.toString());out.addProperty("name",c.getName());out.addProperty("key",s.key);var ownerId=c.getPermissions().getOwner();var onlineOwner=p.getServer().getPlayerList().getPlayer(ownerId);String ownerName=onlineOwner!=null&&ChroniclesBridge.available(onlineOwner)?ChroniclesBridge.publicName(onlineOwner):a.history().stream().filter(e->e.actor().equals(ownerId.toString())).map(ColonyLedger.Entry::name).findFirst().orElse(c.getPermissions().getOwnerName());out.addProperty("owner",ownerName);out.addProperty("manager",ColonyAccess.manager(p,c));out.addProperty("administrator",EconomyPermissions.canAdminister(p));out.addProperty("balance",a.balance());out.addProperty("wallet",ChroniclesBridge.available(p)?ChroniclesBridge.balance(p):-1);out.addProperty("walletAvailable",ChroniclesBridge.available(p));out.addProperty("citizens",c.getCitizenManager().getCurrentCitizenCount());out.addProperty("earned",a.earned());
        long now=Math.max(state.highWater(),System.currentTimeMillis());out.addProperty("reset",Math.max(0,((a.day()+1)*ColonyLedger.DAY-now+999)/1000));var quota=state.exporters().get(p.getUUID().toString());out.addProperty("personalEarned",quota!=null&&quota.day()==a.day()?quota.earned():0);out.add("policy",ColonyLedger.JSON.toJsonTree(state.policy()));
        var exports=new JsonArray();for(var o:a.orders()){var row=new JsonObject();row.addProperty("id",o.id());row.addProperty("itemId",o.item());row.addProperty("quantity",o.quantity());row.addProperty("cents",o.cents());row.addProperty("delivered",o.delivered());var item=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(o.item())));row.add("item",ItemStack.OPTIONAL_CODEC.encodeStart(p.registryAccess().createSerializationContext(JsonOps.INSTANCE),item).getOrThrow());int count=0;for(int i=0;i<36;i++){var stack=p.getInventory().getItem(i);if(ItemStack.isSameItemSameComponents(stack,item))count+=stack.getCount();}row.addProperty("available",count);exports.add(row);}out.add("exports",exports);
        var active=ColonyAccess.active(c);var invoices=new JsonArray();a.invoices().values().stream().filter(i->active.contains(i.id())).sorted(Comparator.comparingLong(ColonyLedger.Invoice::at).reversed()).limit(100).forEach(i->invoices.add(ColonyLedger.JSON.toJsonTree(i)));out.add("invoices",invoices);
        var history=new JsonArray();for(var e:a.history())history.add(ColonyLedger.JSON.toJsonTree(e));out.add("history",history);
        return out;
    }
    private static void send(ServerPlayer p,String error){var value=snapshot(p);value.addProperty("error",error);TradeNetwork.send(p,value);}
    private static String publicName(ServerPlayer p){return ChroniclesBridge.available(p)?ChroniclesBridge.publicName(p):p.getGameProfile().getName();}
    private static Component tr(String key){return Component.translatable("gui.realmeconomy.colony_"+key);}
    private static IllegalStateException error(String key){return new IllegalStateException("error.realmeconomy."+key);}
    private static String message(Throwable e){String key=e.getMessage();return key!=null&&key.startsWith("error.realmeconomy.")?key:"error.realmeconomy.colony_unavailable";}
}
