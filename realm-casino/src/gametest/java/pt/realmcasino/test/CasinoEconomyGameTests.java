package pt.realmcasino.test;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmcasino.block.BlockRegistry;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.data.CasinoData;
import pt.realmcasino.service.CasinoService;

/** These integration branches use the actual Chronicles wallet and actual atomic world/player files. */
@GameTestHolder("realmcasino")
@PrefixGameTestTemplate(false)
public final class CasinoEconomyGameTests {
    private CasinoEconomyGameTests() {}
    private static boolean economy(GameTestHelper h, CasinoFixture f) {
        f.open(f.player);
        boolean loaded = ChroniclesBridge.loaded();
        h.assertTrue(f.view(f.player).get("economyAvailable").getAsBoolean() == loaded,
                "The actual optional wallet availability disagrees with the physical screen");
        return loaded;
    }
    private static long total(CasinoFixture f) {
        return Math.addExact(Math.addExact(ChroniclesBridge.balance(f.owner), ChroniclesBridge.balance(f.player)),
                Math.addExact(f.data.ledger().machines().values().stream().mapToLong(machine -> machine.bank()).sum(),
                        f.data.ledger().credits().values().stream().mapToLong(Long::longValue).sum()));
    }
    private static void denied(GameTestHelper h, CasinoFixture f, CasinoActors.Actor actor, String operation, String json) {
        var before = f.data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
        long wallet = ChroniclesBridge.loaded() ? ChroniclesBridge.balance(actor) : 0;
        CasinoService.action(actor, operation, json);
        h.assertTrue(before.equals(f.data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())), "A denied action changed durable casino records: " + operation);
        h.assertTrue(!ChroniclesBridge.loaded() || wallet == ChroniclesBridge.balance(actor), "A rejected packet charged the actual wallet");
        h.assertTrue(actor.snapshot().has("error") && !actor.snapshot().get("error").getAsString().isEmpty(), "A denied action supplied no actionable screen error");
    }
    private static void bet(CasinoFixture f, long amount, int... draws) {
        try (var entropy = new CasinoFixture.Entropy(CasinoFixture.draws(draws))) { f.amount(f.player, "bet", amount); }
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void missingChroniclesNeverUsesEmeraldsOrInventsAVirtualCurrency(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            f.open(f.owner); f.open(f.player);
            if (!ChroniclesBridge.loaded()) {
                f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 64));
                denied(h, f, f.owner, "deposit", f.amount(f.owner, 100).toString());
                denied(h, f, f.player, "bet", f.amount(f.player, 100).toString());
                denied(h, f, f.player, "claim", f.amount(f.player, 0).toString());
                h.assertTrue(f.player.getInventory().countItem(Items.EMERALD) == 64 && f.machine().bank() == 0,
                        "Standalone play consumed physical emeralds or created a fallback casino balance");
            } else {
                f.fund(10_000); h.assertTrue(f.machine().bank() == 10_000, "Actual wallet funding was disabled with Chronicles installed");
            }
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void actualOwnerDepositConservesMoneyAndDurablySavesTheRealPlayerReceipt(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            long total = total(f); Object profile = CasinoActors.profile(f.owner); f.fund(500_000);
            h.assertTrue(ChroniclesBridge.balance(f.owner) == 1_500_000 && total(f) == total && f.data.journal() == null,
                    "A real wallet deposit minted/lost money or left an unsettled durable intent");
            CasinoActors.stableProfile(h, profile, f.owner);
            try {
                var persisted = CasinoData.load(NbtIo.readCompressed(f.data.path(), NbtAccounter.create(64L * 1024 * 1024)), h.getLevel().registryAccess());
                h.assertTrue(persisted.ledger().machines().equals(f.data.ledger().machines()) && persisted.journal() == null,
                        "The committed machine bank was not actually readable from the atomic world file");
                var playerFile = f.owner.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(f.owner.getUUID() + ".dat");
                h.assertTrue(java.nio.file.Files.size(playerFile) > 0, "Wallet transfer was committed without a durable actual player file");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            System.out.println("CASINO_WALLET_CONSERVATION_PASSED");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void aLosingServerDrawDebitsOneStakeAndCreditsTheBankWithoutCreatingClaims(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); f.open(f.player); long total = total(f);
            bet(f, 100, 0, 1, 2);
            h.assertTrue(ChroniclesBridge.balance(f.player) == 99_900 && f.machine().bank() == 500_100
                    && f.machine().reserved() == 0 && f.pending(f.player) == 0 && total(f) == total,
                    "A losing actual slots bet failed money conservation or retained an obsolete liability");
            h.assertTrue(f.view(f.player).getAsJsonObject("round").get("payout").getAsLong() == 0,
                    "The committed losing result disagrees with the public snapshot");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void theTopServerSlotsPrizeCreatesOneDurableClaimThenPaysTheActualWalletOnce(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); f.open(f.player); long total = total(f); int[] notices = {0}; f.player.clear();
            f.player.onNotice = () -> {
                h.assertTrue(f.data.journal() == null, "A winnings notification arrived before the durable wallet/ledger intent completed");
                if (notices[0]++ == 0) h.assertTrue(f.pending(f.player) == 10_000 && f.machine().bank() == 490_100,
                        "The result notification preceded its committed guaranteed prize");
                else h.assertTrue(f.pending(f.player) == 0 && ChroniclesBridge.balance(f.player) == 109_900,
                        "The collection notification preceded the actual durable wallet credit");
            };
            bet(f, 100, 0, 0, 0);
            h.assertTrue(ChroniclesBridge.balance(f.player) == 99_900 && f.pending(f.player) == 10_000
                    && f.machine().bank() == 490_100 && total(f) == total, "A 100x slots result did not debit the exact guaranteed prize from the bank");
            f.amount(f.player, "claim", 0);
            h.assertTrue(ChroniclesBridge.balance(f.player) == 109_900 && f.pending(f.player) == 0 && total(f) == total,
                    "Claiming the actual slot winnings duplicated or lost money");
            h.assertTrue(notices[0] == 2 && f.player.notificationPackets() == 2 && f.player.chat.isEmpty(),
                    "Actual committed winnings and collection did not produce exactly two private optional notification packets");
            f.player.onNotice = null;
            denied(h, f, f.player, "claim", f.amount(f.player, 0).toString());
            System.out.println("CASINO_NOTIFICATIONS_PASSED");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void anUndercollateralizedBankRejectsBeforeAnyWalletDebitOrRandomRound(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(9_899); f.open(f.player);
            denied(h, f, f.player, "bet", f.amount(f.player, 100).toString());
            h.assertTrue(f.data.states().isEmpty() && f.data.ledger().rounds().isEmpty() && f.data.ledger().history().isEmpty(),
                    "An insufficient bank still created a committed result, active round or settlement");
            f.open(f.owner); f.amount(f.owner, "deposit", 1); f.open(f.player);
            bet(f, 100, 0, 0, 0);
            h.assertTrue(f.pending(f.player) == 10_000 && f.machine().bank() == 0, "An exactly collateralized maximum prize failed despite sufficient guaranteed funds");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void rouletteZeroLosesOutsideBetsAndPaysAStraightZeroExactlyThirtySixTimes(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.ROULETTE_TABLE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); f.open(f.player); long total = total(f);
            for (String selection : java.util.List.of("red", "black", "even", "odd")) {
                var request = f.amount(f.player, 100); request.addProperty("selection", selection);
                try (var entropy = new CasinoFixture.Entropy(CasinoFixture.draws(0))) { f.act(f.player, "bet", request); }
                h.assertTrue(f.view(f.player).getAsJsonObject("round").get("payout").getAsLong() == 0, "European roulette zero incorrectly won an outside bet: " + selection);
            }
            var request = f.amount(f.player, 100); request.addProperty("selection", "number"); request.addProperty("number", 0);
            try (var entropy = new CasinoFixture.Entropy(CasinoFixture.draws(0))) { f.act(f.player, "bet", request); }
            h.assertTrue(f.pending(f.player) == 3_600 && ChroniclesBridge.balance(f.player) == 99_500 && total(f) == total,
                    "A straight zero did not create the exact 36x gross return with money conservation");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void replayingAnAlreadyConsumedBetNonceCannotChargeOrWinTwice(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); f.open(f.player); var request = f.amount(f.player, 100);
            try (var entropy = new CasinoFixture.Entropy(CasinoFixture.draws(0, 0, 0))) { f.act(f.player, "bet", request); }
            denied(h, f, f.player, "bet", request.toString());
            h.assertTrue(ChroniclesBridge.balance(f.player) == 99_900 && f.pending(f.player) == 10_000
                    && f.data.ledger().history().size() == 1, "A duplicate actual C2S action charged or paid the same bet twice");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void staleMachineVersionsAndForgedResultsNeverAuthorizeAChangedBet(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); f.open(f.player); var oldQuote = f.amount(f.player, 100);
            f.open(f.owner); var settings = f.mutation(f.owner); settings.addProperty("name", "Golden Crown");
            settings.addProperty("minBet", 200); settings.addProperty("maxBet", 10_000); f.act(f.owner, "configure", settings);
            denied(h, f, f.player, "bet", oldQuote.toString());
            f.open(f.player); var forged = f.amount(f.player, 200); forged.addProperty("result", 100);
            denied(h, f, f.player, "bet", forged.toString());
            h.assertTrue(f.data.ledger().history().isEmpty(), "An old quote or client-chosen result created a played round");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void anOwnerCannotConfigureOddsPayoutMultipliersOrTheServerRandomSeed(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            f.open(f.owner);
            for (String key : java.util.List.of("rtp", "multiplier", "seed", "winner")) {
                var request = f.mutation(f.owner); request.addProperty("name", "Honest Crown"); request.addProperty("minBet", 100);
                request.addProperty("maxBet", 1_000); request.addProperty(key, 1);
                denied(h, f, f.owner, "configure", request.toString());
            }
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void onlyTheCurrentMachineOwnerMayFundWithdrawOrConfigureItsBank(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            f.open(f.player);
            denied(h, f, f.player, "deposit", f.amount(f.player, 100).toString());
            denied(h, f, f.player, "withdraw", f.amount(f.player, 100).toString());
            var settings = f.mutation(f.player); settings.addProperty("name", "Stolen machine"); settings.addProperty("minBet", 100); settings.addProperty("maxBet", 1_000);
            denied(h, f, f.player, "configure", settings.toString());
            f.player.server.getPlayerList().getOps().add(new ServerOpListEntry(f.player.getGameProfile(), 2, false));
            denied(h, f, f.player, "withdraw", f.amount(f.player, 100).toString());
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void withdrawingAvailableBankMovesExactlyThatAmountToTheOwnersPersonalClaim(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); long total = total(f); f.amount(f.owner, "withdraw", 25_000);
            h.assertTrue(f.machine().bank() == 475_000 && f.pending(f.owner) == 25_000 && ChroniclesBridge.balance(f.owner) == 1_500_000
                    && total(f) == total, "Withdrawing available funds did not transfer bank assets into the correct durable owner claim");
            f.amount(f.owner, "claim", 0);
            h.assertTrue(ChroniclesBridge.balance(f.owner) == 1_525_000 && f.pending(f.owner) == 0 && total(f) == total,
                    "The owner's bank withdrawal was not credited exactly once");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void aNearlyFullWalletReceivesOnlyItsCapacityAndKeepsTheExactPendingRemainder(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); f.open(f.player); bet(f, 100, 0, 0, 0);
            CasinoActors.profile(f.player, true, ChroniclesBridge.MAX_CENTS - 50);
            f.amount(f.player, "claim", 0);
            h.assertTrue(ChroniclesBridge.balance(f.player) == ChroniclesBridge.MAX_CENTS && f.pending(f.player) == 9_950,
                    "A capacity-limited actual wallet lost or duplicated the unpayable prize remainder");
            denied(h, f, f.player, "claim", f.amount(f.player, 0).toString());
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void claimsAreBoundToTheConnectedPlayerAndNeverAcceptAnotherOwnersUuid(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); f.open(f.player); bet(f, 100, 0, 0, 0);
            f.open(f.owner); var forged = f.amount(f.owner, 0); forged.addProperty("player", f.player.getUUID().toString());
            denied(h, f, f.owner, "claim", forged.toString());
            h.assertTrue(f.pending(f.player) == 10_000 && f.pending(f.owner) == 0, "A forged claim transferred another player's prize");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void fundedFurnitureCannotBeDismantledEvenByItsOwnerWithOperatorPrivileges(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            f.fund(500_000); f.owner.server.getPlayerList().getOps().add(new ServerOpListEntry(f.owner.getGameProfile(), 2, false));
            f.owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
            h.assertTrue(!CasinoService.canBreak(f.owner, f.pos) && !f.owner.gameMode.destroyBlock(f.pos.above())
                    && f.block.validStructure(h.getLevel(), f.pos) && f.machine().bank() == 500_000,
                    "A funded bank could be dismantled, orphaned or cloned using owner/operator privileges");
            h.getLevel().destroyBlock(f.pos.below(), false);
            h.assertTrue(f.block.validStructure(h.getLevel(), f.pos) && f.machine().bank() == 500_000,
                    "Removing a funded machine's floor erased the protected bank");
        }
        h.succeed();
    }
}
