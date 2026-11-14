package pt.realmfitness;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid="realmfitness")
public final class FitnessEvents {
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)FitnessService.tick(event.getServer());}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)try{FitnessService.apply(p);}catch(RuntimeException failure){FitnessMod.LOG.error("Athlete data unavailable",failure);}}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)FitnessService.apply(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){FitnessService.logout(p);FitnessService.apply(p);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)FitnessService.logout(p);}
    @SubscribeEvent public static void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event){
        if(event.getEntity() instanceof net.minecraft.world.entity.player.Player p){var attribute=p.getAttribute(FitnessMod.JUMP_STRENGTH.get());if(attribute!=null){var velocity=p.getDeltaMovement();p.setDeltaMovement(velocity.x,velocity.y*attribute.getValue(),velocity.z);}}
    }
    @SubscribeEvent public static void fall(LivingFallEvent e){if(e.getEntity() instanceof ServerPlayer p)e.setDamageMultiplier((float)(e.getDamageMultiplier()*(1-FitnessService.fallReduction(p))));}
    @SubscribeEvent public static void machine(PlayerInteractEvent.RightClickBlock e){
        var state=e.getLevel().getBlockState(e.getPos());if(!(state.getBlock() instanceof GymBlock))return;
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);e.setCanceled(true);
        if(e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND&&e.getEntity() instanceof ServerPlayer p)FitnessService.interact(p,GymBlock.anchor(e.getPos(),state),p.isShiftKeyDown());
    }
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent e){FitnessService.stop(e.getServer());}
    @SubscribeEvent public static void card(PlayerInteractEvent.RightClickItem e){if(e.getEntity() instanceof ServerPlayer p&&e.getItemStack().is(FitnessMod.CARD.get())){FitnessService.open(p,null,false);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);e.setCanceled(true);}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){FitnessService.clear();}
}
