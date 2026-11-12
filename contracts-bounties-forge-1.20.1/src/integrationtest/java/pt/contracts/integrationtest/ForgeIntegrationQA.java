package pt.contracts.integrationtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import pt.contracts.ContractsData;
import pt.contracts.ContractsMod;
import pt.contracts.ContractsService;
import pt.contracts.client.ContractsScreen;
import pt.contracts.client.DailyCinematic;
import pt.contracts.client.DailyScreen;
import pt.contracts.daily.DailyData;
import pt.contracts.daily.DailyLedger;
import pt.contracts.daily.DailyNetwork;
import pt.contracts.daily.DailyRewardJournal;
import pt.contracts.daily.DailyRules;
import pt.contracts.daily.DailyService;
import pt.contracts.domain.Ledger;
import pt.contracts.network.ContractsNetwork;

/** Real Forge channels, host player files and production screens. Test-only controlled quests. */
@EventBusSubscriber(modid = "contracts", value = Dist.CLIENT)
public final class ForgeIntegrationQA {
    private static final Path OUT = Path.of(System.getProperty("contracts.integrationOutput"));
    private static final long START = System.currentTimeMillis();
    private static final BlockPos BOARD = new BlockPos(0, 161, 0);
    private static final List<String> CHECKS = new ArrayList<>();
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static volatile UUID playerId, contractId;
    private static volatile List<UUID> questIds;
    private static volatile String worldFolder;
    private static int stage, quest;
    private static long next;
    private static boolean done;
    private ForgeIntegrationQA() {}
    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private static ServerPlayer player() {
        return mc().getSingleplayerServer().getPlayerList().getPlayer(playerId);
    }
    private static void server(Runnable action) {
        ready = false;
        mc().getSingleplayerServer().execute(() -> {
            try { action.run(); ready = true; }
            catch (Throwable error) { failure = error; }
        });
    }
    private static boolean click(String key) {
        Screen screen = mc().screen;
        if (screen == null) return false;
        String label = Component.translatable(key).getString();
        for (var child : List.copyOf(screen.children())) {
            if (child instanceof AbstractButton button && button.active && button.visible
                    && button.getMessage().getString().equals(label)) {
                button.onPress(); return true;
            }
        }
        return false;
    }
    private static void request(String operation, UUID id) {
        JsonObject input = new JsonObject();
        input.addProperty("id", id.toString());
        DailyNetwork.request(operation, input);
    }
    private static void screenshot(String name) throws Exception {
        Files.createDirectories(OUT);
        try (var image = Screenshot.takeScreenshot(mc().getMainRenderTarget())) {
            image.writeToFile(OUT.resolve(name));
        }
    }
    private static void prepare() {
        var p = player();
        var host = p.getServer();
        host.setDifficulty(Difficulty.PEACEFUL, true);
        host.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, host);
        host.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, host);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            p.serverLevel().setBlockAndUpdate(new BlockPos(x, 160, z), Blocks.STONE.defaultBlockState());
        }
        p.serverLevel().setBlockAndUpdate(BOARD, ContractsMod.BOARD.get().defaultBlockState());
        p.teleportTo(p.serverLevel(), .5, 161, 2.5, 180, 0);
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64));
        var materials = List.of(Items.OAK_LOG, Items.STONE, Items.COBBLESTONE, Items.DIRT, Items.SAND);
        for (int i = 0; i < materials.size(); i++) p.getInventory().setItem(i + 1, new ItemStack(materials.get(i), 2));
        p.inventoryMenu.broadcastChanges();
        ContractsData.get(host).ledger = new Ledger();
        ContractsData.get(host).setDirty();
        DailyData data = DailyData.get(host);
        data.ledger = new DailyLedger();
        data.ledger.initialized = true;
        var settings = data.ledger.settings;
        check(settings.emeraldFallback, "Forge defaults must use emeralds");
        settings.easyMin = settings.easyMax = settings.mediumMin = settings.mediumMax = settings.hardMin = settings.hardMax = 100;
        settings.dailyCap = 600;
        settings.allBonus = 100;
        settings.easyReputation = settings.mediumReputation = settings.hardReputation = 0;
        settings.validate();
        List<DailyRules.Template> templates = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            var difficulty = i < 2 ? DailyRules.Difficulty.EASY : i < 4 ? DailyRules.Difficulty.MEDIUM : DailyRules.Difficulty.HARD;
            String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(materials.get(i)).toString();
            templates.add(new DailyRules.Template("contracts:forge_qa_" + i, "Forge delivery " + i,
                    DailyRules.Category.DELIVERY, difficulty, item, 1, 100, 10,
                    java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), true, DailyRules.Validation.CONSUME_ITEMS));
        }
        var round = data.ledger.generate(p.getUUID(), System.currentTimeMillis(), templates, "EMERALD");
        questIds = round.quests.stream().map(q -> q.id).toList();
        data.persist(host);
        worldFolder = host.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        ContractsService.open(p, BOARD);
    }
    private static void complete(boolean success, String message) {
        done = true;
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "success", success, "message", message, "stage", stage, "checks", CHECKS,
                    "actualMinecraftClient", true, "actualIntegratedServer", true,
                    "actualForgeChannels", true, "controlledQuestTemplates", true)));
        } catch (Exception error) { error.printStackTrace(); }
        System.out.println(success ? "FORGE_INTEGRATION_PASSED" : "FORGE_INTEGRATION_FAILED " + message);
        mc().stop();
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        long now = System.currentTimeMillis();
        try {
            if (failure != null) throw new IllegalStateException("Host check failed", failure);
            check(now - START < 600_000, "Forge integration timed out at stage " + stage);
            if (now < next || mc().getOverlay() != null) return;
            next = now + 300;
            switch (stage) {
                case 0 -> {
                    if (mc().screen instanceof TitleScreen) {
                        mc().options.pauseOnLostFocus = false;
                        mc().options.renderDistance().set(3);
                        mc().options.simulationDistance().set(5);
                        mc().options.guiScale().set(2);
                        mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                        CreateWorldScreen.openFresh(mc(), mc().screen); stage++;
                    } else click("gui.continue");
                }
                case 1 -> {
                    if (mc().screen instanceof CreateWorldScreen world) {
                        world.getUiState().setName("Contracts Forge integration QA");
                        world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                        world.getUiState().setAllowCheats(true);
                        check(click("selectWorld.create"), "Create world control missing"); stage++;
                    }
                }
                case 2 -> {
                    if (mc().player != null && mc().getSingleplayerServer() != null) {
                        playerId = mc().player.getUUID(); server(ForgeIntegrationQA::prepare); stage++;
                    }
                }
                case 3 -> {
                    if (!ready || !(mc().screen instanceof ContractsScreen)) return;
                    check(!(player() instanceof net.minecraftforge.common.util.FakePlayer), "Host must be a real player");
                    CHECKS.add("Forge server-to-client board snapshot opens the production screen");
                    ContractsNetwork.send("publish", "", "Forge network contract", "minecraft:oak_log", 1, 10, "EMERALD");
                    stage++; next = now + 1200;
                }
                case 4 -> {
                    server(() -> {
                        var data = ContractsData.get(player().getServer());
                        check(data.ledger.list().size() == 1 && player().getInventory().countItem(Items.EMERALD) == 54, "Publish did not escrow ten emeralds");
                        contractId = data.ledger.list().get(0).id();
                    }); stage++;
                }
                case 5 -> {
                    if (!ready) return;
                    ContractsNetwork.send("cancel", contractId.toString(), "", "", 0, 0, "EMERALD");
                    stage++; next = now + 1000;
                }
                case 6 -> {
                    ContractsNetwork.send("claim_money", "", "", "", 0, 0, "EMERALD");
                    stage++; next = now + 1000;
                }
                case 7 -> {
                    server(() -> {
                        check(player().getInventory().countItem(Items.EMERALD) == 64, "Cancel/refund changed the original balance");
                        CHECKS.add("Real C2S publish, cancel and claim conserve 64 inventory emeralds");
                    }); stage++;
                }
                case 8 -> {
                    if (!ready) return;
                    check(click("gui.contracts.daily"), "Daily quests button missing"); stage++;
                }
                case 9 -> {
                    if (!(mc().screen instanceof DailyScreen)) return;
                    screenshot("forge-daily-quests.png");
                    request("accept", questIds.get(quest)); stage++; next = now + 900;
                }
                case 10 -> { request("deliver", questIds.get(quest)); stage++; next = now + 1200; }
                case 11 -> {
                    int index = quest;
                    server(() -> {
                        var data = DailyData.get(player().getServer());
                        check(data.ledger.rewards.get(questIds.get(index)).paid, "Delivery reward was not durably paid");
                        check(data.journal(playerId) == null, "Finished reward retained a prepared journal");
                    }); stage++;
                }
                case 12 -> {
                    if (!ready) return;
                    if (++quest < 5) { stage = 9; return; }
                    // Replay the last delivery over the real channel: inventory and rewards must be unchanged.
                    request("deliver", questIds.get(4)); stage++; next = now + 1200;
                }
                case 13 -> {
                    server(() -> {
                        var p = player(); var data = DailyData.get(p.getServer());
                        check(p.getInventory().countItem(Items.EMERALD) == 70, "Five payments and one all-complete bonus must total six emeralds");
                        check(data.ledger.rewards.size() == 6 && data.ledger.rewards.values().stream().allMatch(r -> r.paid), "Finale receipt missing or duplicated");
                        for (var item : List.of(Items.OAK_LOG, Items.STONE, Items.COBBLESTONE, Items.DIRT, Items.SAND))
                            check(p.getInventory().countItem(item) == 1, "Delivery replay consumed a second item");
                        var proof = p.getPersistentData().getCompound(DailyRewardJournal.RECEIPT);
                        check(DailyRewardJournal.savedReceipt(p, proof), "Player file lacks the final receipt");
                        check(DailyRewardJournal.savedHostReceipt(p, proof), "Integrated level.dat lacks the final receipt");
                        CHECKS.add("Five real deliveries, six durable rewards, exact inventory debit and replay rejection");
                        CHECKS.add("Receipt persisted in both playerdata and integrated host level.dat");
                        DailyService.sendFinales(p);
                    }); stage++;
                }
                case 14 -> {
                    if (!ready) return;
                    mc().setScreen(null); stage++; next = now + 2400;
                }
                case 15 -> {
                    check(DailyCinematic.active() || DailyCinematic.pending() > 0, "Forge S2C cinematic was not received");
                    screenshot("forge-daily-celebration.png");
                    CHECKS.add("Real Forge S2C completion cinematic rendered on the HUD");
                    server(() -> player().getServer().saveEverything(true, false, true)); stage++;
                }
                case 16 -> {
                    if (!ready) return;
                    mc().level.disconnect(); mc().clearLevel(); mc().setScreen(new TitleScreen()); stage++;
                }
                case 17 -> {
                    if (mc().level != null || mc().getSingleplayerServer() != null) return;
                    mc().createWorldOpenFlows().loadLevel(new TitleScreen(), worldFolder); stage++;
                }
                case 18 -> {
                    if (mc().player == null || mc().getSingleplayerServer() == null) return;
                    server(() -> {
                        var p = player(); var data = DailyData.get(p.getServer());
                        check(p.getInventory().countItem(Items.EMERALD) == 70, "Reopen lost or duplicated emerald rewards");
                        check(data.ledger.rewards.size() == 6 && data.ledger.rewards.values().stream().allMatch(r -> r.paid), "Reopen changed receipts");
                        check(data.journal(playerId) == null, "Reopen left a journal pending");
                        CHECKS.add("Actual world close/reopen preserves all six paid receipts and 70 emeralds");
                    }); stage++;
                }
                case 19 -> { if (ready) complete(true, "Forge channels, standalone daily payments, dual host save and world reopen passed"); }
                default -> throw new IllegalStateException("Unknown stage");
            }
            System.out.println("FORGE_INTEGRATION_STAGE " + stage);
        } catch (Throwable error) { error.printStackTrace(); complete(false, error.toString()); }
    }
}
