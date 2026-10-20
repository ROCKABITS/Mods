package pt.crownledger.test;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.crownledger.block.BlockRegistry;
import pt.crownledger.block.CrownChestBlock;
import pt.crownledger.compat.ContractsBridge;
import pt.crownledger.compat.RealmBridge;
import pt.crownledger.compat.NotificationsBridge;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Policy;
import pt.crownledger.domain.Treasury;
import pt.crownledger.domain.Treasury.Currency;
import pt.crownledger.domain.Treasury.Kind;
import pt.crownledger.service.CrownService;

/** Real server inventories, blocks, saved data, commands, and installed optional integration jars. */
@GameTestHolder("kingdomtreasury")
@PrefixGameTestTemplate(false)
public final class CrownGameTests {
    private static final Currency EMERALD = Currency.EMERALD;

    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CrownQA"));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static TreasuryData reset(GameTestHelper helper) {
        var data = TreasuryData.get(helper.getLevel().getServer());
        data.treasury = new Treasury();
        data.setDirty();
        if (contractsLoaded()) {
            Object contracts = invokeStatic("pt.contracts.ContractsData", "get",
                    new Class<?>[]{MinecraftServer.class}, helper.getLevel().getServer());
            try {
                contracts.getClass().getField("ledger").set(contracts,
                        Class.forName("pt.contracts.domain.Ledger").getConstructor().newInstance());
                ((SavedData) contracts).setDirty();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot reset the actual optional contracts ledger", exception);
            }
        }
        return data;
    }

    private static void rejected(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "The server accepted an unauthorized or invalid operation");
    }

    private static BlockPos anchor(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(4, 1, 4));
    }

    private static BlockPos placeChest(GameTestHelper helper, Direction facing) {
        var pos = anchor(helper);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var builder = player(helper);
        builder.setPos(pos.getX() + 3.5, pos.getY(), pos.getZ() + 3.5);
        builder.setYRot(facing.getOpposite().toYRot());
        var stack = new ItemStack(BlockRegistry.CROWN_CHEST_ITEM.get(), 2);
        builder.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
        helper.assertTrue(stack.useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit)).consumesAction()
                        && stack.getCount() == 1, "The real Crown Chest item did not place exactly one block");
        return pos;
    }

    private static BlockPos openChest(GameTestHelper helper, FakePlayer player) {
        var pos = placeChest(helper, Direction.NORTH);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        CrownService.requireSession(player);
        return pos;
    }

    private static Policy percentage(int basisPoints) {
        return new Policy(0, Policy.TaxMode.PERCENTAGE, basisPoints, 0, 0, 1, 1, EMERALD, 1, 3);
    }

    private static Policy payroll(long salary, long fixedTax) {
        return new Policy(0, Policy.TaxMode.FIXED, 0, fixedTax, salary, 1, 1, EMERALD, 1, 3);
    }

    /** Seeds a completed real-time period, leaving all wallet effects to the production service. */
    private static void payrollFixture(TreasuryData data, FakePlayer citizen, long salary, long tax, long funds) {
        long now = System.currentTimeMillis();
        long began = now - 3_600_100;
        data.treasury.setPolicy(payroll(salary, tax), citizen.getUUID(), began);
        data.treasury.cycle(began);
        data.treasury.recordActivity(citizen.getUUID(), citizen.getGameProfile().getName(), 1, began + 1);
        data.treasury.addFunds(EMERALD, funds, Kind.DEPOSIT, citizen.getUUID(), "Funded payroll fixture", began + 2);
        data.treasury.cycle(now);
        data.setDirty();
    }

    private static final class Governor implements AutoCloseable {
        private final TreasuryData data;
        private final CouncilFixture council;
        private Governor(GameTestHelper helper, FakePlayer governor) {
            data = TreasuryData.get(helper.getLevel().getServer());
            council = CouncilFixture.loaded() ? new CouncilFixture(governor.server) : null;
            change(governor);
        }
        private void change(FakePlayer governor) {
            long now = System.currentTimeMillis();
            if (council == null) data.treasury.setStandaloneManager(governor.getUUID(), governor.getUUID(), now);
            else council.mandate(governor.getUUID(), now - 1_000, now + 60_000, now - 500);
            data.setDirty();
        }
        @Override public void close() { if (council != null) council.close(); }
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void chestItemRecipeAndCreativeSearchAreAvailable(GameTestHelper helper) {
        var key = ResourceLocation.fromNamespaceAndPath("kingdomtreasury", "crown_chest");
        var chest = BlockRegistry.CROWN_CHEST.get();
        var item = BlockRegistry.CROWN_CHEST_ITEM.get();
        helper.assertTrue(BuiltInRegistries.BLOCK.get(key) == chest && BuiltInRegistries.ITEM.get(key) == item,
                "The Crown Chest block or item is not registered");
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
                "The Crown Chest recipe is unavailable");
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(
                ResourceLocation.fromNamespaceAndPath("kingdomtreasury", "crown_ledger"));
        helper.assertTrue(tab != null, "The Crown Ledger creative inventory tab is missing");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
        helper.assertTrue(tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count() == 1
                        && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(item)),
                "The physical chest is not available in the creative tab and search");
        helper.assertTrue(!chest.defaultBlockState().hasBlockEntity(),
                "The decorative treasury chest must not expose a separate item inventory");
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void chestActuallyPlacesAndRotatesWithAnAccurateOutline(GameTestHelper helper) {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var pos = placeChest(helper, facing);
            var state = helper.getLevel().getBlockState(pos);
            helper.assertTrue(state.is(BlockRegistry.CROWN_CHEST.get())
                            && state.getValue(CrownChestBlock.FACING) == facing,
                    "The placed Crown Chest faces the wrong direction");
            var shape = state.getShape(helper.getLevel(), pos);
            helper.assertTrue(!shape.isEmpty()
                            && Shapes.joinIsNotEmpty(shape, Shapes.block(), BooleanOp.NOT_SAME),
                    "The chest and ledger still use a solid cube outline");
        }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void remotePacketsCannotDonateWithoutAPhysicalChestSession(GameTestHelper helper) {
        var data = reset(helper);
        var donor = player(helper);
        donor.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
        CrownService.logout(donor.getUUID());
        rejected(helper, () -> CrownService.requireSession(donor));
        rejected(helper, () -> CrownService.snapshot(donor));
        CrownService.action(donor, "donate", "{\"amount\":10,\"currency\":\"EMERALD\"}");
        helper.assertTrue(donor.getInventory().countItem(Items.EMERALD) == 20 && data.treasury.balance(EMERALD) == 0,
                "A packet without a physical session moved player or kingdom money");
        CrownService.openBoard(donor, null);
        rejected(helper, () -> CrownService.requireSession(donor));
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void distanceAndBrokenChestInvalidateSessionsWithoutLosingTreasuryMoney(GameTestHelper helper) {
        var data = reset(helper);
        var donor = player(helper);
        donor.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
        var pos = openChest(helper, donor);
        CrownService.donate(donor, 10, EMERALD);
        donor.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        rejected(helper, () -> CrownService.requireSession(donor));
        CrownService.action(donor, "donate", "{\"amount\":10,\"currency\":\"EMERALD\"}");
        donor.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        CrownService.openBoard(donor, pos);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard);
        var breaker = player(helper);
        helper.assertTrue(breaker.gameMode.destroyBlock(pos), "A player could not break the physical chest");
        rejected(helper, () -> CrownService.requireSession(donor));
        rejected(helper, () -> CrownService.snapshot(donor));
        long drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(entity -> entity.getItem().is(BlockRegistry.CROWN_CHEST_ITEM.get()))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(drops == 1 && data.treasury.balance(EMERALD) == 10
                        && donor.getInventory().countItem(Items.EMERALD) == 10,
                "Breaking a station duplicated its item or changed the server-owned treasury");
        CrownService.logout(donor.getUUID());
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void aSessionCannotBeReusedFromAnotherDimension(GameTestHelper helper) {
        var data = reset(helper);
        var donor = player(helper);
        donor.getInventory().setItem(0, new ItemStack(Items.EMERALD, 10));
        openChest(helper, donor);
        var original = donor.serverLevel();
        var other = donor.server.getLevel(Level.NETHER);
        helper.assertTrue(other != null, "The test server did not create its Nether dimension");
        try {
            donor.setServerLevel(other);
            rejected(helper, () -> CrownService.requireSession(donor));
            CrownService.action(donor, "donate", "{\"amount\":10,\"currency\":\"EMERALD\"}");
            helper.assertTrue(data.treasury.balance(EMERALD) == 0
                            && donor.getInventory().countItem(Items.EMERALD) == 10,
                    "A session from a different dimension authorized a treasury payment");
        } finally {
            donor.setServerLevel(original);
            CrownService.logout(donor.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void donationsRequireRealMoneyAndDoNotCreatePersonalClaims(GameTestHelper helper) {
        var data = reset(helper);
        var donor = player(helper);
        donor.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
        rejected(helper, () -> CrownService.donate(donor, 21, EMERALD));
        rejected(helper, () -> CrownService.donate(donor, 0, EMERALD));
        helper.assertTrue(data.treasury.balance(EMERALD) == 0
                        && donor.getInventory().countItem(Items.EMERALD) == 20,
                "A rejected donation changed the real wallet or the treasury");
        CrownService.donate(donor, 12, EMERALD);
        helper.assertTrue(data.treasury.balance(EMERALD) == 12
                        && donor.getInventory().countItem(Items.EMERALD) == 8
                        && data.treasury.claims(donor.getUUID(), EMERALD) == 0,
                "A donation was not funded by the player's actual emerald inventory");
        rejected(helper, () -> CrownService.donate(donor, 12, EMERALD));
        helper.assertTrue(data.treasury.balance(EMERALD) == 12
                        && donor.getInventory().countItem(Items.EMERALD) == 8,
                "Repeating an unfunded donation minted kingdom currency");
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void salaryClaimsRespectInventoryCapacityAndRetainEveryUnclaimedEmerald(GameTestHelper helper) {
        var data = reset(helper);
        var citizen = player(helper);
        payrollFixture(data, citizen, 10, 0, 10);
        helper.assertTrue(data.treasury.claims(citizen.getUUID(), EMERALD) == 10
                        && data.treasury.balance(EMERALD) == 0,
                "The payroll fixture did not reserve exactly ten funded emeralds");
        for (int slot = 0; slot < 36; slot++) citizen.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        helper.assertTrue(CrownService.claim(citizen, EMERALD) == 0
                        && data.treasury.claims(citizen.getUUID(), EMERALD) == 10,
                "A full inventory consumed a pending salary");
        citizen.getInventory().setItem(0, new ItemStack(Items.EMERALD, 61));
        helper.assertTrue(CrownService.claim(citizen, EMERALD) == 3
                        && citizen.getInventory().getItem(0).getCount() == 64
                        && data.treasury.claims(citizen.getUUID(), EMERALD) == 7,
                "A partial salary claim lost its remainder or overfilled a stack");
        citizen.getInventory().setItem(1, ItemStack.EMPTY);
        helper.assertTrue(CrownService.claim(citizen, EMERALD) == 7
                        && data.treasury.claims(citizen.getUUID(), EMERALD) == 0
                        && CrownService.claim(citizen, EMERALD) == 0
                        && citizen.getInventory().countItem(Items.EMERALD) == 71,
                "Salary collection did not transfer exactly the ten allocated emeralds once");
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void fixedTaxesClampToTheActualWalletAndNeverOverdrawOrPersistUnpayableDebt(GameTestHelper helper) {
        var data = reset(helper);
        var citizen = player(helper);
        citizen.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
        payrollFixture(data, citizen, 10, 20, 0);
        helper.assertTrue(data.treasury.taxDue(citizen.getUUID(), EMERALD) == 10,
                "Fixed tax was not offset by the salary before charging the actual wallet");
        CrownService.settlePlayer(citizen);
        helper.assertTrue(citizen.getInventory().countItem(Items.EMERALD) == 0
                        && data.treasury.balance(EMERALD) == 3
                        && data.treasury.taxDue(citizen.getUUID(), EMERALD) == 0
                        && data.treasury.claims(citizen.getUUID(), EMERALD) == 0,
                "A fixed tax overdrew the player, created currency, or retained unpayable debt");
        CrownService.settlePlayer(citizen);
        helper.assertTrue(data.treasury.balance(EMERALD) == 3,
                "The same fixed-tax period was collected twice");
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void ordinaryPlayersCannotChangePolicyOrPublishPublicContracts(GameTestHelper helper) {
        var data = reset(helper);
        var governor = player(helper);
        var stranger = player(helper);
        try (var authority = new Governor(helper, governor)) {
            var original = data.treasury.policy();
            var chest = openChest(helper, stranger);
            var view = JsonParser.parseString(CrownService.snapshot(stranger)).getAsJsonObject();
            helper.assertTrue(!view.get("manager").getAsBoolean(),
                    "The public treasury view marked an ordinary visitor as its manager");
            CrownService.action(stranger, "policy", "{\"taxBasisPoints\":500}");
            rejected(helper, () -> CrownService.setPolicy(stranger, percentage(500)));
            rejected(helper, () -> CrownService.publishPublic(stranger, "Obra nao autorizada", "minecraft:oak_log", 1, 10));
            rejected(helper, () -> CrownService.cancelPayroll(stranger));
            CrownService.openBoard(stranger, chest);
            CrownService.action(stranger, "publish",
                    "{\"title\":\"Publicacao abusiva\",\"item\":\"minecraft:oak_log\",\"quantity\":1,\"reward\":10}");
            helper.assertTrue(data.treasury.policy().equals(original) && data.treasury.balance(EMERALD) == 0,
                    "A non-governor changed kingdom policy or funded a public contract");
            CrownService.setPolicy(governor, percentage(500));
            helper.assertTrue(data.treasury.policy().taxBasisPoints() == 500,
                    "The authorized current governor could not change the policy");
        } finally { CrownService.logout(stranger.getUUID()); }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void commandsNeverOpenRemoteMenusAndStandaloneAppointmentRequiresAnOperator(GameTestHelper helper) {
        var data = reset(helper);
        var citizen = player(helper);
        var source = citizen.createCommandSourceStack();
        var commands = citizen.server.getCommands();
        commands.performPrefixedCommand(source.withPermission(0), "crown");
        commands.performPrefixedCommand(source.withPermission(2), "crown");
        rejected(helper, () -> CrownService.requireSession(citizen));
        commands.performPrefixedCommand(source.withPermission(0), "crown chest");
        helper.assertTrue(citizen.getInventory().countItem(BlockRegistry.CROWN_CHEST_ITEM.get()) == 0,
                "A non-operator received an administrative chest item");
        commands.performPrefixedCommand(source.withPermission(2), "crown chest");
        helper.assertTrue(citizen.getInventory().countItem(BlockRegistry.CROWN_CHEST_ITEM.get()) == 1,
                "The operator could not obtain the physical treasury station");
        rejected(helper, () -> CrownService.appoint(source.withPermission(0), citizen));
        if (CouncilFixture.loaded()) {
            rejected(helper, () -> CrownService.appoint(source.withPermission(2), citizen));
            helper.assertTrue(data.treasury.standaloneManager() == null,
                    "An operator bypassed the installed Realm Council authority");
        } else {
            commands.performPrefixedCommand(source.withPermission(2), "crown steward @s");
            helper.assertTrue(citizen.getUUID().equals(data.treasury.standaloneManager()),
                    "The operator's steward command did not assign the standalone manager");
            CrownService.setPolicy(citizen, percentage(500));
        }
        rejected(helper, () -> CrownService.requireSession(citizen));
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void realCouncilAuthorityRequiresAnOathAnActiveTermAndTheCurrentSuccessor(GameTestHelper helper) {
        var data = reset(helper);
        var former = player(helper);
        var successor = player(helper);
        if (!CouncilFixture.loaded()) {
            helper.assertTrue(RealmBridge.governor(former.server, System.currentTimeMillis()) == null,
                    "An absent Council invented an elected governor");
            CrownService.appoint(former.createCommandSourceStack().withPermission(2), former);
            CrownService.setPolicy(former, percentage(500));
            helper.assertTrue(data.treasury.policy().taxBasisPoints() == 500,
                    "Standalone governance did not work without Realm Council");
            helper.succeed();
            return;
        }
        try (var council = new CouncilFixture(former.server)) {
            long now = System.currentTimeMillis();
            data.treasury.setStandaloneManager(former.getUUID(), former.getUUID(), now);
            council.mandate(former.getUUID(), now - 1_000, now + 60_000, 0);
            rejected(helper, () -> CrownService.setPolicy(former, percentage(500)));
            council.claim(now - 500);
            CrownService.setPolicy(former, percentage(500));
            var chest = openChest(helper, former);
            helper.assertTrue(data.treasury.policy().taxBasisPoints() == 500,
                    "A real, sworn, active mandate did not authorize its winner");
            council.expire(now - 1);
            rejected(helper, () -> CrownService.setPolicy(former, percentage(600)));
            CrownService.openBoard(former, chest);
            CrownService.action(former, "policy", "{\"taxBasisPoints\":600}");
            helper.assertTrue(data.treasury.policy().taxBasisPoints() == 500,
                    "An already-open physical menu retained management authority after mandate expiry");
            council.mandate(successor.getUUID(), now - 1_000, now + 60_000, now - 500);
            rejected(helper, () -> CrownService.setPolicy(former, percentage(700)));
            CrownService.setPolicy(successor, percentage(800));
            helper.assertTrue(data.treasury.policy().taxBasisPoints() == 800,
                    "A governor change did not immediately transfer management authority");
        } finally { CrownService.logout(former.getUUID()); }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void realContractsConserveTaxEscrowRefundsAndMaterialsAcrossGovernors(GameTestHelper helper) {
        var data = reset(helper);
        var governor = player(helper);
        var successor = player(helper);
        var donor = player(helper);
        var worker = player(helper);
        donor.getInventory().setItem(0, new ItemStack(Items.EMERALD, 40));
        try (var authority = new Governor(helper, governor)) {
            CrownService.setPolicy(governor, percentage(500));
            CrownService.donate(donor, 40, EMERALD);
            if (!contractsLoaded()) {
                rejected(helper, () -> CrownService.publishPublic(governor,
                        "Integracao opcional ausente", "minecraft:oak_log", 1, 20));
                helper.assertTrue(data.treasury.balance(EMERALD) == 40,
                        "Publishing without Contracts consumed retained treasury money");
                helper.succeed();
                return;
            }
            Object policy = invokeStatic("pt.contracts.api.ContractsApi", "policy",
                    new Class<?>[]{MinecraftServer.class}, governor.server);
            helper.assertTrue((int) accessor(policy, "taxBasisPoints") == 500,
                    "The actual Contracts mod did not read Crown Ledger's registered policy provider");
            UUID completed = CrownService.publishPublic(governor, "Lenha publica", "minecraft:oak_log", 1, 20);
            helper.assertTrue(data.treasury.balance(EMERALD) == 20 && contractsBalance(governor.server) == 0,
                    "Public escrow was not reserved once from the actual Crown Ledger funds");
            worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 1));
            invokeStatic("pt.contracts.ContractsService", "complete",
                    new Class<?>[]{ServerPlayer.class, UUID.class}, worker, completed);
            helper.assertTrue(contractsBalance(governor.server) == 1,
                    "A 20-emerald contract at 5% did not reserve exactly one emerald of kingdom tax");
            CrownService.sweep(governor.server);
            helper.assertTrue(data.treasury.balance(EMERALD) == 21 && contractsBalance(governor.server) == 0,
                    "Contract tax was not transferred back exactly once into Crown Ledger");
            Object currency = contractsEmeraldCurrency();
            long claimed = (long) invokeStatic("pt.contracts.ContractsService", "claimMoney",
                    new Class<?>[]{ServerPlayer.class, currency.getClass()}, worker, currency);
            helper.assertTrue(claimed == 19 && worker.getInventory().countItem(Items.EMERALD) == 19
                            && donor.getInventory().countItem(Items.EMERALD) == 0,
                    "The real worker claim did not conserve 19 worker + 21 remaining treasury = 40 donated");
            UUID cancelled = CrownService.publishPublic(governor, "Obra do mandato anterior", "minecraft:oak_log", 1, 10);
            helper.assertTrue(data.treasury.balance(EMERALD) == 11,
                    "The second public contract did not reserve its full reward");
            authority.change(successor);
            rejected(helper, () -> CrownService.cancelPublic(governor, cancelled));
            rejected(helper, () -> CrownService.collect(governor));
            CrownService.cancelPublic(successor, cancelled);
            helper.assertTrue(data.treasury.balance(EMERALD) == 21 && contractsBalance(successor.server) == 0,
                    "The successor's cancellation did not return the full reward to the realm");
            rejected(helper, () -> CrownService.cancelPublic(successor, cancelled));
            helper.assertTrue(CrownService.collect(successor) == 1
                            && successor.getInventory().countItem(Items.OAK_LOG) == 1
                            && CrownService.collect(successor) == 0,
                    "A governor change lost public materials or allowed a duplicate collection");
            CrownService.sweep(successor.server);
            helper.assertTrue(data.treasury.balance(EMERALD) == 21,
                    "Repeating escrow synchronization duplicated tax or cancellation refunds");
        }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void olderOpenPublicContractsRemainManageableAfterNewerContractsFinish(GameTestHelper helper) {
        var data = reset(helper);
        if (!contractsLoaded()) { helper.succeed(); return; }
        var governor = player(helper);
        var donor = player(helper);
        donor.getInventory().setItem(0, new ItemStack(Items.EMERALD, 40));
        try (var authority = new Governor(helper, governor)) {
            CrownService.donate(donor, 40, EMERALD);
            UUID olderOpen = CrownService.publishPublic(governor, "Obra publica ainda aberta", "minecraft:oak_log", 1, 1);
            for (int index = 0; index < 16; index++) {
                UUID newer = CrownService.publishPublic(governor, "Obra publica terminada " + index,
                        "minecraft:oak_log", 1, 1);
                CrownService.cancelPublic(governor, newer);
            }
            var visible = ContractsBridge.contracts(governor.server);
            helper.assertTrue(visible.size() == 1 && olderOpen.equals(visible.getFirst().get("id"))
                            && "OPEN".equals(visible.getFirst().get("status").toString()),
                    "Sixteen newer terminal contracts hid an older public contract that still needs management");
            CrownService.cancelPublic(governor, olderOpen);
            helper.assertTrue(data.treasury.balance(EMERALD) == 40 && contractsBalance(governor.server) == 0,
                    "The retained open public contract could not be cancelled and refunded exactly once");
        }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void invalidPublicContractsRetainTreasuryFundsWithoutLeavingEscrowCredit(GameTestHelper helper) {
        var data = reset(helper);
        if (!contractsLoaded()) { helper.succeed(); return; }
        var governor = player(helper);
        var donor = player(helper);
        donor.getInventory().setItem(0, new ItemStack(Items.EMERALD, 40));
        try (var authority = new Governor(helper, governor)) {
            CrownService.donate(donor, 40, EMERALD);
            var before = data.treasury.snapshot();
            for (int attempt = 0; attempt < 2; attempt++) {
                rejected(helper, () -> CrownService.publishPublic(governor,
                        "Objeto inexistente", "minecraft:crown_qa_missing_item", 1, 10));
                rejected(helper, () -> CrownService.publishPublic(governor,
                        "", "minecraft:oak_log", 1, 10));
                rejected(helper, () -> CrownService.publishPublic(governor,
                        "Quantidade invalida", "minecraft:oak_log", 0, 10));
                helper.assertTrue(data.treasury.snapshot().equals(before) && contractsBalance(governor.server) == 0,
                        "A rejected public contract consumed treasury funds or retained unfunded kingdom credit");
            }
            var contracts = (java.util.List<?>) invokeStatic("pt.contracts.api.ContractsApi", "kingdomContracts",
                    new Class<?>[]{MinecraftServer.class}, governor.server);
            helper.assertTrue(contracts.isEmpty(), "Invalid public requests created actual Contracts escrow entries");
            CrownService.sweep(governor.server);
            helper.assertTrue(data.treasury.snapshot().equals(before) && contractsBalance(governor.server) == 0
                            && donor.getInventory().countItem(Items.EMERALD) == 0,
                    "Sweeping after rejected public contracts minted money or changed the funded treasury");
            UUID valid = CrownService.publishPublic(governor, "Obra valida apos rejeicoes", "minecraft:oak_log", 1, 10);
            var visible = ContractsBridge.contracts(governor.server);
            helper.assertTrue(data.treasury.balance(EMERALD) == 30 && contractsBalance(governor.server) == 0
                            && visible.size() == 1 && valid.equals(visible.getFirst().get("id")),
                    "Rejected requests left orphan contracts or prevented a later funded public publication");
            CrownService.cancelPublic(governor, valid);
            helper.assertTrue(data.treasury.balance(EMERALD) == 40 && contractsBalance(governor.server) == 0,
                    "Publication after rejected requests did not return exactly its funded reward");
        }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void treasuryNbtRetainsAllocatedMoneyTaxesAndGovernanceAndRejectsCorruption(GameTestHelper helper) {
        var data = reset(helper);
        var citizen = player(helper);
        payrollFixture(data, citizen, 10, 0, 15);
        data.treasury.setStandaloneManager(citizen.getUUID(), citizen.getUUID(), System.currentTimeMillis());
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = TreasuryData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.treasury.snapshot().equals(data.treasury.snapshot())
                        && restored.treasury.balance(EMERALD) == 5
                        && restored.treasury.claims(citizen.getUUID(), EMERALD) == 10,
                "Native NBT serialization lost treasury money, allocated claims, or policy history");
        var corrupt = saved.copy();
        corrupt.remove("claims");
        rejected(helper, () -> TreasuryData.load(corrupt, helper.getLevel().registryAccess()));
        var unsupported = saved.copy();
        unsupported.putInt("version", 99);
        rejected(helper, () -> TreasuryData.load(unsupported, helper.getLevel().registryAccess()));
        helper.succeed();
    }


    private static Policy marketPolicy(Policy old, int basisPoints) {
        return new Policy(old.minimumReputation(), old.taxMode(), old.taxBasisPoints(), old.fixedTax(), old.salary(),
                old.periodHours(), old.activeMinutes(), old.currency(), old.reputationPerPublicContract(),
                old.dailyReputationLimit(), basisPoints);
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void marketTaxPolicyRequiresCurrentGovernorAndStrictIntegerPacketsAndPreservesOtherRules(GameTestHelper helper) {
        var data = reset(helper);
        var governor = player(helper);
        var successor = player(helper);
        var stranger = player(helper);
        try (var authority = new Governor(helper, governor)) {
            var initial = new Policy(12, Policy.TaxMode.FIXED, 700, 3, 10, 168, 10, EMERALD, 2, 4, 0);
            CrownService.setPolicy(governor, initial);
            var chest = openChest(helper, governor);
            for (String value : new String[]{"-1", "10001", "500.5", "\"500\"", "null", "true"}) {
                CrownService.openBoard(governor, chest);
                CrownService.action(governor, "policy", "{\"marketTaxBasisPoints\":" + value + "}");
                helper.assertTrue(data.treasury.policy().equals(initial),
                        "A negative, overflowing or noninteger market tax packet changed kingdom rules: " + value);
            }
            CrownService.openBoard(governor, chest);
            CrownService.action(governor, "policy", "{\"marketTaxBasisPoints\":500}");
            var expected = marketPolicy(initial, 500);
            helper.assertTrue(data.treasury.policy().equals(expected),
                    "Updating market tax modified independent fixed tax, contract percentage, salary, or reputation settings");
            var view = JsonParser.parseString(CrownService.snapshot(governor)).getAsJsonObject();
            helper.assertTrue(view.getAsJsonObject("policy").get("marketTaxBasisPoints").getAsInt() == 500,
                    "The production Crown snapshot did not expose the saved independent market tax rule");
            CrownService.openBoard(governor, chest);
            CrownService.action(governor, "policy", "{\"taxBasisPoints\":900}");
            helper.assertTrue(data.treasury.policy().marketTaxBasisPoints() == 500
                            && data.treasury.policy().taxBasisPoints() == 900,
                    "A legacy policy payload reset the independent market rate when its field was omitted");
            stranger.server.getPlayerList().op(stranger.getGameProfile());
            try {
                rejected(helper, () -> CrownService.setPolicy(stranger, marketPolicy(data.treasury.policy(), 1_000)));
            } finally { stranger.server.getPlayerList().deop(stranger.getGameProfile()); }
            authority.change(successor);
            CrownService.openBoard(governor, chest);
            CrownService.action(governor, "policy", "{\"marketTaxBasisPoints\":1000}");
            helper.assertTrue(data.treasury.policy().marketTaxBasisPoints() == 500,
                    "An old governor's already-open physical session retained market-tax authority after succession");
            CrownService.setPolicy(successor, marketPolicy(data.treasury.policy(), 1_000));
            helper.assertTrue(data.treasury.policy().marketTaxBasisPoints() == 1_000,
                    "The current non-operator governor could not manage the independent sales tax");
        } finally { CrownService.logout(governor.getUUID()); }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void legacyTreasuryMigrationPreservesFundsClaimsAndRulesWithZeroRetroactiveMarketTax(GameTestHelper helper) {
        var data = reset(helper);
        var citizen = player(helper);
        payrollFixture(data, citizen, 10, 0, 15);
        data.treasury.setStandaloneManager(citizen.getUUID(), citizen.getUUID(), System.currentTimeMillis());
        var policy = data.treasury.policy();
        data.treasury.setPolicy(marketPolicy(policy, 500), citizen.getUUID(), System.currentTimeMillis());
        var saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = TreasuryData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(saved.getInt("version") == 6 && restored.treasury.policy().marketTaxBasisPoints() == 500
                        && restored.treasury.snapshot().equals(data.treasury.snapshot()),
                "Version2 native Crown records lost the independently configured sales-tax rule");
        var legacy = saved.copy();
        legacy.putInt("version", 1);legacy.remove("incomeReceipts");
        legacy.getCompound("policy").remove("marketTaxBasisPoints");
        var migrated = TreasuryData.load(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(migrated.isDirty() && migrated.treasury.policy().equals(policy)
                        && migrated.treasury.balance(EMERALD) == 5
                        && migrated.treasury.claims(citizen.getUUID(), EMERALD) == 10
                        && migrated.treasury.standaloneManager().equals(citizen.getUUID())
                        && migrated.treasury.history().equals(data.treasury.history()),
                "Version1 migration changed existing funds, salaries, governance, other rules or created retroactive market tax");
        var missing = saved.copy(); missing.getCompound("policy").remove("marketTaxBasisPoints");
        rejected(helper, () -> TreasuryData.load(missing, helper.getLevel().registryAccess()));
        var wrongType = saved.copy(); wrongType.getCompound("policy").putString("marketTaxBasisPoints", "500");
        rejected(helper, () -> TreasuryData.load(wrongType, helper.getLevel().registryAccess()));
        var outOfRange = saved.copy(); outOfRange.getCompound("policy").putInt("marketTaxBasisPoints", 10_001);
        rejected(helper, () -> TreasuryData.load(outOfRange, helper.getLevel().registryAccess()));
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void actualMarketsSaleAndOfflineClaimConserveTaxAcrossRepeatedSweepsAndFullTreasury(GameTestHelper helper) {
        var data = reset(helper);
        var governor = player(helper);
        try (var authority = new Governor(helper, governor)) {
            CrownService.setPolicy(governor, new Policy(0, Policy.TaxMode.FIXED, 700, 3, 0, 168, 10, EMERALD, 1, 3, 500));
            if (!MarketsFixture.loaded()) {
                data.treasury.addFunds(EMERALD, 20, Kind.DEPOSIT, governor.getUUID(), "Standalone Crown fixture", System.currentTimeMillis());
                var before = data.treasury.snapshot();
                CrownService.sweepMarkets(governor.server);
                helper.assertTrue(data.treasury.snapshot().equals(before)
                                && data.treasury.policy().marketTaxBasisPoints() == 500,
                        "An absent optional Markets mod changed existing Crown funds or its configured future sales rate");
                helper.succeed();
                return;
            }
            var seller = player(helper);
            var buyer = player(helper);
            buyer.getInventory().setItem(0, new ItemStack(Items.EMERALD, 40));
            try (var markets = new MarketsFixture(helper, seller, buyer, 2, 20)) {
                helper.assertTrue(markets.rate() == 500 && seller.server.getPlayerList().getPlayer(seller.getUUID()) == null,
                        "The real paired provider failed independent5% policy or the seller was not actually offline");
                markets.purchase(1);
                helper.assertTrue(buyer.getInventory().countItem(Items.EMERALD) == 20
                                && buyer.getInventory().countItem(Items.APPLE) == 1
                                && markets.earnings() == 19 && markets.tax() == 1,
                        "A real paid gross20 Markets sale failed to conserve offline seller19 plus escrow1");
                CrownService.sweepMarkets(governor.server);
                helper.assertTrue(data.treasury.balance(EMERALD) == 1 && markets.tax() == 0
                                && data.treasury.history().stream().anyMatch(entry -> entry.kind() == Kind.MARKET_TAX && entry.amount() == 1),
                        "The actual Crown transfer lost funded tax or failed to record a market-specific history entry");
                markets.claim();
                rejected(helper, markets::claim);
                CrownService.sweepMarkets(governor.server);
                helper.assertTrue(data.treasury.balance(EMERALD) == 1 && markets.tax() == 0
                                && seller.getInventory().countItem(Items.EMERALD) == 19,
                        "Repeating a real seller claim or Crown sweep collected/credited a sale tax twice");
                markets.purchase(1);
                data.treasury.addFunds(EMERALD, Long.MAX_VALUE - 1, Kind.DEPOSIT, null,
                        "Native full treasury destination fixture", System.currentTimeMillis());
                var full = data.treasury.snapshot();
                rejected(helper, () -> CrownService.sweepMarkets(governor.server));
                helper.assertTrue(data.treasury.snapshot().equals(full) && markets.tax() == 1,
                        "Failed Crown destination preflight destroyed tax escrow or changed the full treasury history");
                data.treasury.removeFunds(EMERALD, Long.MAX_VALUE - 1, Kind.DEPOSIT, null,
                        "Release native treasury capacity", System.currentTimeMillis());
                CrownService.sweepMarkets(governor.server);
                CrownService.sweepMarkets(governor.server);
                helper.assertTrue(data.treasury.balance(EMERALD) == 2 && markets.tax() == 0
                                && markets.earnings() == 19 && buyer.getInventory().countItem(Items.EMERALD) == 0
                                && buyer.getInventory().countItem(Items.APPLE) == 2,
                        "The retained second tax could not transfer once after capacity returned while conserving two sales");
                System.out.println("CROWN_MARKETS_TAX_INTEGRATION_PASSED");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "crown_empty", timeoutTicks = 100)
    public static void oneHundredPercentActualMarketsTaxCreditsTreasuryWithZeroSellerEarnings(GameTestHelper helper) {
        var data = reset(helper);
        var governor = player(helper);
        try (var authority = new Governor(helper, governor)) {
            CrownService.setPolicy(governor, marketPolicy(Policy.DEFAULT, 10_000));
            if (!MarketsFixture.loaded()) {
                helper.assertTrue(data.treasury.policy().marketTaxBasisPoints() == 10_000
                                && data.treasury.policy().taxBasisPoints() == Policy.DEFAULT.taxBasisPoints(),
                        "Standalone Crown did not retain the maximum independent market rule");
                helper.succeed();
                return;
            }
            var seller = player(helper);
            var buyer = player(helper);
            buyer.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
            try (var markets = new MarketsFixture(helper, seller, buyer, 1, 20)) {
                markets.purchase(1);
                helper.assertTrue(markets.earnings() == 0 && markets.tax() == 20
                                && buyer.getInventory().countItem(Items.EMERALD) == 0
                                && buyer.getInventory().countItem(Items.APPLE) == 1,
                        "The actual100% policy failed to deliver the paid item or reserved negative/nonzero net seller earnings");
                CrownService.sweepMarkets(governor.server);
                CrownService.sweepMarkets(governor.server);
                helper.assertTrue(data.treasury.balance(EMERALD) == 20 && markets.tax() == 0,
                        "The actual100% gross proceeds were not transferred to Crown exactly once");
            }
        }
        helper.succeed();
    }

    private static boolean contractsLoaded() { return ModList.get().isLoaded("contracts"); }

    private static long contractsBalance(MinecraftServer server) {
        return (long) invokeStatic("pt.contracts.api.ContractsApi", "kingdomBalance",
                new Class<?>[]{MinecraftServer.class, String.class}, server, "EMERALD");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object contractsEmeraldCurrency() {
        try { return Enum.valueOf((Class) Class.forName("pt.contracts.domain.Ledger$Currency"), "EMERALD"); }
        catch (ClassNotFoundException exception) {
            throw new IllegalStateException("The actual Contracts currency type is missing", exception);
        }
    }

    private static Object accessor(Object value, String name) {
        try { return value.getClass().getMethod(name).invoke(value); }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The actual optional integration record is incompatible", exception);
        }
    }

    private static Object invokeStatic(String type, String method, Class<?>[] signature, Object... args) {
        try {
            return Class.forName(type).getMethod(method, signature).invoke(null, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Optional integration operation failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The actual optional integration API is unavailable", exception);
        }
    }
    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void unavailableNotificationChannelDoesNotUndoADonation(GameTestHelper helper) {
        var data=reset(helper);
        var donor=player(helper);
        donor.getInventory().setItem(0,new ItemStack(Items.EMERALD,12));
        CrownService.donate(donor,7,EMERALD);
        helper.assertTrue(!NotificationsBridge.send(donor,"treasury",Component.literal("Receipt retry")),
                "A fake player without a supporting client accepted the optional payload");
        NotificationsBridge.notify(donor,"treasury",Component.literal("Receipt retry"));
        helper.assertTrue(data.treasury.balance(EMERALD)==7 && donor.getInventory().countItem(Items.EMERALD)==5,
                "The notification failure lost, repeated or rolled back the donated money");
        helper.succeed();
    }

    @GameTest(template="crown_empty",timeoutTicks=100)
    public static void casinoProfitTaxRequiresTheCurrentGovernorAndPreservesOtherPolicies(GameTestHelper helper){
        var data=reset(helper);var governor=player(helper);var successor=player(helper);
        try(var authority=new Governor(helper,governor)){
            var initial=new Policy(12,Policy.TaxMode.FIXED,700,3,10,168,10,EMERALD,2,4,300,0);CrownService.setPolicy(governor,initial);var chest=openChest(helper,governor);
            for(String value:new String[]{"-1","10001","500.5","\"500\"","null","true"}){CrownService.openBoard(governor,chest);CrownService.action(governor,"policy","{\"casinoTaxBasisPoints\":"+value+"}");helper.assertTrue(data.treasury.policy().equals(initial),"Invalid casino tax changed policy");}
            CrownService.openBoard(governor,chest);CrownService.action(governor,"policy","{\"casinoTaxBasisPoints\":500}");var policy=data.treasury.policy();helper.assertTrue(policy.casinoTaxBasisPoints()==500&&policy.marketTaxBasisPoints()==300&&policy.fixedTax()==3&&policy.salary()==10,"Casino tax reset an independent policy");
            var saved=data.save(new net.minecraft.nbt.CompoundTag(),helper.getLevel().registryAccess());var restored=TreasuryData.load(saved,helper.getLevel().registryAccess());helper.assertTrue(restored.treasury.policy().casinoTaxBasisPoints()==500&&saved.getInt("version")==6,"Casino policy was not persisted in format four");
            authority.change(successor);CrownService.openBoard(governor,chest);CrownService.action(governor,"policy","{\"casinoTaxBasisPoints\":1000}");helper.assertTrue(data.treasury.policy().casinoTaxBasisPoints()==500,"Former governor retained casino tax authority");
            openChest(helper,successor);CrownService.action(successor,"policy","{\"casinoTaxBasisPoints\":1000}");helper.assertTrue(data.treasury.policy().casinoTaxBasisPoints()==1000,"Current governor cannot set casino taxation");
        }finally{CrownService.logout(governor.getUUID());CrownService.logout(successor.getUUID());}helper.succeed();
    }

}
