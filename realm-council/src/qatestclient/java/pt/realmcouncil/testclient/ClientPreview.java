package pt.realmcouncil.testclient;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.realmcouncil.*;
import pt.realmcouncil.client.*;
import pt.realmcouncil.domain.*;
import pt.realmcouncil.domain.CouncilState.*;

@EventBusSubscriber(modid=RealmCouncil.ID,value=Dist.CLIENT)
public final class ClientPreview {
    static int ticks=-1,step=0;static final UUID A=new UUID(71,81),B=new UUID(91,101);static CouncilState state;static CouncilView view;
    static boolean adminPreview;
    static void refresh(String mode){long now=System.currentTimeMillis();var admin=adminPreview?CouncilAdministration.of(state,new CouncilAdministration.Settings(120,1440,2880,2880,20160,1440,1,12,3),Set.of(A),"",0,now):null;view=CouncilView.of(state,A,new Identity("Ricardo da Lua","Elfo",128,true),true,mode,now,120,admin);}
    static void setupAdmin(){adminPreview=true;state=new CouncilState();long now=System.currentTimeMillis();state.observe(A,"Ricardo",150000,now-1);state.observe(B,"Helena",240000,now-1);refresh("remote");}
    static void field(Object object,String name,Object value){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(Exception ex){throw new RuntimeException(ex);}}
    static AdminCreateScreen creation(int page){var s=new AdminCreateScreen(view);field(s,"titleValue","Guardião do Reino");field(s,"page",page);return s;}
    static void review(Minecraft mc){var s=creation(0);mc.setScreen(s);try{var m=AdminCreateScreen.class.getDeclaredMethod("review");m.setAccessible(true);m.invoke(s);}catch(Exception ex){throw new RuntimeException(ex);}}
    static void setup(){long now=System.currentTimeMillis();state=new CouncilState();state.observe(A,"Ricardo",2400,now-1);state.observe(B,"Helena",2400,now-1);state.create("Guardião do Reino",now,3600000,3600000,3600000,1209600000,86400000,0,1,12,3);state.nominate(A,new Identity("Ricardo da Lua","Elfo",128,true),"Um reino para todos","moon","Um mercado aberto a todos. Caminhos seguros entre as aldeias. Eventos públicos todas as semanas.\n\nAs decisões e os projetos serão publicados no livro do conselho.",now);state.nominate(B,new Identity("Helena do Vale","Humano",32,true),"Construir o nosso futuro","tree","Novas casas, uma biblioteca pública e uma praça para os encontros do reino.",now);refresh("board");}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(ticks<0){if(!(mc.screen instanceof TitleScreen))return;setup();mc.getWindow().setWindowed(1100,700);mc.options.guiScale().set(2);mc.resizeDisplay();ticks=0;show(mc);return;}
        ticks++;
        if(ticks==30){Screenshot.grab(mc.gameDirectory,"council-qa-"+step+".png",mc.getMainRenderTarget(),c->RealmCouncil.LOG.info(c.getString()));}
        if(ticks==45){step++;ticks=0;if(step==23){mc.stop();return;}show(mc);}
    }
    static void show(Minecraft mc){
        switch(step){
            case 0->mc.setScreen(new CouncilScreen(view));
            case 1->mc.setScreen(new NominationScreen(view));
            case 2->mc.setScreen(new CandidateScreen(view,A));
            case 3->{state.advance(System.currentTimeMillis());state.advance(System.currentTimeMillis());refresh("urn");mc.setScreen(new BallotScreen(view));}
            case 4->mc.setScreen(new VoteConfirmScreen(view,A,"Ricardo da Lua"));
            case 5->{state.cast(A,state.current.id,1,A,System.currentTimeMillis());state.advance(System.currentTimeMillis());refresh("oath");mc.setScreen(new OathScreen(view));}
            case 6->{state.claim(A,System.currentTimeMillis());state.publish(A,"O conselho vai construir um mercado público na praça, com acesso para todos os habitantes do reino.",System.currentTimeMillis());refresh("board");var screen=new CouncilScreen(view);try{var f=CouncilScreen.class.getDeclaredField("tab");f.setAccessible(true);f.set(screen,3);}catch(Exception ex){throw new RuntimeException(ex);}mc.setScreen(screen);}
            case 7->mc.setScreen(new BlockPreview());
            case 8->{mc.getWindow().setWindowed(854,480);mc.setScreen(new NominationScreen(view));}
            case 9->mc.setScreen(new CouncilScreen(view));
            case 10->{mc.getWindow().setWindowed(1100,700);setupAdmin();mc.setScreen(new AdminScreen(view));}
            case 11->mc.setScreen(creation(0));
            case 12->mc.setScreen(creation(1));
            case 13->review(mc);
            case 14->{setup();for(int i=0;i<8;i++){var id=new UUID(711,i);state.observe(id,"Habitante"+i,48000,System.currentTimeMillis()-1);state.enrol(id);}refresh("remote");var s=new AdminScreen(view);field(s,"tab",1);mc.setScreen(s);}
            case 15->{var s=new AdminScreen(view);field(s,"tab",2);mc.setScreen(s);}
            case 16->{var e=view.election();mc.setScreen(new AdminConfirmScreen(view,new AdminScreen(view),new pt.realmcouncil.network.CouncilPackets.Action(CouncilAdministration.CANCEL,e.id().toString(),e.round(),"","",e.phase()),"title.admin_cancel",net.minecraft.network.chat.Component.translatable("gui.realmcouncil.hint.admin_cancel").getString(),List.of(e.title())));}
            case 17->{mc.getWindow().setWindowed(854,480);mc.setScreen(new AdminScreen(view));}
            case 18->{setupAdmin();mc.setScreen(creation(0));}
            case 19->mc.setScreen(creation(1));
            case 20->review(mc);
            case 21->{mc.getLanguageManager().setSelected("en_us");mc.options.languageCode="en_us";mc.reloadResourcePacks();mc.setScreen(creation(0));}
            case 22->mc.setScreen(new AdminScreen(view));
        }
    }
    static final class BlockPreview extends Screen {
        BlockPreview(){super(Component.literal("Conselho do Reino · Blocos"));}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float pt){}
        @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0xFF142D34);g.drawCenteredString(font,getTitle(),width/2,30,0xE6C07C);var blocks=List.of(CouncilBlocks.BALLOT_BOX.get(),CouncilBlocks.NOTICE_BOARD.get(),CouncilBlocks.OATH_DESK.get());String[] names={"Urna do Conselho","Quadro Eleitoral","Mesa de Juramento"};for(int i=0;i<3;i++){int cx=width*(i+1)/4;g.pose().pushPose();g.pose().translate(cx-48,height/2-42,0);g.pose().scale(6,6,6);g.renderItem(new ItemStack(blocks.get(i)),0,0);g.pose().popPose();g.drawCenteredString(font,names[i],cx,height/2+70,0xF4E6CB);}super.render(g,mx,my,pt);}
    }
}
