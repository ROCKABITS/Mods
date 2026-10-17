package pt.realmnotifications.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid="realmnotifications",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class NotificationClientEvents {
    public static final KeyMapping HISTORY_KEY=new KeyMapping("key.realmnotifications.history",GLFW.GLFW_KEY_N,"key.categories.realmnotifications");
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {event.register(HISTORY_KEY);}
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("realmnotifications","notifications"),(g,delta)-> {
            var window=net.minecraft.client.Minecraft.getInstance().getWindow();
            NotificationsClient.render(g,window.getGuiScaledWidth(),window.getGuiScaledHeight());
        });
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->ModList.get().getModContainerById("realmnotifications").orElseThrow()
            .registerExtensionPoint(IConfigScreenFactory.class,()->(container,parent)->new NotificationScreen(parent,false)));
    }
}
