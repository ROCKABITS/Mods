package pt.realmprogression.menu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.server.level.ServerPlayer;
import pt.realmprogression.service.ProgressService;
public final class ProgressMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"realmprogression");
    public static final java.util.function.Supplier<MenuType<ProgressMenu>> TYPE=MENUS.register("research",()->IMenuTypeExtension.create((id,inventory,b)->new ProgressMenu(id,inventory)));
    public static void register(IEventBus b){MENUS.register(b);}
    public ProgressMenu(int id,Inventory inv){super(TYPE.get(),id);}
    public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
    public boolean stillValid(Player p){return p.level().isClientSide||ProgressService.valid((ServerPlayer)p);}
    public void removed(Player p){super.removed(p);if(p instanceof ServerPlayer s)ProgressService.close(s);}
}
