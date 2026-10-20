package pt.realmeconomy.menu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import pt.realmeconomy.service.Services;
public final class ServicesMenu extends AbstractContainerMenu {
 public static final java.util.function.Supplier<MenuType<ServicesMenu>> TYPE=EconomyMenu.MENUS.register("services",()->IMenuTypeExtension.create((id,inventory,buffer)->new ServicesMenu(id,inventory)));
 public static void load(){}
 public ServicesMenu(int id,Inventory inventory){super(TYPE.get(),id);}
 @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
 @Override public boolean stillValid(Player p){return p.level().isClientSide||Services.valid((net.minecraft.server.level.ServerPlayer)p);}
 @Override public void removed(Player p){super.removed(p);if(p instanceof net.minecraft.server.level.ServerPlayer server)Services.close(server);}
}
