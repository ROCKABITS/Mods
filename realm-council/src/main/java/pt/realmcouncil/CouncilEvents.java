package pt.realmcouncil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.network.chat.Component;
public final class CouncilEvents {
    public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p){CouncilService.observe(p);CouncilService.advanceClock(p.server);var d=CouncilData.get(p.server);if(d.state.current!=null&&!d.state.current.terminal())CouncilService.notify(p,Component.translatable("message.realmcouncil.welcome",d.state.current.title));}}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){CouncilService.logout(event.getEntity().getUUID());}
    public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();if(server.getTickCount()%20!=0)return;
        CouncilService.advanceClock(server);
        if(server.getTickCount()%1200==0)server.getPlayerList().getPlayers().forEach(CouncilService::observe);
    }
    public static void stopped(ServerStoppedEvent event){CouncilService.clear();}
}
