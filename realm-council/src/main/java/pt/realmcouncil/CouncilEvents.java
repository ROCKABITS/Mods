package pt.realmcouncil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.network.chat.Component;
public final class CouncilEvents {
    public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p){CouncilService.observe(p);var d=CouncilData.get(p.server);if(d.state.tick(System.currentTimeMillis()))d.setDirty();if(d.state.current!=null&&!d.state.current.terminal())p.sendSystemMessage(Component.translatable("message.realmcouncil.welcome",d.state.current.title));}}
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event){CouncilService.logout(event.getEntity().getUUID());}
    public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();if(server.getTickCount()%20!=0)return;
        var d=CouncilData.get(server);var e=d.state.current;var phase=e==null?null:e.phase;int round=e==null?0:e.round;
        if(d.state.tick(System.currentTimeMillis())){d.setDirty();server.overworld().getDataStorage().save();server.getPlayerList().broadcastSystemMessage(Component.translatable("message.realmcouncil.phase_changed"),false);}
        if(server.getTickCount()%1200==0)server.getPlayerList().getPlayers().forEach(CouncilService::observe);
    }
    public static void stopped(ServerStoppedEvent event){CouncilService.clear();}
}
