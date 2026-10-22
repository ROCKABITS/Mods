package pt.cronicasrp;

import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import pt.cronicasrp.i18n.ModMessages;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import pt.cronicasrp.domain.CashExchange;
import pt.cronicasrp.domain.CurrencyDenomination;
import pt.cronicasrp.domain.Money;
import pt.cronicasrp.network.CashPackets;
import pt.cronicasrp.network.ProfileNetwork;
import pt.cronicasrp.network.ProfilePackets;

/** Called only on the server main thread; clients never provide balance or inventory contents. */
public final class CashService {
    private CashService() {}
    public static List<CashExchange.Slot> snapshot(ServerPlayer player) {
        var result = new ArrayList<CashExchange.Slot>();
        for (int index = 0; index < 37; index++) {
            ItemStack stack = player.getInventory().getItem(realIndex(index));
            var denomination = CurrencyItems.denomination(stack);
            boolean plain = denomination != null && ItemStack.isSameItemSameComponents(stack, CurrencyItems.stack(denomination, 1));
            result.add(new CashExchange.Slot(denomination, stack.getCount(), plain, index < 36));
        }
        return result;
    }
    private static int realIndex(int index) { return index == 36 ? 40 : index; }
    public static Component perform(ServerPlayer player, CashPackets.Request request) {
        var inventory = snapshot(player);
        var profile = player.getData(ModAttachments.PROFILE);
        CashExchange.Transaction result = switch (request.action()) {
            case CashPackets.WITHDRAW -> CashExchange.withdraw(profile, inventory, request.amount());
            case CashPackets.DEPOSIT -> CashExchange.deposit(profile, inventory, request.amount());
            case CashPackets.WITHDRAW_PIECES -> CashExchange.withdrawPieces(profile, inventory, CurrencyDenomination.byId(request.denomination()), request.quantity());
            case CashPackets.DEPOSIT_PIECES -> CashExchange.depositPieces(profile, inventory, CurrencyDenomination.byId(request.denomination()), request.quantity());
            case CashPackets.DEPOSIT_ALL -> CashExchange.depositAll(profile, inventory);
            default -> throw new IllegalArgumentException("Operação de câmbio inválida.");
        };
        commit(player, inventory, result);
        boolean withdrawing = request.action() == CashPackets.WITHDRAW || request.action() == CashPackets.WITHDRAW_PIECES;
        return ModMessages.tr(withdrawing ? "message.cronicasrp.cash.withdrawn" : "message.cronicasrp.cash.deposited", ModMessages.money(result.amount()));
    }
    public static Component depositHand(ServerPlayer player, int index, int count) {
        var inventory = snapshot(player);
        var result = CashExchange.depositSlot(player.getData(ModAttachments.PROFILE), inventory, index, count);
        commit(player, inventory, result);
        return ModMessages.tr("message.cronicasrp.cash.deposited", ModMessages.money(result.amount()));
    }
    private static void commit(ServerPlayer player, List<CashExchange.Slot> before, CashExchange.Transaction result) {
        // Planning already checked funds, limits, denominations, quantities, and all free slots.
        for (int index = 0; index < before.size(); index++) {
            var original = before.get(index); var planned = result.slots().get(index);
            if (original.equals(planned)) continue;
            ItemStack current = player.getInventory().getItem(realIndex(index));
            ItemStack updated = planned.emptySlot() ? ItemStack.EMPTY
                    : planned.money() == original.money() && original.money() != null
                    ? current.copyWithCount(planned.count()) : CurrencyItems.stack(planned.money(), planned.count());
            player.getInventory().setItem(realIndex(index), updated);
        }
        player.setData(ModAttachments.PROFILE, result.profile());
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        ProfileNetwork.send(player, ProfilePackets.SYNC, "");
    }
}
