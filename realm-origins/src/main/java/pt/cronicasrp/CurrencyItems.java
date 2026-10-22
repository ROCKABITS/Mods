package pt.cronicasrp;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.cronicasrp.domain.CurrencyDenomination;

public final class CurrencyItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CronicasRP.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CronicasRP.MOD_ID);
    private static final Map<CurrencyDenomination, DeferredItem<CashItem>> MONEY = new EnumMap<>(CurrencyDenomination.class);
    static {
        for (var denomination : CurrencyDenomination.values())
            MONEY.put(denomination, ITEMS.registerItem(denomination.id(), properties -> new CashItem(properties, denomination), new Item.Properties().stacksTo(64)));
        TABS.register("economy", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.cronicasrp.economy"))
                .icon(() -> stack(CurrencyDenomination.EURO_2, 1))
                .displayItems((parameters, output) -> { for (var denomination : CurrencyDenomination.values()) output.accept(item(denomination)); }).build());
    }
    private CurrencyItems() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); TABS.register(bus); }
    public static CashItem item(CurrencyDenomination denomination) { return MONEY.get(denomination).get(); }
    public static ItemStack stack(CurrencyDenomination denomination, int count) { return new ItemStack(item(denomination), count); }
    public static CurrencyDenomination denomination(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CashItem cash ? cash.denomination() : null;
    }
}
