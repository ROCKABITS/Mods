package pt.realmcasino.test;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmcasino.block.BlockRegistry;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.data.GameState;
import pt.realmcasino.domain.Blackjack;
import pt.realmcasino.service.CasinoService;

/** Actual dealt cards, table privacy, player actions, disconnects, timeouts and physical authorization. */
@GameTestHolder("realmcasino")
@PrefixGameTestTemplate(false)
public final class CasinoBlackjackGameTests {
    private CasinoBlackjackGameTests() {}
    private static boolean economy(GameTestHelper h, CasinoFixture f) {
        f.open(f.player);
        if (ChroniclesBridge.loaded()) return true;
        h.assertTrue(!f.view(f.player).get("economyAvailable").getAsBoolean(), "Standalone blackjack improperly exposed wagering"); return false;
    }
    private static long activeSeed(Predicate<Blackjack.State> extra) {
        for (long seed = 0; seed < 100_000; seed++) {
            var state = Blackjack.deal(new Random(seed)::nextInt);
            if (!state.finished() && extra.test(state)) return seed;
        }
        throw new AssertionError("Could not arrange a valid deterministic native blackjack hand");
    }
    private static Blackjack.State deal(CasinoFixture f, Predicate<Blackjack.State> extra) {
        long seed = activeSeed(extra); var expected = Blackjack.deal(new Random(seed)::nextInt);
        f.fund(10_000); f.open(f.player);
        try (var entropy = new CasinoFixture.Entropy(new Random(seed))) { f.amount(f.player, "bet", 100); }
        f.helper.assertTrue(f.data.states().containsKey(f.player.getUUID()) && !f.data.states().get(f.player.getUUID()).finished(),
                "A real authoritative blackjack deal did not create its expected active hand");
        return expected;
    }
    private static JsonObject cardAction(CasinoFixture f) {
        var request = f.mutation(f.player); var round = f.view(f.player).getAsJsonObject("round");
        request.addProperty("round", round.get("id").getAsString()); request.addProperty("revision", round.get("revision").getAsLong()); return request;
    }
    private static void rejected(GameTestHelper h, Runnable operation) {
        boolean rejected = false; try { operation.run(); } catch (RuntimeException expected) { rejected = true; }
        h.assertTrue(rejected, "An expired or physically invalid server session was accepted");
    }
    @SuppressWarnings("unchecked") private static void expire(CasinoActors.Actor actor, String name, long value) {
        try {
            Field field = CasinoService.class.getDeclaredField("SESSIONS"); field.setAccessible(true);
            var sessions = (Map<UUID, Object>) field.get(null); Object old = sessions.get(actor.getUUID()); var components = old.getClass().getRecordComponents();
            Class<?>[] types = new Class<?>[components.length]; Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType(); var accessor = components[i].getAccessor(); accessor.setAccessible(true);
                values[i] = components[i].getName().equals(name) ? value : accessor.invoke(old);
            }
            var constructor = old.getClass().getDeclaredConstructor(types); constructor.setAccessible(true); sessions.put(actor.getUUID(), constructor.newInstance(values));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void aRealBlackjackDealReservesTheMaximumAndNeverSendsItsHiddenCardOrShoe(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.BLACKJACK_TABLE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            var expected = deal(f, state -> true); var view = f.view(f.player); var round = view.getAsJsonObject("round");
            h.assertTrue(ChroniclesBridge.balance(f.player) == 99_900 && f.machine().bank() == 10_100 && f.machine().reserved() == 250
                    && f.pending(f.player) == 0, "A live blackjack hand did not reserve its entire 2.5x liability");
            h.assertTrue(round.getAsJsonArray("dealerCards").size() == 2 && round.getAsJsonArray("dealerCards").get(1).getAsInt() == -1
                    && round.getAsJsonArray("dealerCards").get(0).getAsInt() == expected.dealer().getFirst()
                    && !view.toString().contains("\"deck\"") && !view.toString().contains("\"cursor\""),
                    "The public real S2C snapshot leaked the dealer hole card or future authoritative shoe");
            f.open(f.owner); var owner = f.view(f.owner);
            h.assertTrue(owner.get("busy").getAsBoolean() && owner.get("round").isJsonNull(), "The table owner could inspect another player's private blackjack hand");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void realHitUsesThePersistedNextCardAndStaleHandRevisionsCannotActAgain(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.BLACKJACK_TABLE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            var initial = deal(f, state -> !state.hit().finished()); var expected = initial.hit(); var request = cardAction(f);
            f.act(f.player, "hit", request); var after = f.data.states().get(f.player.getUUID());
            h.assertTrue(after.blackjack().equals(expected) && after.revision() == 2 && ChroniclesBridge.balance(f.player) == 99_900,
                    "The actual server hit did not draw the original next card or charged another stake");
            var stale = f.mutation(f.player); stale.addProperty("round", after.id().toString()); stale.addProperty("revision", 1);
            f.act(f.player, "hit", stale);
            h.assertTrue(f.data.states().get(f.player.getUUID()).equals(after) && !f.player.snapshot().get("error").getAsString().isEmpty(),
                    "An old blackjack revision drew another private card");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void closingThePhysicalBlackjackScreenAutoStandsTheExistingCardsExactlyOnce(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.BLACKJACK_TABLE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            var expected = deal(f, state -> true).stand(); CasinoService.action(f.player, "close", "{}");
            var settled = f.data.states().get(f.player.getUUID()); long pending = f.pending(f.player);
            h.assertTrue(settled.finished() && settled.blackjack().equals(expected) && pending == expected.payout(100)
                    && f.machine().reserved() == 0 && f.data.ledger().history().size() == 1, "Closing a blackjack menu refunded, rerolled or lost the original dealt hand");
            CasinoService.action(f.player, "close", "{}"); CasinoService.logout(f.player);
            h.assertTrue(f.pending(f.player) == pending && f.data.ledger().history().size() == 1, "Repeated close/disconnect settled the same blackjack hand twice");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void disconnectSettlesTheOriginalHandIntoOfflineClaimsAndReopeningNeverRedeals(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.BLACKJACK_TABLE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            var expected = deal(f, state -> state.stand().payout(100) > 0).stand();
            f.actors.online.remove(f.player); f.actors.byId.remove(f.player.getUUID()); CasinoService.logout(f.player);
            h.assertTrue(f.pending(f.player) == expected.payout(100) && f.machine().reserved() == 0 && ChroniclesBridge.balance(f.player) == 99_900,
                    "An offline hand paid into an unavailable wallet, lost its credit or escaped its original cards");
            var id = f.data.states().get(f.player.getUUID()).id(); f.actors.online.add(f.player); f.actors.byId.put(f.player.getUUID(), f.player);
            f.open(f.player);
            h.assertTrue(f.data.states().get(f.player.getUUID()).id().equals(id) && f.view(f.player).getAsJsonObject("round").get("phase").getAsString().equals("settled"),
                    "Reopening after disconnect rerolled or charged an already settled blackjack hand");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void anExpiredPersistedHandIsAutoStoodByActualServerTickProcessing(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.BLACKJACK_TABLE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            var expected = deal(f, state -> true).stand(); var original = f.data.states().get(f.player.getUUID());
            long started = System.currentTimeMillis() - 61_000;
            var expired = GameState.blackjack(original.id(), original.machine(), original.player(), original.stake(), started, original.blackjack());
            var ledger = f.data.ledger(); var oldRound = ledger.rounds().get(original.id());
            var agedRound = new pt.realmcasino.domain.CasinoLedger.Round(oldRound.id(), oldRound.machineId(), oldRound.player(), oldRound.stake(), oldRound.maxPayout(), started);
            var agedLedger = new pt.realmcasino.domain.CasinoLedger(ledger.machines().values(), java.util.List.of(agedRound), ledger.credits(), ledger.history());
            f.data.replace(agedLedger, Map.of(f.player.getUUID(), expired));
            for (int i = 0; i < 40; i++) CasinoService.tick(h.getLevel().getServer());
            h.assertTrue(f.data.states().get(f.player.getUUID()).blackjack().equals(expected) && f.pending(f.player) == expected.payout(100)
                    && f.machine().reserved() == 0 && f.data.ledger().history().size() == 1,
                    "A persisted expired hand failed to stand the original cards once on the actual production tick path");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void ownerWithdrawalCannotConsumeAPlayersStillReservedBlackjackPrize(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.BLACKJACK_TABLE.get())) {
            if (!economy(h, f)) { h.succeed(); return; }
            deal(f, state -> true); f.open(f.owner); var before = f.machine();
            f.amount(f.owner, "withdraw", before.available() + 1);
            h.assertTrue(f.machine().equals(before) && f.pending(f.owner) == 0 && !f.owner.snapshot().get("error").getAsString().isEmpty(),
                    "An owner withdrew part of the guaranteed prize while another player's blackjack hand was active");
            f.amount(f.owner, "withdraw", before.available());
            h.assertTrue(f.machine().bank() == 250 && f.machine().reserved() == 250 && f.pending(f.owner) == before.available(),
                    "Withdrawing the exact available amount failed to preserve the entire live blackjack liability");
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void physicalSessionsRecheckRangeDimensionAndBothIndependentExpiryClocks(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            f.open(f.player); f.player.setPos(f.pos.getX() + 20, f.pos.getY(), f.pos.getZ()); rejected(h, () -> CasinoService.requireSession(f.player));
            f.open(f.player); var nether = h.getLevel().getServer().getLevel(Level.NETHER); h.assertTrue(nether != null, "The native server has no second dimension");
            f.player.setServerLevel(nether); rejected(h, () -> CasinoService.requireSession(f.player)); f.player.setServerLevel(h.getLevel());
            f.open(f.player); expire(f.player, "expires", System.currentTimeMillis()); rejected(h, () -> CasinoService.requireSession(f.player));
            f.open(f.player); expire(f.player, "tick", 0); rejected(h, () -> CasinoService.requireSession(f.player));
            f.open(f.player); h.getLevel().removeBlock(f.pos.above(), false); rejected(h, () -> CasinoService.requireSession(f.player));
            System.out.println("CASINO_NATIVE_PASSED integration=" + ChroniclesBridge.loaded());
        }
        h.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void malformedFractionalDuplicateAndRemoteNetworkActionsCannotDebitOrCreateRounds(GameTestHelper h) {
        try (var f = new CasinoFixture(h, BlockRegistry.SLOT_MACHINE.get())) {
            if (ChroniclesBridge.loaded()) f.fund(10_000);
            f.open(f.player); var before = f.data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
            long balance = ChroniclesBridge.loaded() ? ChroniclesBridge.balance(f.player) : 0;
            var quote = f.mutation(f.player); String prefix = "{\"nonce\":\"" + quote.get("nonce").getAsString() + "\",\"version\":" + quote.get("version").getAsLong() + ",";
            for (String json : java.util.List.of("{", "[]", "null", "x".repeat(2049), prefix + "\"amount\":1.5}",
                    prefix + "\"amount\":1e2}", prefix + "\"amount\":\"100\"}", prefix + "\"amount\":true}",
                    prefix + "\"amount\":100,\"amount\":200}", prefix + "\"amount\":100,\"owner\":\"" + f.owner.getUUID() + "\"}",
                    prefix + "\"amount\":100} trailing")) {
                CasinoService.action(f.player, "bet", json);
                h.assertTrue(before.equals(f.data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess()))
                        && (!ChroniclesBridge.loaded() || ChroniclesBridge.balance(f.player) == balance),
                        "A malformed, fractional, duplicate, nested or forged actual network payload mutated money");
            }
            var remote = f.amount(f.player, 100); CasinoService.action(f.player, "close", "{}");
            CasinoService.action(f.player, "bet", remote.toString());
            h.assertTrue(before.equals(f.data.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess()))
                    && f.player.snapshot().get("closed").getAsBoolean(), "An out-of-session network action opened or played a remote casino");
        }
        h.succeed();
    }
}
