package pt.crownledger.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pt.crownledger.domain.Treasury.Currency;

/** Both currencies retain their original units: whole emeralds or Chronicles cents. */
public final class WalletBridge {
    private WalletBridge() {}

    public static Currency activeCurrency() {
        return ChroniclesBridge.loaded() ? Currency.CHRONICLES : Currency.EMERALD;
    }

    public static long balance(ServerPlayer player, Currency currency) {
        requireThread(player);
        if (currency == Currency.CHRONICLES) return ChroniclesBridge.balance(player);
        return inventory(player).stream().filter(WalletBridge::plain).mapToLong(ItemStack::getCount).sum();
    }

    public static long capacity(ServerPlayer player, Currency currency) {
        requireThread(player);
        if (currency == Currency.CHRONICLES) return ChroniclesBridge.MAX_CENTS - ChroniclesBridge.balance(player);
        return inventory(player).stream().mapToLong(stack -> stack.isEmpty() ? Items.EMERALD.getDefaultMaxStackSize()
                : plain(stack) ? Math.max(0, stack.getMaxStackSize() - stack.getCount()) : 0).sum();
    }

    public static void debit(ServerPlayer player, Currency currency, long amount) {
        requireThread(player);
        if (amount < 1) throw new IllegalArgumentException("gui.kingdomtreasury.invalid_amount");
        if (currency == Currency.CHRONICLES) { ChroniclesBridge.debit(player, amount); return; }
        List<ItemStack> plan = inventory(player);
        long available = plan.stream().filter(WalletBridge::plain).mapToLong(ItemStack::getCount).sum();
        if (available < amount) throw new IllegalStateException("gui.kingdomtreasury.insufficient_money");
        long remaining = amount;
        for (ItemStack stack : plan) {
            if (!plain(stack)) continue;
            int take = (int) Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (remaining == 0) break;
        }
        apply(player, plan);
    }

    public static void credit(ServerPlayer player, Currency currency, long amount) {
        requireThread(player);
        if (amount < 1 || amount > capacity(player, currency))
            throw new IllegalStateException("gui.kingdomtreasury.wallet_full");
        if (currency == Currency.CHRONICLES) { ChroniclesBridge.credit(player, amount); return; }
        List<ItemStack> plan = inventory(player);
        long remaining = amount;
        for (ItemStack stack : plan) {
            if (!plain(stack)) continue;
            int add = (int) Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
            stack.grow(add); remaining -= add;
            if (remaining == 0) break;
        }
        for (int i = 0; i < plan.size() && remaining > 0; i++) {
            if (!plan.get(i).isEmpty()) continue;
            int add = (int) Math.min(remaining, Items.EMERALD.getDefaultMaxStackSize());
            plan.set(i, new ItemStack(Items.EMERALD, add)); remaining -= add;
        }
        if (remaining != 0) throw new IllegalStateException("gui.kingdomtreasury.wallet_full");
        apply(player, plan);
    }

    private static List<ItemStack> inventory(ServerPlayer player) {
        return player.getInventory().items.stream().map(ItemStack::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, new ItemStack(Items.EMERALD));
    }

    private static void apply(ServerPlayer player, List<ItemStack> plan) {
        for (int i = 0; i < plan.size(); i++) player.getInventory().items.set(i, plan.get(i));
        player.getInventory().setChanged();
    }

    private static void requireThread(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread() || player.isSpectator())
            throw new IllegalStateException("gui.kingdomtreasury.invalid_request");
    }
}
