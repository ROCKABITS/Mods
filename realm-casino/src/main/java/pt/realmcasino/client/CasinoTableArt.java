package pt.realmcasino.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Six carved seat plaques, private cards, community cards and tactile dice/coin illustrations. */
final class CasinoTableArt {
    private CasinoTableArt(){}
    static String str(JsonObject o,String key){try{return o.get(key).getAsString();}catch(RuntimeException ignored){return "";}}
    static long num(JsonObject o,String key){try{return o.get(key).getAsLong();}catch(RuntimeException ignored){return 0;}}
    static boolean flag(JsonObject o,String key){try{return o.get(key).getAsBoolean();}catch(RuntimeException ignored){return false;}}
    static JsonArray array(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray();}
    static Component tr(String key,Object...args){return Component.translatable("gui.realmcasino."+key,args);}
    static void centered(GuiGraphics g,Font font,Component text,int cx,int y,int width,int color,boolean bold){Component fitted=CasinoTheme.fit(font,text,width,bold);g.drawString(font,fitted,cx-font.width(fitted)/2,y,color,false);}
    static void render(GuiGraphics g,Font font,JsonObject table,String game,int x,int y,int w,int h,double progress,long elapsed,boolean motion){
        centered(g,font,tr(game+"_subtitle"),x+w/2,y+13,w-22,CasinoTheme.GOLD,true);
        String phase=str(table,"phase");String label=phase.equals("playing")&&game.equals("poker")?"poker_street_"+num(table,"street"):"table_"+(phase.isEmpty()?"empty":phase);
        centered(g,font,tr(label),x+w/2,y+29,w-22,CasinoTheme.MUTED,false);
        if(game.equals("coin")){coin(g,font,table,x,y,w,h,progress);return;}
        JsonArray seats=array(table,"seats");int cell=(w-28)/3,sh=game.equals("dice")?88:82,top=y+48;
        for(int i=0;i<6;i++){
            int sx=x+10+(i%3)*(cell+4),sy=top+(i/3)*(game.equals("dice")?98:157);JsonObject seat=i<seats.size()?seats.get(i).getAsJsonObject():new JsonObject();
            CasinoTheme.plaque(g,sx,sy,cell,sh);boolean current=phase.equals("playing")&&num(table,"actor")==i,own=flag(seat,"you");
            if(current)CasinoTheme.border(g,sx,sy,cell,sh,0xFF92B673);else if(own)CasinoTheme.border(g,sx,sy,cell,sh,CasinoTheme.GOLD);
            if(seat.size()==0){centered(g,font,tr("empty_seat",i+1),sx+cell/2,sy+sh/2-4,cell-12,0xFF796F5D,false);continue;}
            centered(g,font,Component.literal(str(seat,"name")),sx+cell/2,sy+7,cell-14,flag(seat,"folded")?CasinoTheme.MUTED:CasinoTheme.LIGHT,true);
            if(game.equals("dice")){
                JsonArray result=array(table,"results");for(int die=0;die<2;die++){int value=result.size()>i*2+die?result.get(i*2+die).getAsInt():0;if(value>0&&progress<1&&motion)value=1+(int)((elapsed/85+i*2+die)%6);die(g,sx+cell/2+(die==0?-14:14),sy+39,11,value);}
                if(result.size()>i*2+1&&progress>=1)centered(g,font,tr("dice_total",result.get(i*2).getAsInt()+result.get(i*2+1).getAsInt()),sx+cell/2,sy+57,cell-12,CasinoTheme.GOLD,false);
            }else{
                JsonArray cards=array(seat,"cards");int cw=Math.min(25,(cell-15)/2),height=34,spacing=cards.size()<=2?cw+3:Math.min(cw+3,Math.max(5,(cell-cw-10)/Math.max(1,cards.size()-1))),full=cards.isEmpty()?cw*2+3:cw+(cards.size()-1)*spacing,left=sx+(cell-full)/2;
                int count=cards.isEmpty()?2:cards.size();for(int c=0;c<count;c++){int id=cards.isEmpty()?-1:cards.get(c).getAsInt();CasinoTheme.card(g,font,CasinoScreen.cardText(id),left+c*spacing,sy+22,cw,height,id<0,1);}
                if(game.equals("blackjack_pvp")&&!cards.isEmpty()){int total=CasinoScreen.total(cards);centered(g,font,total<0?tr("private_cards"):tr("total",total),sx+cell/2,sy+60,cell-12,CasinoTheme.MUTED,false);}
                else if(flag(seat,"folded"))centered(g,font,tr("folded"),sx+cell/2,sy+60,cell-12,0xFFC58A82,false);
                else centered(g,font,tr("table_stack",CasinoScreen.decimal(num(seat,"stack"))),sx+cell/2,sy+60,cell-12,CasinoTheme.MUTED,false);
            }
            if(phase.equals("finished")){long net=num(seat,"payout")-num(seat,"buyin");centered(g,font,Component.literal((net>0?"+":"")+CasinoScreen.decimal(net)+" €"),sx+cell/2,sy+sh-12,cell-10,net>=0?CasinoTheme.GOLD:0xFFD39A8A,true);}
            else if(game.equals("poker"))centered(g,font,tr("table_bet",CasinoScreen.decimal(num(seat,"streetBet"))),sx+cell/2,sy+sh-12,cell-10,CasinoTheme.MUTED,false);
            if(i==num(table,"dealer")&&game.equals("poker")){CasinoTheme.disk(g,sx+8,sy+sh-8,5,CasinoTheme.GOLD);g.drawString(font,"D",sx+6,sy+sh-11,0xFF32241B,false);}
        }
        if(!game.equals("dice")){
            int middle=y+142;JsonArray board=array(table,"board");int cw=Math.min(29,(w-70)/5),spacing=cw+5,total=spacing*5-5,left=x+(w-total)/2;
            if(game.equals("poker"))for(int i=0;i<5;i++){if(i<board.size())CasinoTheme.card(g,font,CasinoScreen.cardText(board.get(i).getAsInt()),left+i*spacing,middle,cw,42,false,1);else {CasinoTheme.border(g,left+i*spacing,middle,cw,42,0xFF527359);CasinoTheme.diamond(g,left+i*spacing+cw/2,middle+21,2,0xFF527359);}}
            else {CasinoTheme.crown(g,x+w/2,middle+17,2);centered(g,font,tr("blackjack_pvp_centre"),x+w/2,middle+32,w-32,CasinoTheme.MUTED,false);}
            centered(g,font,tr("table_pot",CasinoScreen.decimal(num(table,"pot"))),x+w/2,y+190,w-24,CasinoTheme.GOLD,true);
        }
    }
    static void die(GuiGraphics g,int cx,int cy,int radius,int value){
        g.fill(cx-radius+2,cy-radius+3,cx+radius+3,cy+radius+4,0x66000000);g.fill(cx-radius,cy-radius,cx+radius,cy+radius,0xFFC4A76E);g.fill(cx-radius+1,cy-radius+1,cx+radius-1,cy+radius-1,0xFFFFF0CB);g.fill(cx-radius+2,cy-radius+2,cx+radius-2,cy-radius+4,0xFFFFFFFF);
        if(value==0){CasinoTheme.diamond(g,cx,cy,2,CasinoTheme.GOLD);return;}int d=Math.max(3,radius/2),r=Math.max(1,radius/7);
        if(value%2==1)pip(g,cx,cy,r);if(value>=2){pip(g,cx-d,cy-d,r);pip(g,cx+d,cy+d,r);}if(value>=4){pip(g,cx+d,cy-d,r);pip(g,cx-d,cy+d,r);}if(value==6){pip(g,cx-d,cy,r);pip(g,cx+d,cy,r);}
    }
    private static void pip(GuiGraphics g,int x,int y,int r){CasinoTheme.disk(g,x,y,r,0xFF382824);}
    private static void coin(GuiGraphics g,Font font,JsonObject table,int x,int y,int w,int h,double p){
        JsonArray result=array(table,"results"),seats=array(table,"seats");int selected=result.size()==1?result.get(0).getAsInt():0;double turn=result.isEmpty()?0:(1-Math.pow(1-p,3))*Math.PI*12;float width=(float)Math.max(.06,Math.abs(Math.cos(turn)));int r=Math.min(62,(w-50)/3),cx=x+w/2,cy=y+126;
        CasinoTheme.disk(g,cx+3,cy+r+11,Math.max(5,(int)(r*.55)),0x33000000);
        String face=(p>=1?selected:Math.cos(turn)>=0?0:1)==0?"coin_heads":"coin_tails";
        g.pose().pushPose();g.pose().translate(cx,cy-Math.sin(Math.PI*p)*14,0);g.pose().scale(width*r/32f,r/32f,1);g.blit(ResourceLocation.fromNamespaceAndPath("realmcasino","textures/block/"+face+".png"),-32,-32,0,0,64,64,64,64);g.pose().popPose();
        for(int i=0;i<2;i++){
            int cell=(w-30)/2,sx=x+10+i*(cell+10),sy=y+210;CasinoTheme.plaque(g,sx,sy,cell,57);JsonObject seat=i<seats.size()?seats.get(i).getAsJsonObject():new JsonObject();
            centered(g,font,seat.size()==0?tr("empty_seat",i+1):Component.literal(str(seat,"name")),sx+cell/2,sy+8,cell-12,flag(seat,"you")?CasinoTheme.GOLD:CasinoTheme.LIGHT,true);
            int side=i==0?(int)num(table,"coinChoice"):1-(int)num(table,"coinChoice");centered(g,font,tr(side==0?"heads":"tails"),sx+cell/2,sy+24,cell-12,CasinoTheme.MUTED,false);
            if(str(table,"phase").equals("finished")&&p>=1)centered(g,font,tr(num(seat,"payout")>0?"coin_winner":"coin_loser"),sx+cell/2,sy+41,cell-12,num(seat,"payout")>0?CasinoTheme.GOLD:0xFFD39A8A,true);
        }
        if(!result.isEmpty()&&p>=1)centered(g,font,tr(selected==0?"heads":"tails"),cx,y+187,w-26,CasinoTheme.GOLD,true);
    }
}
