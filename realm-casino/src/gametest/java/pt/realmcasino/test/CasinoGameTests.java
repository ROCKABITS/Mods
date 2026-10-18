package pt.realmcasino.test;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.compat.NotificationsBridge;

/** Production blocks, authoritative actions, actual wallet files, durable state, and optional companions. */
@GameTestHolder("realmcasino")
@PrefixGameTestTemplate(false)
public final class CasinoGameTests {
    private CasinoGameTests() {}
    private static void rejected(GameTestHelper helper, Runnable operation) {
        boolean rejected = false;
        try { operation.run(); }
        catch (IllegalArgumentException | IllegalStateException | SecurityException expected) { rejected = true; }
        helper.assertTrue(rejected, "An invalid or unauthorized production operation was accepted");
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void runtimeLoadsTheActualRequestedChroniclesAndNotificationsCompanions(GameTestHelper helper) {
        boolean integration = Boolean.getBoolean("casino.integration");
        for (String id : List.of("cronicasrp", "realmnotifications"))
            helper.assertTrue(ModList.get().isLoaded(id) == integration, "Unexpected runtime mode for " + id);
        if (integration) for (String id : List.of("origins", "jupiter", "contracts", "realmcouncil", "kingdomtreasury",
                "realmmarkets", "realmrewards", "realmidentity", "realmtitles"))
            helper.assertTrue(ModList.get().isLoaded(id), "Actual suite dependency was not loaded: " + id);
        System.out.println("CASINO_RUNTIME_MODE_PASSED integration=" + integration);
        helper.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void actualChroniclesWalletRequiresACreatedCharacterAndPreservesEveryOtherField(GameTestHelper helper) {
        try (var actors = new CasinoActors(helper)) {
            var player = actors.actor("WalletGuardQA", 100_000);
            if (!ChroniclesBridge.loaded()) {
                helper.assertTrue(!ChroniclesBridge.available(player), "Standalone mode unexpectedly has a virtual wallet");
                rejected(helper, () -> ChroniclesBridge.balance(player));
                rejected(helper, () -> ChroniclesBridge.setBalance(player, 100_000, 90_000));
            } else {
                Object before = CasinoActors.profile(player);
                ChroniclesBridge.setBalance(player, 100_000, 90_000);
                helper.assertTrue(ChroniclesBridge.balance(player) == 90_000, "Actual Chronicles wallet debit failed");
                CasinoActors.stableProfile(helper, before, player);
                rejected(helper, () -> ChroniclesBridge.setBalance(player, 100_000, 80_000));
                helper.assertTrue(ChroniclesBridge.balance(player) == 90_000, "A stale expected balance changed the wallet");
                CasinoActors.profile(player, false, 90_000);
                helper.assertTrue(!ChroniclesBridge.available(player), "An uncreated RP character could gamble");
                rejected(helper, () -> ChroniclesBridge.setBalance(player, 90_000, 80_000));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "casino_empty", timeoutTicks = 100)
    public static void actualNotificationApiSendsOnePrivatePayloadOrOneFallbackMessage(GameTestHelper helper) {
        try (var actors = new CasinoActors(helper)) {
            var player = actors.actor("NoticeQA", 100_000);
            player.clear(); NotificationsBridge.send(player, "notification.realmcasino.win", 2_500);
            if (ModList.get().isLoaded("realmnotifications")) helper.assertTrue(player.notificationPackets() == 1 && player.chat.isEmpty(),
                    "The actual optional notification API duplicated or omitted the private casino payload");
            else helper.assertTrue(player.notificationPackets() == 0 && player.chat.size() == 1,
                    "Standalone mode omitted or duplicated the private chat fallback");
        }
        helper.succeed();
    }
}
