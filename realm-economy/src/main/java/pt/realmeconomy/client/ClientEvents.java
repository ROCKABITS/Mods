package pt.realmeconomy.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import pt.realmeconomy.menu.EconomyMenu;

@EventBusSubscriber(modid="realmeconomy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(pt.realmeconomy.menu.ColonyMenu.TYPE.get(),ColonyScreen::new);event.register(EconomyMenu.TYPE.get(),TradingScreen::new);event.register(pt.realmeconomy.menu.ServicesMenu.TYPE.get(),ServicesScreen::new);}
}
