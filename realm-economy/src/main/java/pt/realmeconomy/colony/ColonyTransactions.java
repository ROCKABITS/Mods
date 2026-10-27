package pt.realmeconomy.colony;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import pt.realmeconomy.compat.ChroniclesBridge;
import pt.realmeconomy.data.EconomyData;
import pt.realmeconomy.service.TradeItems;
import pt.realmeconomy.service.TradeJournal;

/** Colony budget, wallet and delivery inventory share the existing durable Economy journal. */
public final class ColonyTransactions {
    public static void commit(ServerPlayer player,String key,String operation,long amount,String order){
        var data=EconomyData.get(player.getServer());data.requireAvailable();
        if(!ChroniclesBridge.available(player))throw new IllegalStateException("error.realmeconomy.chronicles_unavailable");
        long before=ChroniclesBridge.balance(player),now=Math.max(System.currentTimeMillis(),data.colonies().highWater());UUID id=UUID.randomUUID();
        String name=ChroniclesBridge.publicName(player);ColonyLedger.State state=data.colonies(),next;ListTag inventory=TradeItems.inventory(player);long after=before;
        switch(operation){
            case "DEPOSIT","WITHDRAW"->{boolean deposit=operation.equals("DEPOSIT");next=ColonyLedger.transfer(state,key,player.getUUID(),name,amount,deposit,now,id);after=deposit?Math.subtractExact(before,amount):Math.addExact(before,amount);}
            case "EXPORT"->{
                var export=state.account(key).orders().stream().filter(o->o.id().equals(order)).findFirst().orElseThrow();
                var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(export.item()));var standard=new net.minecraft.world.item.ItemStack(item);
                var plan=new pt.realmeconomy.service.InventoryPlan(player);int remaining=export.quantity();
                for(int i=0;i<36&&remaining>0;i++){var stack=player.getInventory().getItem(i);if(net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack,standard)){int take=Math.min(remaining,stack.getCount());plan.take(i,take);remaining-=take;}}
                if(remaining>0)throw new IllegalStateException("error.realmeconomy.materials");inventory=plan.encode();next=ColonyLedger.export(state,key,player.getUUID(),name,order,now,id);amount=export.cents();
            }
            default->throw new IllegalArgumentException("Invalid colony operation");
        }
        if(after<0||after>ChroniclesBridge.MAX_CENTS)throw new IllegalStateException("error.realmeconomy.insufficient_funds");
        var intent=new CompoundTag();intent.putUUID("id",id);intent.putUUID("player",player.getUUID());intent.putLong("before",before);intent.putLong("after",after);intent.putLong("base",0);intent.putLong("tax",0);intent.putInt("rate",0);intent.putLong("at",now);intent.putString("service","COLONY");intent.put("mutations",new ListTag());intent.put("inventoryBefore",TradeItems.inventory(player));intent.put("inventoryAfter",inventory);
        intent.putString("colonyOperation",operation);intent.putString("colonyKey",key);intent.putString("colonyActorName",name);intent.putLong("colonyAmount",amount);intent.putString("colonyOrder",order);ColonyCodec.write(intent,"colonyBefore",state);ColonyCodec.write(intent,"colonyAfter",next);
        validate(intent,state);TradeJournal.commit(player,intent);
    }
    public static ColonyLedger.State validate(CompoundTag j,ColonyLedger.State current){
        if(!j.getAllKeys().equals(Set.of("id","player","before","after","base","tax","rate","at","service","mutations","inventoryBefore","inventoryAfter","colonyOperation","colonyKey","colonyActorName","colonyAmount","colonyOrder","colonyBefore","colonyAfter"))||!j.getString("service").equals("COLONY")||j.getLong("at")<current.highWater())throw new IllegalStateException("Unexpected colony transaction fields or clock");
        for(String field:List.of("colonyOperation","colonyKey","colonyActorName","colonyOrder"))if(!j.contains(field,Tag.TAG_STRING))throw new IllegalStateException("Incomplete colony intent");
        if(!j.contains("colonyAmount",Tag.TAG_LONG)||!j.hasUUID("id")||!j.hasUUID("player")||!j.getList("mutations",Tag.TAG_COMPOUND).isEmpty()||j.getLong("base")!=0||j.getLong("tax")!=0||j.getInt("rate")!=0)throw new IllegalStateException("Invalid colony intent");
        var before=ColonyCodec.read(j,"colonyBefore");if(!before.equals(current))throw new IllegalStateException("Colony intent changed its starting state");
        var operation=j.getString("colonyOperation");long cents=j.getLong("colonyAmount"),at=j.getLong("at");String key=j.getString("colonyKey"),name=j.getString("colonyActorName");UUID actor=j.getUUID("player"),id=j.getUUID("id");ColonyLedger.State expected;long wallet;
        switch(operation){
            case "DEPOSIT","WITHDRAW"->{boolean deposit=operation.equals("DEPOSIT");expected=ColonyLedger.transfer(before,key,actor,name,cents,deposit,at,id);wallet=deposit?Math.subtractExact(j.getLong("before"),cents):Math.addExact(j.getLong("before"),cents);if(!j.getList("inventoryBefore",Tag.TAG_COMPOUND).equals(j.getList("inventoryAfter",Tag.TAG_COMPOUND)))throw new IllegalStateException("Transfer changed an inventory");}
            case "EXPORT"->{var order=before.account(key).orders().stream().filter(o->o.id().equals(j.getString("colonyOrder"))).findFirst().orElseThrow();if(order.cents()!=cents)throw new IllegalStateException("Export amount changed");expected=ColonyLedger.export(before,key,actor,name,order.id(),at,id);wallet=j.getLong("before");removal(j.getList("inventoryBefore",Tag.TAG_COMPOUND),j.getList("inventoryAfter",Tag.TAG_COMPOUND),order.item(),order.quantity());}
            default->throw new IllegalStateException("Unknown colony operation");
        }
        if(j.getLong("before")<0||j.getLong("after")!=wallet||wallet<0||wallet>ChroniclesBridge.MAX_CENTS||!expected.equals(ColonyCodec.read(j,"colonyAfter")))throw new IllegalStateException("Colony monetary conservation failed");
        return expected;
    }
    private static void removal(ListTag before,ListTag after,String item,int count){
        if(before.size()!=36||after.size()!=36)throw new IllegalStateException("Incomplete export inventory");int removed=0;
        for(int i=0;i<36;i++){
            var a=before.getCompound(i);var b=after.getCompound(i);if(a.getInt("slot")!=i||b.getInt("slot")!=i)throw new IllegalStateException("Inventory slot mismatch");if(a.equals(b))continue;
            var old=a.getCompound("item");var next=b.getCompound("item");int n=old.getInt("count");
            if(!old.getString("id").equals(item)||old.contains("components")&&!old.getCompound("components").isEmpty()||n<1||next.contains("id")&&(!next.getString("id").equals(item)||next.getInt("count")<1||next.getInt("count")>=n))throw new IllegalStateException("Invalid export removal");
            var expected=a.copy();int left=next.contains("id")?next.getInt("count"):0;removed=Math.addExact(removed,n-left);if(left==0)expected.remove("item");else expected.getCompound("item").putInt("count",left);if(!expected.equals(b))throw new IllegalStateException("Export changed unrelated components");
        }
        if(removed!=count)throw new IllegalStateException("Export quantity mismatch");
    }
}
