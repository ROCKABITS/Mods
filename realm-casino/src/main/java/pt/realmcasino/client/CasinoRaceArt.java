package pt.realmcasino.client;

import com.google.gson.JsonArray;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Four miniature horses follow the finishing order already committed by the server. */
final class CasinoRaceArt {
    private static final int[] COATS={0xFFE9D9AF,0xFF965C36,0xFF403A35,0xFFB7A28A};
    private static final int[] SILKS={0xFFB84C48,0xFF48845F,0xFF4E7AA5,0xFFCA9B42};
    private CasinoRaceArt(){}
    static void render(GuiGraphics g,Font font,int x,int y,int w,int h,JsonArray order,int selected,double progress){
        int left=x+24,right=x+w-25,top=y+46,lane=36;
        CasinoTheme.plaque(g,left-4,top-5,right-left+8,lane*4+11);
        for(int i=0;i<4;i++){
            int yy=top+i*lane;g.fill(left,yy,right,yy+lane-2,i%2==0?0xFF674B32:0xFF705339);
            g.fill(left,yy,right,yy+1,0xFFAD9260);g.fill(left,yy+lane-3,right,yy+lane-2,0xFF352F24);
            for(int xx=left+5;xx<right-2;xx+=18){g.fill(xx,yy+8,xx+7,yy+9,0xFF8F7146);g.fill(xx+6,yy+25,xx+13,yy+26,0xFF594733);}
            for(int yy2=0;yy2<lane-2;yy2+=5)for(int xx=0;xx<2;xx++)g.fill(right-11+xx*5,yy+yy2,right-6+xx*5,yy+yy2+5,((yy2/5+xx)%2==0)?0xFFE4D9B3:0xFF35372F);
            int rank=i;if(order.size()==4)for(int k=0;k<4;k++)if(order.get(k).getAsInt()==i)rank=k;
            double p=order.size()==4?Math.min(1,Math.pow(progress/(.86+rank*.04),1.2)):0;
            int hx=left+16+(int)((right-left-39)*p),hy=yy+20;
            g.drawString(font,""+(i+1),left-14,yy+13,selected==i?CasinoTheme.GOLD:CasinoTheme.MUTED,false);
            if(selected==i)CasinoTheme.diamond(g,left-10,yy+27,2,CasinoTheme.GOLD);
            horse(g,hx,hy,COATS[i],SILKS[i],progress<1?System.nanoTime()/70_000_000:0);
            if(order.size()==4&&progress>=1&&rank==0)CasinoTheme.crown(g,right+8,hy,1);
        }
        CasinoTableArt.centered(g,font,Component.translatable("gui.realmcasino.horses_selection",selected+1),x+w/2,y+h-45,w-24,CasinoTheme.MUTED,false);
    }
    private static void horse(GuiGraphics g,int x,int y,int coat,int silks,long time){
        int stride=(time%2==0?1:-1);g.fill(x-9,y+3,x+9,y+5,0x55000000);
        g.fill(x-7,y-5,x+6,y+1,coat);g.fill(x+3,y-10,x+7,y-3,coat);g.fill(x+5,y-11,x+12,y-7,coat);g.fill(x+6,y-14,x+8,y-10,coat);g.fill(x+10,y-10,x+11,y-9,0xFF272B24);
        g.fill(x-7,y+1,x-5,y+6+stride,coat);g.fill(x+4,y+1,x+6,y+6-stride,coat);g.fill(x-10,y-5,x-7,y-2,0xFF382D26);
        g.fill(x-3,y-6,x+3,y-2,silks);g.fill(x-1,y-11,x+3,y-6,0xFF59462F);g.fill(x-2,y-14,x+3,y-11,silks);g.fill(x+1,y-12,x+4,y-10,0xFFD5BA82);
    }
}
