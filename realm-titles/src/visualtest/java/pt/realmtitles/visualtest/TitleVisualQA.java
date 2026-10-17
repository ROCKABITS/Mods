package pt.realmtitles.visualtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import pt.realmtitles.service.TitleService;

/** Opt-in actual-client QA. This class and its demonstration world never enter the release JAR. */
@EventBusSubscriber(modid = "realmtitles", value = Dist.CLIENT)
public final class TitleVisualQA {
    private static final Path OUT = Path.of(System.getProperty("titles.qaOutput", "qa-results/titles-client")).toAbsolutePath().normalize();
    private static final BlockPos DESK = new BlockPos(0, 161, 0);
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final String[] NAMES = {"Warden of the Green Roads", "Keeper of the Royal Archive", "Envoy of the Crown", "Friend of the Realm", "Master Cartographer", "Guardian of the Northern Gate"};
    private static final List<String> CHECKS = new ArrayList<>(), CAPTURES = new ArrayList<>();
    private static final List<Map<String, Object>> REQUESTS = new ArrayList<>();
    private static final Map<String, Integer> MODEL_QUADS = new LinkedHashMap<>();
    private static final long STARTED = System.currentTimeMillis();
    private static volatile Throwable serverFailure;
    private static volatile boolean prepared, moved, seeded, publicReady, stationInvalidated, identityReady, identityRevoked;
    private static volatile boolean councilFixture, chroniclesFixture, identityFixture;
    private static volatile String permanentGrantId, permanentTitleId, temporaryTitleId;
    private static volatile ItemStack identityDocument;
    private static volatile long identitySerial;
    private static int step, facing, transportStage, lastStep = -1;
    private static long nextAction, stepStarted;
    private static boolean done, languageRequested, realS2c;

