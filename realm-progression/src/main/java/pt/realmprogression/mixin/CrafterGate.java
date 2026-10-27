package pt.realmprogression.mixin;
import java.util.Optional;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(CrafterBlock.class)
public abstract class CrafterGate {
    @Inject(method="getPotentialResults",at=@At("RETURN"),cancellable=true)
    private static void progression$automatic(Level level,CraftingInput input,CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> ci){var result=ci.getReturnValue();if(result.isPresent()&&pt.realmprogression.service.CraftGate.blocked(level,result.get().value().assemble(input,level.registryAccess())))ci.setReturnValue(Optional.empty());}
}
