package pt.cronicasrp.client;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.ModAttachments;
import pt.cronicasrp.RpNames;
import pt.cronicasrp.domain.CharacterProfile;
import pt.cronicasrp.network.ProfileNetwork;
import pt.cronicasrp.network.ProfilePackets;
import pt.cronicasrp.network.RpNamePackets;

/** Actual-client/integrated-server QA. Demonstration entities are fixtures, never release code. */
@EventBusSubscriber(modid = CronicasRP.MOD_ID, value = Dist.CLIENT)
public final class NamesClientQA {
    private static final String ENABLED = System.getProperty("cronicasrp.nameQaOutput");
    private static final Path OUT = Path.of(ENABLED == null ? "qa-results/rp-names-client" : ENABLED);
    private static final UUID NAMED_ID = UUID.fromString("7d857b39-ddde-4832-8d33-70947bbf5301");
    private static final UUID FALLBACK_ID = UUID.fromString("7d857b39-ddde-4832-8d33-70947bbf5302");
    private static final GameProfile NAMED_PROFILE = new GameProfile(NAMED_ID, "Builder_42");
    private static final GameProfile FALLBACK_PROFILE = new GameProfile(FALLBACK_ID, "Ricardo123");
    private static final String LOCAL_NAME = "Mira da Coroa";
    private static final String CHAT_TEXT = "Os contratos do reino estão abertos.";
    private static final List<String> CHECKS = new ArrayList<>(), CAPTURES = new ArrayList<>();
    private static final long STARTED = System.currentTimeMillis();
    private static volatile Throwable serverFailure;
    private static volatile boolean prepared;
    private static volatile FakePlayer named, fallback;
    private static RemotePlayer namedEntity, fallbackEntity;
    private static String trueLocalUsername;
    private static int step, lastStep = -1;
    private static long nextAction, stepStarted;
    private static boolean done;

    private NamesClientQA() {}

