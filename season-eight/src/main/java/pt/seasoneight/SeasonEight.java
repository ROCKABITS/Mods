package pt.seasoneight;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = SeasonEight.ID, dist = Dist.CLIENT)
public final class SeasonEight {
    public static final String ID = "seasoneight";
    public SeasonEight(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SeasonConfig.SPEC);
        bus.addListener(this::setup);
    }
    private void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.Minecraft.getInstance().updateTitle();
            pt.seasoneight.client.WindowBranding.install();
        });
    }
}
