package pt.realmprogression.mixin;
import net.minecraft.world.inventory.CrafterMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(CrafterMenu.class)
public interface CrafterMenuAccess {@Invoker("refreshRecipeResult") void realmProgression$refresh();}
