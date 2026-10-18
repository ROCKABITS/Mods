package pt.realmcasino.visualtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.data.CasinoData;
import pt.realmcasino.service.CasinoService;
import pt.realmcasino.service.WalletJournal;

/** Actual-client demonstration and transport checks, excluded from every production JAR. */
@EventBusSubscriber(modid = "realmcasino", value = Dist.CLIENT)
public final class CasinoVisualQA {
    private static final Path OUT = Path.of(System.getProperty("casino.qaOutput", "qa-results/casino-client")).toAbsolutePath().normalize();
    private static final BlockPos[] MACHINES = {new BlockPos(0, 161, 0), new BlockPos(-3, 161, 0), new BlockPos(3, 161, 0)};
    private static final String[] GAMES = {"slots", "roulette", "blackjack"};
    private static final String[] BLOCKS = {"slot_machine", "roulette_table", "blackjack_table"};
    private static final long INITIAL = 2_000_000, DEPOSIT = 500_000;
    private static final List<String> CHECKS = new ArrayList<>(), CAPTURES = new ArrayList<>();
    private static final List<Map<String, Object>> REQUESTS = new ArrayList<>();
    private static final Map<String, Integer> MODEL_QUADS = new LinkedHashMap<>();
    private static final Map<String, Object> ROUNDS = new LinkedHashMap<>();
    private static final List<Map<String, Object>> HOST_WALLET_SAVES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final long STARTED = System.currentTimeMillis();
    private static volatile Throwable serverFailure;
    private static volatile boolean prepared, moved, opened, conservationChecked, walletFixture;
    private static int step, lastStep = -1, table, c2sCount, worldAnimations;
    private static long nextAction, stepStarted, preparedAt, lastVersion, expectedBalance;
    private static double cameraX, cameraY = 161, cameraZ;
    private static float cameraYaw, cameraPitch;
    private static boolean done, languageRequested, realS2c, realC2s;
    private static JsonObject committedRound;
    private static int[] movingWheelPixels;
    private static int animatedPixelChanges;

