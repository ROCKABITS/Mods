package pt.lawsjustice.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Recoverable reference to the server's sealed pouch; the item contains no editable work counters. */
public final class WorkBundleItem extends Item {
    public WorkBundleItem(Properties properties){super(properties);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){
        lines.add(Component.translatable("item.lawsjustice.work_bundle.tooltip"));
        lines.add(Component.translatable("item.lawsjustice.work_bundle.tooltip.delivery"));
    }
}
