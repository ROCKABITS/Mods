package pt.realmprogression.mixin;
import net.minecraft.server.MinecraftServer;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Treasury;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="pt.crownledger.service.CrownService",remap=false)
public abstract class TreasuryCommitObserver {
    @Inject(method="commit",at=@At("RETURN"),remap=false)
    private static void progression$commit(MinecraftServer server,TreasuryData data,Treasury next,CallbackInfo ci){pt.realmprogression.service.ProgressService.observe(server);}
}
