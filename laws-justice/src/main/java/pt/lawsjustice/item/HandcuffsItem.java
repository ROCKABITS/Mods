package pt.lawsjustice.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import pt.lawsjustice.service.CourtService;

/** A tool, never an authorization: the server must find a valid physical warrant. */
public final class HandcuffsItem extends Item {
    public HandcuffsItem(Properties properties) { super(properties); }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                            InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(target instanceof Player)) return InteractionResult.PASS;
        if (player.getUUID().equals(target.getUUID())) return InteractionResult.FAIL;
        if (!player.level().isClientSide) {
            if (!(player instanceof ServerPlayer actor) || !(target instanceof ServerPlayer accused)
                    || actor.serverLevel() != accused.serverLevel() || actor.distanceToSqr(accused) > 9) {
                return InteractionResult.FAIL;
            }
            if (actor.isShiftKeyDown()) CourtService.releaseHandcuffs(actor, accused);
            else CourtService.applyHandcuffs(actor, accused);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lawsjustice.handcuffs.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.lawsjustice.handcuffs.tooltip.release").withStyle(ChatFormatting.GOLD));
    }
}
