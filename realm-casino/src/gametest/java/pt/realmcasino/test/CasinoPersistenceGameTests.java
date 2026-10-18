package pt.realmcasino.test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmcasino.block.BlockRegistry;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.data.CasinoData;
import pt.realmcasino.data.GameState;
import pt.realmcasino.domain.Blackjack;
import pt.realmcasino.domain.CasinoLedger;
import pt.realmcasino.service.WalletJournal;

/** Crash boundaries are arranged through real NBT/player files and test-only private-path injection. */
@GameTestHolder("realmcasino")
@PrefixGameTestTemplate(false)
public final class CasinoPersistenceGameTests {
    private CasinoPersistenceGameTests() {}
    private static void rejected(GameTestHelper helper, Runnable operation) {
        boolean rejected = false; try { operation.run(); } catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected, "A corrupt persisted state or ambiguous transfer was accepted");
    }
    private static CasinoData reload(CasinoFixture f) {
        try {
            CasinoData restored = CasinoData.load(NbtIo.readCompressed(f.data.path(), NbtAccounter.create(64L * 1024 * 1024)), f.helper.getLevel().registryAccess());
            CasinoFixture.field(restored, "file", f.data.path()); f.loaded.put(f.helper.getLevel().getServer(), restored); return restored;
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static CasinoData.Journal intent(CasinoFixture f) {
        return new CasinoData.Journal(UUID.randomUUID(), f.owner.getUUID(), 2_000_000, 1_999_000, "deposit",
                f.data.ledger().deposit(f.machine().id(), f.owner.getUUID(), 1_000), f.data.states());
    }
    private static CompoundTag receipt(CasinoData.Journal intent) {
        var tag = new CompoundTag(); tag.putUUID("id", intent.id()); tag.putUUID("player", intent.player());
        tag.putLong("before", intent.before()); tag.putLong("after", intent.after()); return tag;
    }
    private static boolean integration(GameTestHelper h, CasinoFixture f) {
        if (ChroniclesBridge.loaded()) return true;
        h.assertTrue(!ChroniclesBridge.available(f.player), "Absent Chronicles unexpectedly provided a recoverable wallet"); return false;
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void nativeNbtRoundTripPreservesAnActiveBlackjackShoeReservationAndOwnerClaim(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.BLACKJACK_TABLE.get())) {
            var machine = f.machine(); long now = System.currentTimeMillis();
            Blackjack.State hand = null;
            for (int seed = 0; seed < 100 && (hand == null || hand.finished()); seed++) hand = Blackjack.deal(new Random(seed)::nextInt);
            h.assertTrue(hand != null && !hand.finished(), "No active deterministic native hand could be arranged");
            var ledger = f.data.ledger().deposit(machine.id(), f.owner.getUUID(), 10_000).withdraw(machine.id(), f.owner.getUUID(), 500);
            UUID round = UUID.randomUUID(); ledger = ledger.startBet(machine.id(), f.player.getUUID(), 100, round, ledger.machine(machine.id()).orElseThrow().version(), now);
            var state = GameState.blackjack(round, machine.id(), f.player.getUUID(), 100, now, hand);
            f.data.replace(ledger, Map.of(f.player.getUUID(), state)); var restored = reload(f);
            h.assertTrue(restored.ledger().machines().equals(ledger.machines()) && restored.ledger().rounds().equals(ledger.rounds())
                    && restored.ledger().credit(f.owner.getUUID()) == 500 && restored.states().equals(f.data.states()),
                    "An active shoe, exact reservation or offline owner claim changed across the actual native world file");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void malformedNativeSchemasDuplicateMachinesAndNegativeBanksNeverResetTheLedger(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            CompoundTag valid = f.data.save(new CompoundTag(), h.getLevel().registryAccess());
            for (String mutation : List.of("version", "missing", "unknown", "duplicate", "negative", "wrong_type", "reservation")) {
                CompoundTag tag = valid.copy(); var row = tag.getCompound("ledger").getList("machines", Tag.TAG_COMPOUND).getCompound(0);
                switch (mutation) {
                    case "version" -> tag.putInt("version", 99);
                    case "missing" -> tag.remove("states");
                    case "unknown" -> tag.putString("invented", "field");
                    case "duplicate" -> tag.getCompound("ledger").getList("machines", Tag.TAG_COMPOUND).add(row.copy());
                    case "negative" -> row.putLong("bank", -1);
                    case "wrong_type" -> row.putInt("bank", 0);
                    case "reservation" -> { row.putLong("bank", 100); row.putLong("reserved", 100); }
                    default -> throw new AssertionError();
                }
                rejected(h, () -> CasinoData.load(tag, h.getLevel().registryAccess()));
            }
            h.assertTrue(CasinoData.load(valid, h.getLevel().registryAccess()).ledger().machines().equals(f.data.ledger().machines()),
                    "Rejecting corrupt data damaged the valid source ledger");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void anActualAtomicMoveFailureFreezesAndKeepsThePreviousBankWithoutPretendingSuccess(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            var before = f.data.ledger(); var original = f.data.path(); CasinoFixture.field(f.data, "file", f.directory);
            rejected(h, () -> f.data.replace(before.deposit(f.machine().id(), f.owner.getUUID(), 1_000), Map.of()));
            h.assertTrue(f.data.frozen() && f.data.ledger() == before && java.nio.file.Files.isRegularFile(original),
                    "An actual failed atomic file replacement changed the live bank or hid its storage failure");
            rejected(h, () -> f.data.replace(before, Map.of()));
            CasinoFixture.field(f.data, "file", original);
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void aJournalWhoseCandidateCreatesMoreAssetsThanTheWalletDebitIsRejected(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            var before = f.data.ledger();
            var counterfeit = new CasinoData.Journal(UUID.randomUUID(), f.player.getUUID(), 100_000, 99_000, "deposit",
                    before.deposit(f.machine().id(), f.owner.getUUID(), 2_000), Map.of());
            rejected(h, () -> f.data.prepare(counterfeit));
            h.assertTrue(f.data.journal() == null && f.data.ledger() == before, "An inconsistent journal became a payable persistent money intent");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void persistedJournalSchemaAndMoneyDeltaAreValidatedBeforeAnyRecovery(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            f.data.prepare(intent(f));
            CompoundTag valid = f.data.save(new CompoundTag(), h.getLevel().registryAccess());
            for (String mutation : List.of("delta", "sign", "kind", "unknown", "missing", "type", "player")) {
                CompoundTag tag = valid.copy(), journal = tag.getCompound("journal");
                switch (mutation) {
                    case "delta" -> journal.putLong("after", 1_998_000);
                    case "sign" -> journal.putString("kind", "claim");
                    case "kind" -> journal.putString("kind", "refund");
                    case "unknown" -> journal.putLong("payout", 1_000);
                    case "missing" -> journal.remove("states");
                    case "type" -> journal.putInt("before", 2_000_000);
                    case "player" -> journal.putString("player", UUID.randomUUID().toString());
                    default -> throw new AssertionError();
                }
                rejected(h, () -> CasinoData.load(tag, h.getLevel().registryAccess()));
            }
            h.assertTrue(CasinoData.load(valid, h.getLevel().registryAccess()).journal().id().equals(f.data.journal().id()),
                    "Rejecting malformed persisted intents modified or discarded the authentic pending transfer");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void aMatchingReceiptPreservesALaterLegitimateWalletChangeAndCompletesOnlyOnce(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!integration(h, f)) { h.succeed(); return; }
            var intent = intent(f); f.data.prepare(intent);
            f.owner.getPersistentData().put(WalletJournal.RECEIPT_KEY, receipt(intent));
            ChroniclesBridge.setBalance(f.owner, intent.before(), intent.after() - 500);
            CasinoActors.save(f.owner);
            h.assertTrue(WalletJournal.savedReceipt(f.owner, intent), "A genuine receipt disappeared after a later legitimate Chronicles wallet save");
            WalletJournal.recover(f.owner); WalletJournal.recover(f.owner);
            h.assertTrue(!f.data.frozen() && f.data.journal() == null && f.machine().bank() == 1_000
                    && ChroniclesBridge.balance(f.owner) == intent.after() - 500,
                    "Receipt recovery replayed an already applied debit, discarded a later legitimate wallet change, or failed to commit exactly once");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void actualPlayerFileVerificationRejectsForgedReceiptsAndDifferentSavedBalances(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!integration(h, f)) { h.succeed(); return; }
            var intent = intent(f);
            ChroniclesBridge.setBalance(f.owner, intent.before(), intent.after());
            f.owner.getPersistentData().put(WalletJournal.RECEIPT_KEY, receipt(intent)); CasinoActors.save(f.owner);
            h.assertTrue(WalletJournal.savedReceipt(f.owner, intent), "A genuine saved receipt was rejected before adversarial file mutations");
            var file = f.owner.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(f.owner.getUUID() + ".dat");
            try {
                CompoundTag valid = NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024));
                for (String mutation : List.of("owner", "extra", "balance", "foreign", "nested")) {
                    CompoundTag tag = valid.copy();
                    String container = tag.get("NeoForgeData") instanceof CompoundTag ? "NeoForgeData" : "ForgeData";
                    CompoundTag saved = tag.getCompound(container).getCompound(WalletJournal.RECEIPT_KEY);
                    switch (mutation) {
                        case "owner" -> tag.putUUID("UUID", UUID.randomUUID());
                        case "extra" -> saved.putLong("bonus", 1_000);
                        case "balance" -> tag.getCompound("neoforge:attachments").getCompound("cronicasrp:profile").putLong("balance", intent.after() + 1);
                        case "foreign" -> saved.putUUID("player", UUID.randomUUID());
                        case "nested" -> {
                            tag.getCompound(container).remove(WalletJournal.RECEIPT_KEY);
                            var item = new CompoundTag(); item.put(WalletJournal.RECEIPT_KEY, receipt(intent)); tag.put("SelectedItem", item);
                        }
                        default -> throw new AssertionError();
                    }
                    NbtIo.writeCompressed(tag, file);
                    h.assertTrue(!WalletJournal.savedReceipt(f.owner, intent), "Player file verification accepted mutation: " + mutation);
                }
                NbtIo.writeCompressed(valid, file);
                h.assertTrue(WalletJournal.savedReceipt(f.owner, intent), "Restoring the authentic file did not restore receipt verification");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void anUnreceiptedPersistedIntentResumesFromTheExactBeforeBalanceOnce(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!integration(h, f)) { h.succeed(); return; }
            var intent = intent(f); f.data.prepare(intent); var restored = reload(f);
            h.assertTrue(restored.journal().id().equals(intent.id()) && ChroniclesBridge.balance(f.owner) == 2_000_000,
                    "An intent could not be reloaded before the wallet file was changed");
            WalletJournal.recover(f.owner); WalletJournal.recover(f.owner);
            h.assertTrue(ChroniclesBridge.balance(f.owner) == 1_999_000 && restored.ledger().machine(f.machine().id()).orElseThrow().bank() == 1_000
                    && restored.journal() == null && WalletJournal.savedReceipt(f.owner, intent), "An unreceipted crash recovery debited twice, lost bank assets, or skipped player-file verification");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void aMatchingDurableWalletReceiptCompletesTheLedgerWithoutDebitingAgain(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!integration(h, f)) { h.succeed(); return; }
            var intent = intent(f); f.data.prepare(intent);
            ChroniclesBridge.setBalance(f.owner, 2_000_000, 1_999_000); f.owner.getPersistentData().put(WalletJournal.RECEIPT_KEY, receipt(intent));
            CasinoActors.save(f.owner);
            h.assertTrue(WalletJournal.savedReceipt(f.owner, intent), "The arranged actual player file does not contain the matching durable receipt");
            var restored = reload(f); WalletJournal.recover(f.owner); WalletJournal.recover(f.owner);
            h.assertTrue(ChroniclesBridge.balance(f.owner) == 1_999_000 && restored.ledger().machine(f.machine().id()).orElseThrow().bank() == 1_000
                    && restored.journal() == null, "A receipted crash recovery charged the stake twice or failed to finish its original bank commit");
            System.out.println("CASINO_RECOVERY_PASSED");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void anAmbiguousWalletBalanceFreezesWithoutBlindlyReplayingOrDiscardingTheIntent(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!integration(h, f)) { h.succeed(); return; }
            var intent = intent(f); f.data.prepare(intent); ChroniclesBridge.setBalance(f.owner, 2_000_000, 1_999_500);
            var restored = reload(f); rejected(h, () -> WalletJournal.recover(f.owner));
            h.assertTrue(restored.frozen() && restored.journal().id().equals(intent.id()) && ChroniclesBridge.balance(f.owner) == 1_999_500
                    && restored.ledger().machine(f.machine().id()).orElseThrow().bank() == 0, "Ambiguous recovery invented a payment, discarded evidence or failed to freeze financial writes");
            rejected(h, () -> restored.replace(restored.ledger(), restored.states()));
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void aDifferentReceiptIdCannotAuthorizeAnAlreadyChangedWallet(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!integration(h, f)) { h.succeed(); return; }
            var intent = intent(f); f.data.prepare(intent);
            CompoundTag foreign = receipt(intent); foreign.putUUID("id", UUID.randomUUID());
            f.owner.getPersistentData().put(WalletJournal.RECEIPT_KEY, foreign);
            ChroniclesBridge.setBalance(f.owner, intent.before(), intent.after()); CasinoActors.save(f.owner);
            h.assertTrue(!WalletJournal.savedReceipt(f.owner, intent), "A receipt from another transfer authorized this pending intent");
            rejected(h, () -> WalletJournal.recover(f.owner));
            h.assertTrue(f.data.frozen() && f.data.journal().id().equals(intent.id()) && f.machine().bank() == 0
                    && ChroniclesBridge.balance(f.owner) == intent.after(),
                    "A mismatched receipt was accepted, charged an ambiguous wallet again, or discarded its pending intent");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void aFailureAfterTheWalletReceiptRecoversWithoutASecondDebit(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (!integration(h, f)) { h.succeed(); return; }
            var intent = intent(f); f.data.prepare(intent); var original = f.data.path(); CasinoFixture.field(f.data, "file", f.directory);
            rejected(h, () -> WalletJournal.recover(f.owner));
            h.assertTrue(f.data.frozen() && f.data.journal() != null && ChroniclesBridge.balance(f.owner) == 1_999_000
                    && WalletJournal.savedReceipt(f.owner, intent), "The arranged crash did not occur after a durable wallet receipt and before the ledger commit");
            CasinoFixture.field(f.data, "file", original); WalletJournal.recover(f.owner); WalletJournal.recover(f.owner);
            h.assertTrue(!f.data.frozen() && f.data.journal() == null && ChroniclesBridge.balance(f.owner) == 1_999_000 && f.machine().bank() == 1_000,
                    "Retrying after the actual ledger-write fault charged a receipted wallet again or lost the guaranteed bank credit");
        }
        h.succeed();
    }
}
