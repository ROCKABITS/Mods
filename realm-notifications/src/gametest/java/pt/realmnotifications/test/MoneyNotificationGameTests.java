package pt.realmnotifications.test;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmnotifications.RealmNotificationsMod;
import pt.realmnotifications.test.NotificationsGameTests.Actor;

/** Real companion transaction hooks, real profiles and server advancements; no economic fixture implementation. */
@GameTestHolder("realmnotifications")
@PrefixGameTestTemplate(false)
public final class MoneyNotificationGameTests {
    private static boolean standalone(GameTestHelper helper) {
        if (Boolean.getBoolean("notifications.integration")) return false;
        helper.succeed();
        return true;
    }

    @SuppressWarnings("unchecked")
    private static AttachmentType<Object> attachment() {
        try {
            return (AttachmentType<Object>) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments")
                    .getField("PROFILE").get(null)).get();
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void profile(Actor player, long balance) {
        try {
            Object value = Class.forName("pt.cronicasrp.domain.CharacterProfile")
                    .getConstructor(boolean.class, String.class, int.class, String.class, String.class,
                            String.class, String.class, long.class, long.class, String.class)
                    .newInstance(true, "Aetherion", 28, "origins:human", "Private biography",
                            "Private signature", "Comum", 10_000L, balance, "");
            player.setData(attachment(), value);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static long balance(Actor player) {
        Object profile = player.getData(attachment());
        return ((Number) invoke(profile, "balance", new Class<?>[0])).longValue();
    }
    private static Object invoke(Object owner, String method, Class<?>[] types, Object... arguments) {
        try { return owner.getClass().getMethod(method, types).invoke(owner, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static Object call(String type, String method, Class<?>[] types, Object... arguments) {
        try { return Class.forName(type).getMethod(method, types).invoke(null, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static AdvancementHolder advancement(GameTestHelper helper, String id) {
        AdvancementHolder value = helper.getLevel().getServer().getAdvancements().get(ResourceLocation.parse(id));
        helper.assertTrue(value != null, "Actual Vanilla advancement missing: " + id);
        return value;
    }
    private static void award(Actor player, AdvancementHolder advancement) {
        for (String criterion : advancement.value().criteria().keySet())
            player.getAdvancements().award(advancement, criterion);
    }
    private static long pending(Actor player) {
        Object data = call("pt.realmrewards.data.RewardData", "get", new Class<?>[]{MinecraftServer.class}, player.getServer());
        try {
            Object ledger = data.getClass().getField("ledger").get(data);
            Object account = invoke(ledger, "account", new Class<?>[]{java.util.UUID.class}, player.getUUID());
            return ((Number) invoke(account, "pending", new Class<?>[0])).longValue();
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void actualAdvancementPaysWalletAndSendsOneNotificationWithoutChat(GameTestHelper helper) {
        if (standalone(helper)) return;
        Actor player = new Actor(helper, true);
        profile(player, 1000);
        player.reset();
        AdvancementHolder advancement = advancement(helper, "minecraft:story/root");
        award(player, advancement);
        helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "Real advancement tracker did not finish");
        helper.assertTrue(balance(player) == 1200 && pending(player) == 0, "Actual reward hook did not conserve its wallet payment");
        helper.assertTrue(player.shows().size() == 1 && player.chats().isEmpty(), "Actual reward duplicated into chat or sent more than one notice");
        var show = player.shows().getFirst();
        helper.assertTrue(show.kind().equals("reward") && show.body().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("message.realmrewards.earned") && contents.getArgs().length == 2,
                "Actual advancement notification lost its amount or precise reason");
        for (String criterion : advancement.value().criteria().keySet()) player.getAdvancements().revoke(advancement, criterion);
        award(player, advancement);
        helper.assertTrue(balance(player) == 1200 && player.shows().size() == 1, "Re-awarding an advancement repeated payment or its notice");
        RealmNotificationsMod.LOGGER.info("NOTIFICATIONS_MONEY_HOOKS_PASSED");
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void realAggregatePendingPaymentUsesAnHonestReason(GameTestHelper helper) {
        if (standalone(helper)) return;
        Actor player = new Actor(helper, true);
        award(player, advancement(helper, "minecraft:story/root"));
        helper.assertTrue(pending(player) == 200, "Missing character did not reserve its reward");
        profile(player, 1000);
        player.reset();
        award(player, advancement(helper, "minecraft:story/mine_stone"));
        helper.assertTrue(balance(player) == 1400 && pending(player) == 0, "Old and current real rewards were not paid exactly once");
        helper.assertTrue(player.shows().size() == 1 && player.chats().isEmpty(), "Aggregate payment produced duplicate notifications");
        var body = (TranslatableContents) player.shows().getFirst().body().getContents();
        helper.assertTrue(body.getKey().equals("message.realmrewards.pending_paid") && body.getArgs().length == 1,
                "Aggregate payment attributed old money to the newest advancement");
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void failedNotificationChannelKeepsTheRealRewardAndFallsBackToChat(GameTestHelper helper) {
        if (standalone(helper)) return;
        Actor player = new Actor(helper, true);
        profile(player, 1000);
        player.reset();
        player.failChannel = true;
        award(player, advancement(helper, "minecraft:story/root"));
        helper.assertTrue(balance(player) == 1200 && pending(player) == 0, "Notification failure changed the completed reward");
        helper.assertTrue(player.shows().isEmpty() && player.chats().size() == 1,
                "Failed optional channel lost the fallback message or duplicated it");
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void realAdministratorGrantConversionAndTreasuryDonationUseTheirActualHooks(GameTestHelper helper) {
        if (standalone(helper)) return;
        Actor player = new Actor(helper, true);
        profile(player, 1000);
        player.reset();
        var source = player.createCommandSourceStack().withPermission(2);
        var commands = helper.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(source, "rp admin give @s 1.23");
        helper.assertTrue(balance(player) == 1123 && player.shows().size() == 1 && player.chats().isEmpty(),
                "Real administrator grant did not produce exactly one private money receipt");
        helper.assertTrue(player.shows().getFirst().kind().equals("money"), "Administrator grant used the wrong notification kind");
        player.reset();
        commands.performPrefixedCommand(source, "rp withdraw 1.00");
        helper.assertTrue(balance(player) == 1023 && player.shows().size() == 1 && player.chats().isEmpty()
                        && player.shows().getFirst().kind().equals("info"),
                "Actual withdrawal was described as income or its feedback duplicated");
        commands.performPrefixedCommand(source, "rp deposit all");
        helper.assertTrue(balance(player) == 1123, "Real cash conversion lost money");
        player.reset();
        try {
            Class<? extends Enum> type = Class.forName("pt.crownledger.domain.Treasury$Currency").asSubclass(Enum.class);
            Object currency = Enum.valueOf(type, "CHRONICLES");
            call("pt.crownledger.service.CrownService", "donate", new Class<?>[]{ServerPlayer.class, long.class, type}, player, 123L, currency);
        } catch (ClassNotFoundException failure) { throw new IllegalStateException(failure); }
        helper.assertTrue(balance(player) == 1000 && player.shows().size() == 1 && player.chats().isEmpty(),
                "Real treasury donation receipt changed the amount or duplicated into chat");
        helper.assertTrue(player.shows().getFirst().kind().equals("treasury"), "Donation used the wrong notification kind");
        RealmNotificationsMod.LOGGER.info("NOTIFICATIONS_CHRONICLES_CROWN_HOOKS_PASSED");
        helper.succeed();
    }
}
