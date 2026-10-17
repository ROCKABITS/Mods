package pt.realmtitles.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmtitles.api.TitlesApi;
import pt.realmtitles.block.BlockRegistry;
import pt.realmtitles.block.SealDeskBlock;
import pt.realmtitles.compat.ChroniclesBridge;
import pt.realmtitles.compat.CouncilBridge;
import pt.realmtitles.data.TitleData;
import pt.realmtitles.domain.TitleLedger;
import pt.realmtitles.network.TitlePackets;
import pt.realmtitles.service.TitleService;

/** Native blocks, server sessions, public SavedData, installed optional mods, and actual packets. */
@GameTestHolder("realmtitles")
@PrefixGameTestTemplate(false)
public final class TitleGameTests {
    private static final UUID TITLE_ONE = new UUID(71, 1);
    private static final UUID TITLE_TWO = new UUID(71, 2);
    private TitleGameTests() {}

    /** A real server connection captures the production payloads and private fallback messages. */
    private static final class Actor extends FakePlayer {
        private final List<Packet<?>> packets = new ArrayList<>();
        private final List<Component> chat = new ArrayList<>();
        private final boolean notifications;
        private Runnable onNotice;

        Actor(GameTestHelper helper, String name, boolean notifications) {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            this.notifications = notifications;
            setGameMode(GameType.SURVIVAL);
            connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                    this, CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public boolean isAcceptingMessages() { return true; }
                @Override public boolean hasChannel(ResourceLocation id) {
                    return id.getNamespace().equals("realmtitles")
                            || (Actor.this.notifications && id.getNamespace().equals("realmnotifications"));
                }
                @Override public void send(Packet<?> packet) {
                    packets.add(packet);
                    if (packet instanceof ClientboundCustomPayloadPacket payload
                            && payload.payload().type().id().getNamespace().equals("realmnotifications")) notice();
                }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
            };
            if (ChroniclesBridge.loaded()) profile(this, true, "Public " + name);
        }

