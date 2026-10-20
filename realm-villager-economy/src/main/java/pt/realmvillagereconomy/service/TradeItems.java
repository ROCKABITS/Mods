package pt.realmvillagereconomy.service;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Plans a complete inventory replacement before charging. Only the 36 main slots are tradable. */
public final class TradeItems {
    private TradeItems(){}
    public static ListTag inventory(ServerPlayer player){var out=new ListTag();for(int i=0;i<36;i++){var tag=new CompoundTag();tag.putInt("slot",i);var item=player.getInventory().getItem(i);if(!item.isEmpty())tag.put("item",item.save(player.registryAccess()));out.add(tag);}return out;}
    public static List<ItemStack> decode(ServerPlayer player,ListTag tags){if(tags.size()!=36)throw new IllegalStateException("Invalid trade inventory");var stacks=new ArrayList<ItemStack>();for(int i=0;i<36;i++){var tag=tags.getCompound(i);if(!tag.contains("slot",Tag.TAG_INT)||tag.getInt("slot")!=i)throw new IllegalStateException("Invalid trade inventory slot");stacks.add(tag.contains("item")?ItemStack.parse(player.registryAccess(),tag.getCompound("item")).orElseThrow():ItemStack.EMPTY);}return stacks;}
    public static void install(ServerPlayer player,ListTag tags){var items=decode(player,tags);for(int i=0;i<36;i++)player.getInventory().setItem(i,items.get(i).copy());player.getInventory().setChanged();}
    /** Read vanilla's saved inventory directly. Creating a duplicate ServerPlayer would rebind
     * the shared advancement tracker and could send future recipe packets to a null connection. */
    public static ListTag savedInventory(ServerPlayer context,CompoundTag saved){
        if(!saved.contains("Inventory",Tag.TAG_LIST))throw new IllegalStateException("Saved inventory missing");
        var out=new ListTag();for(int slot=0;slot<36;slot++){var entry=new CompoundTag();entry.putInt("slot",slot);out.add(entry);}
        var seen=new java.util.HashSet<Integer>();for(Tag raw:saved.getList("Inventory",Tag.TAG_COMPOUND)){
            var item=(CompoundTag)raw;int slot=item.getByte("Slot")&255;if(slot>=36)continue;if(!seen.add(slot))throw new IllegalStateException("Duplicate saved inventory slot");
            var stack=ItemStack.parse(context.registryAccess(),item).orElseThrow();if(!stack.isEmpty())out.getCompound(slot).put("item",stack.save(context.registryAccess()));
        }return out;
    }
    public static ListTag plan(ServerPlayer player,MerchantOffer offer,boolean purchase){
        var items=decode(player,inventory(player));
        consume(items,offer.getItemCostA(),offer.getCostA().getCount(),purchase&&offer.getCostA().is(Items.EMERALD));
        if(offer.getItemCostB().isPresent())consume(items,offer.getItemCostB().get(),offer.getCostB().getCount(),purchase&&offer.getCostB().is(Items.EMERALD));
        var remaining=offer.assemble();
        for(var stack:items)if(!remaining.isEmpty()&&!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,remaining)){int count=Math.min(remaining.getCount(),stack.getMaxStackSize()-stack.getCount());if(count>0){stack.grow(count);remaining.shrink(count);}}
        for(int i=0;i<items.size()&&!remaining.isEmpty();i++)if(items.get(i).isEmpty()){int count=Math.min(remaining.getCount(),remaining.getMaxStackSize());items.set(i,remaining.copyWithCount(count));remaining.shrink(count);}
        if(!remaining.isEmpty())throw new IllegalStateException("error.realmvillagereconomy.inventory_full");
        var out=new ListTag();for(int i=0;i<36;i++){var tag=new CompoundTag();tag.putInt("slot",i);if(!items.get(i).isEmpty())tag.put("item",items.get(i).save(player.registryAccess()));out.add(tag);}return out;
    }
    private static void consume(List<ItemStack> slots,ItemCost cost,int count,boolean virtual){if(virtual)return;for(var stack:slots)if(cost.test(stack)){int take=Math.min(count,stack.getCount());stack.shrink(take);count-=take;if(count==0)return;}throw new IllegalStateException("error.realmvillagereconomy.materials");}
}
