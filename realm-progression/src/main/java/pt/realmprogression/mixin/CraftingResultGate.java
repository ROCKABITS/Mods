package pt.realmprogression.mixin;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pt.realmprogression.service.CraftGate;

@Mixin(CraftingMenu.class)
public abstract class CraftingResultGate {
    @Inject(method="slotChangedCraftingGrid",at=@At("TAIL"))
    private static void progression$result(AbstractContainerMenu menu,Level level,Player player,CraftingContainer grid,ResultContainer result,RecipeHolder<CraftingRecipe> hint,CallbackInfo ci){
        if(player instanceof ServerPlayer p&&CraftGate.blocked(level,result.getItem(0))){result.setItem(0,ItemStack.EMPTY);menu.setRemoteSlot(0,ItemStack.EMPTY);p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId,menu.incrementStateId(),0,ItemStack.EMPTY));}
    }
}
