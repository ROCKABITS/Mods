package pt.cronicasrp.client;

import com.iafenvoy.origins.screen.ChooseOriginScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.compat.OriginsBridge;

/** Defer the first Origins popup until the player clicks the RP lineage field. */
@EventBusSubscriber(modid = CronicasRP.MOD_ID, value = Dist.CLIENT)
public final class OriginsCreationFlow {
    private static ChooseOriginScreen deferred;
    private static CharacterCreationScreen parent;
    private static boolean choosing;

    private OriginsCreationFlow() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void opening(ScreenEvent.Opening event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (event.getNewScreen() instanceof ChooseOriginScreen screen && !choosing && !ClientState.profile().created()) {
            deferred = screen;
            event.setCanceled(true);
        }
    }

    static void open(CharacterCreationScreen draft) {
        var mc = Minecraft.getInstance();
        var layers = OriginsBridge.pendingLayers(mc.player);
        if (layers.isEmpty()) {
            mc.setScreen(new OriginsViewScreen(draft));
            return;
        }
        deferred = null;
        parent = draft;
        choosing = true;
        mc.setScreen(new ChooseOriginScreen(layers, 0, false));
    }

    static void tick() {
        var mc = Minecraft.getInstance();
        // Closing to the world does not emit ScreenEvent.Opening. Resume on
        // the next client tick, after Origins confirms its final native choice.
        if (choosing && parent != null && mc.screen == null && OriginsBridge.ready(mc.player)) {
            var draft = parent;
            reset();
            mc.setScreen(draft);
            return;
        }
        // A late RP sync may identify an existing character; preserve its normal
        // Origins flow (new layers, an Orb of Origin, administrative changes).
        if (deferred != null && ClientState.profile().created() && mc.screen == null) {
            var screen = deferred;
            deferred = null;
            mc.setScreen(screen);
        }
    }

    static void reset() {
        deferred = null;
        parent = null;
        choosing = false;
    }
}
