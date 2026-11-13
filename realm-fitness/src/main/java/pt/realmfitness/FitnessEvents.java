package pt.realmfitness;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid="realmfitness")
public final class FitnessEvents {
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){FitnessService.tick(event.getServer());}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)try{FitnessService.apply(p);}catch(RuntimeException failure){FitnessMod.LOG.error("Athlete data unavailable",failure);}}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)FitnessService.apply(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){FitnessService.logout(p);FitnessService.apply(p);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)FitnessService.logout(p);}
    @SubscribeEvent public static void fall(LivingFallEvent e){if(e.getEntity() instanceof ServerPlayer p)e.setDamageMultiplier((float)(e.getDamageMultiplier()*(1-FitnessService.fallReduction(p))));}
    @SubscribeEvent public static void card(PlayerInteractEvent.RightClickItem e){if(e.getEntity() instanceof ServerPlayer p&&e.getItemStack().is(FitnessMod.CARD.get())){FitnessService.open(p,null,false);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);e.setCanceled(true);}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){FitnessService.clear();}
}
