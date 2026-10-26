package pt.crownledger.service;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import pt.crownledger.domain.Treasury.Currency;

/** Full main-inventory snapshots preserve custom item components; only plain emeralds are currency. */
public final class LoanInventory {
    public static ListTag inventory(ServerPlayer p){return encode(p,p.getInventory().items);}
    private static ListTag encode(ServerPlayer p,List<ItemStack> items){var out=new ListTag();for(int i=0;i<36;i++){var entry=new CompoundTag();entry.putInt("slot",i);if(!items.get(i).isEmpty())entry.put("item",items.get(i).save(p.registryAccess()));out.add(entry);}return out;}
    public static ListTag plan(ServerPlayer p,Currency currency,long amount,boolean credit){
        if(currency==Currency.CHRONICLES)return inventory(p);
        var items=new ArrayList<ItemStack>();p.getInventory().items.forEach(s->items.add(s.copy()));long left=amount;
        for(var stack:items)if(!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,new ItemStack(Items.EMERALD))){
            int count=(int)Math.min(left,credit?Math.max(0,stack.getMaxStackSize()-stack.getCount()):stack.getCount());
            if(credit)stack.grow(count);else stack.shrink(count);left-=count;if(left==0)break;
        }
        if(credit)for(int i=0;i<36&&left>0;i++)if(items.get(i).isEmpty()){int count=(int)Math.min(left,64);items.set(i,new ItemStack(Items.EMERALD,count));left-=count;}
        if(left!=0)throw new IllegalStateException("realm.treasury.loan.error."+(credit?"wallet_full":"balance"));
        return encode(p,items);
    }
    public static void install(ServerPlayer p,ListTag entries){if(entries.size()!=36)throw new IllegalStateException("Invalid loan inventory");var items=new ArrayList<ItemStack>();for(int i=0;i<36;i++){var e=entries.getCompound(i);if(e.getInt("slot")!=i)throw new IllegalStateException("Invalid loan slot");items.add(e.contains("item")?ItemStack.parse(p.registryAccess(),e.getCompound("item")).orElseThrow():ItemStack.EMPTY);}for(int i=0;i<36;i++)p.getInventory().setItem(i,items.get(i).copy());p.getInventory().setChanged();}
    public static ListTag saved(ServerPlayer p,CompoundTag root){
        if(!root.contains("Inventory",Tag.TAG_LIST))throw new IllegalStateException("Missing saved loan inventory");
        var items=new ArrayList<ItemStack>(Collections.nCopies(36,ItemStack.EMPTY));Set<Integer> seen=new HashSet<>();
        for(var raw:root.getList("Inventory",Tag.TAG_COMPOUND)){var entry=(CompoundTag)raw;int slot=entry.getByte("Slot")&255;if(slot>=36)continue;if(!seen.add(slot))throw new IllegalStateException("Duplicate saved slot");items.set(slot,ItemStack.parse(p.registryAccess(),entry).orElseThrow());}
        return encode(p,items);
    }
    private LoanInventory(){}
}