    private static CharacterProfile profile(String name) {
        return new CharacterProfile(true, name, 28, "origins:human", "Uma viajante do reino.", name,
                "Comum", 5_000, 5_000, "");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static boolean click(String label) {
        var screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        for (var child : List.copyOf(screen.children())) {
            if (child instanceof AbstractButton button && button.active && button.visible
                    && (button.getMessage().getString().equals(label)
                    || button.getMessage().getContents() instanceof TranslatableContents key && key.getKey().equals(label))) {
                button.onPress(); return true;
            }
        }
        return false;
    }

    private static void serverAction(Runnable action) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        check(server != null, "Expected a real integrated server");
        server.execute(() -> {
            try { action.run(); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void prepareWorld() {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        serverAction(() -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var player = server.getPlayerList().getPlayer(uuid);
            check(player != null, "Local player missing from real PlayerList");
            var level = player.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setDayTime(1_000);
            for (int x = -5; x <= 5; x++) for (int z = -3; z <= 8; z++)
                level.setBlock(new BlockPos(x, 160, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            for (int x = -5; x <= 5; x++) for (int y = 161; y <= 164; y++)
                level.setBlock(new BlockPos(x, y, -3), (Math.abs(x) == 5 ? Blocks.STRIPPED_OAK_LOG
                        : Blocks.OAK_PLANKS).defaultBlockState(), 3);
            player.setData(ModAttachments.PROFILE, profile(LOCAL_NAME));
            ProfileNetwork.send(player, ProfilePackets.VIEW, "");
            player.connection.teleport(0.5, 161, 6.5, 180, -7);
            named = FakePlayerFactory.get(level, NAMED_PROFILE);
            fallback = FakePlayerFactory.get(level, FALLBACK_PROFILE);
            named.setData(ModAttachments.PROFILE, profile("Aetherion"));
            fallback.setData(ModAttachments.PROFILE, CharacterProfile.empty());
            RpNames.synchronize(named);
            RpNames.synchronize(fallback);
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(named, fallback)));
            check(named.getGameProfile().equals(NAMED_PROFILE), "Server RP name changed GameProfile identity");
            check(named.getDisplayName().getString().equals("Aetherion"), "Production server NameFormat did not apply");
            check(fallback.getDisplayName().getString().equals("Ricardo123"), "Server fallback name was replaced");
            prepared = true;
        });
    }

    private static RemotePlayer remote(GameProfile profile, int id, double x) {
        var mc = Minecraft.getInstance();
        var entity = new RemotePlayer(mc.level, profile);
        entity.setId(id);
        entity.setPos(x, 161, 0.5);
        entity.setYRot(0);
        entity.setYBodyRot(0);
        entity.setYHeadRot(0);
        entity.setNoGravity(true);
        mc.level.addEntity(entity);
        return entity;
    }

    private static void assertWorldNames() {
        var mc = Minecraft.getInstance();
        check(namedEntity.getDisplayName().getString().equals("Aetherion"), "Client entity still displayed the account name");
        check(fallbackEntity.getDisplayName().getString().equals("Ricardo123"), "Uncreated remote character lost username fallback");
        check(namedEntity.getGameProfile().equals(NAMED_PROFILE), "Client entity identity was mutated");
        check(mc.player.getGameProfile().getName().equals(trueLocalUsername), "Local account identity was mutated");
        check(mc.player.getDisplayName().getString().equals(LOCAL_NAME), "Local production display name did not update");
        check(ClientState.profile().name().equals(LOCAL_NAME), "Private profile sync did not complete");
    }

    private static void assertTabNames() {
        var connection = Minecraft.getInstance().getConnection();
        var rp = connection.getPlayerInfo(NAMED_ID);
        var normal = connection.getPlayerInfo(FALLBACK_ID);
        check(rp != null && normal != null, "Vanilla player-info packets did not reach client");
        check(rp.getTabListDisplayName() != null && rp.getTabListDisplayName().getString().equals("Aetherion"),
                "Native TAB entry did not receive authoritative RP display component");
        check(Minecraft.getInstance().gui.getTabList().getNameForDisplay(normal).getString().equals("Ricardo123"),
                "Native TAB username fallback was lost");
        check(rp.getProfile().equals(NAMED_PROFILE), "TAB replaced the authentic GameProfile");
    }

    @SuppressWarnings("unchecked")
    private static boolean receivedRealChat() throws ReflectiveOperationException {
        var chat = Minecraft.getInstance().gui.getChat();
        var field = chat.getClass().getDeclaredField("allMessages");
        field.setAccessible(true);
        for (Object line : (List<?>) field.get(chat)) {
            Component content = (Component) line.getClass().getMethod("content").invoke(line);
            if (content.getString().contains(CHAT_TEXT)) {
                check(content.getString().contains(LOCAL_NAME), "Real chat message did not use the RP sender");
                check(!content.getString().contains(trueLocalUsername), "Real chat still exposed the account name");
                return true;
            }
        }
        return false;
    }

    private static void capture(String file) throws Exception {
        Files.createDirectories(OUT);
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            check(image.getWidth() == 1600 && image.getHeight() == 900, "Expected a real 1600x900 framebuffer");
            image.writeToFile(OUT.resolve(file));
        }
        CAPTURES.add(file);
        System.out.println("CRP_NAMES_CLIENT_QA capture " + file);
    }

    private static void complete(boolean success, String message) {
        done = true;
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "success", success, "message", message, "step", step, "checks", CHECKS, "captures", CAPTURES,
                    "actual_minecraft_client", true, "actual_integrated_server", true,
                    "demonstration_remote_entities", true, "real_chat_transport", true)));
        } catch (Exception failure) { failure.printStackTrace(); }
        System.out.println(success ? "CRP_NAMES_CLIENT_QA_PASSED" : "CRP_NAMES_CLIENT_QA_FAILED " + message);
        Minecraft.getInstance().stop();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (ENABLED == null || done) return;
        var mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        try {
            if (serverFailure != null) throw new IllegalStateException("Integrated-server assertion failed", serverFailure);
            check(now - STARTED < 420_000, "Name client QA timed out");
            if (now < nextAction || mc.getOverlay() != null) return;
            nextAction = now + 700;
            if (lastStep != step) { lastStep = step; stepStarted = now; }
            else check(now - stepStarted < 90_000, "Name client QA stalled at step " + step);
            switch (step) {
                case 0 -> {
                    if (mc.screen instanceof TitleScreen) {
                        mc.getWindow().setWindowed(1600, 900); mc.options.guiScale().set(2);
                        mc.options.renderDistance().set(4); mc.options.simulationDistance().set(4);
                        mc.resizeDisplay(); CreateWorldScreen.openFresh(mc, mc.screen); step++;
                    } else if (mc.screen != null && mc.screen.getClass().getSimpleName().contains("Accessibility")) click("gui.continue");
                }
                case 1 -> {
                    if (mc.screen instanceof CreateWorldScreen screen) {
                        screen.getUiState().setName("RP public names QA");
                        screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                        screen.getUiState().setAllowCommands(true);
                        screen.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                        check(click("selectWorld.create"), "Missing native create-world button"); step++;
                    }
                }
                case 2 -> {
                    if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) {
                        trueLocalUsername = mc.player.getGameProfile().getName();
                        prepareWorld(); step++;
                    }
                }
                case 3 -> {
                    if (!prepared || !ClientState.profile().created() || !RpNamesClient.publicName(NAMED_ID).equals("Aetherion")) return;
                    OriginsCreationFlow.reset(); mc.setScreen(null);
                    namedEntity = remote(NAMED_PROFILE, 7_000_001, -1.3);
                    fallbackEntity = remote(FALLBACK_PROFILE, 7_000_002, 2.0);
                    mc.gui.getChat().clearMessages(true);
                    assertWorldNames(); assertTabNames();
                    CHECKS.add("Production server NameFormat and S2C UUID/name packet reached actual client entities; uncreated account falls back.");
                    CHECKS.add("Vanilla PlayerInfo/TAB display components use RP names while both server and client GameProfiles stay authentic.");
                    nextAction = now + 3_500; step++;
                }
                case 4 -> {
                    assertWorldNames();
                    capture("chronicles-rp-names-world.png");
                    mc.options.keyPlayerList.setDown(true); nextAction = now + 2_000; step++;
                }
                case 5 -> {
                    assertTabNames(); capture("chronicles-rp-names-tab.png");
                    mc.options.keyPlayerList.setDown(false);
                    mc.player.connection.sendChat(CHAT_TEXT); step++;
                }
                case 6 -> {
                    if (!receivedRealChat()) return;
                    CHECKS.add("Normal client sendChat travelled through integrated-server player chat and returned decorated with RP name, without chat cancellation/rebroadcast.");
                    mc.setScreen(new ChatScreen("")); nextAction = now + 2_000; step++;
                }
                case 7 -> {
                    capture("chronicles-rp-names-chat.png");
                    mc.setScreen(new CharacterProfileScreen()); nextAction = now + 2_000; step++;
                }
                case 8 -> {
                    capture("chronicles-rp-names-profile.png"); mc.setScreen(null);
                    serverAction(() -> {
                        named.setData(ModAttachments.PROFILE, profile("Aetherion da Lua"));
                        RpNames.synchronize(named);
                    }); step++;
                }
                case 9 -> {
                    if (!RpNamesClient.publicName(NAMED_ID).equals("Aetherion da Lua")) return;
                    check(namedEntity.getDisplayName().getString().equals("Aetherion da Lua"), "Changed RP identity did not refresh an existing entity");
                    CHECKS.add("An authoritative RP-name edit invalidated an already cached client entity display name.");
                    serverAction(() -> {
                        named.setData(ModAttachments.PROFILE, CharacterProfile.empty());
                        RpNames.synchronize(named);
                    }); step++;
                }
                case 10 -> {
                    if (!RpNamesClient.publicName(NAMED_ID).isEmpty()) return;
                    check(namedEntity.getDisplayName().getString().equals("Builder_42"), "Cleared character did not restore real username fallback");
                    CHECKS.add("An authoritative empty-name update removed the old alias and restored username fallback.");
                    var uuid = mc.player.getUUID();
                    serverAction(() -> PacketDistributor.sendToPlayer(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid), new RpNamePackets.Reset()));
                    step++;
                }
                case 11 -> {
                    if (!RpNamesClient.publicName(mc.player.getUUID()).isEmpty()) return;
                    check(mc.player.getDisplayName().getString().equals(trueLocalUsername), "Roster reset retained a stale local alias");
                    check(ClientState.profile().created() && ClientState.profile().name().equals(LOCAL_NAME), "Public-name reset leaked into private character state");
                    CHECKS.add("The real Reset payload clears public aliases and existing display-name caches without altering the private profile.");
                    var uuid = mc.player.getUUID();
                    serverAction(() -> RpNames.sendRoster(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid))); step++;
                }
                case 12 -> {
                    if (!RpNamesClient.publicName(mc.player.getUUID()).equals(LOCAL_NAME)) return;
                    check(mc.player.getDisplayName().getString().equals(LOCAL_NAME), "Server roster did not restore the authoritative local RP name");
                    CHECKS.add("A fresh server roster restored online aliases after reset; real account UUID and username remain unchanged.");
                    complete(true, "Actual Minecraft rendering, remote name tags, TAB, normal player chat, own journal, name edits/clears and network reset/roster passed.");
                }
                default -> throw new IllegalStateException("Unknown name QA step");
            }
            System.out.println("CRP_NAMES_CLIENT_QA step " + step + " screen " + (mc.screen == null ? "world" : mc.screen.getClass().getSimpleName()));
        } catch (Throwable failure) { failure.printStackTrace(); complete(false, failure.toString()); }
    }
}
