package pt.contracts.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.contracts.ContractsData;
import pt.contracts.ContractsService;
import pt.contracts.compat.ChroniclesBridge;
import pt.contracts.domain.Ledger;
import pt.contracts.network.ContractsPackets;

/** These tests exercise real server inventories and SavedData, without a client. */
@GameTestHolder("contracts")
@PrefixGameTestTemplate(false)
public final class ContractsGameTests {
    private static final String OAK = "minecraft:oak_log";

    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ContractsQA"));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ContractsData reset(GameTestHelper helper) {
        var data = ContractsData.get(helper.getLevel().getServer());
        data.ledger = new Ledger();
        data.setDirty();
        return data;
    }

    private static Ledger.Currency currency() {
        return ChroniclesBridge.loaded() ? Ledger.Currency.CHRONICLES : Ledger.Currency.EMERALD;
    }

    /** Seed the currency actually selected by the installed runtime. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void funds(FakePlayer player, long amount) {
        if (!ChroniclesBridge.loaded()) {
            player.getInventory().clearContent();
            int remaining = Math.toIntExact(amount);
            for (int slot = 0; remaining > 0; slot++) {
                int count = Math.min(64, remaining);
                player.getInventory().setItem(slot, new ItemStack(Items.EMERALD, count));
                remaining -= count;
            }
            return;
        }
        try {
            var attachment = (AttachmentType) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments")
                    .getField("PROFILE").get(null)).get();
            var type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object profile = type.getConstructor(boolean.class, String.class, int.class, String.class,
                            String.class, String.class, String.class, long.class, long.class, String.class)
                    .newInstance(true, "Artesao QA", 30, "Humano", "Test character", "QA", "Comum",
                            amount, amount, "");
            player.setData(attachment, profile);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to seed the actual Chronicles RP character", exception);
        }
    }

    private static long balance(FakePlayer player) {
        return ChroniclesBridge.loaded() ? ChroniclesBridge.balance(player)
                : player.getInventory().countItem(Items.EMERALD);
    }

    private static void rejected(GameTestHelper helper, Runnable operation) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Invalid operation was accepted");
    }

    private static int pending(Ledger ledger, FakePlayer player) {
        return ledger.deliveries(player.getUUID()).stream().mapToInt(Ledger.Delivery::quantity).sum();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void boardBlockItemAndRecipeAreRegistered(GameTestHelper helper) {
        var key = ResourceLocation.fromNamespaceAndPath("contracts", "contract_board");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(key) != Blocks.AIR, "Contract board block is missing");
        helper.assertTrue(BuiltInRegistries.ITEM.get(key) != Items.AIR, "Contract board item is missing");
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
                "Contract board recipe is missing");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void boardIsVisibleInCreativeInventoryAndSearch(GameTestHelper helper) {
        var tabKey = ResourceLocation.fromNamespaceAndPath("contracts", "contracts");
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabKey);
        helper.assertTrue(tab != null, "Contracts creative inventory tab is missing");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));

        var boardKey = ResourceLocation.fromNamespaceAndPath("contracts", "contract_board");
        var board = BuiltInRegistries.ITEM.get(boardKey);
        helper.assertTrue(board != Items.AIR, "Contract board item is missing");
        helper.assertTrue(tab.getDisplayItems().stream().anyMatch(stack -> stack.is(board)),
                "Contract board is absent from the Contracts creative inventory tab");
        helper.assertTrue(tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(board)),
                "Contract board is absent from creative inventory search");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void publicationReservesExactlyTheAdvertisedReward(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        funds(author, 31);
        var contract = ContractsService.publish(author, "Lenha para a vila", OAK, 64, 20);
        helper.assertTrue(balance(author) == 11, "Publication did not reserve exactly 20 currency units");
        helper.assertTrue(contract.currency() == currency() && contract.status() == Ledger.Status.OPEN,
                "Published contract has the wrong currency or status");
        helper.assertTrue(data.ledger.list().size() == 1
                        && data.ledger.credits(author.getUUID(), currency()) == 0,
                "Publishing created an unexpected refund or duplicate contract");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void fullDeliveryEscrowsMaterialsAndPaysWorkerExactlyOnce(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 20);
        funds(worker, 0);
        var contract = ContractsService.publish(author, "Setenta troncos", OAK, 70, 20);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 64));
        worker.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 9));
        ContractsService.complete(worker, contract.id());
        helper.assertTrue(worker.getInventory().countItem(Items.OAK_LOG) == 3,
                "Delivery did not consume exactly 70 logs across inventory stacks");
        helper.assertTrue(pending(data.ledger, author) == 70
                        && data.ledger.credits(worker.getUUID(), currency()) == 20,
                "Offline-safe material delivery or pending worker reward is missing");
        helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 20 && balance(worker) == 20,
                "Worker did not receive the exact reward");
        helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 0 && balance(worker) == 20,
                "Repeated reward claim duplicated money");
        helper.assertTrue(ContractsService.collect(author) == 70
                        && author.getInventory().countItem(Items.OAK_LOG) == 70
                        && pending(data.ledger, author) == 0,
                "Author could not collect exactly the delivered materials");
        helper.assertTrue(ContractsService.collect(author) == 0, "Repeated collection duplicated materials");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void duplicateCompletionAndCancellationCannotChangeSettledEscrow(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 12);
        var contract = ContractsService.publish(author, "Uma entrega", OAK, 8, 12);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 16));
        ContractsService.complete(worker, contract.id());
        rejected(helper, () -> ContractsService.complete(worker, contract.id()));
        rejected(helper, () -> ContractsService.cancel(author, contract.id()));
        helper.assertTrue(worker.getInventory().countItem(Items.OAK_LOG) == 8,
                "Duplicate completion consumed additional materials");
        helper.assertTrue(pending(data.ledger, author) == 8
                        && data.ledger.credits(worker.getUUID(), currency()) == 12
                        && data.ledger.credits(author.getUUID(), currency()) == 0,
                "Settled escrow changed after duplicate requests");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void authorCannotCompleteOwnContractOrCancelAnotherPlayersContract(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var stranger = player(helper);
        funds(author, 10);
        var contract = ContractsService.publish(author, "Entrega publica", OAK, 4, 10);
        author.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 4));
        rejected(helper, () -> ContractsService.complete(author, contract.id()));
        rejected(helper, () -> ContractsService.cancel(stranger, contract.id()));
        helper.assertTrue(author.getInventory().countItem(Items.OAK_LOG) == 4
                        && data.ledger.list().getFirst().status() == Ledger.Status.OPEN
                        && pending(data.ledger, author) == 0,
                "Unauthorized action consumed materials or changed the contract");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void cancellationReturnsReservedRewardExactlyOnce(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        funds(author, 17);
        var contract = ContractsService.publish(author, "Cancelar encomenda", OAK, 5, 12);
        ContractsService.cancel(author, contract.id());
        rejected(helper, () -> ContractsService.cancel(author, contract.id()));
        helper.assertTrue(balance(author) == 5 && data.ledger.credits(author.getUUID(), currency()) == 12,
                "Cancellation must place exactly the reserved amount in the author's claims");
        helper.assertTrue(ContractsService.claimMoney(author, currency()) == 12 && balance(author) == 17,
                "Cancellation refund changed the total balance");
        helper.assertTrue(ContractsService.claimMoney(author, currency()) == 0 && balance(author) == 17,
                "Duplicate refund claim duplicated money");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void rewardClaimsRespectCapacityAndPreserveEveryUnclaimedUnit(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 20);
        funds(worker, 0);
        var contract = ContractsService.publish(author, "Pagamento sem perdas", OAK, 1, 20);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 1));
        ContractsService.complete(worker, contract.id());
        if (ChroniclesBridge.loaded()) {
            funds(worker, ChroniclesBridge.MAX_CENTS);
            helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 0
                            && data.ledger.credits(worker.getUUID(), currency()) == 20,
                    "A full Chronicles wallet lost its pending reward");
            funds(worker, ChroniclesBridge.MAX_CENTS - 5);
            helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 5
                            && balance(worker) == ChroniclesBridge.MAX_CENTS
                            && data.ledger.credits(worker.getUUID(), currency()) == 15,
                    "Partial Chronicles payment lost its remainder or exceeded the wallet limit");
            funds(worker, 0);
        } else {
            for (int slot = 0; slot < 36; slot++) worker.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 0
                            && data.ledger.credits(worker.getUUID(), currency()) == 20,
                    "Full inventory lost its pending emerald reward");
            worker.getInventory().setItem(0, new ItemStack(Items.EMERALD, 59));
            helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 5
                            && worker.getInventory().getItem(0).getCount() == 64
                            && data.ledger.credits(worker.getUUID(), currency()) == 15,
                    "Partial emerald payment lost its remainder or overfilled its stack");
            worker.getInventory().setItem(1, ItemStack.EMPTY);
        }
        helper.assertTrue(ContractsService.claimMoney(worker, currency()) == 15
                        && data.ledger.credits(worker.getUUID(), currency()) == 0,
                "The final reward remainder could not be claimed");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void missingFundsAndMaterialsLeaveBalancesAndInventoriesUntouched(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 9);
        rejected(helper, () -> ContractsService.publish(author, "Sem saldo", OAK, 4, 10));
        helper.assertTrue(balance(author) == 9 && data.ledger.list().isEmpty(),
                "Rejected publication changed funds or inserted a contract");
        var contract = ContractsService.publish(author, "Sem materiais", OAK, 4, 9);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
        worker.getInventory().setItem(1, new ItemStack(Items.BIRCH_LOG, 8));
        rejected(helper, () -> ContractsService.complete(worker, contract.id()));
        helper.assertTrue(worker.getInventory().countItem(Items.OAK_LOG) == 3
                        && worker.getInventory().countItem(Items.BIRCH_LOG) == 8
                        && data.ledger.list().getFirst().status() == Ledger.Status.OPEN
                        && data.ledger.credits(worker.getUUID(), currency()) == 0,
                "Rejected delivery consumed partial materials or paid a reward");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void fullInventoryCollectionsKeepAllUndeliveredRemainders(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 1);
        var contract = ContractsService.publish(author, "Entrega volumosa", OAK, 70, 1);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 64));
        worker.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 6));
        ContractsService.complete(worker, contract.id());
        for (int slot = 0; slot < 36; slot++) author.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        helper.assertTrue(ContractsService.collect(author) == 0 && pending(data.ledger, author) == 70,
                "Full inventory lost pending materials");
        author.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 63));
        helper.assertTrue(ContractsService.collect(author) == 1 && pending(data.ledger, author) == 69
                        && author.getInventory().getItem(0).getCount() == 64,
                "Partially available stack did not preserve its remainder");
        author.getInventory().setItem(1, ItemStack.EMPTY);
        helper.assertTrue(ContractsService.collect(author) == 64 && pending(data.ledger, author) == 5,
                "Free inventory slot did not receive exactly one stack");
        author.getInventory().setItem(2, ItemStack.EMPTY);
        helper.assertTrue(ContractsService.collect(author) == 5 && pending(data.ledger, author) == 0,
                "Final collection lost its remainder");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void namedAndCustomComponentMaterialsAreNeverConsumed(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 5);
        var contract = ContractsService.publish(author, "Apenas troncos comuns", OAK, 4, 5);
        var named = new ItemStack(Items.OAK_LOG, 32);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Relicario de familia"));
        var tagged = new ItemStack(Items.OAK_LOG, 32);
        var tag = new CompoundTag();
        tag.putBoolean("roleplay_relic", true);
        tagged.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        worker.getInventory().setItem(0, named);
        worker.getInventory().setItem(1, tagged);
        worker.getInventory().setItem(2, new ItemStack(Items.OAK_LOG, 3));
        rejected(helper, () -> ContractsService.complete(worker, contract.id()));
        helper.assertTrue(named.getCount() == 32 && tagged.getCount() == 32
                        && worker.getInventory().getItem(2).getCount() == 3,
                "Failed delivery consumed protected custom items or partial materials");
        worker.getInventory().setItem(3, new ItemStack(Items.OAK_LOG, 1));
        ContractsService.complete(worker, contract.id());
        helper.assertTrue(worker.getInventory().getItem(0).getCount() == 32
                        && worker.getInventory().getItem(1).getCount() == 32
                        && worker.getInventory().getItem(0).has(DataComponents.CUSTOM_NAME)
                        && worker.getInventory().getItem(1).has(DataComponents.CUSTOM_DATA)
                        && worker.getInventory().getItem(2).isEmpty()
                        && worker.getInventory().getItem(3).isEmpty()
                        && pending(data.ledger, author) == 4,
                "Delivery did not exclusively consume default-component material stacks");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void offhandMaterialsCannotFulfilMainInventoryDelivery(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 2);
        var contract = ContractsService.publish(author, "So inventario principal", OAK, 4, 2);
        worker.getInventory().setItem(40, new ItemStack(Items.OAK_LOG, 4));
        rejected(helper, () -> ContractsService.complete(worker, contract.id()));
        helper.assertTrue(worker.getOffhandItem().getCount() == 4
                        && data.ledger.list().getFirst().status() == Ledger.Status.OPEN,
                "Offhand materials were consumed");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void currencyPrerequisitesCannotBeBypassedWithProtectedEmeralds(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        if (ChroniclesBridge.loaded()) {
            // Installing Chronicles makes a created character mandatory; no silent currency fallback.
            author.getInventory().setItem(0, new ItemStack(Items.EMERALD, 10));
            rejected(helper, () -> ContractsService.publish(author, "Personagem em falta", OAK, 1, 5));
            helper.assertTrue(author.getInventory().getItem(0).getCount() == 10 && data.ledger.list().isEmpty(),
                    "An uncreated Chronicles character fell back to spending emeralds");
        } else {
            var named = new ItemStack(Items.EMERALD, 10);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Tesouro ancestral"));
            author.getInventory().setItem(0, named);
            author.getInventory().setItem(40, new ItemStack(Items.EMERALD, 10));
            rejected(helper, () -> ContractsService.publish(author, "Esmeraldas protegidas", OAK, 1, 5));
            helper.assertTrue(named.getCount() == 10 && author.getOffhandItem().getCount() == 10
                            && data.ledger.list().isEmpty(),
                    "Protected named or offhand emeralds funded a contract");
        }
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void packetsNeedAServerOpenedSessionAndCannotChooseTheirCurrency(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        funds(author, 20);
        ContractsService.logout(author.getUUID());
        String forgedCurrency = currency() == Ledger.Currency.EMERALD ? "CHRONICLES" : "EMERALD";
        var packet = new ContractsPackets.Action("publish", "", "Contrato do menu", OAK, 4, 7, forgedCurrency);
        ContractsService.action(author, packet);
        helper.assertTrue(data.ledger.list().isEmpty() && balance(author) == 20,
                "A packet without a server-opened session spent money or created a contract");
        ContractsService.open(author, null);
        ContractsService.action(author, packet);
        helper.assertTrue(data.ledger.list().isEmpty() && balance(author) == 20,
                "A forged packet currency created a contract or changed its wallet");
        // Reopening also starts a fresh server-side action throttle window.
        ContractsService.open(author, null);
        ContractsService.action(author, new ContractsPackets.Action("publish", "", "Contrato do menu",
                OAK, 4, 7, currency().name()));
        helper.assertTrue(data.ledger.list().size() == 1
                        && data.ledger.list().getFirst().currency() == currency() && balance(author) == 13,
                "Client packet overrode server-selected currency or failed valid publication");
        ContractsService.logout(author.getUUID());
        rejected(helper, () -> ContractsService.requireSession(author));
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void physicalBoardSessionsRejectDistanceAndChangedBlocks(GameTestHelper helper) {
        reset(helper);
        var author = player(helper);
        var pos = helper.absolutePos(new BlockPos(3, 1, 3));
        var board = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("contracts", "contract_board"));
        helper.getLevel().setBlockAndUpdate(pos, board.defaultBlockState());
        author.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 1.5);
        ContractsService.open(author, pos);
        ContractsService.requireSession(author);
        author.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        rejected(helper, () -> ContractsService.requireSession(author));
        author.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 1.5);
        ContractsService.open(author, pos);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        rejected(helper, () -> ContractsService.requireSession(author));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        rejected(helper, () -> ContractsService.requireSession(author));
        ContractsService.logout(author.getUUID());
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void savedDataRoundtripRetainsContractsCreditsAndDeliveries(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        var worker = player(helper);
        funds(author, 31);
        var completed = ContractsService.publish(author, "Entrega persistida", OAK, 7, 20);
        var cancelled = ContractsService.publish(author, "Reembolso persistido", "minecraft:birch_log", 3, 11);
        worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 7));
        ContractsService.complete(worker, completed.id());
        ContractsService.cancel(author, cancelled.id());
        var tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = ContractsData.load(tag, helper.getLevel().registryAccess());
        helper.assertTrue(restored.ledger.list().equals(data.ledger.list()),
                "Contract identities or terminal states changed during SavedData serialization");
        helper.assertTrue(restored.ledger.credits(worker.getUUID(), currency()) == 20
                        && restored.ledger.credits(author.getUUID(), currency()) == 11
                        && pending(restored.ledger, author) == 7,
                "SavedData lost pending rewards, cancellation refunds, or materials");
        helper.succeed();
    }

    @GameTest(template = "contracts_empty", timeoutTicks = 100)
    public static void corruptOrUnsupportedSavedDataIsRejectedInsteadOfBecomingEmpty(GameTestHelper helper) {
        var data = reset(helper);
        var author = player(helper);
        funds(author, 9);
        var contract = ContractsService.publish(author, "Reserva persistida", OAK, 3, 9);
        ContractsService.cancel(author, contract.id());
        var valid = data.save(new CompoundTag(), helper.getLevel().registryAccess());

        var unsupported = valid.copy();
        unsupported.putInt("version", 99);
        rejected(helper, () -> ContractsData.load(unsupported, helper.getLevel().registryAccess()));
        var missingList = valid.copy();
        missingList.remove("deliveries");
        rejected(helper, () -> ContractsData.load(missingList, helper.getLevel().registryAccess()));
        var wrongListType = valid.copy();
        wrongListType.putString("credits", "not a ledger list");
        rejected(helper, () -> ContractsData.load(wrongListType, helper.getLevel().registryAccess()));
        var invalidCredit = valid.copy();
        invalidCredit.getList("credits", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putLong("amount", 0);
        rejected(helper, () -> ContractsData.load(invalidCredit, helper.getLevel().registryAccess()));
        var duplicateContract = valid.copy();
        var entries = duplicateContract.getList("contracts", net.minecraft.nbt.Tag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        rejected(helper, () -> ContractsData.load(duplicateContract, helper.getLevel().registryAccess()));

        helper.assertTrue(data.ledger.list().size() == 1
                        && data.ledger.credits(author.getUUID(), currency()) == 9,
                "Rejected SavedData replaced or modified the live ledger");
        helper.succeed();
    }
}
