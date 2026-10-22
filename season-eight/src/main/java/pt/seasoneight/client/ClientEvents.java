package pt.seasoneight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import pt.seasoneight.SeasonConfig;
import pt.seasoneight.SeasonEight;

@EventBusSubscriber(modid = SeasonEight.ID, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        // Other mods' custom TitleScreen subclasses keep ownership of their screens.
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == TitleScreen.class &&
                SeasonConfig.enabled(SeasonConfig.MENU) && !Minecraft.getInstance().isDemo())
            event.setNewScreen(new SeasonTitleScreen());
    }
    private ClientEvents() {}
}