        @Override public void displayClientMessage(Component message, boolean overlay) { recordChat(message); }
        @Override public void sendSystemMessage(Component message) { recordChat(message); }
        @Override public void sendSystemMessage(Component message, boolean overlay) { recordChat(message); }
        private void recordChat(Component message) { if (chat != null) { chat.add(message); notice(); } }
        private void notice() { if (onNotice != null) onNotice.run(); }
        void clear() { packets.clear(); chat.clear(); }
        long notices() { return notificationPackets() + chat.size(); }
        long notificationPackets() {
            return packets.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                    .map(ClientboundCustomPayloadPacket.class::cast)
                    .filter(packet -> packet.payload().type().id().getNamespace().equals("realmnotifications")).count();
        }
        JsonObject lastSnapshot() {
            return packets.reversed().stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                    .map(ClientboundCustomPayloadPacket.class::cast).map(ClientboundCustomPayloadPacket::payload)
                    .filter(TitlePackets.Snapshot.class::isInstance).map(TitlePackets.Snapshot.class::cast)
                    .map(packet -> JsonParser.parseString(packet.json()).getAsJsonObject()).findFirst()
                    .orElseThrow(() -> new AssertionError("No production Titles snapshot was sent"));
        }
    }

    /** Installs actual public RP data using the installed Chronicles record and exported attachment. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void profile(Actor actor, boolean created, String name) {
        try {
            var attachment = (AttachmentType) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments")
                    .getField("PROFILE").get(null)).get();
            Object value = Class.forName("pt.cronicasrp.domain.CharacterProfile")
                    .getConstructor(boolean.class, String.class, int.class, String.class, String.class,
                            String.class, String.class, long.class, long.class, String.class)
                    .newInstance(created, name, 31, "Humano", "PRIVATE BIOGRAPHY MUST NOT BE SENT", "Private signature",
                            "Private origin", 731L, 619L, "");
            actor.setData(attachment, value);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Incompatible actual Chronicles profile", failure); }
    }

    /** Mutates the installed Council's real mandate; no alternative authority implementation is bundled. */
    private static final class CouncilFixture implements AutoCloseable {
        final Object data;
        final Object original;
        final Class<?> stateType;
        final Field stateField;

        CouncilFixture(MinecraftServer server) {
            try {
                Class<?> dataType = Class.forName("pt.realmcouncil.CouncilData");
                stateType = Class.forName("pt.realmcouncil.domain.CouncilState");
                data = dataType.getMethod("get", MinecraftServer.class).invoke(null, server);
                stateField = dataType.getField("state");
                original = stateField.get(data);
                stateField.set(data, stateType.getConstructor().newInstance());
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        void mandate(UUID player, long begins, long ends, long sworn) {
            try {
                Object state = stateField.get(data);
                Class<?> type = Class.forName("pt.realmcouncil.domain.CouncilState$Mandate");
                Object mandate = type.getConstructor().newInstance();
                type.getField("electionId").set(mandate, UUID.randomUUID());
                type.getField("playerId").set(mandate, player);
                type.getField("name").set(mandate, "Native governor");
                type.getField("title").set(mandate, "Governor");
                type.getField("beginsAt").setLong(mandate, begins);
                type.getField("endsAt").setLong(mandate, ends);
                type.getField("claimedAt").setLong(mandate, sworn);
                stateType.getField("mandate").set(state, mandate);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        void unavailable(boolean unavailable) {
            try { stateField.set(data, unavailable ? null : stateType.getConstructor().newInstance()); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        @Override public void close() {
            try { stateField.set(data, original); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final TitleData data;
        final TitleLedger previous;
        final UUID previousSteward;
        final CouncilFixture council;
        final List<Actor> actors = new ArrayList<>();
        final List<ServerPlayer> online;
        final Map<UUID, ServerPlayer> byId;
        final BlockPos desk;

        @SuppressWarnings("unchecked")
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            data = TitleData.get(helper.getLevel().getServer());
            previous = data.ledger();
            previousSteward = data.steward();
            clearSteward(data);
            data.replace(new TitleLedger().createDefinition(TITLE_ONE, "Keeper of the Seal", "Public service to the realm")
                    .createDefinition(TITLE_TWO, "Royal Envoy", "A public honorary title"));
            council = CouncilBridge.loaded() ? new CouncilFixture(helper.getLevel().getServer()) : null;
            desk = helper.absolutePos(new BlockPos(4, 1, 4));
            try {
                Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
                Field ids = PlayerList.class.getDeclaredField("playersByUUID"); ids.setAccessible(true);
                online = (List<ServerPlayer>) players.get(helper.getLevel().getServer().getPlayerList());
                byId = (Map<UUID, ServerPlayer>) ids.get(helper.getLevel().getServer().getPlayerList());
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        Actor actor(String name) { return actor(name, true); }
        Actor actor(String name, boolean notices) {
            Actor actor = new Actor(helper, name, notices);
            actors.add(actor); online.add(actor); byId.put(actor.getUUID(), actor);
            TitleService.observe(actor);
            return actor;
        }
        void manager(Actor actor) {
            data.steward(actor.getUUID());
            if (council != null) {
                long now = System.currentTimeMillis();
                council.mandate(actor.getUUID(), now - 1_000, now + 60_000, now - 500);
            }
        }
        void open(Actor actor) {
            if (!BlockRegistry.SEAL_DESK.get().validStructure(helper.getLevel(), desk)) place(helper, desk, Direction.NORTH);
            actor.setPos(desk.getX() + .5, desk.getY(), desk.getZ() - 1.5);
            helper.getLevel().getBlockState(desk.above()).useWithoutItem(helper.getLevel(), actor,
                    new BlockHitResult(Vec3.atCenterOf(desk.above()), Direction.NORTH, desk.above(), false));
            TitleService.requireStationSession(actor);
        }
        void create(Actor actor, String name) {
            JsonObject request = new JsonObject(); request.addProperty("name", name); request.addProperty("description", "Native public honour");
            TitleService.action(actor, "create", request.toString());
        }
        void grant(Actor actor, UUID owner, UUID title, int days) {
            JsonObject request = new JsonObject(); request.addProperty("selectedPlayer", owner.toString());
            request.addProperty("titleId", title.toString()); request.addProperty("days", days);
            TitleService.action(actor, "grant", request.toString());
        }
        void revoke(Actor actor, UUID id) { TitleService.action(actor, "revoke", "{\"id\":\"" + id + "\"}"); }
        @Override public void close() {
            for (Actor actor : actors) {
                actor.onNotice = null;
                TitleService.logout(actor.getUUID());
                online.remove(actor); byId.remove(actor.getUUID(), actor);
                helper.getLevel().getServer().getPlayerList().getOps().remove(actor.getGameProfile());
            }
            if (council != null) council.close();
            clearSteward(data);
            data.replace(previous);
            try { Field field = TitleData.class.getDeclaredField("steward"); field.setAccessible(true); field.set(data, previousSteward); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
    }

    private static void clearSteward(TitleData data) {
        try { Field field = TitleData.class.getDeclaredField("steward"); field.setAccessible(true); field.set(data, null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static BlockPos place(GameTestHelper helper, BlockPos pos, Direction facing) {
        var level = helper.getLevel();
        level.removeBlock(pos, false); level.removeBlock(pos.above(), false);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        Actor builder = new Actor(helper, "TitleBuilder", false);
        builder.setPos(pos.getX() + 3.5, pos.getY(), pos.getZ() - 3.5);
        builder.setYRot(facing.getOpposite().toYRot());
        ItemStack stack = new ItemStack(BlockRegistry.SEAL_DESK_ITEM.get(), 2);
        builder.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        helper.assertTrue(stack.useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit)).consumesAction()
                        && stack.getCount() == 1, "The real desk item did not place one supported pair or consume exactly one item");
        return pos;
    }

    private static void rejected(GameTestHelper helper, Runnable operation) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "An invalid native operation was accepted");
    }

    private static void unchanged(GameTestHelper helper, Fixture fixture, Actor actor, Runnable operation, String error) {
        var before = fixture.data.ledger().snapshot();
        actor.clear(); operation.run();
        helper.assertTrue(before.equals(fixture.data.ledger().snapshot()), "A denied packet changed persisted title data");
        helper.assertTrue(actor.lastSnapshot().get("error").getAsString().equals("gui.realmtitles." + error),
                "A denied packet returned the wrong production error: " + actor.lastSnapshot());
    }

    private static List<ItemEntity> drops(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                entity -> entity.getItem().is(BlockRegistry.SEAL_DESK_ITEM.get()));
    }
    private static int dropCount(GameTestHelper helper, BlockPos pos) {
        return drops(helper, pos).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static void noPair(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(!helper.getLevel().getBlockState(pos).is(BlockRegistry.SEAL_DESK.get())
                        && !helper.getLevel().getBlockState(pos.above()).is(BlockRegistry.SEAL_DESK.get()), "A desk half survived without its matching pair");
    }

    /** Change only a production session's timestamps, retaining all physical and authority fields. */
    @SuppressWarnings("unchecked")
    private static void ageSession(Actor actor, String fieldName, long value) {
        try {
            Field field = TitleService.class.getDeclaredField("SESSIONS"); field.setAccessible(true);
            var sessions = (Map<UUID, Object>) field.get(null);
            Object old = sessions.get(actor.getUUID());
            RecordComponent[] components = old.getClass().getRecordComponents();
            Class<?>[] types = new Class<?>[components.length]; Object[] values = new Object[components.length];
            for (int index = 0; index < components.length; index++) {
                types[index] = components[index].getType();
                var accessor = components[index].getAccessor(); accessor.setAccessible(true);
                values[index] = components[index].getName().equals(fieldName) ? value : accessor.invoke(old);
            }
            var constructor = old.getClass().getDeclaredConstructor(types); constructor.setAccessible(true);
            sessions.put(actor.getUUID(), constructor.newInstance(values));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void realCompanionsMatchTheRequestedRuntime(GameTestHelper helper) {
        boolean integration = Boolean.getBoolean("titles.integration");
        for (String id : List.of("realmcouncil", "cronicasrp", "realmnotifications", "realmidentity"))
            helper.assertTrue(ModList.get().isLoaded(id) == integration, "Unexpected runtime mode for " + id);
        if (integration) for (String id : List.of("contracts", "kingdomtreasury", "realmmarkets", "realmrewards", "origins", "jupiter"))
            helper.assertTrue(ModList.get().isLoaded(id), "Actual full-stack integration dependency missing: " + id);
        System.out.println("TITLES_RUNTIME_MODE_PASSED integration=" + integration);
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void realDeskRecipeCreativeSearchAndBlockedPistonsAreAvailable(GameTestHelper helper) {
        var id = ResourceLocation.fromNamespaceAndPath("realmtitles", "royal_seal_desk");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(id) == BlockRegistry.SEAL_DESK.get()
                && BuiltInRegistries.ITEM.get(id) == BlockRegistry.SEAL_DESK_ITEM.get(), "Desk block or physical item is not registered");
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(id).isPresent(), "The actual desk crafting recipe is absent");
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("realmtitles", "realm_titles"));
        helper.assertTrue(tab != null, "Titles creative tab is missing");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
        helper.assertTrue(tab.getDisplayItems().stream().filter(stack -> stack.is(BlockRegistry.SEAL_DESK_ITEM.get())).count() == 1
                && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(BlockRegistry.SEAL_DESK_ITEM.get())), "Desk is missing or duplicated in creative/search");
        helper.assertTrue(BlockRegistry.SEAL_DESK.get().defaultBlockState().getPistonPushReaction() == PushReaction.BLOCK,
                "Pistons can split the physical desk pair");
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void actualItemPlacesFourFacingsWithBothBoundedCollisionShapes(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            place(helper, pos, facing);
            helper.assertTrue(BlockRegistry.SEAL_DESK.get().validStructure(helper.getLevel(), pos), "Placed desk pair has invalid support or halves");
            for (int half = 0; half < 2; half++) {
                BlockPos part = half == 0 ? pos : pos.above(); var state = helper.getLevel().getBlockState(part);
                helper.assertTrue(state.getValue(SealDeskBlock.FACING) == facing
                        && state.getValue(SealDeskBlock.HALF) == (half == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER), "Desk placement facing or half is wrong");
                var shape = state.getCollisionShape(helper.getLevel(), part); var bounds = shape.bounds();
                helper.assertTrue(!shape.isEmpty() && bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0
                        && bounds.maxX <= 1 && bounds.maxY <= 1 && bounds.maxZ <= 1, "A half has no collision or extends into a neighboring block");
            }
            helper.assertTrue(SealDeskBlock.master(helper.getLevel(), pos.above()).equals(pos), "Upper half did not resolve to the physical lower station");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void actualPlacementRejectsMissingSupportBlockedUpperAndBuildCeiling(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(4, 1, 4));
        Actor builder = new Actor(helper, "DeniedBuilder", false);
        builder.setPos(base.getX() + 3.5, base.getY(), base.getZ() - 3.5);
        for (String reason : List.of("floor", "upper", "ceiling")) {
            BlockPos pos = reason.equals("ceiling") ? new BlockPos(base.getX(), helper.getLevel().getMaxBuildHeight() - 1, base.getZ()) : base;
            helper.getLevel().removeBlock(pos, false); helper.getLevel().removeBlock(pos.above(), false);
            helper.getLevel().setBlockAndUpdate(pos.below(), reason.equals("floor") ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
            if (reason.equals("upper")) helper.getLevel().setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
            ItemStack stack = new ItemStack(BlockRegistry.SEAL_DESK_ITEM.get()); builder.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var result = stack.useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false)));
            helper.assertTrue(!result.consumesAction() && stack.getCount() == 1
                    && !helper.getLevel().getBlockState(pos).is(BlockRegistry.SEAL_DESK.get()), "Invalid " + reason + " placement consumed an item or left a desk half");
            if (reason.equals("upper")) helper.assertTrue(helper.getLevel().getBlockState(pos.above()).is(Blocks.STONE), "Failed placement overwrote the occupied upper block");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void survivalBreakingEitherRealHalfDropsExactlyOneDesk(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        for (boolean upper : List.of(false, true)) {
            drops(helper, pos).forEach(ItemEntity::discard); place(helper, pos, Direction.NORTH);
            Actor breaker = new Actor(helper, "SurvivalBreaker", false);
            breaker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
            helper.assertTrue(breaker.gameMode.destroyBlock(upper ? pos.above() : pos), "Survival could not break a real desk half");
            noPair(helper, pos);
            helper.assertTrue(dropCount(helper, pos) == 1, "Survival destruction dropped zero or multiple desk items: upper=" + upper);
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void creativeBreakingEitherRealHalfDropsNoDesk(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        for (boolean upper : List.of(false, true)) {
            drops(helper, pos).forEach(ItemEntity::discard); place(helper, pos, Direction.SOUTH);
            Actor breaker = new Actor(helper, "CreativeBreaker", false); breaker.setGameMode(GameType.CREATIVE);
            helper.assertTrue(breaker.gameMode.destroyBlock(upper ? pos.above() : pos), "Creative could not break a real desk half");
            noPair(helper, pos);
            helper.assertTrue(dropCount(helper, pos) == 0, "Creative half destruction generated a physical desk item");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void removingRealFloorRemovesBothHalvesAndDropsOneItem(GameTestHelper helper) {
        BlockPos pos = place(helper, helper.absolutePos(new BlockPos(4, 1, 4)), Direction.WEST);
        helper.getLevel().destroyBlock(pos.below(), false);
        helper.runAfterDelay(3, () -> { noPair(helper, pos); helper.assertTrue(dropCount(helper, pos) == 1,
                "Support loss duplicated or lost the single desk item"); helper.succeed(); });
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void actualExplosionLeavesNoOrphansAndNeverDuplicatesDeskLoot(GameTestHelper helper) {
        BlockPos pos = place(helper, helper.absolutePos(new BlockPos(4, 1, 4)), Direction.EAST);
        helper.getLevel().explode(null, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 5.0F, Level.ExplosionInteraction.BLOCK);
        helper.runAfterDelay(3, () -> {
            boolean lower = helper.getLevel().getBlockState(pos).is(BlockRegistry.SEAL_DESK.get());
            boolean upper = helper.getLevel().getBlockState(pos.above()).is(BlockRegistry.SEAL_DESK.get());
            helper.assertTrue(lower == upper, "An actual explosion left one orphan desk half");
            helper.assertTrue(!lower || BlockRegistry.SEAL_DESK.get().validStructure(helper.getLevel(), pos), "Explosion left an unsupported or mismatched pair");
            helper.assertTrue(dropCount(helper, pos) <= 1, "An explosion duplicated the desk item"); helper.succeed();
        });
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void actualUpperHalfOpensPublicReadOnlyRegistryAndDeniedWritesStayUnchanged(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor governor = fixture.actor("GovernorQA"); Actor visitor = fixture.actor("VisitorQA");
            fixture.manager(governor); fixture.open(visitor);
            var view = TitleService.snapshot(visitor);
            helper.assertTrue(!view.get("canManage").getAsBoolean() && view.getAsJsonArray("definitions").size() == 2,
                    "Public physical interaction did not open a read-only registry");
            unchanged(helper, fixture, visitor, () -> fixture.create(visitor, "Unauthorized honour"), "permission");
            fixture.open(governor); helper.assertTrue(TitleService.snapshot(governor).get("canManage").getAsBoolean(), "Current manager cannot govern from the desk");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void remoteAndMalformedPacketsNeverCreateTitlesEvenForAnOperator(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor actor = fixture.actor("OperatorQA"); fixture.manager(actor);
            actor.server.getPlayerList().getOps().add(new ServerOpListEntry(actor.getGameProfile(), 2, false));
            helper.assertTrue(actor.hasPermissions(2), "Native fixture is not an actual operator");
            unchanged(helper, fixture, actor, () -> fixture.create(actor, "Remote honour"), "station_required");
            rejected(helper, () -> TitleService.requireStationSession(actor));
            fixture.open(actor);
            for (String json : List.of("{\"name\":\"A\",\"name\":\"B\",\"description\":\"\"}",
                    "{\"name\":{},\"description\":\"\"}", "{\"name\":\"A\",\"description\":\"\",\"unknown\":1}",
                    "{\"name\":\"A\",\"description\":\"\"} trailing", "x".repeat(2049)))
                unchanged(helper, fixture, actor, () -> TitleService.action(actor, "create", json), "invalid_request");
            unchanged(helper, fixture, actor, () -> TitleService.action(actor, "grant", "{\"selectedPlayer\":\"1-1-1-1-1\",\"titleId\":\"" + TITLE_ONE + "\",\"days\":0}"), "invalid_request");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void physicalSessionRechecksRangeBothHalvesFacingAndDimension(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor actor = fixture.actor("PhysicalQA"); fixture.manager(actor); fixture.open(actor);
            actor.setPos(fixture.desk.getX() + 20, fixture.desk.getY(), fixture.desk.getZ());
            unchanged(helper, fixture, actor, () -> fixture.create(actor, "Too far"), "station_required");
            fixture.open(actor);
            var upper = helper.getLevel().getBlockState(fixture.desk.above());
            helper.getLevel().setBlock(fixture.desk.above(), upper.setValue(SealDeskBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            unchanged(helper, fixture, actor, () -> fixture.create(actor, "Mismatched halves"), "station_required");
            fixture.open(actor); helper.getLevel().removeBlock(fixture.desk.above(), false);
            unchanged(helper, fixture, actor, () -> fixture.create(actor, "Missing upper"), "station_required");
            fixture.open(actor);
            var other = actor.server.getLevel(Level.NETHER); helper.assertTrue(other != null, "Native server has no second dimension");
            actor.setServerLevel(other);
            unchanged(helper, fixture, actor, () -> fixture.create(actor, "Wrong dimension"), "station_required");
            actor.setServerLevel(helper.getLevel());
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void sessionsExpireByBothWallClockAndServerTicksAndRejectClockRollback(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor actor = fixture.actor("SessionClockQA"); fixture.manager(actor);
            fixture.open(actor); ageSession(actor, "openedAt", System.currentTimeMillis() - TitleService.SESSION_MILLIS);
            unchanged(helper, fixture, actor, () -> fixture.create(actor, "Expired wall clock"), "station_required");
            fixture.open(actor); ageSession(actor, "openedTick", actor.server.overworld().getGameTime() - TitleService.SESSION_TICKS);
            unchanged(helper, fixture, actor, () -> fixture.create(actor, "Expired tick clock"), "station_required");
            fixture.open(actor); ageSession(actor, "openedAt", System.currentTimeMillis() + 60_000);
            unchanged(helper, fixture, actor, () -> fixture.create(actor, "Rolled back clock"), "station_required");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void liveGovernorRequiresCurrentSwornMandateAndNeverUsesOperatorOrStewardFallback(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor first = fixture.actor("FirstGovernor"); Actor next = fixture.actor("NextGovernor"); Actor operator = fixture.actor("CouncilOpQA");
            operator.server.getPlayerList().getOps().add(new ServerOpListEntry(operator.getGameProfile(), 2, false));
            fixture.manager(first); fixture.open(first); fixture.open(operator);
            unchanged(helper, fixture, operator, () -> fixture.create(operator, "Operator bypass"), "permission");
            fixture.create(first, "First mandate honour");
            helper.assertTrue(fixture.data.ledger().definitions().size() == 3, "The actual current manager could not create a title");
            if (fixture.council != null) {
                long now = System.currentTimeMillis();
                for (long[] times : List.of(new long[]{now - 1_000, now + 60_000, 0},
                        new long[]{now + 60_000, now + 120_000, now - 500},
                        new long[]{now - 60_000, now, now - 500}, new long[]{now - 1_000, now + 60_000, now + 500})) {
                    fixture.council.mandate(first.getUUID(), times[0], times[1], times[2]);
                    unchanged(helper, fixture, first, () -> fixture.create(first, "Invalid mandate honour"), "permission");
                }
                fixture.council.unavailable(true);
                unchanged(helper, fixture, first, () -> fixture.create(first, "Unavailable council fallback"), "governor_unavailable");
                fixture.council.unavailable(false);
                fixture.council.mandate(next.getUUID(), now - 1_000, now + 60_000, now - 500);
                rejected(helper, () -> TitleService.appointSteward(operator));
            } else fixture.data.steward(next.getUUID());
            unchanged(helper, fixture, first, () -> fixture.create(first, "Stale former governor"), "permission");
            fixture.open(next); fixture.create(next, "New mandate honour");
            helper.assertTrue(fixture.data.ledger().definitions().size() == 4, "Authority change lost catalog or blocked the new manager");
            System.out.println("TITLES_COUNCIL_AUTHORITY_PASSED integration=" + (fixture.council != null));
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void multipleOfflineHonoursPersistWithoutReadingOrWritingOfflineProfileFiles(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor actor = fixture.actor("OfflineGovernor"); fixture.manager(actor); fixture.open(actor);
            UUID offline = UUID.randomUUID();
            var file = actor.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(offline + ".dat");
            helper.assertTrue(Files.notExists(file), "Offline fixture unexpectedly has a profile file");
            fixture.data.replace(fixture.data.ledger().observeCitizen(offline, "Known Offline Citizen", true));
            fixture.grant(actor, offline, TITLE_ONE, 0); fixture.grant(actor, offline, TITLE_TWO, 365);
            var active = JsonParser.parseString(TitlesApi.activeTitlesJson(actor.server, offline)).getAsJsonArray();
            helper.assertTrue(active.size() == 2 && active.get(0).getAsJsonObject().get("expiresAt").getAsLong() == 0
                    && active.get(1).getAsJsonObject().get("expiresAt").getAsLong() > System.currentTimeMillis(), "Offline permanent and temporary public titles were not both granted");
            var saved = fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            TitleData restored = TitleData.load(saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored.ledger().snapshot().equals(fixture.data.ledger().snapshot())
                    && restored.steward().equals(actor.getUUID()) && Files.notExists(file), "Native persistence lost titles, changed authority, or wrote an offline profile");
            String json = active.toString();
            helper.assertTrue(!json.contains("PRIVATE") && !json.contains("balance") && !json.contains("signature")
                    && !json.contains("biography"), "The public API exposed private character fields");
            System.out.println("TITLES_NATIVE_PASSED");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void renewingThroughRealActionRejectsStaleRevokeAndDeletingActiveDefinitions(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor governor = fixture.actor("RenewGovernor"); Actor owner = fixture.actor("HonouredCitizen");
            fixture.manager(governor); fixture.open(governor);
            fixture.grant(governor, owner.getUUID(), TITLE_ONE, 1);
            var old = fixture.data.ledger().activeGrants(owner.getUUID(), System.currentTimeMillis()).getFirst();
            fixture.grant(governor, owner.getUUID(), TITLE_ONE, 0);
            var renewed = fixture.data.ledger().activeGrants(owner.getUUID(), System.currentTimeMillis()).getFirst();
            helper.assertTrue(!renewed.id().equals(old.id()) && renewed.expiresAt() == 0, "Renewal reused its old grant identity or duration");
            unchanged(helper, fixture, governor, () -> fixture.revoke(governor, old.id()), "stale_grant");
            unchanged(helper, fixture, governor, () -> TitleService.action(governor, "delete", "{\"id\":\"" + TITLE_ONE + "\"}"), "definition_in_use");
            fixture.revoke(governor, renewed.id());
            TitleService.action(governor, "delete", "{\"id\":\"" + TITLE_ONE + "\"}");
            helper.assertTrue(!fixture.data.ledger().definitions().containsKey(TITLE_ONE)
                    && fixture.data.ledger().activeGrants(owner.getUUID(), System.currentTimeMillis()).isEmpty(), "Explicit revoke/delete did not commit");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void nativePublicApiFiltersExactExpiryAndRequiresServerThread(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor owner = fixture.actor("ExpiryReaderQA"); long now = System.currentTimeMillis();
            UUID temporary = UUID.randomUUID(); UUID permanent = UUID.randomUUID();
            fixture.data.replace(new TitleLedger(List.copyOf(fixture.data.ledger().definitions().values()),
                    List.of(new TitleLedger.Grant(temporary, owner.getUUID(), TITLE_ONE, now - 1_000, now),
                            new TitleLedger.Grant(permanent, owner.getUUID(), TITLE_TWO, now - 1_000, 0)),
                    List.copyOf(fixture.data.ledger().citizens().values())));
            helper.assertTrue(TitlesApi.activeTitles(fixture.data.ledger(), owner.getUUID(), now - 1).size() == 2
                    && TitlesApi.activeTitles(fixture.data.ledger(), owner.getUUID(), now).size() == 1
                    && JsonParser.parseString(TitlesApi.activeTitlesJson(owner.server, owner.getUUID())).getAsJsonArray().size() == 1,
                    "Offline-safe API did not exclude the exact expiry instant");
            var rejected = new java.util.concurrent.atomic.AtomicBoolean();
            Thread worker = new Thread(() -> { try { TitlesApi.activeTitlesJson(owner.server, owner.getUUID()); }
                catch (IllegalStateException expected) { rejected.set(true); } }, "titles-off-thread-native-qa");
            worker.start();
            try { worker.join(5_000); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            helper.assertTrue(!worker.isAlive() && rejected.get(), "Public API accepted an off-server-thread read");
            rejected(helper, () -> TitlesApi.activeTitlesJson(owner.server, null));
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void strictNativeNbtRejectsWrongTypesUnknownFieldsDuplicatesAndDanglingReferences(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor owner = fixture.actor("NbtCitizenQA"); fixture.manager(owner);
            fixture.data.replace(fixture.data.ledger().grant(owner.getUUID(), TITLE_ONE, 0, System.currentTimeMillis(), UUID.randomUUID()));
            CompoundTag valid = fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            List<Consumer<CompoundTag>> corruptions = List.of(
                    tag -> tag.putByte("version", (byte) 1), tag -> tag.putInt("version", 2), tag -> tag.remove("definitions"),
                    tag -> tag.putString("citizens", "invalid"), tag -> tag.putString("extra", "not a schema field"),
                    tag -> tag.getList("definitions", Tag.TAG_COMPOUND).getCompound(0).putIntArray("id", new int[]{1, 2, 3}),
                    tag -> tag.getList("grants", Tag.TAG_COMPOUND).getCompound(0).putInt("grantedAt", 1),
                    tag -> tag.getList("grants", Tag.TAG_COMPOUND).getCompound(0).putUUID("titleId", UUID.randomUUID()),
                    tag -> tag.getList("grants", Tag.TAG_COMPOUND).getCompound(0).putUUID("owner", UUID.randomUUID()),
                    tag -> tag.getList("citizens", Tag.TAG_COMPOUND).getCompound(0).putByte("eligible", (byte) 2),
                    tag -> tag.getList("definitions", Tag.TAG_COMPOUND).add(tag.getList("definitions", Tag.TAG_COMPOUND).getCompound(0).copy()),
                    tag -> tag.getList("grants", Tag.TAG_COMPOUND).add(tag.getList("grants", Tag.TAG_COMPOUND).getCompound(0).copy()),
                    tag -> tag.getList("citizens", Tag.TAG_COMPOUND).add(tag.getList("citizens", Tag.TAG_COMPOUND).getCompound(0).copy()),
                    tag -> tag.putUUID("steward", UUID.randomUUID()),
                    tag -> { ListTag strings = new ListTag(); strings.add(StringTag.valueOf("invalid")); tag.put("definitions", strings); });
            for (var corrupt : corruptions) { CompoundTag broken = valid.copy(); corrupt.accept(broken);
                rejected(helper, () -> TitleData.load(broken, helper.getLevel().registryAccess())); }
            helper.assertTrue(fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess()).equals(valid), "A rejected load altered the live SavedData");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void nativeStorageAndActionsEnforceCatalogAndPerCitizenCapacity(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor governor = fixture.actor("CapacityGovernor"); fixture.manager(governor); fixture.open(governor);
            List<TitleLedger.Definition> definitions = new ArrayList<>(); List<TitleLedger.Grant> grants = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 1; i <= 256; i++) definitions.add(new TitleLedger.Definition(new UUID(72, i), "Capacity title " + i, ""));
            for (int i = 1; i <= 16; i++) grants.add(new TitleLedger.Grant(new UUID(73, i), governor.getUUID(), new UUID(72, i), now, 0));
            fixture.data.replace(new TitleLedger(definitions, grants, List.copyOf(fixture.data.ledger().citizens().values())));
            unchanged(helper, fixture, governor, () -> fixture.create(governor, "Over catalog capacity"), "definition_limit");
            unchanged(helper, fixture, governor, () -> fixture.grant(governor, governor.getUUID(), new UUID(72, 17), 0), "owner_grant_limit");
            CompoundTag atLimit = fixture.data.save(new CompoundTag(), helper.getLevel().registryAccess());
            helper.assertTrue(TitleData.load(atLimit, helper.getLevel().registryAccess()).ledger().grants().size() == 16, "A valid exact-capacity native save could not load");
            CompoundTag tooManyDefinitions = atLimit.copy(); CompoundTag extra = new CompoundTag();
            extra.putUUID("id", UUID.randomUUID()); extra.putString("name", "Definition 257"); extra.putString("description", "");
            tooManyDefinitions.getList("definitions", Tag.TAG_COMPOUND).add(extra);
            rejected(helper, () -> TitleData.load(tooManyDefinitions, helper.getLevel().registryAccess()));
            CompoundTag tooManyGrants = atLimit.copy(); CompoundTag added = tooManyGrants.getList("grants", Tag.TAG_COMPOUND).getCompound(0).copy();
            added.putUUID("id", UUID.randomUUID()); added.putUUID("titleId", new UUID(72, 17));
            tooManyGrants.getList("grants", Tag.TAG_COMPOUND).add(added);
            rejected(helper, () -> TitleData.load(tooManyGrants, helper.getLevel().registryAccess()));
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void publicSnapshotsArePagedAndExposeOnlyTheObservedPublicDirectory(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor reader = fixture.actor("PublicReaderQA"); fixture.open(reader);
            TitleLedger ledger = fixture.data.ledger();
            for (int i = 0; i < 21; i++) ledger = ledger.createDefinition(new UUID(74, i), "Paged title " + i, "")
                    .observeCitizen(new UUID(75, i), "Offline public " + i, true);
            fixture.data.replace(ledger);
            TitleService.action(reader, "list", "{\"dSearch\":\"Paged title\",\"pSearch\":\"Offline public\",\"dPage\":1,\"pPage\":2}");
            JsonObject snapshot = reader.lastSnapshot();
            helper.assertTrue(snapshot.getAsJsonArray("definitions").size() == 8 && snapshot.getAsJsonArray("citizens").size() == 5
                    && snapshot.get("definitionTotal").getAsInt() == 21 && snapshot.get("citizenTotal").getAsInt() == 21,
                    "Public registry did not enforce search/page bounds");
            helper.assertTrue(snapshot.getAsJsonArray("citizens").asList().stream().noneMatch(row -> row.getAsJsonObject().get("online").getAsBoolean()),
                    "Cached offline citizens were falsely presented as online");
            helper.assertTrue(!snapshot.toString().contains("PRIVATE") && !snapshot.toString().contains("signature")
                    && !snapshot.toString().contains("balance"), "Public directory snapshot leaked private RP fields");
            if (ChroniclesBridge.loaded()) {
                profile(reader, false, "Uncreated private profile"); TitleService.observe(reader);
                helper.assertTrue(!fixture.data.ledger().citizens().get(reader.getUUID()).eligible(), "Uncreated Chronicles character became eligible");
                profile(reader, true, "Public renamed character"); TitleService.observe(reader);
                helper.assertTrue(fixture.data.ledger().citizens().get(reader.getUUID()).name().equals("Public renamed character"), "Observed RP name did not refresh");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void honoursNotificationsUseRealOptionalChannelOnlyAfterCommitAndNeverAfterDeniedMutation(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor governor = fixture.actor("NoticeGovernor"); Actor target = fixture.actor("NoticeCitizen", true);
            fixture.manager(governor); fixture.open(governor); governor.clear(); target.clear();
            target.onNotice = () -> helper.assertTrue(fixture.data.ledger().activeGrants(target.getUUID(), System.currentTimeMillis()).size() == 1,
                    "Grant notification was delivered before the actual registry commit");
            fixture.grant(governor, target.getUUID(), TITLE_ONE, 0);
            helper.assertTrue(target.notices() == 1 && governor.notices() == 0, "Grant notification was duplicated, lost, or sent to the governor");
            boolean loaded = ModList.get().isLoaded("realmnotifications");
            helper.assertTrue(target.notificationPackets() == (loaded ? 1 : 0) && target.chat.size() == (loaded ? 0 : 1),
                    "Supported optional channel/chat delivery was incorrect for the actual runtime");
            target.clear(); target.onNotice = null;
            var grant = fixture.data.ledger().activeGrants(target.getUUID(), System.currentTimeMillis()).getFirst();
            unchanged(helper, fixture, governor, () -> fixture.grant(governor, target.getUUID(), TITLE_TWO, 366), "invalid_request");
            helper.assertTrue(target.notices() == 0, "A rejected grant notified its target");
            target.onNotice = () -> helper.assertTrue(fixture.data.ledger().activeGrants(target.getUUID(), System.currentTimeMillis()).isEmpty(),
                    "Revoke notification preceded the committed revocation");
            fixture.revoke(governor, grant.id());
            helper.assertTrue(target.notices() == 1, "Committed revocation did not send exactly one target notification");
            target.onNotice = null; target.clear();
            unchanged(helper, fixture, governor, () -> fixture.revoke(governor, grant.id()), "stale_grant");
            helper.assertTrue(target.notices() == 0, "A stale revocation emitted a success notification");
            governor.clear(); fixture.grant(governor, governor.getUUID(), TITLE_ONE, 0);
            helper.assertTrue(governor.notices() == 0, "A governor's own grant duplicated success into a target alert");
            System.out.println("TITLES_NOTIFICATIONS_PASSED integration=" + loaded);
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void unsupportedNotificationClientGetsOneLocalizedPrivateChatFallback(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor governor = fixture.actor("FallbackGovernor"); Actor target = fixture.actor("FallbackCitizen", false);
            fixture.manager(governor); fixture.open(governor); target.clear();
            fixture.grant(governor, target.getUUID(), TITLE_ONE, 0);
            helper.assertTrue(target.chat.size() == 1 && target.notificationPackets() == 0,
                    "Client without optional notification support did not get exactly one chat fallback");
            String text = target.chat.getFirst().getString();
            helper.assertTrue(text.contains("Keeper of the Seal") && !text.contains("PRIVATE"), "Fallback lost public title wording or leaked private profile data");
        }
        helper.succeed();
    }

    @GameTest(template = "title_empty", timeoutTicks = 100)
    public static void expiryPurgeCommitsBeforeOneOnlineNotificationAndKeepsOfflineApiFiltered(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            Actor owner = fixture.actor("ExpiredCitizen", true); long now = System.currentTimeMillis(); UUID offline = UUID.randomUUID();
            var ledger = fixture.data.ledger().observeCitizen(offline, "Offline expired citizen", true);
            fixture.data.replace(new TitleLedger(List.copyOf(ledger.definitions().values()),
                    List.of(new TitleLedger.Grant(UUID.randomUUID(), owner.getUUID(), TITLE_ONE, now - 2_000, now - 1),
                            new TitleLedger.Grant(UUID.randomUUID(), offline, TITLE_TWO, now - 2_000, now - 1)),
                    List.copyOf(ledger.citizens().values())));
            owner.clear(); owner.onNotice = () -> helper.assertTrue(fixture.data.ledger().grants().isEmpty(), "Expiry notice preceded the committed purge");
            try { Field field = TitleService.class.getDeclaredField("LAST_PURGE"); field.setAccessible(true);
                ((Map<?, ?>) field.get(null)).remove(owner.server); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            TitleService.tick(owner.server);
            helper.assertTrue(fixture.data.ledger().grants().isEmpty() && owner.notices() == 1
                    && JsonParser.parseString(TitlesApi.activeTitlesJson(owner.server, offline)).getAsJsonArray().isEmpty(), "Expiry purge failed offline filtering or sent an incorrect online notice count");
            TitleService.tick(owner.server); helper.assertTrue(owner.notices() == 1, "Repeated ticks duplicated an expiry alert");
        }
        helper.succeed();
    }
}
