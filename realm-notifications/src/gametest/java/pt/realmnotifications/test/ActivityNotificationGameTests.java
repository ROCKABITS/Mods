package pt.realmnotifications.test;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmnotifications.test.NotificationsGameTests.Actor;
import pt.realmnotifications.RealmNotificationsMod;

/** Uses fresh companion production services, real inventories, escrow and the real RP attachment. */
@GameTestHolder("realmnotifications")
@PrefixGameTestTemplate(false)
public final class ActivityNotificationGameTests {
    private ActivityNotificationGameTests() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void character(ServerPlayer player, long balance) throws ReflectiveOperationException {
        var attachment = (AttachmentType) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments")
                .getField("PROFILE").get(null)).get();
        var type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
        Object profile = type.getConstructor(boolean.class, String.class, int.class, String.class,
                        String.class, String.class, String.class, long.class, long.class, String.class)
                .newInstance(true, "Realm Notices QA", 30, "Humano", "Native financial integration",
                        "QA", "Comum", balance, balance, "");
        player.setData(attachment, profile);
    }

    private static long balance(ServerPlayer player) throws ReflectiveOperationException {
        return (long) Class.forName("pt.contracts.compat.ChroniclesBridge")
                .getMethod("balance", ServerPlayer.class).invoke(null, player);
    }

    private static void notice(GameTestHelper helper, Actor actor, String kind) {
        helper.assertTrue(actor.shows().size() == 1 && actor.shows().getFirst().kind().equals(kind),
                "Expected exactly one " + kind + " Show after the committed transaction.");
        helper.assertTrue(actor.chats().isEmpty(), "A supported client received duplicate HUD and chat feedback.");
    }

    @GameTest(template = "notifications_empty", timeoutTicks = 100)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void actualContractEscrowAndClaimProduceDistinctNotifications(GameTestHelper helper) throws Exception {
        if (!Boolean.getBoolean("notifications.integration")) {
            RealmNotificationsMod.LOGGER.info("NOTIFICATIONS_ACTIVITY_INTEGRATION_NOT_REQUESTED");helper.succeed();return;
        }
        var service = Class.forName("pt.contracts.ContractsService");
        var dataType = Class.forName("pt.contracts.ContractsData");
        var data = dataType.getMethod("get", MinecraftServer.class).invoke(null, helper.getLevel().getServer());
        var ledgerField = dataType.getField("ledger");
        var original = ledgerField.get(data);
        var currencyType = Class.forName("pt.contracts.domain.Ledger$Currency");
        var currency = Enum.valueOf((Class<Enum>) currencyType, "CHRONICLES");
        Method publish = service.getMethod("publish", ServerPlayer.class, String.class, String.class, int.class, long.class);
        Method complete = service.getMethod("complete", ServerPlayer.class, UUID.class);
        Method claim = service.getMethod("claimMoney", ServerPlayer.class, currencyType);
        var author = new Actor(helper, true);
        var worker = new Actor(helper, true);
        var unsupported = new Actor(helper, false);
        character(author, 1_000);character(worker, 0);character(unsupported, 0);
        try {
            ledgerField.set(data, original.getClass().getConstructor().newInstance());
            author.reset();
            var contract = publish.invoke(null, author, "Repair the royal gate", "minecraft:oak_log", 3, 200L);
            var id = (UUID) contract.getClass().getMethod("id").invoke(contract);
            notice(helper, author, "contract");
            helper.assertTrue(balance(author) == 800, "Actual publication did not reserve the reward once.");

            worker.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));worker.reset();
            complete.invoke(null, worker, id);
            notice(helper, worker, "contract");
            var current = ledgerField.get(data);
            long retained = (long) current.getClass().getMethod("credits", UUID.class, currencyType).invoke(current, worker.getUUID(), currency);
            helper.assertTrue(retained == 200 && balance(worker) == 0 && worker.getInventory().countItem(Items.OAK_LOG) == 0,
                    "Completion must retain the real reward instead of pretending it was paid into the wallet.");

            worker.reset();long paid = (long) claim.invoke(null, worker, currency);
            notice(helper, worker, "money");
            helper.assertTrue(paid == 200 && balance(worker) == 200, "Claim did not pay the real wallet exactly once.");
            worker.reset();helper.assertTrue((long) claim.invoke(null, worker, currency) == 0 && worker.shows().isEmpty() && worker.chats().isEmpty(),
                    "An empty repeat claim sent a success notice or paid again.");

            var second = publish.invoke(null, author, "Another gate repair", "minecraft:oak_log", 1, 100L);
            var secondId = (UUID) second.getClass().getMethod("id").invoke(second);
            unsupported.getInventory().setItem(0, new ItemStack(Items.OAK_LOG));unsupported.reset();
            complete.invoke(null, unsupported, secondId);
            helper.assertTrue(unsupported.shows().isEmpty() && unsupported.chats().size() == 1 && balance(unsupported) == 0,
                    "An unsupported client must receive exactly one completion chat, with the reward still retained.");
            unsupported.reset();helper.assertTrue((long) claim.invoke(null, unsupported, currency) == 100,
                    "Unsupported clients must still collect the real reward.");
            helper.assertTrue(unsupported.shows().isEmpty() && unsupported.chats().size() == 1 && balance(unsupported) == 100,
                    "An unsupported client must receive exactly one claim chat without affecting payment semantics.");
            RealmNotificationsMod.LOGGER.info("NOTIFICATIONS_ACTUAL_CONTRACT_ESCROW_AND_CLAIM_PASSED");
            RealmNotificationsMod.LOGGER.info("NOTIFICATIONS_ACTIVITY_HOOKS_PASSED");
        } finally {
            ledgerField.set(data, original);
            ((net.minecraft.world.level.saveddata.SavedData) data).setDirty();
            helper.getLevel().getServer().overworld().getDataStorage().save();
        }
        helper.succeed();
    }
}
