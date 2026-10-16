package pt.cronicasrp.client;

import java.util.List;
import com.iafenvoy.origins.screen.ViewOriginScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Keep Origins' full powers/layers UI, with a working return to the RP draft. */
final class OriginsViewScreen extends ViewOriginScreen {
    private final Screen parent;
    OriginsViewScreen(Screen parent) { this.parent = parent; }
    @Override protected void init() {
        super.init();
        for (var child : List.copyOf(children())) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("origins.gui.close")) {
                removeWidget(button);
                addRenderableWidget(new RpgButton(button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                        UiText.s("gui.cronicasrp.button.back"), false, true, this::onClose));
            }
        }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

