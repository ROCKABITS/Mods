package pt.realmprogression.mixin;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="pt.crownledger.api.CrownIncomeApi",remap=false)
public abstract class TreasuryIncomeObserver {
    @Inject(method="credit",at=@At("RETURN"),remap=false)
    private static void progression$income(MinecraftServer s,UUID id,String currency,long amount,UUID payer,String memo,CallbackInfoReturnable<Boolean> ci){pt.realmprogression.service.ProgressService.observe(s);}
}
