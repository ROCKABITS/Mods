package pt.cronicasrp.test;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.cronicasrp.*;
import pt.cronicasrp.domain.*;
import pt.cronicasrp.compat.NotificationsBridge;
import pt.cronicasrp.network.CashPackets;

@GameTestHolder(CronicasRP.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CashGameTests {
    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CurrencyQA"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        player.setData(ModAttachments.PROFILE, new CharacterProfile(true, "Ari", 128, "Elfo", "", "Ari", "Comum", 11_000, 1_000_000,
                "", RpProgress.EMPTY.withOccupation("Estudioso").withReputation(64)));
        return player;
    }
    private static CashPackets.Request request(int action, long amount) { return new CashPackets.Request(action, amount, "", 0); }
    private static void fail(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException ex) { rejected = true; }
        helper.assertTrue(rejected, "A operação inválida devia ser rejeitada.");
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void allFifteenRegisteredItemsRoundtrip(GameTestHelper helper) {
        var player = player(helper); var before = player.getData(ModAttachments.PROFILE);
        long sum = 0;
        for (var denomination : CurrencyDenomination.values()) {
            ItemStack stack = CurrencyItems.stack(denomination, 1);
            helper.assertTrue(stack.getMaxStackSize() == 64 && CurrencyItems.denomination(stack) == denomination, "Item ou pilha inválida.");
            CashService.perform(player, new CashPackets.Request(CashPackets.WITHDRAW_PIECES, 0, denomination.id(), 1)); sum += denomination.cents();
            helper.assertTrue(CashExchange.count(CashService.snapshot(player), denomination) == 1, "A denominação não foi entregue.");
        }
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == before.balance() - sum, "O levantamento descontou um valor incorreto.");
        helper.assertTrue(CashExchange.total(CashService.snapshot(player)) == sum, "O dinheiro físico não tem o valor debitado.");
        CashService.perform(player, request(CashPackets.DEPOSIT_ALL, 0));
        helper.assertTrue(before.equals(player.getData(ModAttachments.PROFILE)), "O depósito não recuperou o saldo original ou alterou os dados de RP.");
        helper.assertTrue(CashExchange.total(CashService.snapshot(player)) == 0, "O dinheiro físico não foi consumido.");
        helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void physicalChangeAndRepeatedDeposit(GameTestHelper helper) {
        var player = player(helper);
        player.getInventory().setItem(0, CurrencyItems.stack(CurrencyDenomination.NOTE_20, 1));
        long before = player.getData(ModAttachments.PROFILE).balance();
        CashService.perform(player, request(CashPackets.DEPOSIT, 1250));
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == before + 1250, "Depósito digital incorreto.");
        helper.assertTrue(CashExchange.total(CashService.snapshot(player)) == 750, "Troco físico incorreto.");
        CashService.perform(player, request(CashPackets.DEPOSIT_ALL, 0));
        fail(helper, () -> CashService.perform(player, request(CashPackets.DEPOSIT_ALL, 0)));
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == before + 2000, "Foi criado dinheiro num depósito repetido."); helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void fullInventoryAndMissingChangeAreAtomic(GameTestHelper helper) {
        var player = player(helper);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        player.getInventory().setItem(0, CurrencyItems.stack(CurrencyDenomination.NOTE_20, 64));
        var inventory = List.copyOf(CashService.snapshot(player)); var profile = player.getData(ModAttachments.PROFILE);
        fail(helper, () -> CashService.perform(player, request(CashPackets.WITHDRAW, 1250)));
        fail(helper, () -> CashService.perform(player, request(CashPackets.DEPOSIT, 1250)));
        helper.assertTrue(inventory.equals(CashService.snapshot(player)) && profile.equals(player.getData(ModAttachments.PROFILE)), "Uma operação rejeitada alterou o inventário ou saldo.");
        CashService.perform(player, request(CashPackets.DEPOSIT, 2000));
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 63, "Um depósito exato com inventário cheio não consumiu a nota."); helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void renamedPaperCannotBeDeposited(GameTestHelper helper) {
        var player = player(helper); var paper = new ItemStack(Items.PAPER, 64);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal("Nota de 500 euros")); player.getInventory().setItem(0, paper);
        var profile = player.getData(ModAttachments.PROFILE);
        fail(helper, () -> CashService.perform(player, request(CashPackets.DEPOSIT_ALL, 0)));
        helper.assertTrue(profile.equals(player.getData(ModAttachments.PROFILE)) && player.getInventory().getItem(0).getCount() == 64, "Papel com nome foi aceite como dinheiro."); helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void handDepositsConsumeTheRealStack(GameTestHelper helper) {
        var player = player(helper); long before = player.getData(ModAttachments.PROFILE).balance();
        player.setItemInHand(InteractionHand.OFF_HAND, CurrencyItems.stack(CurrencyDenomination.EURO_2, 3));
        player.gameMode.useItem(player, helper.getLevel(), player.getOffhandItem(), InteractionHand.OFF_HAND);
        helper.assertTrue(player.getOffhandItem().getCount() == 2 && player.getData(ModAttachments.PROFILE).balance() == before + 200, "O botão direito não depositou uma unidade.");
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, helper.getLevel(), player.getOffhandItem(), InteractionHand.OFF_HAND);
        helper.assertTrue(player.getOffhandItem().isEmpty() && player.getData(ModAttachments.PROFILE).balance() == before + 600, "Shift + botão direito não consumiu a pilha.");
        player.setShiftKeyDown(false); player.setGameMode(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, CurrencyItems.stack(CurrencyDenomination.NOTE_5, 2));
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && player.getData(ModAttachments.PROFILE).balance() == before + 1100, "A conversão em criativo não conservou o valor."); helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void malformedRequestsAndWalletLimitCannotMintCash(GameTestHelper helper) {
        var player = player(helper); var before = player.getData(ModAttachments.PROFILE);
        fail(helper, () -> CashService.perform(player, request(42, 1)));
        fail(helper, () -> CashService.perform(player, request(CashPackets.WITHDRAW, -1)));
        fail(helper, () -> CashService.perform(player, request(CashPackets.WITHDRAW, 0)));
        fail(helper, () -> CashService.perform(player, new CashPackets.Request(CashPackets.WITHDRAW_PIECES, 0, "paper", 1)));
        fail(helper, () -> CashService.perform(player, new CashPackets.Request(CashPackets.WITHDRAW_PIECES, 0, "1c", Integer.MAX_VALUE)));
        helper.assertTrue(before.equals(player.getData(ModAttachments.PROFILE)) && CashExchange.total(CashService.snapshot(player)) == 0, "Um pedido inválido criou dinheiro.");
        player.setData(ModAttachments.PROFILE, before.withBalance(Money.MAX_CENTS));
        player.getInventory().setItem(0, CurrencyItems.stack(CurrencyDenomination.CENT_1, 1));
        fail(helper, () -> CashService.perform(player, request(CashPackets.DEPOSIT_ALL, 0)));
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == Money.MAX_CENTS && player.getInventory().getItem(0).getCount() == 1, "Um depósito no limite perdeu ou duplicou dinheiro."); helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void portugueseAndDotDecimalsWorkThroughActualCommands(GameTestHelper helper) {
        var player = player(helper); var server = helper.getLevel().getServer(); var source = player.createCommandSourceStack();
        long before = player.getData(ModAttachments.PROFILE).balance();
        server.getCommands().performPrefixedCommand(source, "rp levantar 12,50");
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == before - 1250, "O comando não interpretou vírgula decimal.");
        server.getCommands().performPrefixedCommand(source, "rp depositar 0.01");
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == before - 1249, "O comando não interpretou ponto decimal.");
        server.getCommands().performPrefixedCommand(source, "rp levantar 0,001");
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == before - 1249, "O comando aceitou uma fração de cêntimo.");
        server.getCommands().performPrefixedCommand(source, "rp depositar tudo");
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == before && CashExchange.total(CashService.snapshot(player)) == 0, "O comando de depósito total não conservou o dinheiro."); helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void englishCommandsConserveMoneyAndKeepAdminPermissions(GameTestHelper helper) {
        var player = player(helper); var commands = helper.getLevel().getServer().getCommands();
        var source = player.createCommandSourceStack().withPermission(0);
        var original = player.getData(ModAttachments.PROFILE);
        commands.performPrefixedCommand(source, "rp withdraw 12.50");
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == original.balance() - 1250, "English withdrawal failed.");
        commands.performPrefixedCommand(source, "rp deposit 0.01");
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance() == original.balance() - 1249, "English decimal deposit failed.");
        commands.performPrefixedCommand(source, "rp withdrawpieces 1c 3");
        commands.performPrefixedCommand(source, "rp depositpieces 1c 3");
        commands.performPrefixedCommand(source, "rp deposit all");
        helper.assertTrue(original.equals(player.getData(ModAttachments.PROFILE)) && CashExchange.total(CashService.snapshot(player)) == 0, "English commands changed RP fields or money.");
        commands.performPrefixedCommand(source, "rp admin give @s 1.23");
        helper.assertTrue(original.equals(player.getData(ModAttachments.PROFILE)), "English alias bypassed operator permissions.");
        var operator = source.withPermission(2);
        commands.performPrefixedCommand(operator, "rp admin give @s 1.23");
        commands.performPrefixedCommand(operator, "rp admin reputation @s 88");
        commands.performPrefixedCommand(operator, "rp admin mission @s 3");
        commands.performPrefixedCommand(operator, "rp admin medal @s silver");
        var result = player.getData(ModAttachments.PROFILE);
        helper.assertTrue(result.balance() == original.balance() + 123 && result.progress().reputation() == 88
                && result.progress().missions() == 3 && result.progress().bestMedal() == RpProgress.Medal.SILVER, "English operator commands failed.");
        helper.assertTrue(result.race().equals("Elfo") && result.origin().equals("Comum") && result.progress().occupation().equals("Estudioso"), "Canonical saved labels were translated.");
        helper.succeed();
    }
    @GameTest(template="cash_empty",timeoutTicks=100)
    public static void optionalConversionNoticeCannotCreateIncome(GameTestHelper helper) {
        var player=player(helper);
        var original=player.getData(ModAttachments.PROFILE);
        var commands=helper.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(player.createCommandSourceStack(),"rp withdraw 12.50");
        helper.assertTrue(!NotificationsBridge.send(player,"info",Component.literal("Conversion receipt retry")),
                "A fake player without a supporting client accepted the optional payload");
        NotificationsBridge.notify(player,"info",Component.literal("Conversion receipt retry"));
        helper.assertTrue(player.getData(ModAttachments.PROFILE).balance()+CashExchange.total(CashService.snapshot(player))==original.balance(),
                "A conversion notice created or lost money");
        commands.performPrefixedCommand(player.createCommandSourceStack(),"rp deposit all");
        helper.assertTrue(original.equals(player.getData(ModAttachments.PROFILE)),"Notification fallback changed RP fields or wallet total");
        helper.succeed();
    }

}
