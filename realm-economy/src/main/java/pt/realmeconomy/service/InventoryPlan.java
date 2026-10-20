package pt.realmeconomy.service;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
public final class InventoryPlan {
 private final ServerPlayer player;private final List<ItemStack> items;
 public InventoryPlan(ServerPlayer player){this.player=player;items=TradeItems.decode(player,TradeItems.inventory(player));}
 public void take(int slot,int count){if(slot<0||slot>=36||count<1||items.get(slot).getCount()<count)throw new IllegalStateException("error.realmeconomy.materials");items.get(slot).shrink(count);}
 public void take(Item item,int count){for(int i=0;i<36&&count>0;i++)if(items.get(i).is(item)){int n=Math.min(count,items.get(i).getCount());items.get(i).shrink(n);count-=n;}if(count>0)throw new IllegalStateException("error.realmeconomy.materials");}
 public void add(ItemStack output){var remaining=output.copy();for(var stack:items)if(!remaining.isEmpty()&&!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,remaining)){int n=Math.min(remaining.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount()));stack.grow(n);remaining.shrink(n);}
  for(int i=0;i<36&&!remaining.isEmpty();i++)if(items.get(i).isEmpty()){int n=Math.min(remaining.getCount(),remaining.getMaxStackSize());items.set(i,remaining.copyWithCount(n));remaining.shrink(n);}if(!remaining.isEmpty())throw new IllegalStateException("error.realmeconomy.inventory_full");}
 public ListTag encode(){var out=new ListTag();for(int i=0;i<36;i++){var tag=new CompoundTag();tag.putInt("slot",i);if(!items.get(i).isEmpty())tag.put("item",items.get(i).save(player.registryAccess()));out.add(tag);}return out;}
}
