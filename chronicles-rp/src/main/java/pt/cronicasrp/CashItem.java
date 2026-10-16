package pt.cronicasrp;

import java.util.List;
import pt.cronicasrp.i18n.ModMessages;
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
import pt.cronicasrp.domain.CurrencyDenomination;
import pt.cronicasrp.domain.Money;

public final class CashItem extends Item {
    private final CurrencyDenomination denomination;
    public CashItem(Properties properties, CurrencyDenomination denomination) { super(properties); this.denomination = denomination; }
    public CurrencyDenomination denomination() { return denomination; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.cronicasrp.cash.value", ModMessages.money(denomination.cents())).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.cronicasrp.cash.stack", ModMessages.money((long) denomination.cents() * stack.getCount())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.cronicasrp.cash.use").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.cronicasrp.cash.shift").withStyle(ChatFormatting.DARK_GRAY));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(stack);
        try {
            int index = hand == InteractionHand.OFF_HAND ? 36 : player.getInventory().selected;
            Component message = CashService.depositHand(serverPlayer, index, player.isShiftKeyDown() ? stack.getCount() : 1);
            player.displayClientMessage(message, true);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        } catch (IllegalArgumentException ex) {
            player.displayClientMessage(ModMessages.error(ex.getMessage()).copy().withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
    }
}
