package pt.realmprogression;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import pt.realmprogression.service.ProgressService;
@Mod("realmprogression")
public final class RealmProgressionMod {
    public RealmProgressionMod(IEventBus bus){
        pt.realmprogression.block.ProgressBlocks.register(bus);pt.realmprogression.menu.ProgressMenu.register(bus);bus.addListener(pt.realmprogression.network.ProgressNetwork::register);
        NeoForge.EVENT_BUS.addListener(ProgressService::interact);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->ProgressService.observe(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{if(e.getServer().getTickCount()%20==0)ProgressService.observe(e.getServer());});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->ProgressService.stop(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)ProgressService.close(p);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p){ProgressService.observe(p.getServer());try{ProgressService.sync(p);}catch(RuntimeException failure){p.displayClientMessage(net.minecraft.network.chat.Component.translatable("error.realmprogression.storage"),false);}}});
    }
}
