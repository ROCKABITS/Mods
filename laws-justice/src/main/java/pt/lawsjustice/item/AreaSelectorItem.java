package pt.lawsjustice.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import pt.lawsjustice.service.JusticeService;

/** Surveying rod; the authoritative selection lives on the server, not item NBT. */
public final class AreaSelectorItem extends Item {
    public AreaSelectorItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.FAIL;
        if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer actor) {
            JusticeService.select(actor, context.getClickedPos(), actor.isShiftKeyDown());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lawsjustice.area_selector.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lawsjustice.area_selector.tooltip.anchor")
                .withStyle(ChatFormatting.GOLD));
    }
}
