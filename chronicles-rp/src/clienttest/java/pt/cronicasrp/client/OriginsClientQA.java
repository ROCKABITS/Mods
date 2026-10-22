package pt.cronicasrp.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import com.iafenvoy.origins.screen.ChooseOriginScreen;
import com.iafenvoy.origins.attachment.OriginDataHolder;
import com.iafenvoy.origins.data.layer.LayerRegistries;
import com.iafenvoy.origins.data.origin.OriginRegistries;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.ModAttachments;
import pt.cronicasrp.compat.OriginsBridge;
import pt.cronicasrp.domain.CharacterProfile;

/** Opt-in end-to-end harness. Never packaged into the release. */
@EventBusSubscriber(modid = CronicasRP.MOD_ID, value = Dist.CLIENT)
public final class OriginsClientQA {
    private static final Path OUT = Path.of(System.getProperty("cronicasrp.qaOutput", "qa-results/client"));
    private static int step;
    private static long started = System.currentTimeMillis(), nextAction;
    private static CharacterProfile saved;
    private static boolean done, raceButtonClicked, raceSelected;
    private static CharacterCreationScreen draft;
    private static long firstCreationAt, welcomeAt;
    private static boolean storyWritten, signed;
    private static int profileStage;
    private static int lastStep = -1;
    private static net.minecraft.client.gui.screens.Screen lastRenderedScreen;
    private static long renderedFrames, lastActionFrame = -1;
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event) {
        lastRenderedScreen = event.getScreen(); renderedFrames++;
    }
    private static long stepStarted;
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private static boolean click(String label) {
        var mc = Minecraft.getInstance();
        if (mc.screen == null) return false;
        for (var child : List.copyOf(mc.screen.children()))
            if (child instanceof AbstractButton button && button.active && button.visible &&
                    (button.getMessage().getString().equals(label) ||
                     button.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(label))) {
                button.onPress(); return true;
            }
        return false;
    }
    private static void capture(String name) throws Exception {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof BookScreen) {
            var widgets = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget)
                    .map(c -> (net.minecraft.client.gui.components.AbstractWidget)c).filter(w -> w.visible).toList();
            for (var w : widgets) {
                check(w.getX() >= 0 && w.getY() >= 0 && w.getX() + w.getWidth() <= screen.width && w.getY() + w.getHeight() <= screen.height,
                        "Widget outside " + name + ": " + w.getMessage().getString());
                for (var other : widgets) if (other != w) check(w.getX() >= other.getX() + other.getWidth() || other.getX() >= w.getX() + w.getWidth()
                                || w.getY() >= other.getY() + other.getHeight() || other.getY() >= w.getY() + w.getHeight(),
                        "Overlapping controls on " + name + ": " + w.getMessage().getString() + " / " + other.getMessage().getString());
            }
        }
        Files.createDirectories(OUT);
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(OUT.resolve(name + ".png"));
        }
        System.out.println("CRP_ORIGINS_QA capture " + name);
    }
    private static void complete(boolean success, String message) {
        done = true;
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.json"), new com.google.gson.Gson().toJson(java.util.Map.of(
                    "success", success, "step", step, "message", message,
                    "with_classes", net.neoforged.fml.ModList.get().isLoaded("origins_classes"),
                    "visual_rework", success && step == 23,
                    "race", saved == null ? "" : saved.race(),
                    "balance", saved == null ? 0 : saved.balance())));
        } catch (Exception ignored) {}
        System.out.println("CRP_ORIGINS_QA " + success + " " + message);
        Minecraft.getInstance().stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (System.getProperty("cronicasrp.nameQaOutput") != null) return;
        if (done) return;
        var mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now - started > 360000) { complete(false, "Timeout at " + (mc.screen == null ? "world" : mc.screen.getClass().getSimpleName())); return; }
        if (now < nextAction || mc.getOverlay() != null) return;
        // Catch-up ticks can run after changing screens but before the first rendered frame.
        // Capture the current screen only after it has actually reached the framebuffer.
        if (mc.screen != null && (mc.screen != lastRenderedScreen || renderedFrames <= lastActionFrame)) return;
        lastActionFrame = renderedFrames;
        nextAction = now + 700;
        if (lastStep != step) { lastStep = step; stepStarted = now; }
        else if (now - stepStarted > 60000 && step >= 2) {
            complete(false, "Stalled at step " + step + " on " + (mc.screen == null ? "world" : mc.screen.getClass().getSimpleName())); return;
        }
        try {
            check(raceButtonClicked || !(mc.screen instanceof ChooseOriginScreen), "Origins appeared before the RP lineage button was clicked");
            switch (step) {
                case 0 -> {
                    if (mc.screen instanceof TitleScreen) {
                        mc.options.guiScale().set(2); mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE; mc.resizeDisplay();
                        CreateWorldScreen.openFresh(mc, mc.screen); step++;
                    } else if (mc.screen != null && mc.screen.getClass().getSimpleName().contains("Accessibility"))
                        click("gui.continue");
                }
                case 1 -> {
                    if (mc.screen instanceof CreateWorldScreen screen) {
                        screen.getUiState().setName("Origins RP QA");
                        screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                        screen.getUiState().setAllowCommands(true);
                        screen.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                        check(click("selectWorld.create"), "No create world button"); step++;
                    }
                }
                case 2 -> {
                    if (mc.screen instanceof CharacterCreationScreen screen) {
                        check(!OriginsBridge.ready(mc.player), "Fresh character already had Origins choices");
                        if (firstCreationAt == 0) firstCreationAt = now;
                        if (now - firstCreationAt < 2100) return;
                        draft = screen;
                        for (var child : screen.children()) if (child instanceof EditBox box) {
                            if (box.getMessage().getString().equals(UiText.s("gui.cronicasrp.field.rp_name"))) box.setValue("Ari Moonborn");
                            if (box.getMessage().getString().equals(UiText.s("gui.cronicasrp.field.rp_age"))) box.setValue("128");
                        }
                        check(screen.children().stream().anyMatch(c -> c instanceof AbstractButton b &&
                                b.getMessage().getString().equals(UiText.s("gui.cronicasrp.button.continue")) && !b.active),
                                "Creation advanced before Origins choices");
                        capture("00-rp-before-origins");
                        raceButtonClicked = true;
                        check(click(UiText.s("gui.cronicasrp.label.lineage_prefix") + UiText.selectedRace()), "No lineage selector");
                        step++;
                    }
                }
                case 3 -> {
                    if (mc.screen instanceof ChooseOriginScreen screen) {
                        String layer = screen.getCurrentLayer().unwrapKey().orElseThrow().location().toString();
                        if (layer.equals("origins_classes:class")) check(raceSelected, "Class opened before race");
                        else raceSelected = true;
                        String desired = layer.equals("origins_classes:class") ? "origins_classes:warrior" : "origins:avian";
                        boolean selected = false;
                        for (var child : List.copyOf(screen.children())) {
                            if (child instanceof AbstractButton button && button.getMessage().getString().isEmpty()) {
                                button.onPress();
                                if (screen.getCurrentOrigin().unwrapKey().isPresent() &&
                                        screen.getCurrentOrigin().unwrapKey().orElseThrow().location().toString().equals(desired)) {
                                    selected = true; break;
                                }
                            }
                        }
                        check(selected, "Target native choice not available: " + desired);
                        capture(layer.equals("origins_classes:class") ? "02-origins-classes" : "01-origins-race");
                        check(click("origins.gui.select"), "Missing native select button");
                        if (layer.equals("origins_classes:class") || !net.neoforged.fml.ModList.get().isLoaded("origins_classes")) step++;
                    }
                }
                case 4 -> {
                    if (mc.screen instanceof CharacterCreationScreen screen) {
                        check(screen == draft, "Origins returned to a different draft object");
                        check(OriginsBridge.ready(mc.player), "RP resumed without all selections");
                        check(screen.children().stream().anyMatch(c -> c instanceof EditBox b && b.getValue().equals("Ari Moonborn")), "Origins lost the RP name");
                        check(screen.children().stream().anyMatch(c -> c instanceof EditBox b && b.getValue().equals("128")), "Origins lost the RP age");
                        check(OriginsBridge.race(mc.player).orElseThrow().id().toString().equals("origins:avian"), "Incorrect native race");
                        if (net.neoforged.fml.ModList.get().isLoaded("origins_classes"))
                            check(OriginsBridge.playerClass(mc.player).orElseThrow().id().toString().equals("origins_classes:warrior"), "Incorrect native class");
                        else check(OriginsBridge.playerClass(mc.player).isEmpty(), "Optional class was fabricated");
                        capture("03-creation-en");
                        check(click(UiText.s("gui.cronicasrp.label.lineage_prefix") + UiText.selectedRace()), "No Origins link"); step++;
                    }
                }
                case 5 -> {
                    if (mc.screen instanceof OriginsViewScreen) {
                        capture("04-powers-en");
                        check(click(UiText.s("gui.cronicasrp.button.back")), "No back button in native powers view"); step++;
                    }
                }
                case 6 -> {
                    if (mc.screen instanceof CharacterCreationScreen screen) {
                        check(screen.children().stream().anyMatch(c -> c instanceof EditBox box && box.getValue().equals("Ari Moonborn")),
                                "Returning from Origins lost the character draft");
                        check(click(UiText.s("gui.cronicasrp.button.continue")), "Identity step did not advance"); step++;
                    }
                }
                case 7 -> {
                    if (mc.screen instanceof CharacterCreationScreen screen) {
                        if (!storyWritten) {
                            screen.children().stream().filter(c -> c instanceof RpgStoryBox).map(c -> (RpgStoryBox)c).findFirst().orElseThrow()
                                    .setValue("Born beneath a violet sky, Ari set out to write a new chapter among the kingdoms of this world.");
                            storyWritten = true; return;
                        }
                        capture("14-story-en");
                        check(click(UiText.s("gui.cronicasrp.button.continue")), "Story did not advance"); step++;
                    }
                }
                case 8 -> {
                    if (mc.screen instanceof CharacterCreationScreen screen) {
                        var pad = screen.children().stream().filter(c -> c instanceof SignaturePad).map(c -> (SignaturePad)c).findFirst().orElseThrow();
                        if (signed) {
                            capture("05-signature-en");
                            check(click(UiText.s("gui.cronicasrp.button.continue")), "Signature did not advance"); step++; return;
                        }
                        double x = pad.getX()+pad.getWidth()*0.2, y = pad.getY()+pad.getHeight()*0.5;
                        pad.mouseClicked(x,y,0);
                        for (int i=1;i<20;i++) pad.mouseDragged(x+i*2,y+Math.sin(i*0.6)*6,0,2,0);
                        pad.mouseReleased(x+38,y,0);
                        signed = true;
                    }
                }
                case 9 -> {
                    if (mc.screen instanceof CharacterCreationScreen) {
                        capture("06-oath-en");
                        check(click(UiText.s("gui.cronicasrp.button.seal_destiny")), "No confirmation button"); step++;
                    }
                }
                case 10 -> {
                    if (mc.screen instanceof DestinyRevealScreen) {
                        saved = ClientState.profile();
                        check(saved.created() && saved.name().equals("Ari Moonborn") && saved.age() == 128 && saved.race().equals("origins:avian"), "Actual server created incorrect RP identity");
                        if (welcomeAt == 0) welcomeAt = now;
                        if (now - welcomeAt < 5200) return;
                        capture("13-welcome-en");
                        check(click(UiText.s("gui.cronicasrp.button.open_journal")), "No journal action after welcome");
                        if (mc.screen instanceof CharacterProfileScreen) step++;
                    }
                }
                case 11 -> {
                    if (profileStage == 0 && mc.screen instanceof CharacterProfileScreen) {
                        check(ClientState.profile().equals(saved), "Wealth reveal altered saved data");
                        capture("07-profile-en"); check(click(UiText.s("gui.cronicasrp.tab.deeds")), "No deeds tab"); profileStage++;
                    } else if (profileStage == 1) {
                        capture("15-deeds-en"); check(click(UiText.s("gui.cronicasrp.tab.story")), "No story tab"); profileStage++;
                    } else if (profileStage == 2) {
                        capture("16-profile-story-en"); check(click(UiText.s("gui.cronicasrp.tab.wallet")), "No wallet tab"); profileStage++;
                    } else if (profileStage == 3) {
                        capture("17-wallet-en"); check(click(UiText.s("gui.cronicasrp.button.exchange")), "No exchange action"); profileStage++;
                    } else if (profileStage == 4 && mc.screen instanceof CurrencyExchangeScreen screen) {
                        capture("18-exchange-en");
                        screen.children().stream().filter(c -> c instanceof RpgEditBox b && b.getMessage().getString().equals(UiText.s("gui.cronicasrp.cash.amount")))
                                .map(c -> (RpgEditBox)c).findFirst().orElseThrow().setValue("0.01");
                        check(saved.balance() > 0, "Fixture needs one cent for exchange");
                        check(click(UiText.s("gui.cronicasrp.button.withdraw_amount")), "Withdraw action missing"); profileStage++;
                    } else if (profileStage == 5 && ClientState.profile().balance() == saved.balance() - 1) {
                        check(click(UiText.s("gui.cronicasrp.button.deposit")), "No deposit tab");
                        check(click(UiText.s("gui.cronicasrp.button.deposit_amount")), "No deposit action"); profileStage++;
                    } else if (profileStage == 6 && ClientState.profile().balance() == saved.balance()) {
                        check(ClientState.profile().equals(saved), "Exchange changed character data");
                        mc.setScreen(new CharacterProfileScreen());
                        mc.getLanguageManager().setSelected("pt_pt"); mc.options.languageCode="pt_pt";
                        mc.reloadResourcePacks(); step++;
                    }
                }
                case 12 -> {
                    if (mc.screen instanceof CharacterProfileScreen) {
                        capture("08-profile-pt");
                        check(click(UiText.s("gui.cronicasrp.origins.button")), "No Origins link on RP book"); step++;
                    }
                }
                case 13 -> {
                    if (mc.screen instanceof OriginsViewScreen) {
                        capture("09-powers-pt");
                        check(click(UiText.s("gui.cronicasrp.button.back")), "Return from book's powers failed");
                        var server=mc.getSingleplayerServer(); var uuid=mc.player.getUUID();
                        server.execute(() -> {
                            var player=server.getPlayerList().getPlayer(uuid);
                            var layer=player.registryAccess().registryOrThrow(LayerRegistries.LAYER_KEY).getHolderOrThrow(
                                    ResourceKey.create(LayerRegistries.LAYER_KEY,OriginsBridge.RACE_LAYER));
                            var origin=player.registryAccess().registryOrThrow(OriginRegistries.ORIGIN_KEY).getHolderOrThrow(
                                    ResourceKey.create(OriginRegistries.ORIGIN_KEY,ResourceLocation.parse("origins:shulk")));
                            var holder=OriginDataHolder.get(player); holder.setOrigin(layer,origin); holder.sync();
                        });
                        step++;
                    }
                }
                case 14 -> {
                    // The RP book normally pauses singleplayer; let the queued server action run.
                    if (mc.screen instanceof CharacterProfileScreen) mc.setScreen(null);
                    if (ClientState.profile().race().equals("origins:shulk")) {
                        check(ClientState.profile().withRace(saved.race()).equals(saved), "Changing Origins altered RP identity or money");
                        mc.setScreen(new CharacterProfileScreen()); step++;
                    }
                }
                case 15 -> {
                    if (mc.screen instanceof CharacterProfileScreen) {
                        capture("10-profile-changed");
                        mc.getWindow().setWindowed(854,480); mc.options.guiScale().set(2); mc.resizeDisplay(); step++;
                    }
                }
                case 16 -> {
                    if (mc.screen instanceof CharacterProfileScreen) {
                        capture("11-profile-compact");
                        var server=mc.getSingleplayerServer();
                        var uuid=mc.player.getUUID();
                        mc.setScreen(null);
                        server.execute(() -> {
                            var player=server.getPlayerList().getPlayer(uuid);
                            player.setData(ModAttachments.PROFILE, CharacterProfile.empty());
                            pt.cronicasrp.network.ProfileNetwork.send(player,pt.cronicasrp.network.ProfilePackets.CREATE,"");
                        });
                        step++;
                    }
                }
                case 17 -> {
                    if (mc.screen instanceof CharacterCreationScreen) {
                        capture("12-creation-compact");
                        step++;
                    }
                }
                case 18 -> {
                    if (mc.screen instanceof CharacterCreationScreen) {
                        var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
                        mc.setScreen(null);
                        server.execute(() -> {
                            var player = server.getPlayerList().getPlayer(uuid);
                            player.setData(ModAttachments.PROFILE, saved);
                            pt.cronicasrp.network.ProfileNetwork.send(player, pt.cronicasrp.network.ProfilePackets.SYNC, "");
                        }); step++;
                    }
                }
                case 19 -> {
                    if (mc.screen == null && ClientState.profile().created()) { mc.setScreen(new CurrencyExchangeScreen()); return; }
                    if (mc.screen instanceof CurrencyExchangeScreen) {
                        capture("19-exchange-compact");
                        mc.setScreen(new CharacterCreationScreen(true)); step++;
                    }
                }
                case 20 -> {
                    if (mc.screen instanceof CharacterCreationScreen) {
                        capture("20-edit-story-compact");
                        check(click(UiText.s("gui.cronicasrp.button.continue")), "Compact story did not advance"); step++;
                    }
                }
                case 21 -> {
                    if (mc.screen instanceof CharacterCreationScreen) {
                        capture("21-edit-signature-compact");
                        check(click(UiText.s("gui.cronicasrp.button.continue")), "Compact signature did not advance"); step++;
                    }
                }
                case 22 -> {
                    if (mc.screen instanceof CharacterCreationScreen) {
                        capture("22-edit-review-compact");
                        mc.setScreen(new DestinyRevealScreen(ClientState.profile(), true)); step++;
                    }
                }
                case 23 -> {
                    if (mc.screen instanceof DestinyRevealScreen screen) {
                        if (now - stepStarted < 5200) return;
                        capture("23-welcome-compact");
                        var before = ClientState.profile();
                        check(screen.keyPressed(257, 0, 0) && mc.screen == null, "Enter world did not return to gameplay");
                        check(ClientState.profile().equals(before), "Replaying welcome changed saved identity or funds");
                        complete(true, "Realm Origins: native Origins and optional Classes, preserved draft, four chapters, drawn signature, saved welcome, four profile tabs, actual one-cent withdraw/deposit round-trip, EN/PT, compact controls and Enter world without another payment passed.");
                    }
                }
                default -> throw new IllegalStateException("Unknown QA step");
            }
            System.out.println("CRP_ORIGINS_QA step " + step + " screen " + (mc.screen==null ? "world" : mc.screen.getClass().getSimpleName()));
        } catch (Throwable ex) {
            ex.printStackTrace(); complete(false, ex.toString());
        }
    }
}

