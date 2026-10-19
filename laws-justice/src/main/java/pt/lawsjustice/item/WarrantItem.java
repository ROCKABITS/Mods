package pt.lawsjustice.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import pt.lawsjustice.service.CourtService;

/** A paper reference to a server-owned warrant; client item text grants no power. */
public final class WarrantItem extends Item {
    public WarrantItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer actor) CourtService.inspectWarrant(actor, held);
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lawsjustice.warrant.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
