package pt.realmcouncil.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import pt.realmcouncil.RealmCouncil;
@EventBusSubscriber(modid=RealmCouncil.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){NeoForge.EVENT_BUS.addListener(CouncilClient::tick);}
}