    private TitleVisualQA() {}
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static ResourceLocation key(String mod, String name) { return ResourceLocation.fromNamespaceAndPath(mod, name); }
    private static ServerPlayer player() {
        var mc = Minecraft.getInstance();
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        check(player != null, "Local player is absent from the real integrated PlayerList"); return player;
    }
    private static void server(Runnable action) {
        var server = Minecraft.getInstance().getSingleplayerServer(); check(server != null, "No real integrated server");
        server.execute(() -> { try { action.run(); } catch (Throwable failure) { serverFailure = failure; } });
    }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Missing QA reflection field " + name, failure); }
    }
    private static JsonObject titleData() {
        var screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.getClass().getName().equals("pt.realmtitles.client.TitleScreen")) return null;
        return (JsonObject) field(screen, "data");
    }
    private static boolean click(String key) {
        var screen = Minecraft.getInstance().screen; if (screen == null) return false;
        String translated = Component.translatable(key).getString();
        for (var child : List.copyOf(screen.children())) if (child instanceof AbstractButton button && button.active && button.visible
                && (button.getMessage().getString().equals(translated)
                || button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key))) {
            button.onPress(); return true;
        }
        return false;
    }
    private static void quietCompanionCreation() {
        try {
            var state = Class.forName("pt.cronicasrp.client.ClientState"); var pending = state.getDeclaredField("pending"); pending.setAccessible(true); pending.setInt(null, 0);
            var reset = Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(null);
        } catch (ClassNotFoundException ignored) { check(!ModList.get().isLoaded("cronicasrp"), "Installed Chronicles is missing its creation fixture API"); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Companion creation fixture failed", failure); }
    }
    private static void profile(ServerPlayer player) {
        try {
            Class<?> type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object profile = type.getConstructor(boolean.class, String.class, int.class, String.class, String.class, String.class, String.class, long.class, long.class, String.class)
                    .newInstance(true, "Aveline Vale", 28, "origins:human", "A demonstration royal archivist.", "Aveline Vale", "Comum", 0L, 0L, "");
            Object supplier = Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null);
            Object attachment = ((Supplier<?>) supplier).get();
            Method setter = null;
            for (Method method : player.getClass().getMethods()) if (method.getName().equals("setData") && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isInstance(attachment)) { setter = method; break; }
            check(setter != null, "Chronicles attachment setter is unavailable"); setter.invoke(player, attachment, profile);
            chroniclesFixture = true;
        } catch (ClassNotFoundException ignored) { check(!ModList.get().isLoaded("cronicasrp"), "Installed Chronicles is missing its real profile API"); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Real Chronicles character fixture failed", failure); }
    }
    private static void authority(ServerPlayer player, boolean manage) {
        try {
            Class<?> dataType = Class.forName("pt.realmcouncil.CouncilData");
            Object data = dataType.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, player.getServer());
            Object state = dataType.getField("state").get(data);
            Class<?> mandateType = Class.forName("pt.realmcouncil.domain.CouncilState$Mandate"); Object mandate = mandateType.getConstructor().newInstance();
            long now = System.currentTimeMillis();
            mandateType.getField("electionId").set(mandate, UUID.fromString("1c91b550-d803-42cb-a3da-e41c740be437"));
            mandateType.getField("playerId").set(mandate, manage ? player.getUUID() : UUID.fromString("31871970-a4dd-4b47-b9ed-58a9a3277720"));
            mandateType.getField("name").set(mandate, manage ? "Aveline Vale" : "The next governor");
            mandateType.getField("title").set(mandate, "Royal governor — visual QA fixture");
            mandateType.getField("beginsAt").setLong(mandate, now - 10_000);
            mandateType.getField("endsAt").setLong(mandate, now + 86_400_000);
            mandateType.getField("claimedAt").setLong(mandate, now - 5_000);
            state.getClass().getField("mandate").set(state, mandate);
            ((net.minecraft.world.level.saveddata.SavedData) data).setDirty(); councilFixture = true;
        } catch (ClassNotFoundException absent) {
            check(!ModList.get().isLoaded("realmcouncil"), "Installed Council is unavailable; steward fallback is forbidden");
            // Only the absent-Council configuration may appoint a steward.
            if (manage) {
                player.getServer().getCommands().performPrefixedCommand(player.getServer().createCommandSourceStack(), "titles steward " + player.getGameProfile().getName());
            } else try {
                var registry = pt.realmtitles.data.TitleData.get(player.getServer());
                UUID successor = UUID.fromString("31871970-a4dd-4b47-b9ed-58a9a3277720");
                registry.replace(registry.ledger().observeCitizen(successor, "The next steward", true));
                registry.steward(successor);
            } catch (RuntimeException failure) { throw new IllegalStateException("Steward removal fixture failed", failure); }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Real sworn Council mandate fixture failed", failure); }
    }
    private static void placeDesk(ServerPlayer player, Direction direction) {
        var block = BuiltInRegistries.BLOCK.get(key("realmtitles", "royal_seal_desk"));
        check(block != Blocks.AIR, "Royal seal desk block is not registered");
        var lower = block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, direction).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        // A different facing is a replacement structure. Remove the previous
        // complete pair before placing either new half, just as actual item
        // placement does; rotating a lower half in-place tears its old pair down.
        var level = player.serverLevel();
        level.removeBlock(DESK, false); level.removeBlock(DESK.above(), false);
        int staged = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
        check(level.setBlock(DESK, lower, staged), "Could not place the lower demonstration desk");
        check(level.setBlock(DESK.above(), lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), staged), "Could not place the upper demonstration desk");
        level.updateNeighborsAt(DESK, block); level.updateNeighborsAt(DESK.above(), block);
        check(((pt.realmtitles.block.SealDeskBlock) block).validStructure(level, DESK), "The rotated demonstration desk is not a supported matching pair");
        level.sendBlockUpdated(DESK, lower, lower, 3);
    }
    private static float yaw(Direction facing) { return switch (facing) { case NORTH -> 0; case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> throw new IllegalStateException(); }; }
    private static double cameraX(Direction facing) { return .5 + facing.getStepX() * 4.5; }
    private static double cameraZ(Direction facing) { return .5 + facing.getStepZ() * 4.5; }
    private static void move(Direction direction) {
        moved = false;
        server(() -> { var player = player(); placeDesk(player, direction); player.connection.teleport(cameraX(direction), 161, cameraZ(direction), yaw(direction), 8); moved = true; });
    }
    private static boolean cameraReady(Direction direction) {
        var mc = Minecraft.getInstance(); if (!moved || mc.player == null || mc.level == null) return false;
        if (mc.player.distanceToSqr(cameraX(direction), 161, cameraZ(direction)) > .04) return false;
        var state = mc.level.getBlockState(DESK);
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || state.getValue(BlockStateProperties.HORIZONTAL_FACING) != direction) return false;
        // Apply after the vanilla teleport packet, then let a complete frame render before capture.
        mc.player.setYRot(yaw(direction)); mc.player.yRotO = yaw(direction);
        mc.player.setXRot(8); mc.player.xRotO = 8; mc.player.setYHeadRot(yaw(direction)); mc.player.setYBodyRot(yaw(direction));
        mc.setCameraEntity(mc.player);
        check(Math.abs(mc.player.getXRot() - 8) < .01, "Client camera pitch did not synchronize"); return true;
    }
    private static void prepare() {
        server(() -> {
            var player = player(); var level = player.serverLevel(); var server = player.getServer();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server); level.setDayTime(1_000); level.setWeatherParameters(0, 60_000, false, false);
            for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                level.setBlock(new BlockPos(x, 160, z), (Math.abs(x) <= 2 && Math.abs(z) <= 2 ? Blocks.POLISHED_DEEPSLATE : Blocks.SPRUCE_PLANKS).defaultBlockState(), 2);
                for (int y = 161; y <= 168; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                if (Math.abs(x) == 7 || Math.abs(z) == 7) for (int y = 161; y <= 166; y++) {
                    var wall = y == 161 ? Blocks.STONE_BRICKS : y == 166 || x % 4 == 0 || z % 4 == 0 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.DARK_OAK_PLANKS;
                    level.setBlock(new BlockPos(x, y, z), wall.defaultBlockState(), 2);
                }
            }
            for (int x : new int[]{-6, 6}) for (int z : new int[]{-6, 6}) {
                for (int y = 161; y <= 164; y++) level.setBlock(new BlockPos(x, y, z), Blocks.BOOKSHELF.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, 165, z), Blocks.LANTERN.defaultBlockState(), 2);
            }
            for (int x : new int[]{-4, 4}) for (int z : new int[]{-4, 4}) { level.setBlock(new BlockPos(x, 161, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 2); level.setBlock(new BlockPos(x, 162, z), Blocks.LANTERN.defaultBlockState(), 2); }
            profile(player); authority(player, true); TitleService.observe(player); prepared = true;
        });
    }
    private static JsonObject payload(String... fields) {
        var data = new JsonObject(); for (int i = 0; i < fields.length; i += 2) data.addProperty(fields[i], fields[i + 1]); return data;
    }
    private static void seed() {
        seeded = false;
        server(() -> {
            var player = player(); TitleService.openStation(player, DESK);
            for (int i = 0; i < NAMES.length; i++) TitleService.action(player, "create", payload("name", NAMES[i], "description", switch (i) {
                case 0 -> "Demonstration honour. For protecting travellers and keeping the forest roads open to every citizen.";
                case 1 -> "Demonstration honour. Custodian of the realm's histories, maps and public records.";
                case 2 -> "Demonstration honour. Entrusted to carry the crown's words with care and dignity.";
                default -> "A public honour of the realm. Demonstration title created through the real governing service.";
            }).toString());
            var definitions = TitleService.snapshot(player).getAsJsonArray("definitions");
            for (var element : definitions) {
                var definition = element.getAsJsonObject();
                if (definition.get("name").getAsString().equals(NAMES[0])) permanentTitleId = definition.get("id").getAsString();
                if (definition.get("name").getAsString().equals(NAMES[1])) temporaryTitleId = definition.get("id").getAsString();
            }
            check(permanentTitleId != null && temporaryTitleId != null, "Real service did not create the demonstration title definitions");
            var grant = payload("selectedPlayer", player.getUUID().toString(), "titleId", permanentTitleId); grant.addProperty("days", 0);
            TitleService.action(player, "grant", grant.toString()); grant.addProperty("titleId", temporaryTitleId); grant.addProperty("days", 30); TitleService.action(player, "grant", grant.toString());
            var snapshot = TitleService.snapshot(player); check(snapshot.get("canManage").getAsBoolean(), "Real authority fixture has no governing access");
            check(snapshot.getAsJsonArray("grants").size() == 2, "Real service did not commit both permanent and temporary titles");
            for (var element : snapshot.getAsJsonArray("grants")) if (element.getAsJsonObject().get("titleId").getAsString().equals(permanentTitleId)) permanentGrantId = element.getAsJsonObject().get("id").getAsString();
            TitleService.action(player, "list", "{}"); seeded = true;
        });
    }
    private static void tab(String name) {
        Screen screen = Minecraft.getInstance().screen; check(titleData() != null, "Production title menu is not open");
        try {
            Field tab = screen.getClass().getDeclaredField("tab"); tab.setAccessible(true);
            @SuppressWarnings({"unchecked", "rawtypes"}) Object value = Enum.valueOf((Class) tab.getType(), name);
            tab.set(screen, value); Field scroll = screen.getClass().getDeclaredField("scroll"); scroll.setAccessible(true); scroll.setInt(screen, 0);
            screen.resize(Minecraft.getInstance(), screen.width, screen.height);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot exercise production title tab " + name, failure); }
    }
    private static void bounds(Screen screen, int width, int height) {
        int visible = 0;
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            visible++; check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= width && widget.getY() + widget.getHeight() <= height,
                    "Visible widget exceeds " + width + "x" + height + ": " + widget.getMessage().getString());
        }
        check(visible > 0, "No native widgets were checked");
    }
    private static void compact() {
        var mc = Minecraft.getInstance(); Screen screen = mc.screen;
        boolean manage = titleData().get("canManage").getAsBoolean();
        for (String tab : manage ? List.of("REGISTRY", "HONOURS", "CATALOG") : List.of("REGISTRY")) {
            tab(tab); screen.resize(mc, 320, 240); bounds(screen, 320, 240);
            for (int scroll = 0; scroll < 8; scroll++) { screen.mouseScrolled(100, 130, 0, -1); bounds(screen, 320, 240); }
            screen.mouseScrolled(100, 130, 0, 1_000); bounds(screen, 320, 240);
        }
        screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        CHECKS.add((manage ? "All three native governing tabs" : "The native public registry") + " fit 320x240; visible widget bounds remain valid while scrolling.");
    }
    private static void publicControls() {
        check(!titleData().get("canManage").getAsBoolean(), "Public control check received a governor snapshot");
        for (var child : Minecraft.getInstance().screen.children()) if (child instanceof AbstractButton button
                && button.getMessage().getContents() instanceof TranslatableContents text) {
            String suffix = text.getKey().replace("gui.realmtitles.", "");
            check(!List.of("tab_honours", "tab_catalog", "new", "create", "save", "delete", "confirm_delete", "grant", "revoke", "permanent", "temporary").contains(suffix), "Public registry exposed management control " + suffix);
        }
    }
    private static void recordRealRequests(Screen screen) {
        try {
            Field sender = screen.getClass().getDeclaredField("requestSender"); sender.setAccessible(true);
            @SuppressWarnings("unchecked") var transport = (BiConsumer<String, JsonObject>) sender.get(screen);
            BiConsumer<String, JsonObject> recording = (operation, payload) -> {
                var entry = new LinkedHashMap<String, Object>(); entry.put("operation", operation); entry.put("payload", payload.deepCopy()); REQUESTS.add(entry);
                transport.accept(operation, payload);
            };
            sender.set(screen, recording);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not record real native C2S requests", failure); }
    }
    private static JsonObject requestPayload(String operation) {
        check(!REQUESTS.isEmpty(), "No production C2S request was sent"); var request = REQUESTS.getLast();
        check(operation.equals(request.get("operation")), "Expected actual C2S operation " + operation);
        JsonObject payload = (JsonObject) request.get("payload");
        check(payload.get("selectedPlayer").getAsString().equals(Minecraft.getInstance().player.getUUID().toString()), "Native request replaced selected player's UUID");
        check(!payload.has("canManage") && !payload.has("playerName") && !payload.has("profile") && !payload.has("managerName"), "Native request attempted to send authority or private profile data");
        return payload;
    }
    private static void scrollBottom() {
        Screen screen = Minecraft.getInstance().screen;
        int x = (int) field(screen, "px"), top = (int) field(screen, "bodyTop"), bottom = (int) field(screen, "bodyBottom");
        screen.mouseScrolled(x + 30, (top + bottom) / 2.0, 0, -1_000);
    }
    private static void validateModels() {
        var mc = Minecraft.getInstance(); var block = BuiltInRegistries.BLOCK.get(key("realmtitles", "royal_seal_desk"));
        int states = 0;
        for (var state : block.getStateDefinition().getPossibleStates()) {
            var model = mc.getBlockRenderer().getBlockModel(state); var quads = new ArrayList<BakedQuad>();
            quads.addAll(model.getQuads(state, null, RandomSource.create(0)));
            for (Direction side : Direction.values()) quads.addAll(model.getQuads(state, side, RandomSource.create(0)));
            check(!quads.isEmpty(), "Royal seal desk has no geometry: " + state);
            check(!model.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing desk particle: " + state);
            for (var quad : quads) check(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Missing desk texture: " + state);
            MODEL_QUADS.put(state.toString(), quads.size()); states++;
        }
        check(states == 8, "Expected lower/upper models in all four desk facings");
        var item = BuiltInRegistries.ITEM.get(key("realmtitles", "royal_seal_desk")); check(item != Items.AIR, "Desk item missing");
        var itemModel = mc.getItemRenderer().getModel(new ItemStack(item), null, null, 0);
        check(!itemModel.getParticleIcon().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Desk item texture missing");
        CHECKS.add("All eight baked desk states (two halves by four facings) have real geometry and resolved textures; registered item model resolves.");
    }
    private static boolean matches(JsonArray array, String name) {
        if (array == null) return false;
        for (var element : array) if (element.getAsJsonObject().get("name").getAsString().equals(name)) return true;
        return false;
    }
    private static void capture(String name) throws Exception {
        Files.createDirectories(OUT); GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), 1, 1);
        check(Minecraft.getInstance().options.guiScale().get() == 2, "Gallery capture must use GUI scale 2");
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            check(image.getWidth() == 1600 && image.getHeight() == 900, "Actual framebuffer must be 1600x900"); image.writeToFile(OUT.resolve(name));
        }
        CAPTURES.add(name); System.out.println("TITLES_CLIENT_CAPTURE " + name);
    }
    private static void openPublic() {
        server(() -> { var player = player(); authority(player, false); TitleService.openStation(player, DESK);
            check(!TitleService.snapshot(player).get("canManage").getAsBoolean(), "Authority removal did not produce a real public registry"); publicReady = true; });
    }
    private static void invalidateStation() {
        server(() -> { var player = player(); player.serverLevel().setBlock(DESK.above(), Blocks.AIR.defaultBlockState(), 3);
            TitleService.action(player, "list", "{}"); stationInvalidated = true; });
    }
    private static void openIdentity() {
        server(() -> {
            try {
                Class<?> service = Class.forName("pt.realmidentity.service.IdentityService"); var player = player();
                var desk = BuiltInRegistries.BLOCK.get(key("realmidentity", "writing_desk")); check(desk != Blocks.AIR, "Identity installed without its desk");
                BlockPos pos = DESK.offset(2, 0, 0); var state = desk.defaultBlockState();
                if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
                player.serverLevel().setBlock(pos, state, 3); player.getInventory().add(new ItemStack(Items.PAPER, 2)); player.getInventory().add(new ItemStack(Items.INK_SAC, 2));
                service.getMethod("openStation", ServerPlayer.class, BlockPos.class).invoke(null, player, pos);
                identityDocument = (ItemStack) service.getMethod("trustedIssue", ServerPlayer.class).invoke(null, player);
                player.setItemInHand(InteractionHand.MAIN_HAND, identityDocument.copy());
                @SuppressWarnings("unchecked") var view = (Map<String, Object>) service.getMethod("inspection", ServerPlayer.class, ItemStack.class).invoke(null, player, identityDocument);
                check("available".equals(view.get("titlesStatus")), "Real Identity did not load the Titles API");
                check(view.get("titles").toString().contains(NAMES[0]) && view.get("titles").toString().contains(NAMES[1]), "Real Identity inspection omitted active titles");
                identitySerial = ((Number) view.get("serial")).longValue();
                service.getMethod("inspectHeld", ServerPlayer.class, InteractionHand.class).invoke(null, player, InteractionHand.MAIN_HAND);
                identityFixture = true;
            } catch (ClassNotFoundException absent) { check(!ModList.get().isLoaded("realmidentity"), "Installed Identity is missing its real inspection API"); identityFixture = false; }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Real Identity inspection fixture failed", failure); }
            identityReady = true;
        });
    }
    private static JsonObject identityData() {
        Screen screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().equals("pt.realmidentity.client.IdentityScreen") ? (JsonObject) field(screen, "state") : null;
    }
    private static void revokeForIdentity() {
        server(() -> {
            try {
                var player = player(); authority(player, true); placeDesk(player, Direction.NORTH); TitleService.openStation(player, DESK);
                TitleService.action(player, "revoke", payload("id", permanentGrantId).toString());
                check(ItemStack.isSameItemSameComponents(identityDocument, player.getItemInHand(InteractionHand.MAIN_HAND)), "A title change rewrote the physical identity document");
                Class<?> service = Class.forName("pt.realmidentity.service.IdentityService");
                @SuppressWarnings("unchecked") var view = (Map<String, Object>) service.getMethod("inspection", ServerPlayer.class, ItemStack.class).invoke(null, player, player.getItemInHand(InteractionHand.MAIN_HAND));
                check(((Number) view.get("serial")).longValue() == identitySerial, "Title revocation changed identity serial");
                check(!view.get("titles").toString().contains(NAMES[0]) && view.get("titles").toString().contains(NAMES[1]), "Fresh Identity view did not reflect title revocation");
                service.getMethod("inspectHeld", ServerPlayer.class, InteractionHand.class).invoke(null, player, InteractionHand.MAIN_HAND); identityRevoked = true;
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Live Identity refresh fixture failed", failure); }
        });
    }
    private static void complete(boolean success, String message) {
        done = true;
        try {
            Files.createDirectories(OUT); var report = new LinkedHashMap<String, Object>();
            report.put("success", success); report.put("message", message); report.put("step", step); report.put("checks", CHECKS); report.put("screenshots", CAPTURES);
            report.put("actual_minecraft_client", true); report.put("actual_integrated_server", prepared); report.put("real_s2c_transport", realS2c);
            report.put("real_c2s_transport", transportStage >= 4); report.put("physical_station_closed_when_broken", stationInvalidated && step >= 15);
            report.put("world_desk_facings_captured", facing); report.put("capture_resolution", "1600x900"); report.put("capture_gui_scale", 2);
            report.put("demonstration_world_and_titles", true); report.put("synthetic_screen_snapshots", false);
            report.put("real_council_sworn_mandate_fixture", councilFixture); report.put("real_chronicles_created_profile_fixture", chroniclesFixture);
            report.put("real_identity_live_titles", identityFixture && identityRevoked); report.put("model_quads", MODEL_QUADS);
            report.put("actual_c2s_requests", REQUESTS);
            Files.writeString(OUT.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { failure.printStackTrace(); }
        System.out.println(success ? "TITLES_CLIENT_PASSED" : "TITLES_CLIENT_FAILED " + message); Minecraft.getInstance().stop();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (done) return; var mc = Minecraft.getInstance(); long now = System.currentTimeMillis();
        try {
            if (serverFailure != null) throw new IllegalStateException("Integrated-server fixture failed", serverFailure);
            check(now - STARTED < 480_000, "Titles visual QA timed out"); if (now < nextAction || mc.getOverlay() != null) return; nextAction = now + 400;
            if (lastStep != step) { lastStep = step; stepStarted = now; } else check(now - stepStarted < 100_000, "Titles visual QA stalled at " + step);
            switch (step) {
                case 0 -> {
                    if (mc.screen instanceof TitleScreen) {
                        if (!languageRequested) { languageRequested = true; if (!"en_us".equals(mc.options.languageCode)) { mc.getLanguageManager().setSelected("en_us"); mc.options.languageCode = "en_us"; mc.reloadResourcePacks(); return; } }
                        mc.getWindow().setWindowed(1600, 900); mc.options.guiScale().set(2); mc.options.renderDistance().set(4); mc.options.simulationDistance().set(4); mc.options.fov().set(60);
                        mc.resizeDisplay(); CreateWorldScreen.openFresh(mc, mc.screen); step++;
                    } else if (mc.screen != null && mc.screen.getClass().getSimpleName().contains("Accessibility")) click("gui.continue");
                }
                case 1 -> { if (mc.screen instanceof CreateWorldScreen world) { world.getUiState().setName("Realm Titles demonstration QA"); world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE); world.getUiState().setAllowCommands(true); world.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null); check(click("selectWorld.create"), "Native create-world button missing"); step++; } }
                case 2 -> { if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) { prepare(); step++; } }
                case 3 -> { if (!prepared) return; quietCompanionCreation(); mc.setScreen(null); mc.gui.getChat().clearMessages(true); mc.options.hideGui = true; validateModels(); move(FACINGS[facing]); step++; }
                case 4 -> { if (!cameraReady(FACINGS[facing])) return; nextAction = now + 1_600; step++; }
                case 5 -> {
                    check(cameraReady(FACINGS[facing]), "Camera moved before world capture"); capture("realm-titles-desk-" + FACINGS[facing].getName() + ".png"); facing++;
                    if (facing < FACINGS.length) { move(FACINGS[facing]); step = 4; }
                    else { CHECKS.add("Four actual-world captures use real two-high desks in all facings, with client camera yaw/pitch applied after server teleport synchronization."); mc.options.hideGui = false; move(Direction.NORTH); step = 6; }
                }
                case 6 -> { if (!cameraReady(Direction.NORTH)) return; quietCompanionCreation(); mc.setScreen(null); seed(); step++; }
                case 7 -> {
                    JsonObject data = titleData(); if (!seeded || data == null || !data.has("grants") || data.getAsJsonArray("grants").size() != 2) return;
                    check(data.get("canManage").getAsBoolean(), "Governing S2C snapshot denied the real governor");
                    check(data.get("selectedPlayer").getAsString().equals(mc.player.getUUID().toString()), "S2C selected a different UUID");
                    check(matches(data.getAsJsonArray("definitions"), NAMES[0]) && matches(data.getAsJsonArray("grants"), NAMES[1]), "Genuine S2C content did not reach the production menu");
                    realS2c = true; CHECKS.add("Real physical openStation and create/grant service calls reached the production menu over actual S2C, with a permanent and a 30-day title. No screen snapshots are injected.");
                    recordRealRequests(mc.screen); tab("CATALOG"); nextAction = now + 800; step++;
                }
                case 8 -> {
                    JsonObject data = titleData(); if (data == null) return;
                    if (transportStage == 0) {
                        ((EditBox) field(mc.screen, "descriptionInput")).setValue("Demonstration honour. Recorded with care in the royal register. Edited through the native menu.");
                        check(click("gui.realmtitles.save"), "Native edit button is unavailable");
                        JsonObject request = requestPayload("edit"); check(request.has("id") && UUID.fromString(request.get("id").getAsString()).toString().equals(request.get("id").getAsString()), "Native edit omitted a canonical title UUID");
                        transportStage++; return;
                    }
                    if (transportStage == 1) {
                        if (!"gui.realmtitles.saved".equals(data.get("message").getAsString())) return;
                        boolean edited = false; for (var row : data.getAsJsonArray("definitions")) if (row.getAsJsonObject().get("description").getAsString().contains("Edited through the native menu.")) edited = true;
                        check(edited, "Real native edit C2S did not update the authoritative definition");
                        ((EditBox) field(mc.screen, "definitionSearch")).setValue("Warden"); check(click("gui.realmtitles.find"), "Native title search button is unavailable");
                        check("Warden".equals(requestPayload("list").get("dSearch").getAsString()), "Native search lost its bounded filter"); transportStage++; return;
                    }
                    if (transportStage == 2) {
                        if (!"Warden".equals(data.get("dSearch").getAsString()) || data.get("definitionTotal").getAsInt() != 1) return;
                        check(matches(data.getAsJsonArray("definitions"), NAMES[0]), "Real S2C search returned the wrong title");
                        ((EditBox) field(mc.screen, "definitionSearch")).setValue(""); check(click("gui.realmtitles.find"), "Native search reset button is unavailable"); requestPayload("list"); transportStage++; return;
                    }
                    if (transportStage == 3) {
                        if (!data.get("dSearch").getAsString().isEmpty() || data.get("definitionTotal").getAsInt() != NAMES.length) return;
                        CHECKS.add("Native edit and filtered search travelled over actual C2S and authoritative S2C; payloads contain canonical selection/title UUIDs and bounded view fields, without claimed authority or private profiles.");
                        tab("REGISTRY"); transportStage++; nextAction = now + 1_000; return;
                    }
                    capture("realm-titles-governor-registry.png"); tab("CATALOG"); nextAction = now + 1_000; step++;
                }
                case 9 -> { capture("realm-titles-governor-catalog.png"); tab("HONOURS"); nextAction = now + 1_000; step++; }
                case 10 -> { capture("realm-titles-governor-honours.png"); compact(); openPublic(); step++; }
                case 11 -> { JsonObject data = titleData(); if (!publicReady || data == null || data.get("canManage").getAsBoolean()) return; compact(); tab("REGISTRY"); publicControls(); CHECKS.add("Removing the current authority produces a real server-owned public menu with no governing tabs or mutation controls; public and governor tabs both pass compact bounds."); nextAction = now + 1_000; step++; }
                case 12 -> { capture("realm-titles-public-registry.png"); scrollBottom(); nextAction = now + 1_000; step++; }
                case 13 -> { capture("realm-titles-public-record.png"); invalidateStation(); step++; }
                case 14 -> { if (!stationInvalidated || titleData() != null) return; CHECKS.add("Breaking the real physical station closes the production menu through the server's invalid-session S2C response."); openIdentity(); step++; }
                case 15 -> {
                    if (!identityReady) return;
                    if (!identityFixture) { CHECKS.add("Optional Identity runtime absent; live Identity integration capture is not applicable in the standalone run."); complete(true, "Actual world, four desk facings, real governing/public S2C menus and compact native controls passed."); return; }
                    JsonObject data = identityData(); if (data == null || !data.has("titles") || data.getAsJsonArray("titles").size() != 2) return;
                    check(matches(data.getAsJsonArray("titles"), NAMES[0]) && matches(data.getAsJsonArray("titles"), NAMES[1]), "Actual Identity S2C omitted the real active titles");
                    Screen screen = mc.screen; screen.resize(mc, 320, 240); bounds(screen, 320, 240); screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()); screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1_000); nextAction = now + 1_000; step++;
                }
                case 16 -> { capture("realm-titles-identity-current.png"); revokeForIdentity(); step++; }
                case 17 -> {
                    JsonObject data = identityData(); if (!identityRevoked || data == null || !data.has("titles") || data.getAsJsonArray("titles").size() != 1) return;
                    check(!matches(data.getAsJsonArray("titles"), NAMES[0]) && matches(data.getAsJsonArray("titles"), NAMES[1]), "Fresh real Identity S2C retained a revoked title");
                    check(data.get("serial").getAsLong() == identitySerial, "Live Identity S2C changed the original serial"); mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -1_000); nextAction = now + 1_000; step++;
                }
                case 18 -> { capture("realm-titles-identity-after-revocation.png"); CHECKS.add("Real Identity issuance and inspection S2C show active permanent/temporary titles; fresh inspection removes a revoked title without reissuing, rewriting the physical document or changing its serial. Native Identity controls fit 320x240."); complete(true, "Actual Minecraft four-facing desk, real public/governor S2C, compact native controls and live unchanged-document Identity titles passed."); }
                default -> throw new IllegalStateException("Unexpected visual QA stage");
            }
            System.out.println("TITLES_CLIENT_STEP " + step + " screen=" + (mc.screen == null ? "world" : mc.screen.getClass().getSimpleName()));
        } catch (Throwable failure) { failure.printStackTrace(); complete(false, failure.toString()); }
    }
}