    private CasinoVisualQA() {}
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static ResourceLocation key(String name) { return ResourceLocation.fromNamespaceAndPath("realmcasino", name); }
    private static ServerPlayer player() {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer();
        check(server != null && mc.player != null, "Missing real integrated player/server");
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        check(player != null, "Actual integrated PlayerList is missing the local player"); return player;
    }
    private static void server(Runnable action) {
        var server = Minecraft.getInstance().getSingleplayerServer(); check(server != null, "Missing integrated server");
        server.execute(() -> { try { action.run(); } catch (Throwable failure) { serverFailure = failure; } });
    }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Missing production fixture field " + name, failure); }
    }
    private static JsonObject data() {
        Screen screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().equals("pt.realmcasino.client.CasinoScreen") ? (JsonObject) field(screen, "data") : null;
    }
    private static JsonObject round(JsonObject data) { return data != null && data.has("round") && data.get("round").isJsonObject() ? data.getAsJsonObject("round") : null; }
    private static boolean click(String key) {
        var screen = Minecraft.getInstance().screen; if (screen == null) return false;
        String translated = Component.translatable(key).getString();
        for (var child : List.copyOf(screen.children())) if (child instanceof AbstractButton button && button.active
                && (button.getMessage().getString().equals(translated) || button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key))) {
            if (!button.visible && data() != null) {
                int x = (int) field(screen, "px"), top = (int) field(screen, "bodyTop"), bottom = (int) field(screen, "bodyBottom");
                for (int attempt = 0; attempt < 50 && !button.visible; attempt++) screen.mouseScrolled(x + 30, (top + bottom) / 2.0, 0, button.getY() < top ? 1 : -1);
            }
            if (!button.visible) continue;
            button.onPress(); return true;
        }
        return false;
    }
    private static void input(String field, String value) { ((EditBox) field(Minecraft.getInstance().screen, field)).setValue(value); }
    private static void scrollTop() {
        Screen screen = Minecraft.getInstance().screen; if (data() == null) return;
        int x = (int) field(screen, "px"), top = (int) field(screen, "bodyTop"), bottom = (int) field(screen, "bodyBottom");
        screen.mouseScrolled(x + 30, (top + bottom) / 2.0, 0, 1_000);
    }
    private static void tab(String name) {
        Screen screen = Minecraft.getInstance().screen; check(data() != null, "No production casino menu");
        try {
            Field tab = screen.getClass().getDeclaredField("tab"); tab.setAccessible(true);
            @SuppressWarnings({"unchecked", "rawtypes"}) Object value = Enum.valueOf((Class) tab.getType(), name);
            tab.set(screen, value); Field scroll = screen.getClass().getDeclaredField("scroll"); scroll.setAccessible(true); scroll.setInt(screen, 0);
            screen.resize(Minecraft.getInstance(), screen.width, screen.height);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot select actual casino tab " + name, failure); }
    }
    private static void recordTransport() {
        Screen screen = Minecraft.getInstance().screen;
        try {
            Field field = screen.getClass().getDeclaredField("requestSender"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var transport = (BiConsumer<String, JsonObject>) field.get(screen);
            BiConsumer<String, JsonObject> recording = (operation, payload) -> {
                var request = new LinkedHashMap<String, Object>(); request.put("operation", operation); request.put("payload", payload.deepCopy()); REQUESTS.add(request);
                c2sCount++; transport.accept(operation, payload);
            };
            field.set(screen, recording);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot record real production C2S", failure); }
    }
    private static void request(String operation) {
        check(!REQUESTS.isEmpty(), "Native UI sent no C2S request"); var latest = REQUESTS.getLast();
        check(operation.equals(latest.get("operation")), "Expected native C2S " + operation + ", got " + latest.get("operation"));
        var json = (JsonObject) latest.get("payload");
        check(json.has("nonce") && json.has("version"), "Financial C2S omitted its nonce/version");
        UUID.fromString(json.get("nonce").getAsString());
        check(!json.has("balance") && !json.has("bank") && !json.has("results") && !json.has("owner") && !json.has("shoe"), "Native request contains client-authoritative finance/outcome/authority");
    }
    private static void quiet() {
        try {
            var state = Class.forName("pt.cronicasrp.client.ClientState"); var pending = state.getDeclaredField("pending"); pending.setAccessible(true); pending.setInt(null, 0);
            var reset = Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(null);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Actual companion character creation fixture failed", failure); }
        // Keep optional tutorial/toast APIs reflective: these are presentation-only QA controls.
        try {
            var mc = Minecraft.getInstance(); Object tutorial = mc.getClass().getMethod("getTutorial").invoke(mc);
            @SuppressWarnings({"unchecked", "rawtypes"}) Object none = Enum.valueOf((Class) Class.forName("net.minecraft.client.tutorial.TutorialSteps"), "NONE");
            tutorial.getClass().getMethod("setStep", none.getClass()).invoke(tutorial, none);
        } catch (ReflectiveOperationException ignored) { }
        try { Object toasts = Minecraft.getInstance().getClass().getMethod("getToasts").invoke(Minecraft.getInstance()); toasts.getClass().getMethod("clear").invoke(toasts); }
        catch (ReflectiveOperationException ignored) { }
    }
    private static void profile(ServerPlayer player) {
        check(ModList.get().isLoaded("cronicasrp"), "Integrated Casino visual QA requires the actual Chronicles mod");
        try {
            Class<?> type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object value = type.getConstructor(boolean.class, String.class, int.class, String.class, String.class, String.class, String.class, long.class, long.class, String.class)
                    .newInstance(true, "Aveline Vale", 28, "origins:human", "Casino demonstration character; fixture money only.", "Aveline Vale", "Comum", INITIAL, INITIAL, "");
            Object attachment = ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get(); Method setter = null;
            for (Method method : player.getClass().getMethods()) if (method.getName().equals("setData") && method.getParameterCount() == 2 && method.getParameterTypes()[0].isInstance(attachment)) { setter = method; break; }
            check(setter != null, "Missing real Chronicles attachment setter"); setter.invoke(player, attachment, value);
            player.getServer().getPlayerList().saveAll();
            check(ChroniclesBridge.available(player) && ChroniclesBridge.balance(player) == INITIAL, "Real Chronicles wallet fixture was not persisted");
            ChroniclesBridge.sync(player); walletFixture = true;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Real Chronicles profile fixture failed", failure); }
    }
    private static void place(ServerPlayer player, int index) {
        var level = player.serverLevel(); var block = BuiltInRegistries.BLOCK.get(key(BLOCKS[index]));
        check(block != Blocks.AIR, "Unregistered casino block " + BLOCKS[index]); var state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) state = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        check(level.setBlock(MACHINES[index], state, flags), "Could not place actual " + BLOCKS[index]);
        if (index == 0) check(level.setBlock(MACHINES[index].above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), flags), "Could not place actual slots upper half");
        level.updateNeighborsAt(MACHINES[index], block); CasinoService.placed(player, MACHINES[index], GAMES[index]);
        check(level.getBlockEntity(MACHINES[index]) != null, "Casino machine has no actual block entity");
    }
    private static void prepare() {
        server(() -> {
            var player = player(); var level = player.serverLevel(); var server = player.getServer();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server); level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server); level.setDayTime(6_000); level.setWeatherParameters(0, 60_000, false, false);
            for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
                var floor = Math.abs(x) < 6 && z > -6 && z < 4 ? Blocks.POLISHED_DEEPSLATE : Blocks.DARK_OAK_PLANKS;
                level.setBlock(new BlockPos(x, 160, z), floor.defaultBlockState(), 2);
                for (int y = 161; y <= 168; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                if (Math.abs(x) == 11 || Math.abs(z) == 11) for (int y = 161; y <= 167; y++) {
                    var wall = y == 161 ? Blocks.POLISHED_BLACKSTONE_BRICKS : y == 167 || Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.SPRUCE_PLANKS;
                    level.setBlock(new BlockPos(x, y, z), wall.defaultBlockState(), 2);
                }
                if (Math.abs(x) <= 5 && z == 3) level.setBlock(new BlockPos(x, 161, z), Blocks.RED_CARPET.defaultBlockState(), 2);
            }
            for (int x : new int[]{-8, 8}) for (int z : new int[]{-8, 7}) {
                for (int y = 161; y <= 164; y++) level.setBlock(new BlockPos(x, y, z), Blocks.BOOKSHELF.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, 165, z), Blocks.LANTERN.defaultBlockState(), 2);
            }
            for (int x : new int[]{-5, 5}) for (int z : new int[]{-4, 4}) {
                level.setBlock(new BlockPos(x, 161, z), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, 162, z), Blocks.LANTERN.defaultBlockState(), 2);
            }
            // A furnished rear wall fills the photograph instead of an unfinished edge of the world.
            for (int x = -6; x <= 6; x++) for (int y = 161; y <= 165; y++) level.setBlock(new BlockPos(x, y, 5), (x % 3 == 0 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_PLANKS).defaultBlockState(), 2);
            for (int x : new int[]{-4, 0, 4}) level.setBlock(new BlockPos(x, 164, 4), Blocks.LANTERN.defaultBlockState(), 2);
            profile(player); for (int i = 0; i < 3; i++) place(player, i);
            preparedAt = System.currentTimeMillis(); prepared = true;
        });
    }
    private static void move(double x, double z, float yaw, float pitch) {
        cameraX = x; cameraY = 162.2; cameraZ = z; cameraYaw = yaw; cameraPitch = pitch; moved = false;
        server(() -> { var player = player(); player.getAbilities().flying = true; player.onUpdateAbilities(); player.connection.teleport(x, 162.2, z, yaw, pitch); moved = true; });
    }
    private static void detailCamera(int game) {
        var mc = Minecraft.getInstance(); mc.options.fov().set(game == 0 ? 50 : 42);
        cameraX = MACHINES[game].getX() + 2; cameraY = 161; cameraZ = MACHINES[game].getZ() - 2.6;
        cameraYaw = 30; cameraPitch = game == 0 ? 11 : 22; moved = false;
        server(() -> { var player = player(); player.connection.teleport(cameraX, cameraY, cameraZ, cameraYaw, cameraPitch); moved = true; });
    }
    private static boolean cameraReady() {
        var mc = Minecraft.getInstance(); if (!moved || mc.player == null || mc.level == null) return false;
        if (mc.player.distanceToSqr(cameraX, cameraY, cameraZ) > .04) return false;
        for (int i = 0; i < 3; i++) if (!mc.level.getBlockState(MACHINES[i]).is(BuiltInRegistries.BLOCK.get(key(BLOCKS[i])))) return false;
        mc.player.setYRot(cameraYaw); mc.player.yRotO = cameraYaw; mc.player.setXRot(cameraPitch); mc.player.xRotO = cameraPitch;
        mc.player.setYHeadRot(cameraYaw); mc.player.setYBodyRot(cameraYaw); mc.setCameraEntity(mc.player); return true;
    }
    private static void open(int game) {
        table = game; opened = false;
        server(() -> {
            var player = player(); var pos = MACHINES[game]; player.connection.teleport(pos.getX() + .5, 161, pos.getZ() - 3, 0, 12);
            CasinoService.open(player, pos); opened = true;
        });
    }
    private static boolean ready(int game) {
        var data = data(); if (!opened || data == null || !data.has("game") || !GAMES[game].equals(data.get("game").getAsString())) return false;
        check(data.get("economyAvailable").getAsBoolean() && data.get("owner").getAsBoolean(), "Genuine server snapshot has no wallet/owner access");
        if (data.has("error")) check(data.get("error").getAsString().isEmpty(), "Authoritative casino error: " + data.get("error").getAsString());
        realS2c = true; return true;
    }
    private static void conservation() {
        conservationChecked = false;
        server(() -> {
            var player = player(); var ledger = CasinoData.get(player.getServer()).ledger(); long value = ChroniclesBridge.balance(player);
            for (var machine : ledger.machines().values()) value = Math.addExact(value, machine.bank());
            for (long pending : ledger.credits().values()) value = Math.addExact(value, pending);
            check(value == INITIAL, "Actual integrated wallet + all casino banks + pending credit is not conserved: " + value);
            check(CasinoData.get(player.getServer()).journal() == null, "Completed operation left a pending durable wallet journal"); conservationChecked = true;
        });
    }
    /** Verify both actual restart sources; IntegratedPlayerList prefers level.dat for its owner. */
    private static void hostWalletSave(String operation) {
        server(() -> {
            if (HOST_WALLET_SAVES.stream().anyMatch(saved -> operation.equals(saved.get("operation")))) return;
            var player = player(); var server = player.getServer();
            check(server.isSingleplayerOwner(player.getGameProfile()), "Visual wallet proof is not the actual integrated host");
            try {
                var playerPath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
                var levelPath = server.getWorldPath(LevelResource.ROOT).resolve("level.dat");
                var playerTag = NbtIo.readCompressed(playerPath, NbtAccounter.create(64L * 1024 * 1024));
                var hostTag = NbtIo.readCompressed(levelPath, NbtAccounter.create(64L * 1024 * 1024)).getCompound("Data").getCompound("Player");
                long expected = ChroniclesBridge.balance(player);
                CompoundTag playerReceipt = persistedWallet(playerTag, player.getUUID(), expected);
                CompoundTag hostReceipt = persistedWallet(hostTag, player.getUUID(), expected);
                check(playerReceipt.equals(hostReceipt), "Player file and level.dat contain different casino wallet receipts after " + operation);
                // Load the same host tag selected by vanilla PlayerList.load into a fresh server-side
                // player. This does not reconnect a client or replace the live demonstration player.
                var restored = new ServerPlayer(server, player.serverLevel(), player.getGameProfile(), ClientInformation.createDefault());
                restored.load(hostTag.copy());
                check(ChroniclesBridge.available(restored) && ChroniclesBridge.balance(restored) == expected,
                        "Loading the actual level.dat host restart tag restored an old Chronicles wallet after " + operation);
                check(restored.getPersistentData().getCompound(WalletJournal.RECEIPT_KEY).equals(hostReceipt),
                        "Loading the actual host restart tag lost its wallet receipt after " + operation);
                check(CasinoData.get(server).journal() == null, "Dual-save proof ran before its ledger commit completed");
                var proof = new LinkedHashMap<String, Object>(); proof.put("operation", operation); proof.put("balance", expected);
                proof.put("receipt", hostReceipt.getUUID("id").toString()); proof.put("player_file", true);
                proof.put("level_dat_host", true); proof.put("loaded_host_restart_tag", true); HOST_WALLET_SAVES.add(proof);
            } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot read actual integrated host wallet restart files", failure); }
        });
    }
    private static boolean hostWalletSavesComplete() {
        var operations = HOST_WALLET_SAVES.stream().map(saved -> saved.get("operation")).collect(java.util.stream.Collectors.toSet());
        return operations.containsAll(List.of("slots_deposit", "pending_claim", "slots_bet", "roulette_bet", "blackjack_bet"));
    }
    private static CompoundTag persistedWallet(CompoundTag tag, UUID player, long balance) {
        check(tag.hasUUID("UUID") && tag.getUUID("UUID").equals(player), "Saved host wallet has the wrong player UUID");
        var profile = tag.getCompound("neoforge:attachments").getCompound("cronicasrp:profile");
        check(profile.getBoolean("created") && profile.getInt("moneyVersion") == 1 && profile.getLong("balance") == balance,
                "Saved host restart profile has an old or missing Chronicles wallet");
        for (String key : List.of("ForgeData", "NeoForgeData")) {
            var persistent = tag.getCompound(key);
            if (persistent.get(WalletJournal.RECEIPT_KEY) instanceof CompoundTag receipt) {
                check(receipt.getAllKeys().equals(java.util.Set.of("id", "player", "before", "after"))
                                && receipt.hasUUID("id") && receipt.hasUUID("player") && receipt.getUUID("player").equals(player)
                                && receipt.getLong("after") == balance,
                        "Saved host restart profile has an invalid casino wallet receipt");
                return receipt.copy();
            }
        }
        throw new IllegalStateException("Saved host restart profile is missing its casino receipt");
    }
    private static void validateModels() {
        var mc = Minecraft.getInstance();
        for (String name : BLOCKS) {
            var block = BuiltInRegistries.BLOCK.get(key(name)); int states = 0;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var model = mc.getBlockRenderer().getBlockModel(state); var quads = new ArrayList<BakedQuad>();
                quads.addAll(model.getQuads(state, null, RandomSource.create(0)));
                for (Direction side : Direction.values()) quads.addAll(model.getQuads(state, side, RandomSource.create(0)));
                check(!quads.isEmpty(), "Casino model has no geometry: " + state);
                check(!model.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing model particle: " + state);
                for (var quad : quads) check(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing casino texture: " + state);
                MODEL_QUADS.put(state.toString(), quads.size()); states++;
            }
            check(states >= 4, "Casino model has fewer than four native facing states");
            var item = BuiltInRegistries.ITEM.get(key(name)); check(item != Items.AIR, "Casino furniture item missing: " + name);
            check(!mc.getItemRenderer().getModel(new ItemStack(item), null, null, 0).getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Casino item texture missing: " + name);
        }
        CHECKS.add("All registered baked states and all three furniture item models have native geometry and resolved textures.");
    }
    private static boolean worldResult(int game, JsonObject round) {
        var mc = Minecraft.getInstance(); var be = mc.level.getBlockEntity(MACHINES[game]); if (be == null) return false;
        try {
            var id = be.getClass().getMethod("animationId").invoke(be); if (id == null || !id.toString().equals(round.get("id").getAsString())) return false;
            check(mc.getBlockEntityRenderDispatcher().getRenderer(be) != null, "Actual casino block entity has no client animation renderer");
            check(be.getClass().getMethod("game").invoke(be).equals(GAMES[game]), "Native BE packet contains wrong game");
            var result = (int[]) be.getClass().getMethod("result").invoke(be);
            if (game < 2) {
                int[] expected = new int[round.getAsJsonArray("results").size()]; for (int i = 0; i < expected.length; i++) expected[i] = round.getAsJsonArray("results").get(i).getAsInt();
                check(Arrays.equals(result, expected), "Native BE committed result differs from authoritative S2C result");
                check(((Long) be.getClass().getMethod("payout").invoke(be)) == round.get("payout").getAsLong(), "Native BE committed payout differs from authoritative S2C");
            }
            check((long) be.getClass().getMethod("startedAt").invoke(be) > 0, "Native animation has no server-owned start time");
            worldAnimations++; return true;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Actual casino animation API is unavailable", failure); }
    }
    private static void bounds(Screen screen, int width, int height) {
        int count = 0;
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            count++; check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= width && widget.getY() + widget.getHeight() <= height,
                    "Visible casino widget exceeds " + width + "x" + height + ": " + widget.getMessage().getString());
        }
        check(count > 0, "No native casino widgets checked");
    }
    private static void compact() {
        var mc = Minecraft.getInstance(); Screen screen = mc.screen;
        for (String tab : List.of("PLAY", "RULES", "OWNER")) {
            tab(tab); screen.resize(mc, 320, 240); bounds(screen, 320, 240);
            for (int i = 0; i < 9; i++) { screen.mouseScrolled(110, 135, 0, -1); bounds(screen, 320, 240); }
            screen.mouseScrolled(110, 135, 0, 1_000); bounds(screen, 320, 240);
        }
        screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()); tab("PLAY");
        CHECKS.add("Native " + GAMES[table] + " Play, Rules and Owner tabs fit 320x240; visible controls remain within bounds before and after scrolling.");
    }
    private static void stagePhoto(long now) {
        GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), 1, 1);
        quiet(); nextAction = now + 700;
    }
    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance(); Files.createDirectories(OUT);
        check(mc.options.guiScale().get() == 2, "Gallery captures require GUI scale 2");
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            check(image.getWidth() == 1600 && image.getHeight() == 900, "Gallery framebuffer must be 1600x900"); image.writeToFile(OUT.resolve(name));
        }
        CAPTURES.add(name); System.out.println("CASINO_CLIENT_CAPTURE " + name);
    }
    private static int[] wheelPixels() {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            int[] pixels = new int[400 * 300];
            for (int y = 0; y < 300; y++) for (int x = 0; x < 400; x++) pixels[y * 400 + x] = image.getPixelRGBA(600 + x, 210 + y);
            return pixels;
        }
    }
    private static void complete(boolean success, String message) {
        done = true;
        try {
            Files.createDirectories(OUT); var report = new LinkedHashMap<String, Object>();
            report.put("success", success); report.put("message", message); report.put("step", step); report.put("checks", CHECKS); report.put("screenshots", CAPTURES);
            report.put("actual_minecraft_client", true); report.put("actual_integrated_server", prepared); report.put("real_s2c_transport", realS2c);
            report.put("real_c2s_transport", realC2s); report.put("real_chronicles_wallet", walletFixture && conservationChecked);
            report.put("lan_host_wallet_dual_save", hostWalletSavesComplete());
            report.put("actual_host_wallet_saves", HOST_WALLET_SAVES);
            report.put("real_world_models", !MODEL_QUADS.isEmpty()); report.put("real_world_animation", worldAnimations >= 3);
            report.put("server_result_block_entity_updates", worldAnimations); report.put("capture_resolution", "1600x900"); report.put("capture_gui_scale", 2);
            report.put("world_animation_changing_pixels", animatedPixelChanges);
            report.put("demonstration_world_and_money", true); report.put("synthetic_financial_snapshots", false); report.put("server_rng_overridden", false);
            report.put("actual_c2s_requests", REQUESTS); report.put("model_quads", MODEL_QUADS); report.put("real_server_rounds", ROUNDS);
            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { failure.printStackTrace(); }
        System.out.println(success ? "CASINO_CLIENT_PASSED" : "CASINO_CLIENT_FAILED " + message); Minecraft.getInstance().stop();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done) return; var mc = Minecraft.getInstance(); long now = System.currentTimeMillis();
        try {
            if (serverFailure != null) throw new IllegalStateException("Integrated-server fixture failed", serverFailure);
            check(now - STARTED < 480_000, "Casino actual-client QA timed out"); if (now < nextAction || mc.getOverlay() != null) return; nextAction = now + 250;
            if (lastStep != step) { lastStep = step; stepStarted = now; } else check(now - stepStarted < 100_000, "Casino actual-client QA stalled at " + step);
            switch (step) {
                case 0 -> {
                    if (mc.screen instanceof TitleScreen) {
                        if (!languageRequested) { languageRequested = true; if (!"en_us".equals(mc.options.languageCode)) { mc.getLanguageManager().setSelected("en_us"); mc.options.languageCode = "en_us"; mc.reloadResourcePacks(); return; } }
                        mc.getWindow().setWindowed(1600, 900); mc.options.guiScale().set(2); mc.options.renderDistance().set(5); mc.options.simulationDistance().set(5); mc.options.fov().set(56);
                        mc.resizeDisplay(); CreateWorldScreen.openFresh(mc, mc.screen); step++;
                    } else if (mc.screen != null && mc.screen.getClass().getSimpleName().contains("Accessibility")) click("gui.continue");
                }
                case 1 -> { if (mc.screen instanceof CreateWorldScreen world) { world.getUiState().setName("Realm Casino demonstration QA"); world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE); world.getUiState().setAllowCommands(true); world.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null); check(click("selectWorld.create"), "Native create-world button missing"); step++; } }
                case 2 -> { if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) { prepare(); step++; } }
                case 3 -> { if (!prepared) return; quiet(); mc.setScreen(null); mc.gui.getChat().clearMessages(true); mc.options.hideGui = true; move(5.6, -7.2, 36, 12); step++; }
                case 4 -> { if (!cameraReady() || now - preparedAt < 12_000) return; validateModels(); stagePhoto(now); step++; }
                case 5 -> { check(cameraReady(), "World camera did not settle after synchronization"); capture("realm-casino-furniture.png"); CHECKS.add("All three real furniture blocks photographed together from a three-quarter camera angle, more than 12 seconds after scene creation, with a furnished wall and resolved chunks."); mc.options.hideGui = false; open(0); step++; }
                case 6 -> { if (!ready(0)) return; recordTransport(); check(data().get("balance").getAsLong() == INITIAL, "Physical S2C snapshot did not read real initial Chronicles wallet"); tab("OWNER"); input("amountInput", "5000.00"); check(click("gui.realmcasino.deposit"), "Native owner deposit button unavailable"); request("deposit"); step++; }
                case 7 -> { if (!ready(0) || data().get("bank").getAsLong() != DEPOSIT) return; check(data().get("balance").getAsLong() == INITIAL-DEPOSIT, "Real deposit failed to debit Chronicles wallet"); hostWalletSave("slots_deposit"); input("amountInput", "100.00"); check(click("gui.realmcasino.withdraw"), "Native owner withdrawal button unavailable"); request("withdraw"); step++; }
                case 8 -> { if (!ready(0) || data().get("pending").getAsLong() != 10_000) return; check(data().get("bank").getAsLong() == DEPOSIT-10_000, "Withdrawal failed to transfer available bank into pending credits"); check(click("gui.realmcasino.claim"), "Native pending-credit claim button unavailable"); request("claim"); step++; }
                case 9 -> { if (!ready(0) || data().get("pending").getAsLong() != 0 || data().get("balance").getAsLong() != INITIAL-DEPOSIT+10_000) return; realC2s = true; hostWalletSave("pending_claim"); input("nameInput", "The Velvet Crown"); input("minInput", "1.00"); input("maxInput", "10.00"); check(click("gui.realmcasino.configure"), "Native owner configuration button unavailable"); request("configure"); conservation(); step++; }
                case 10 -> { if (!ready(0) || !conservationChecked || !data().get("name").getAsString().equals("The Velvet Crown")) return; check(data().get("minBet").getAsLong() == 100 && data().get("maxBet").getAsLong() == 1000, "Native C2S owner limits were not committed in cents"); CHECKS.add("Actual native owner deposit, withdrawal, pending-credit claim and configuration travelled through C2S and authoritative S2C; real Chronicles balance, casino bank and pending credit conserve the initial fixture money."); compact(); tab("OWNER"); stagePhoto(now); step++; }
                case 11 -> { capture("realm-casino-owner.png"); tab("RULES"); stagePhoto(now); step++; }
                case 12 -> { capture("realm-casino-rules.png"); tab("PLAY"); input("stakeInput", "1.00"); stagePhoto(now); step++; }
                case 13 -> { capture("realm-casino-slots.png"); expectedBalance = data().get("balance").getAsLong()-100; lastVersion=data().get("version").getAsLong(); check(click("gui.realmcasino.spin"), "Native slots bet button unavailable"); request("bet"); step++; }
                case 14 -> { if (!ready(0) || round(data()) == null || data().get("version").getAsLong() == lastVersion) return; committedRound = round(data()).deepCopy(); check(data().get("balance").getAsLong() == expectedBalance, "Actual slots stake did not debit Chronicles"); hostWalletSave("slots_bet"); check(committedRound.get("stake").getAsLong() == 100 && committedRound.getAsJsonArray("results").size() == 3, "Actual slots result is invalid"); if (!worldResult(0, committedRound)) return; ROUNDS.put("slots", committedRound); scrollTop(); stagePhoto(now); step++; }
                case 15 -> { capture("realm-casino-slots-spinning.png"); nextAction=now+3_500; step++; }
                case 16 -> { capture("realm-casino-slots-result.png"); conservation(); open(1); step++; }
                case 17 -> { if (!ready(1) || !conservationChecked) return; recordTransport(); compact(); tab("OWNER"); input("amountInput", "5000.00"); check(click("gui.realmcasino.deposit"), "Roulette owner deposit unavailable"); request("deposit"); step++; }
                case 18 -> { if (!ready(1) || data().get("bank").getAsLong() != DEPOSIT) return; tab("PLAY"); input("stakeInput", "1.00"); expectedBalance=data().get("balance").getAsLong()-100; lastVersion=data().get("version").getAsLong(); check(click("gui.realmcasino.roulette_spin"), "Native roulette bet button unavailable"); request("bet"); step++; }
                case 19 -> { if (!ready(1) || round(data()) == null || data().get("version").getAsLong() == lastVersion) return; committedRound=round(data()).deepCopy(); check(data().get("balance").getAsLong() == expectedBalance, "Actual roulette stake did not debit Chronicles"); hostWalletSave("roulette_bet"); check(committedRound.getAsJsonArray("results").size() == 1, "Actual roulette S2C result missing"); if (!worldResult(1, committedRound)) return; ROUNDS.put("roulette", committedRound); mc.setScreen(null); mc.options.hideGui=true; cameraX=MACHINES[1].getX()+.5;cameraY=161;cameraZ=-3;cameraYaw=0;cameraPitch=16;moved=true;check(cameraReady(),"Actual roulette camera was not synchronized");stagePhoto(now);nextAction=now+350;step=190; }
                case 190 -> { check(cameraReady(),"Camera moved during actual roulette animation");check(now-committedRound.get("startedAt").getAsLong()<3_200,"Actual roulette animation finished before the first world capture");capture("realm-casino-roulette-world-spinning.png");movingWheelPixels=wheelPixels();nextAction=now+3_600;step=191; }
                case 191 -> { check(cameraReady(),"Camera moved between actual roulette animation frames");check(worldResult(1,committedRound),"Committed world result changed during animation");capture("realm-casino-roulette-world-settled.png");int[] settled=wheelPixels();for(int i=0;i<settled.length;i++)if(settled[i]!=movingWheelPixels[i])animatedPixelChanges++;check(animatedPixelChanges>24,"Actual roulette world surface did not change between moving and settled frames");CHECKS.add("The actual roulette block was photographed while its committed 3.2-second animation was running and after settlement, at an unchanged camera and unchanged BE round id; the rendered tabletop region changed pixels.");mc.options.hideGui=false;open(1);step=192; }
                case 192 -> { if(!ready(1))return;scrollTop();stagePhoto(now);step=20; }
                case 20 -> { stagePhoto(now); nextAction=now+3_500; step++; }
                case 21 -> { capture("realm-casino-roulette.png"); conservation(); open(2); step++; }
                case 22 -> { if (!ready(2) || !conservationChecked) return; recordTransport(); compact(); tab("OWNER"); input("amountInput", "5000.00"); check(click("gui.realmcasino.deposit"), "Blackjack owner deposit unavailable"); request("deposit"); step++; }
                case 23 -> { if (!ready(2) || data().get("bank").getAsLong() != DEPOSIT) return; tab("PLAY"); input("stakeInput", "1.00"); expectedBalance=data().get("balance").getAsLong()-100; check(click("gui.realmcasino.deal"), "Native blackjack deal button unavailable"); request("bet"); step++; }
                case 24 -> { if (!ready(2) || round(data()) == null) return; committedRound=round(data()).deepCopy(); check(data().get("balance").getAsLong() == expectedBalance, "Actual blackjack stake did not debit Chronicles"); hostWalletSave("blackjack_bet"); check(!committedRound.has("shoe"), "Blackjack snapshot leaked undealt cards"); if (!worldResult(2, committedRound)) return; ROUNDS.put("blackjack_dealt", committedRound); scrollTop(); stagePhoto(now); nextAction=now+1_500; step++; }
                case 25 -> { capture("realm-casino-blackjack.png"); if (round(data()).get("phase").getAsString().equals("active")) { check(click("gui.realmcasino.stand"), "Native stand unavailable during active hand"); request("stand"); } step++; }
                case 26 -> { if (!ready(2) || !round(data()).get("phase").getAsString().equals("settled")) return; ROUNDS.put("blackjack_settled", round(data()).deepCopy()); check(data().get("reserved").getAsLong()==0, "Settled real blackjack left money reserved"); conservation(); stagePhoto(now); nextAction=now+1_300; step++; }
                case 27 -> { if (!conservationChecked) return; capture("realm-casino-blackjack-result.png"); CHECKS.add("All three actual native games debit the genuine Chronicles wallet, return authoritative server results, update rendered world block entities through native BE packets and preserve wallet + bank + pending credit. Blackjack was stood through actual C2S when the deal remained active; no shoe was sent to the client."); mc.setScreen(null); mc.options.hideGui=true; move(5.6,-7.2,36,12); step++; }
                case 28 -> { if (!cameraReady()) return; stagePhoto(now); step++; }
                case 29 -> { capture("realm-casino-furniture-after-play.png"); detailCamera(0); step++; }
                case 30 -> { if (!cameraReady()) return; stagePhoto(now); step++; }
                case 31 -> { check(cameraReady(),"Slots detail camera did not settle"); capture("realm-casino-slots-detail.png"); detailCamera(1); step++; }
                case 32 -> { if (!cameraReady()) return; stagePhoto(now); step++; }
                case 33 -> { check(cameraReady(),"Roulette detail camera did not settle"); capture("realm-casino-roulette-detail.png"); detailCamera(2); step++; }
                case 34 -> { if (!cameraReady()) return; stagePhoto(now); step++; }
                case 35 -> { check(cameraReady(),"Blackjack detail camera did not settle"); capture("realm-casino-blackjack-detail.png"); check(c2sCount>=9 && worldAnimations>=3 && animatedPixelChanges>24 && realC2s && realS2c && hostWalletSavesComplete(), "Actual transport/animation coverage incomplete"); CHECKS.add("All three original furniture models were also photographed individually from synchronized three-quarter detail cameras."); CHECKS.add("After real deposit, claim and each game stake, both playerdata UUID.dat and level.dat Data.Player contain the exact Chronicles balance and identical casino receipt; loading each actual host restart tag into a fresh server player restores that balance. This is integrated-host persistence proof, not a remote authenticated multiplayer reconnect."); complete(true,"Actual integrated Minecraft world, all three custom furniture models and game menus, owner controls, real Chronicles finances, native C2S/S2C/BE updates and compact controls passed."); }
                default -> throw new IllegalStateException("Unexpected Casino client QA stage");
            }
            System.out.println("CASINO_CLIENT_STEP " + step + " screen=" + (mc.screen == null ? "world" : mc.screen.getClass().getSimpleName()));
        } catch (Throwable failure) { failure.printStackTrace(); complete(false,failure.toString()); }
    }
}
