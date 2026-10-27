package pt.realmeconomy.menu;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import pt.realmeconomy.colony.ColonyService;
public final class ColonyMenu extends AbstractContainerMenu {
 public static final java.util.function.Supplier<MenuType<ColonyMenu>> TYPE=EconomyMenu.MENUS.register("colony",()->IMenuTypeExtension.create((id,inventory,buffer)->new ColonyMenu(id,inventory)));
 public static void load(){}
 public ColonyMenu(int id,Inventory inventory){super(TYPE.get(),id);}
 public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
 public boolean stillValid(Player p){return p.level().isClientSide||ColonyService.valid((net.minecraft.server.level.ServerPlayer)p);}
 public void removed(Player p){super.removed(p);if(p instanceof net.minecraft.server.level.ServerPlayer server)ColonyService.close(server);}
}
