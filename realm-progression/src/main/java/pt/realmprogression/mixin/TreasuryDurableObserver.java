package pt.realmprogression.mixin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import pt.crownledger.domain.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="pt.crownledger.data.TreasuryData",remap=false)
public abstract class TreasuryDurableObserver {
    @Inject(method="durable",at=@At("RETURN"),remap=false)
    private void progression$durable(MinecraftServer s,Treasury t,Governance g,Loans l,CompoundTag journal,CallbackInfo ci){if(journal==null)pt.realmprogression.service.ProgressService.observe(s);}
}
