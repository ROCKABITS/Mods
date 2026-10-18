package pt.realmcasino.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import pt.realmcasino.blockentity.CasinoBlockEntities;

@EventBusSubscriber(modid="realmcasino",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CasinoClientEvents {
    private CasinoClientEvents() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(CasinoBlockEntities.MACHINE.get(),CasinoRenderer::new);}
}
