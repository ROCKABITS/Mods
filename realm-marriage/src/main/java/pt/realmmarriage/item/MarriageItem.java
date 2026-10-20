package pt.realmmarriage.item;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import pt.realmmarriage.service.MarriageService;
public final class MarriageItem extends Item {
    private final boolean document;
    public MarriageItem(Properties properties,boolean document){super(properties);this.document=document;}
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player p,LivingEntity target,InteractionHand hand){if(document||!(target instanceof Player))return InteractionResult.PASS;if(p instanceof ServerPlayer sender&&target instanceof ServerPlayer recipient&&hand==InteractionHand.MAIN_HAND)try{MarriageService.propose(sender,recipient);}catch(RuntimeException e){sender.displayClientMessage(net.minecraft.network.chat.Component.translatable(MarriageService.error(e)),false);}return InteractionResult.sidedSuccess(p.level().isClientSide);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player p,InteractionHand hand){if(p instanceof ServerPlayer server){if(document&&p.isShiftKeyDown())MarriageService.inspect(server,hand);else MarriageService.personal(server);}return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),level.isClientSide);}
}
