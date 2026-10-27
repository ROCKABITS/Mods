package pt.realmeconomy.mixin.colony;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.minecolonies.api.colony.ICitizenData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=RecruitmentInteraction.class,remap=false)
public abstract class RecruitInvoiceMixin {
 @Inject(method="onServerResponseTriggered",at=@At("HEAD"),cancellable=true)
 private void economy$recruit(int response,Player player,ICitizenData data,CallbackInfo ci){var responses=((RecruitmentInteraction)(Object)this).getPossibleResponses();if(response<0||response>=responses.size()){ci.cancel();return;}if(responses.get(response).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text&&text.getKey().equals("com.minecolonies.coremod.gui.chat.recruit")&&player instanceof ServerPlayer p&&!pt.realmeconomy.colony.ColonyHooks.recruit(p,data))ci.cancel();}
}
