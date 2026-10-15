package pt.realmcouncil.network;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pt.realmcouncil.*;
import pt.realmcouncil.domain.Rules.RuleException;
public final class CouncilNetwork {
    public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("3");
        r.playToServer(CouncilPackets.Action.TYPE,CouncilPackets.Action.CODEC,(p,c)->{
            if(!(c.player() instanceof ServerPlayer player))return;
            try{CouncilService.action(player,p);}catch(RuleException ex){CouncilService.reject(player,ex.code);}
        });
        r.playToClient(CouncilPackets.State.TYPE,CouncilPackets.State.CODEC,(p,c)->c.enqueueWork(()->pt.realmcouncil.client.CouncilClient.receive(p)));
    }
    public static void send(ServerPlayer player,CouncilPackets.State packet){PacketDistributor.sendToPlayer(player,packet);}
}
