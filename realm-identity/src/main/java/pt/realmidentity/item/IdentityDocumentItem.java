package pt.realmidentity.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import pt.realmidentity.service.IdentityService;

/** A portable signed reference. A renamed item or edited lore grants no authority. */
public final class IdentityDocumentItem extends Item {
    public IdentityDocumentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            IdentityService.inspectHeld(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Player) || player.getUUID().equals(target.getUUID())) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide && player instanceof ServerPlayer actor
                && target instanceof ServerPlayer recipient) {
            IdentityService.offer(actor, recipient, hand);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.realmidentity.identity_document.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.realmidentity.identity_document.tooltip.unissued")
                .withStyle(ChatFormatting.DARK_GREEN));
    }
}
