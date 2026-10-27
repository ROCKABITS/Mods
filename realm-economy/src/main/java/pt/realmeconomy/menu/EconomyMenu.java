package pt.realmeconomy.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import pt.realmeconomy.service.TradingService;

/** No native result/input slots: all trades must pass the quoted, receipted server transaction. */
public final class EconomyMenu extends AbstractContainerMenu {
    static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(net.minecraft.core.registries.Registries.MENU,"realmeconomy");
    public static final Supplier<MenuType<EconomyMenu>> TYPE=MENUS.register("trading",()->IMenuTypeExtension.create((int id,Inventory inventory,RegistryFriendlyByteBuf buffer)->new EconomyMenu(id,inventory)));
    public static void register(IEventBus bus){ServicesMenu.load();ColonyMenu.load();MENUS.register(bus);}
    public EconomyMenu(int id,Inventory inventory){super(TYPE.get(),id);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player){return player.level().isClientSide||TradingService.valid((net.minecraft.server.level.ServerPlayer)player);}
    @Override public void removed(Player player){super.removed(player);if(player instanceof net.minecraft.server.level.ServerPlayer server)TradingService.close(server);}
}
