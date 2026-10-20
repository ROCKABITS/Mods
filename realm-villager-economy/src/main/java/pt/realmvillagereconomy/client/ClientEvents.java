package pt.realmvillagereconomy.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import pt.realmvillagereconomy.menu.EconomyMenu;

@EventBusSubscriber(modid="realmvillagereconomy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(EconomyMenu.TYPE.get(),TradingScreen::new);}
}
