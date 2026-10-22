package pt.seasoneight.client;

import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class SeasonTitleScreen extends TitleScreen {
    private MenuLayout layout;
    @Override protected void init() {
        super.init();
        var originals = new LinkedHashMap<String, AbstractButton>();
        for (var child : List.copyOf(children())) if (child instanceof AbstractButton b && b.getMessage().getContents() instanceof TranslatableContents t)
            originals.put(t.getKey(), b);
        // Keeping vanilla's real actions also keeps its multiplayer safety dialog and demo/account restrictions.
        clearWidgets();
        layout = MenuLayout.of(width, height);
        String[] keys = {"menu.multiplayer", "menu.singleplayer", "fml.menu.mods", "menu.options", "menu.online",
                "title.credits", "options.language", "options.accessibility", "menu.quit"};
        for (int i = 0; i < keys.length; i++) {
            var button = originals.get(keys[i]);
            if (button != null) {
                var styled = new SeasonButton(button, layout.controls().get(i), i == 0);
                if (keys[i].equals("title.credits")) styled.setMessage(Component.translatable("seasoneight.menu.credits"));
                if (keys[i].equals("menu.quit")) styled.setMessage(Component.translatable("seasoneight.menu.quit"));
                if (keys[i].equals("options.accessibility")) styled.setMessage(Component.translatable("seasoneight.menu.accessibility"));
                addRenderableWidget(styled);
            }
        }
    }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        SeasonArt.background(g, width, height);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        int x = layout.margin();
        if (height >= 250) {
            g.drawString(font, "R O C K A B I T S  /  R O L E P L A Y", x, 19, 0xFF85798F, false);
        }
        SeasonArt.image(g, "wordmark", x - 3, layout.logoY(), layout.contentWidth() + 6, layout.logoHeight());
        if (layout.description()) {
            g.fill(x + 1, layout.logoY() + layout.logoHeight() + 9, x + 32, layout.logoY() + layout.logoHeight() + 10, 0xFFBDA070);
            g.drawWordWrap(font, Component.translatable("seasoneight.menu.story"), x + 1,
                    layout.logoY() + layout.logoHeight() + 23, layout.contentWidth() - 8, 0xFFA79AAF);
        }
        if (layout.artwork()) {
            int cx = (x + layout.contentWidth() + width - x) / 2;
            int cy = height / 2 - 9;
            int size = Math.min(Math.min(width - x - layout.contentWidth() - 20, 255), (int)(height * .65));
            SeasonArt.seal(g, cx, cy, size);
            if (height >= 330) {
                g.drawCenteredString(font, Component.translatable("seasoneight.menu.chapter"), cx, cy + size / 2 + 17, 0xFFC4AFD3);
                g.drawCenteredString(font, Component.translatable("seasoneight.menu.world"), cx, cy + size / 2 + 33, 0xFF73677F);
            }
        }
        g.fill(x, height - 21, width - x, height - 20, 0xFF39303F);
        g.drawString(font, "SEASON 8", x, height - 13, 0xFFB3A18C, false);
        String version = "Minecraft 1.21.1  /  NeoForge";
        if (width > 370) g.drawString(font, version, width - x - font.width(version), height - 13, 0xFF6F657B, false);
        // Screen.render would draw the vanilla panorama; render just the preserved controls instead.
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.Renderable r) r.render(g, mouseX, mouseY, partial);
    }
    @Override public void tick() { /* No hidden Realms notifications or remote connections are started by this screen. */ }
}
