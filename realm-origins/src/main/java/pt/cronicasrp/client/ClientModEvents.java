package pt.cronicasrp.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import pt.cronicasrp.CronicasRP;

@EventBusSubscriber(modid = CronicasRP.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    public static final KeyMapping PROFILE_KEY = new KeyMapping("key.cronicasrp.profile", GLFW.GLFW_KEY_P, "key.categories.cronicasrp");
    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(PROFILE_KEY); }
}
