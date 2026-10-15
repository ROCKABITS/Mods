package pt.realmcouncil.client;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.network.CouncilPackets;
public final class CouncilClient {
    public static CouncilView view;
    private static int ticks;
    public static void receive(CouncilPackets.State p){
        var mc=Minecraft.getInstance();
        if(p.json().isEmpty()){
            view=null;
            if(mc.screen instanceof CouncilBase)mc.setScreen(null);
            if(mc.player!=null&&!p.message().isEmpty())mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(p.message().replace("error.","error.realmcouncil.")),true);
            return;
        }
        view=CouncilState.JSON.fromJson(p.json(),CouncilView.class);
        if(p.open()){
            if(view.mode().equals("urn"))mc.setScreen(new BallotScreen(view));
            else if(view.mode().equals("oath")&&view.mandate()!=null&&view.mandate().playerId.equals(view.viewer())&&view.mandate().claimedAt==0)mc.setScreen(new OathScreen(view));
            else mc.setScreen(new CouncilScreen(view));
        }else if(mc.screen instanceof CouncilBase s)s.accept(view,p.message());
    }
    public static void send(int action,String election,int round,String choice,String text,String extra){PacketDistributor.sendToServer(new CouncilPackets.Action(action,election,round,choice,text,extra));}
    public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(mc.player==null)return;
        if(++ticks%100==0&&mc.screen instanceof CouncilBase)send(0,"",0,"","","");
    }
}
