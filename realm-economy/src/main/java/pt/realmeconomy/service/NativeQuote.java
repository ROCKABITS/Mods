package pt.realmeconomy.service;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import pt.realmeconomy.domain.ServiceKind;
import pt.realmeconomy.data.EconomyData;
/** Quotes on copies using Minecraft's actual calculators. No money, XP, seed or inventory is changed. */
public record NativeQuote(ServiceKind kind,long units,ItemStack result,ListTag inventory,ListTag mutations,int seed,String detail){
 private static ItemStack selected(ServerPlayer p,int[] slots,int i){int slot=i<slots.length?slots[i]:-1;return slot>=0&&slot<36?p.getInventory().getItem(slot).copy():ItemStack.EMPTY;}
 private static void require(boolean yes){if(!yes)throw new IllegalStateException("error.realmeconomy.recipe");}
 public static NativeQuote calculate(ServerPlayer p,BlockPos pos,ServiceKind context,int[] slots,String name,boolean rename,int choice){
  var plan=new InventoryPlan(p);var mutations=new ListTag();ItemStack result;long units=1;ServiceKind kind=context;String detail="";int seed=p.getEnchantmentSeed();var access=ContainerLevelAccess.create(p.level(),pos);
  if(context==ServiceKind.ANVIL_REPAIR){
   var a=selected(p,slots,0);var b=selected(p,slots,1);require(!a.isEmpty());var menu=new AnvilMenu(-1,p.getInventory(),access);menu.getSlot(0).set(a.copy());menu.getSlot(1).set(b.copy());menu.setItemName(rename?name:a.getHoverName().getString());menu.createResult();result=menu.getSlot(2).getItem().copy();require(!result.isEmpty()&&menu.getCost()>0);
   kind=b.isEmpty()?ServiceKind.ANVIL_RENAME:b.has(DataComponents.STORED_ENCHANTMENTS)||b.is(a.getItem())?ServiceKind.ANVIL_COMBINE:ServiceKind.ANVIL_REPAIR;
   units=menu.getCost();plan.take(slots[0],a.getCount());if(!b.isEmpty())plan.take(slots[1],menu.repairItemCountCost>0?menu.repairItemCountCost:b.getCount());plan.add(result);detail=""+units;
  }else if(context==ServiceKind.ENCHANT){
   var a=selected(p,slots,0);require(!a.isEmpty()&&a.isEnchantable()&&choice>=0&&choice<3);var menu=new EnchantmentMenu(-1,p.getInventory(),access);menu.getSlot(0).set(a.copyWithCount(1));menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,3));menu.slotsChanged(menu.getSlot(0).container);require(menu.costs[choice]>0);
   List<EnchantmentInstance> enchantments;
   try{Method method=EnchantmentMenu.class.getDeclaredMethod("getEnchantmentList",net.minecraft.core.RegistryAccess.class,ItemStack.class,int.class,int.class);method.setAccessible(true);@SuppressWarnings("unchecked")var list=(List<EnchantmentInstance>)method.invoke(menu,p.registryAccess(),a.copyWithCount(1),choice,menu.costs[choice]);enchantments=list;}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
   require(!enchantments.isEmpty());result=a.getItem().applyEnchantments(a.copyWithCount(1),enchantments);plan.take(slots[0],1);plan.take(Items.LAPIS_LAZULI,choice+1);plan.add(result);units=menu.costs[choice];seed=java.util.concurrent.ThreadLocalRandom.current().nextInt();detail=menu.enchantClue[choice]+":"+menu.levelClue[choice]+":"+(choice+1);
  }else if(context==ServiceKind.SMITHING){
   var menu=new SmithingMenu(-1,p.getInventory(),access);for(int i=0;i<3;i++)menu.getSlot(i).set(selected(p,slots,i).copyWithCount(1));menu.createResult();result=menu.getSlot(3).getItem().copy();require(!result.isEmpty());for(int i=0;i<3;i++)if(!selected(p,slots,i).isEmpty())plan.take(slots[i],1);plan.add(result);var input=new net.minecraft.world.item.crafting.SmithingRecipeInput(selected(p,slots,0).copyWithCount(1),selected(p,slots,1).copyWithCount(1),selected(p,slots,2).copyWithCount(1));detail=p.level().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING,input,p.level()).orElseThrow().id().toString();
  }else if(context==ServiceKind.BREWING){
   var reagent=selected(p,slots,3);require(!reagent.isEmpty()&&p.level().potionBrewing().isIngredient(reagent));var output=new ListTag();boolean changes=false;
   for(int i=0;i<3;i++){var bottle=selected(p,slots,i);if(bottle.isEmpty())continue;require(p.level().potionBrewing().isInput(bottle)||bottle.is(Items.GLASS_BOTTLE));changes|=p.level().potionBrewing().hasMix(bottle,reagent);var mixed=p.level().potionBrewing().mix(reagent,bottle.copyWithCount(1));output.add(mixed.save(p.registryAccess()));plan.take(slots[i],1);}
   require(changes&&!output.isEmpty());plan.take(slots[3],1);if(reagent.hasCraftingRemainingItem())plan.add(reagent.getCraftingRemainingItem());
   var data=EconomyData.get(p.getServer());var fuel=data.entry("fuel",p.getUUID().toString());int unitsLeft=fuel.getInt("units");require(unitsLeft>=0&&unitsLeft<=19);if(unitsLeft==0){plan.take(Items.BLAZE_POWDER,1);unitsLeft=20;}
   var nextFuel=new CompoundTag();nextFuel.putInt("units",unitsLeft-1);mutation(mutations,"fuel",p.getUUID().toString(),nextFuel);
   var jobs=data.collection("jobs");long owned=jobs.getAllKeys().stream().filter(k->jobs.getCompound(k).getUUID("owner").equals(p.getUUID())).count();require(owned<64);
   var job=new CompoundTag();job.putUUID("owner",p.getUUID());job.putLong("readyAt",0);job.put("items",output);mutation(mutations,"jobs",java.util.UUID.randomUUID().toString(),job);result=ItemStack.parse(p.registryAccess(),output.getCompound(0)).orElseThrow();detail="20";
  }else throw new IllegalStateException("error.realmeconomy.recipe");
  return new NativeQuote(kind,units,result,plan.encode(),mutations,seed,detail);
 }
 public static void mutation(ListTag list,String map,String key,CompoundTag value){var m=new CompoundTag();m.putString("map",map);m.putString("key",key);m.put("value",value);list.add(m);}
}
