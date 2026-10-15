package pt.realmidentity.test;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmidentity.block.BlockRegistry;
import pt.realmidentity.compat.ChroniclesBridge;
import pt.realmidentity.data.IdentityData;
import pt.realmidentity.domain.Document;
import pt.realmidentity.domain.IdentityLedger;
import pt.realmidentity.domain.ProfileSnapshot;
import pt.realmidentity.service.IdentityService;

/** Real inventories, item interactions, server sessions, consent, and native SavedData. */
@GameTestHolder("realmidentity")
@PrefixGameTestTemplate(false)
public final class IdentityGameTests {
    private IdentityGameTests() {}

    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "IdentityQA"));
        player.setGameMode(GameType.SURVIVAL);
        var pos = helper.absolutePos(new BlockPos(3, 1, 3));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        if (ChroniclesBridge.loaded()) chroniclesProfile(player, true, "Personagem QA");
        return player;
    }

    /** Uses the installed mod's real exported attachment and record; no replacement mod is bundled. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object chroniclesProfile(FakePlayer player, boolean created, String name) {
        try {
            var attachment = (AttachmentType) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments")
                    .getField("PROFILE").get(null)).get();
            var type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object profile = type.getConstructor(boolean.class, String.class, int.class, String.class,
                            String.class, String.class, String.class, long.class, long.class, String.class)
                    .newInstance(created, name, 30, "Humano", "Descricao privada", "Assinatura QA", "Comum",
                            51L, 37L, "");
            player.setData(attachment, profile);
            return profile;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The installed Chronicles RP profile API is incompatible", exception);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object chroniclesProfile(FakePlayer player) {
        try {
            var attachment = (AttachmentType) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments")
                    .getField("PROFILE").get(null)).get();
            return player.getData(attachment);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The installed Chronicles RP profile API is incompatible", exception);
        }
    }

    /** Online lookup is real PlayerList state; this fixture changes no service authority. */
    private static final class OnlinePlayers implements AutoCloseable {
        private final PlayerList list;
        private final Map<UUID, ServerPlayer> byId;
        private final List<FakePlayer> players = new ArrayList<>();

        @SuppressWarnings("unchecked")
        private OnlinePlayers(GameTestHelper helper, FakePlayer... fixtures) {
            list = helper.getLevel().getServer().getPlayerList();
            try {
                Field field = PlayerList.class.getDeclaredField("playersByUUID");
                field.setAccessible(true);
                byId = (Map<UUID, ServerPlayer>) field.get(list);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot register native test players in PlayerList", exception);
            }
            for (FakePlayer player : fixtures) {
                list.getPlayers().add(player);
                byId.put(player.getUUID(), player);
                players.add(player);
            }
        }

        @Override public void close() {
            for (FakePlayer player : players) {
                list.getPlayers().remove(player);
                byId.remove(player.getUUID(), player);
                IdentityService.logout(player.getUUID());
            }
        }
    }

    private static IdentityData reset(GameTestHelper helper) {
        var data = IdentityData.get(helper.getLevel().getServer());
        data.replace(new IdentityLedger());
        return data;
    }

    private static void supplies(FakePlayer player, int paper, int ink) {
        player.getInventory().clearContent();
        if (paper > 0) player.getInventory().setItem(1, new ItemStack(Items.PAPER, paper));
        if (ink > 0) player.getInventory().setItem(2, new ItemStack(Items.INK_SAC, ink));
    }

    private static void rejected(GameTestHelper helper, Runnable operation) {
        boolean failed = false;
        try { operation.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { failed = true; }
        helper.assertTrue(failed, "An invalid operation was accepted");
    }

    private static BlockPos desk(GameTestHelper helper, FakePlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, BlockRegistry.WRITING_DESK.get().defaultBlockState());
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2.5);
        level.getBlockState(pos).useWithoutItem(level, player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        IdentityService.requireStationSession(player);
        return pos;
    }

    private static ItemStack issued(GameTestHelper helper, FakePlayer owner) {
        supplies(owner, 2, 2);
        desk(helper, owner);
        ItemStack item = IdentityService.trustedIssue(owner);
        helper.assertTrue(item.is(BlockRegistry.IDENTITY_DOCUMENT.get()), "Issue returned no physical document");
        return item;
    }

    private static void hold(FakePlayer player, ItemStack document) {
        // The held item is an actual inventory slot, never a client-supplied document description.
        player.setItemInHand(InteractionHand.MAIN_HAND, document.copy());
    }

    private static void nearby(FakePlayer presenter, FakePlayer recipient) {
        recipient.setPos(presenter.getX() + 2, presenter.getY(), presenter.getZ());
    }

    private static UUID offer(GameTestHelper helper, FakePlayer presenter, FakePlayer recipient) {
        nearby(presenter, recipient);
        IdentityService.offer(presenter, recipient, InteractionHand.MAIN_HAND);
        return IdentityService.pendingOfferToken(presenter.getUUID(), recipient.getUUID())
                .orElseThrow(() -> new AssertionError("A valid presentation created no consent offer"));
    }

    private static boolean valid(FakePlayer viewer, ItemStack item) {
        return Boolean.TRUE.equals(IdentityService.inspection(viewer, item).get("authentic"));
    }

    @SuppressWarnings("unchecked")
    private static void replaceOffer(IdentityService.PendingOffer replacement) {
        try {
            Field field = IdentityService.class.getDeclaredField("OFFERS");
            field.setAccessible(true);
            ((Map<UUID, IdentityService.PendingOffer>) field.get(null)).put(replacement.token(), replacement);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot arrange the server's pending offer deadline", exception);
        }
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void physicalDeskItemPlacesAndOpensAStation(GameTestHelper helper) {
        reset(helper);
        var owner = player(helper);
        var pos = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        owner.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2.5);
        var stack = new ItemStack(BlockRegistry.WRITING_DESK_ITEM.get(), 2);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        helper.assertTrue(stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                "The actual writing desk BlockItem could not be placed");
        helper.assertTrue(stack.getCount() == 1 && helper.getLevel().getBlockState(pos).is(BlockRegistry.WRITING_DESK.get()),
                "Placement did not create exactly one desk and consume one item");
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), owner,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
        IdentityService.requireStationSession(owner);
        var key = ResourceLocation.fromNamespaceAndPath("realmidentity", "writing_desk");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(key) == BlockRegistry.WRITING_DESK.get()
                        && helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
                "The registered physical desk or its survival recipe is missing");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void issueChargesOnePaperAndInkAndCreatesOneAuthenticatedPhysicalItem(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        supplies(owner, 3, 4);
        desk(helper, owner);
        var item = IdentityService.trustedIssue(owner);
        var document = data.ledger.current(owner.getUUID()).orElseThrow();
        helper.assertTrue(owner.getInventory().countItem(Items.PAPER) == 2
                        && owner.getInventory().countItem(Items.INK_SAC) == 3,
                "Issuing did not charge exactly one paper and one ink sac");
        helper.assertTrue(owner.getInventory().countItem(BlockRegistry.IDENTITY_DOCUMENT.get()) == 1
                        && item.getCount() == 1 && item.getMaxStackSize() == 1 && valid(owner, item),
                "Issuing did not deliver exactly one authentic, non-stackable document");
        helper.assertTrue(document.owner().equals(owner.getUUID()) && document.serial() == 1
                        && data.ledger.documents().size() == 1 && data.isDirty(),
                "The physical issue was not recorded once in persistent server data");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void onlyMainInventorySuppliesPayForAnIssue(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        desk(helper, owner);
        supplies(owner, 1, 0);
        owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.INK_SAC));
        rejected(helper, () -> IdentityService.trustedIssue(owner));
        helper.assertTrue(owner.getInventory().countItem(Items.PAPER) == 1
                        && owner.getOffhandItem().is(Items.INK_SAC)
                        && data.ledger.current(owner.getUUID()).isEmpty(),
                "Offhand ink was charged or a missing-main-inventory issue changed the registry");
        owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        owner.getInventory().setItem(2, new ItemStack(Items.GLOW_INK_SAC));
        rejected(helper, () -> IdentityService.trustedIssue(owner));
        helper.assertTrue(owner.getInventory().countItem(Items.PAPER) == 1
                        && owner.getInventory().countItem(Items.GLOW_INK_SAC) == 1,
                "An invalid alternative ink was consumed");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void fullInventoryFailureRetainsSuppliesAndThePreviouslyValidDocument(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        var originalItem = issued(helper, owner).copy();
        var original = data.ledger.current(owner.getUUID()).orElseThrow();
        for (int slot = 0; slot < 36; slot++) owner.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        owner.getInventory().setItem(1, new ItemStack(Items.PAPER, 2));
        owner.getInventory().setItem(2, new ItemStack(Items.INK_SAC, 2));
        var before = data.ledger.snapshot();
        rejected(helper, () -> IdentityService.trustedIssue(owner));
        helper.assertTrue(owner.getInventory().countItem(Items.PAPER) == 2
                        && owner.getInventory().countItem(Items.INK_SAC) == 2
                        && owner.getInventory().countItem(BlockRegistry.IDENTITY_DOCUMENT.get()) == 0
                        && data.ledger.snapshot().equals(before) && valid(owner, originalItem)
                        && data.ledger.lookup(original.id()).isPresent(),
                "A full inventory charged materials, delivered an item, or revoked the old valid document");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void reissueInvalidatesOldPhysicalCopiesWithoutDuplicatingRegistryOwners(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        var oldItem = issued(helper, owner).copy();
        Document old = data.ledger.current(owner.getUUID()).orElseThrow();
        var replacement = IdentityService.trustedIssue(owner);
        var current = data.ledger.current(owner.getUUID()).orElseThrow();
        helper.assertTrue(!current.id().equals(old.id()) && current.serial() == old.serial() + 1
                        && data.ledger.lookup(old.id()).isEmpty() && data.ledger.documents().size() == 1,
                "Reissue retained an old valid ID or created duplicate current records");
        helper.assertTrue(!valid(owner, oldItem) && valid(owner, replacement)
                        && owner.getInventory().countItem(BlockRegistry.IDENTITY_DOCUMENT.get()) == 2
                        && owner.getInventory().countItem(Items.PAPER) == 0
                        && owner.getInventory().countItem(Items.INK_SAC) == 0,
                "Reissue did not revoke the physical old copy and charge the second issue exactly once");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void sessionRequiresARealNearbyUnbrokenDesk(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        supplies(owner, 8, 8);
        rejected(helper, () -> IdentityService.trustedIssue(owner));
        var pos = desk(helper, owner);
        owner.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
        rejected(helper, () -> IdentityService.requireStationSession(owner));
        rejected(helper, () -> IdentityService.trustedIssue(owner));
        desk(helper, owner);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        rejected(helper, () -> IdentityService.requireStationSession(owner));
        rejected(helper, () -> IdentityService.trustedIssue(owner));
        helper.assertTrue(data.ledger.documents().isEmpty() && owner.getInventory().countItem(Items.PAPER) == 8
                        && owner.getInventory().countItem(Items.INK_SAC) == 8,
                "A missing, distant, or destroyed station still authorized an issue");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void deadSpectatorAndDifferentDimensionSessionsFailClosed(GameTestHelper helper) {
        reset(helper);
        var owner = player(helper);
        supplies(owner, 4, 4);
        desk(helper, owner);
        owner.setGameMode(GameType.SPECTATOR);
        rejected(helper, () -> IdentityService.requireStationSession(owner));
        rejected(helper, () -> IdentityService.trustedIssue(owner));
        owner.setGameMode(GameType.SURVIVAL);
        desk(helper, owner);
        owner.setHealth(0);
        rejected(helper, () -> IdentityService.requireStationSession(owner));
        rejected(helper, () -> IdentityService.trustedIssue(owner));
        var traveller = player(helper);
        supplies(traveller, 4, 4);
        desk(helper, traveller);
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "Native test server did not create the Nether");
        traveller.setServerLevel(nether);
        rejected(helper, () -> IdentityService.requireStationSession(traveller));
        rejected(helper, () -> IdentityService.trustedIssue(traveller));
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void editedNamesAndNbtNeverOverrideServerProfilesOrAuthenticateUnknownIds(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        var original = issued(helper, owner);
        var document = data.ledger.current(owner.getUUID()).orElseThrow();
        var edited = original.copy();
        edited.set(DataComponents.CUSTOM_NAME, Component.literal("Forged royal identity"));
        var nbt = edited.get(DataComponents.CUSTOM_DATA).copyTag();
        nbt.putString("name", "Forged RP name");
        nbt.putInt("age", 999);
        nbt.putString("race", "Forged race");
        nbt.getCompound("realmidentity").putString("signature", "Forged signature");
        edited.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        var view = IdentityService.inspection(owner, edited);
        helper.assertTrue(Boolean.TRUE.equals(view.get("authentic")) && document.profile().equals(view.get("profile")),
                "Editable display names or custom NBT replaced the authoritative server profile");
        var unknown = nbt.copy();
        unknown.getCompound("realmidentity").putUUID("documentId", UUID.randomUUID());
        edited.set(DataComponents.CUSTOM_DATA, CustomData.of(unknown));
        helper.assertTrue(!valid(owner, edited), "An unknown edited document ID authenticated through an owner search");
        helper.assertTrue(!valid(owner, IdentityService.documentStack(document, UUID.randomUUID())),
                "A document reference copied from a different world seal authenticated");
        var paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_DATA, original.get(DataComponents.CUSTOM_DATA));
        helper.assertTrue(!valid(owner, paper)
                        && !valid(owner, new ItemStack(BlockRegistry.IDENTITY_DOCUMENT.get())),
                "Copied NBT on another item or an unissued creative document authenticated");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void networkRequestsRejectRemoteAndClientSuppliedIdentityFields(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        supplies(owner, 5, 5);
        IdentityService.action(owner, "issue", "{}");
        helper.assertTrue(data.ledger.current(owner.getUUID()).isEmpty(), "A remote packet issued without a physical desk");
        desk(helper, owner);
        for (String payload : List.of("{\"name\":\"Forged\",\"age\":999}", "{\"signature\":\"Forged\"}",
                "{\"ownerUuid\":\"" + UUID.randomUUID() + "\"}", "[]", "null", "{")) {
            IdentityService.action(owner, "issue", payload);
        }
        IdentityService.action(owner, "edit", "{}");
        helper.assertTrue(data.ledger.current(owner.getUUID()).isEmpty()
                        && owner.getInventory().countItem(Items.PAPER) == 5
                        && owner.getInventory().countItem(Items.INK_SAC) == 5,
                "A forged, malformed, or unknown packet changed a profile, issued a document, or charged supplies");
        IdentityService.action(owner, "issue", "{}");
        var issued = data.ledger.current(owner.getUUID()).orElseThrow();
        helper.assertTrue(issued.profile().equals(ChroniclesBridge.snapshot(owner)),
                "A valid issue did not read identity fields directly from the server's current profile");
        var before = data.ledger.snapshot();
        IdentityService.action(owner, "issue", "{}");
        helper.assertTrue(before.equals(data.ledger.snapshot()) && owner.getInventory().countItem(Items.PAPER) == 4,
                "Repeating issue bypassed the explicit reissue workflow or charged a rejected operation");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void itemUseReadsOwnAndBorrowedDocumentsAndOfflineSnapshotsWithoutClaimingFreshness(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        var reader = player(helper);
        var document = issued(helper, owner);
        var record = data.ledger.current(owner.getUUID()).orElseThrow();
        hold(owner, document);
        helper.assertTrue(document.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "Right-clicking the actual held document did not reach its read interaction");
        rejected(helper, () -> IdentityService.requireStationSession(owner));
        helper.assertTrue(Boolean.TRUE.equals(IdentityService.inspection(owner, document).get("holderIsOwner")),
                "The owner was not distinguished from a temporary holder");
        hold(reader, document);
        var view = IdentityService.inspection(reader, reader.getMainHandItem());
        helper.assertTrue(Boolean.TRUE.equals(view.get("authentic")) && Boolean.TRUE.equals(view.get("readOnly"))
                        && Boolean.FALSE.equals(view.get("holderIsOwner"))
                        && owner.getUUID().toString().equals(view.get("ownerUuid"))
                        && record.profile().equals(view.get("profile")),
                "A borrowed physical document lost authenticity or changed its recorded owner/profile");
        helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(owner.getUUID()) == null
                        && "OFFLINE".equals(view.get("status")),
                "An offline owner's retained snapshot was falsely described as freshly checked/current");
        reader.getMainHandItem().use(helper.getLevel(), reader, InteractionHand.MAIN_HAND);
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void realRightClickOnAnotherPlayerCreatesOnlyAPendingConsentOffer(GameTestHelper helper) {
        reset(helper);
        var owner = player(helper);
        var viewer = player(helper);
        var document = issued(helper, owner);
        desk(helper, viewer);
        hold(owner, document);
        nearby(owner, viewer);
        try (var online = new OnlinePlayers(helper, owner, viewer)) {
            var inventoryBefore = viewer.getInventory().countItem(BlockRegistry.IDENTITY_DOCUMENT.get());
            helper.assertTrue(owner.interactOn(viewer, InteractionHand.MAIN_HAND).consumesAction(),
                    "Vanilla player right-click did not dispatch to the custom document interaction");
            UUID token = IdentityService.pendingOfferToken(owner.getUUID(), viewer.getUUID()).orElseThrow(
                    () -> new AssertionError("Actual player right-click failed to create the consent offer"));
            var pending = IdentityService.pendingOffer(token).orElseThrow();
            helper.assertTrue(pending.recipient().equals(viewer.getUUID()) && pending.presenter().equals(owner.getUUID())
                            && pending.expiresAt() > pending.createdAt()
                            && pending.expiresAt() - pending.createdAt() <= 30_000,
                    "A presentation was not bound to its intended players or exceeded the 30-second consent lifetime");
            IdentityService.requireStationSession(viewer);
            helper.assertTrue(viewer.getInventory().countItem(BlockRegistry.IDENTITY_DOCUMENT.get()) == inventoryBefore,
                    "A presentation moved/copied the physical document into the recipient's inventory");
        }
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void consentTokenIsRecipientBoundAndSingleUse(GameTestHelper helper) {
        reset(helper);
        var owner = player(helper);
        var viewer = player(helper);
        var stranger = player(helper);
        hold(owner, issued(helper, owner));
        desk(helper, viewer);
        try (var online = new OnlinePlayers(helper, owner, viewer, stranger)) {
            UUID token = offer(helper, owner, viewer);
            IdentityService.acceptOffer(stranger, token);
            helper.assertTrue(IdentityService.pendingOffer(token).isPresent(),
                    "An unintended recipient consumed someone else's consent token");
            IdentityService.acceptOffer(viewer, UUID.randomUUID());
            IdentityService.requireStationSession(viewer);
            IdentityService.acceptOffer(viewer, token);
            helper.assertTrue(IdentityService.pendingOffer(token).isEmpty(), "Valid explicit recipient consent did not consume the token");
            rejected(helper, () -> IdentityService.requireStationSession(viewer));
            desk(helper, viewer);
            nearby(owner, viewer);
            IdentityService.acceptOffer(viewer, token);
            IdentityService.requireStationSession(viewer);
            helper.assertTrue(viewer.getInventory().countItem(BlockRegistry.IDENTITY_DOCUMENT.get()) == 0,
                    "Accepting or replaying consent duplicated or transferred the presenter's document");
        }
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void aBorrowedDocumentCanBeReadButOnlyItsTrueOwnerCanOfferIt(GameTestHelper helper) {
        reset(helper);
        var owner = player(helper);
        var borrower = player(helper);
        var viewer = player(helper);
        var document = issued(helper, owner);
        hold(borrower, document);
        nearby(borrower, viewer);
        try (var online = new OnlinePlayers(helper, owner, borrower, viewer)) {
            helper.assertTrue(valid(borrower, document), "A physical borrowed document could not be read");
            IdentityService.offer(borrower, viewer, InteractionHand.MAIN_HAND);
            helper.assertTrue(IdentityService.pendingOfferToken(borrower.getUUID(), viewer.getUUID()).isEmpty(),
                    "The temporary holder impersonated the recorded identity owner");
            borrower.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
            IdentityService.offer(borrower, viewer, InteractionHand.MAIN_HAND);
            helper.assertTrue(IdentityService.pendingOfferToken(borrower.getUUID(), viewer.getUUID()).isEmpty(),
                    "An unrelated held item produced an identity presentation");
            hold(owner, document);
            offer(helper, owner, viewer);
        }
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void consentRechecksThatThePresenterStillPhysicallyHoldsTheSameValidId(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        var viewer = player(helper);
        hold(owner, issued(helper, owner));
        desk(helper, viewer);
        try (var online = new OnlinePlayers(helper, owner, viewer)) {
            UUID token = offer(helper, owner, viewer);
            var physical = owner.getMainHandItem().copy();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
            IdentityService.acceptOffer(viewer, token);
            IdentityService.requireStationSession(viewer);
            helper.assertTrue(IdentityService.pendingOffer(token).isPresent(),
                    "An offer was accepted after its presenter put away the physical document");
            hold(owner, physical);
            var old = data.ledger.current(owner.getUUID()).orElseThrow();
            data.ledger.issue(owner.getUUID(), old.profile(), old.issuedAt() + 1);
            IdentityService.acceptOffer(viewer, token);
            IdentityService.requireStationSession(viewer);
            helper.assertTrue(IdentityService.pendingOffer(token).isPresent() && !valid(owner, physical),
                    "An offer authenticated a document that had been revoked after the presentation");
        }
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void consentRechecksDistanceLineOfSightDimensionAndPlayerState(GameTestHelper helper) {
        reset(helper);
        for (String invalidation : List.of("distance", "wall", "dimension", "spectator", "dead")) {
            var owner = player(helper);
            var viewer = player(helper);
            hold(owner, issued(helper, owner));
            desk(helper, viewer);
            try (var online = new OnlinePlayers(helper, owner, viewer)) {
                UUID token = offer(helper, owner, viewer);
                BlockPos wall = BlockPos.containing(owner.getX() + 1, owner.getY(), owner.getZ());
                switch (invalidation) {
                    case "distance" -> owner.setPos(viewer.getX() + 7, viewer.getY(), viewer.getZ());
                    case "wall" -> {
                        for (int y = 0; y < 3; y++) helper.getLevel().setBlockAndUpdate(wall.above(y), Blocks.STONE.defaultBlockState());
                        helper.assertTrue(!owner.hasLineOfSight(viewer), "The test obstruction failed to block line of sight");
                    }
                    case "dimension" -> owner.setServerLevel(helper.getLevel().getServer()
                            .getLevel(net.minecraft.world.level.Level.NETHER));
                    case "spectator" -> owner.setGameMode(GameType.SPECTATOR);
                    case "dead" -> owner.setHealth(0);
                }
                IdentityService.acceptOffer(viewer, token);
                IdentityService.requireStationSession(viewer);
                helper.assertTrue(IdentityService.pendingOffer(token).isPresent(),
                        "Recipient consent ignored changed presenter " + invalidation);
                if (invalidation.equals("wall")) {
                    for (int y = 0; y < 3; y++) helper.getLevel().setBlockAndUpdate(wall.above(y), Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void wallClockAndServerTickDeadlinesExpireOtherwiseValidConsent(GameTestHelper helper) {
        reset(helper);
        for (boolean wallClock : List.of(true, false)) {
            var owner = player(helper);
            var viewer = player(helper);
            hold(owner, issued(helper, owner));
            desk(helper, viewer);
            try (var online = new OnlinePlayers(helper, owner, viewer)) {
                UUID token = offer(helper, owner, viewer);
                var pending = IdentityService.pendingOffer(token).orElseThrow();
                helper.assertTrue(valid(owner, owner.getMainHandItem()) && owner.distanceToSqr(viewer) <= 36
                                && owner.hasLineOfSight(viewer),
                        "Expiry fixture was invalid before its deadline changed");
                // Only the immutable server deadline is arranged; identity, holding and proximity stay valid.
                replaceOffer(new IdentityService.PendingOffer(pending.token(), pending.presenter(), pending.recipient(),
                        pending.documentId(), pending.createdAt(),
                        wallClock ? System.currentTimeMillis() - 1 : pending.expiresAt(),
                        wallClock ? pending.expiresTick() : helper.getLevel().getServer().overworld().getGameTime()));
                IdentityService.acceptOffer(viewer, token);
                IdentityService.requireStationSession(viewer);
                helper.assertTrue(IdentityService.pendingOffer(token).isEmpty(),
                        "An expired " + (wallClock ? "wall-clock" : "server-tick") + " consent token remained usable");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void commandsRequireAnOperatorForDeskItemsAndRecipientConsentForInspection(GameTestHelper helper) {
        reset(helper);
        var citizen = player(helper);
        var commands = helper.getLevel().getServer().getCommands();
        var source = citizen.createCommandSourceStack();
        commands.performPrefixedCommand(source.withPermission(0), "identity");
        commands.performPrefixedCommand(source.withPermission(2), "identity");
        rejected(helper, () -> IdentityService.requireStationSession(citizen));
        commands.performPrefixedCommand(source.withPermission(0), "identity desk");
        helper.assertTrue(citizen.getInventory().countItem(BlockRegistry.WRITING_DESK_ITEM.get()) == 0,
                "A non-operator obtained an administrative desk item");
        commands.performPrefixedCommand(source.withPermission(2), "identity desk");
        helper.assertTrue(citizen.getInventory().countItem(BlockRegistry.WRITING_DESK_ITEM.get()) == 1,
                "An operator could not obtain a physical desk item");
        commands.performPrefixedCommand(source.withPermission(2), "identity inspect " + UUID.randomUUID());
        rejected(helper, () -> IdentityService.requireStationSession(citizen));
        var owner = player(helper);
        hold(owner, issued(helper, owner));
        desk(helper, citizen);
        try (var online = new OnlinePlayers(helper, owner, citizen)) {
            UUID token = offer(helper, owner, citizen);
            commands.performPrefixedCommand(source.withPermission(0), "identity inspect " + token);
            helper.assertTrue(IdentityService.pendingOffer(token).isEmpty(),
                    "An ordinary intended recipient could not explicitly accept a valid presentation");
            rejected(helper, () -> IdentityService.requireStationSession(citizen));
        }
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void standaloneIdentityIsMinecraftOnlyAndRealChroniclesProfilesRemainReadOnly(GameTestHelper helper) {
        var data = reset(helper);
        var owner = player(helper);
        if (!ChroniclesBridge.loaded()) {
            var profile = ChroniclesBridge.snapshot(owner);
            helper.assertTrue(profile.source() == ProfileSnapshot.Source.MINECRAFT
                            && profile.name().equals(owner.getGameProfile().getName()) && profile.age() == 0
                            && profile.race().isEmpty() && profile.occupation().isEmpty()
                            && profile.signature().isEmpty() && profile.signatureDrawing().isEmpty(),
                    "Standalone mode invented RP fields without an installed Chronicles profile");
            helper.succeed();
            return;
        }
        Object original = chroniclesProfile(owner);
        var physical = issued(helper, owner);
        var profile = data.ledger.current(owner.getUUID()).orElseThrow().profile();
        helper.assertTrue(profile.source() == ProfileSnapshot.Source.CHRONICLES
                        && profile.name().equals("Personagem QA") && profile.age() == 30
                        && profile.race().equals("Humano") && profile.signature().equals("Assinatura QA")
                        && original.equals(chroniclesProfile(owner)),
                "Reading/issuing did not preserve the actual Chronicles profile, money, and progression");
        try (var online = new OnlinePlayers(helper, owner)) {
            helper.assertTrue("CURRENT".equals(IdentityService.inspection(owner, physical).get("status")),
                    "An unchanged online Chronicles profile was not recognized as current");
            Object changed = chroniclesProfile(owner, true, "Novo nome QA");
            var outdated = IdentityService.inspection(owner, physical);
            helper.assertTrue(Boolean.TRUE.equals(outdated.get("authentic")) && "OUTDATED".equals(outdated.get("status"))
                            && profile.equals(outdated.get("profile")) && changed.equals(chroniclesProfile(owner)),
                    "A changed profile rewrote the issued snapshot or reading mutated Chronicles data");
        }
        var uncreated = player(helper);
        Object empty = chroniclesProfile(uncreated, false, "");
        supplies(uncreated, 2, 2);
        desk(helper, uncreated);
        rejected(helper, () -> IdentityService.trustedIssue(uncreated));
        helper.assertTrue(data.ledger.current(uncreated.getUUID()).isEmpty()
                        && uncreated.getInventory().countItem(Items.PAPER) == 2
                        && uncreated.getInventory().countItem(Items.INK_SAC) == 2
                        && empty.equals(chroniclesProfile(uncreated)),
                "An uncreated Chronicles character fell back to Minecraft or lost materials/profile data");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void nativeNbtRoundTripPreservesWorldSealCurrentIdsAndRevocation(GameTestHelper helper) {
        var data = reset(helper);
        UUID owner = UUID.randomUUID();
        var profile = new ProfileSnapshot(ProfileSnapshot.Source.MINECRAFT, "Arquivo QA", 0, "", "", "", "");
        var old = data.ledger.issue(owner, profile, 1_000);
        var current = data.ledger.issue(owner, profile, 2_000);
        var other = data.ledger.issue(UUID.randomUUID(), profile, 3_000);
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = IdentityData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.ledger.snapshot().equals(data.ledger.snapshot())
                        && restored.ledger.lookup(old.id()).isEmpty()
                        && restored.ledger.lookup(current.id()).orElseThrow().equals(current)
                        && restored.ledger.lookup(other.id()).orElseThrow().equals(other),
                "Native NBT lost the world seal, serial counter, authoritative profiles, or old-ID revocation");
        var next = restored.ledger.issue(owner, profile, 4_000);
        helper.assertTrue(next.serial() == 4 && restored.ledger.lookup(current.id()).isEmpty(),
                "Reloaded registry reused a serial or stopped revoking replaced physical IDs");
        helper.succeed();
    }

    @GameTest(template = "identity_empty", timeoutTicks = 100)
    public static void nativeNbtRejectsMissingWrongTypeDuplicateAndUnsupportedRecords(GameTestHelper helper) {
        var data = reset(helper);
        data.ledger.issue(UUID.randomUUID(),
                new ProfileSnapshot(ProfileSnapshot.Source.MINECRAFT, "Arquivo QA", 0, "", "", "", ""), 1_000);
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        List<java.util.function.Consumer<CompoundTag>> corruptions = List.of(
                tag -> tag.remove("documents"),
                tag -> tag.remove("seal"),
                tag -> tag.putInt("version", 99),
                tag -> tag.putInt("nextSerial", 2),
                tag -> tag.putLong("nextSerial", 1),
                tag -> {
                    var wrongType = new ListTag();
                    wrongType.add(net.minecraft.nbt.StringTag.valueOf("forged"));
                    tag.put("documents", wrongType);
                },
                tag -> tag.getList("documents", Tag.TAG_COMPOUND).getCompound(0).remove("id"),
                tag -> tag.getList("documents", Tag.TAG_COMPOUND).getCompound(0).getCompound("profile").remove("source"),
                tag -> tag.getList("documents", Tag.TAG_COMPOUND).getCompound(0).getCompound("profile").putString("source", "FORGED"),
                tag -> tag.getList("documents", Tag.TAG_COMPOUND).getCompound(0).getCompound("profile").putString("age", "30"),
                tag -> tag.getList("documents", Tag.TAG_COMPOUND).getCompound(0).getCompound("profile").putString("name", "\u00a7aForged"),
                tag -> {
                    var records = tag.getList("documents", Tag.TAG_COMPOUND);
                    records.add(records.getCompound(0).copy());
                });
        for (var corruption : corruptions) {
            var bad = saved.copy();
            corruption.accept(bad);
            rejected(helper, () -> IdentityData.load(bad, helper.getLevel().registryAccess()));
        }
        helper.assertTrue(IdentityData.load(saved, helper.getLevel().registryAccess()).ledger.snapshot()
                        .equals(data.ledger.snapshot()),
                "Rejecting corrupt input altered the valid source registry");
        helper.succeed();
    }
}
