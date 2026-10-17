package pt.realmrewards.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.lwjgl.glfw.GLFW;
import pt.realmrewards.network.RewardNetwork;

/** Operator-only production menu. Server authorization is checked again on every request. */
public final class RewardScreen extends Screen {
    private final JsonObject data;
    private final BiConsumer<String,JsonObject> sender;
    private boolean defaultsTab;
    private JsonObject selected;
    private String searchDraft,amountDraft="",taskDraft,goalDraft,challengeDraft;
    private EditBox searchBox,amountBox;
    private int scroll,visible,left,right,half;

    public RewardScreen(JsonObject data) { this(data,RewardNetwork::request); }
    public RewardScreen(JsonObject data,BiConsumer<String,JsonObject> sender) {
        super(text("title")); this.data=data; this.sender=sender;
        searchDraft=data.get("search").getAsString(); taskDraft=data.get("task").getAsString();
        goalDraft=data.get("goal").getAsString(); challengeDraft=data.get("challenge").getAsString();
    }
    public static void receive(String json) {
        JsonObject data=JsonParser.parseString(json).getAsJsonObject();
        Minecraft mc=Minecraft.getInstance(); RewardScreen next=new RewardScreen(data);
        if (mc.screen instanceof RewardScreen before) {
            next.defaultsTab=before.defaultsTab;
            if (before.selected!=null) for (var value:data.getAsJsonArray("rows")) {
                JsonObject row=value.getAsJsonObject();
                if (row.get("id").equals(before.selected.get("id"))) {
                    next.selected=row; next.amountDraft=row.get("amount").getAsString();
                }
            }
        }
        mc.setScreen(next);
    }
    private static Component text(String key,Object...args) { return Component.translatable("gui.realmrewards."+key,args); }
    private Button button(Component label,int x,int y,int w,Runnable run) {
        return addRenderableWidget(Button.builder(label,b->run.run()).bounds(x,y,w,20).build());
    }
    private EditBox edit(String value,int x,int y,int w,java.util.function.Consumer<String> responder) {
        EditBox box=new EditBox(font,x,y,w,20,Component.empty());
        box.setMaxLength(128); box.setValue(value); box.setResponder(responder); return addRenderableWidget(box);
    }
    @Override protected void init() {
        left=20; half=width/2; right=half+9; visible=Math.max(1,(height-145)/24);
        button(text("advancements"),left,31,Math.min(180,half-25),()->{ defaultsTab=false; rebuildWidgets(); });
        button(text("defaults"),half+5,31,Math.min(180,half-25),()->{ defaultsTab=true; rebuildWidgets(); });
        button(Component.translatable("gui.done"),width-80,height-26,60,this::onClose);
        if (defaultsTab) {
            edit(taskDraft,left,84,width-40,v->taskDraft=v);
            edit(goalDraft,left,128,width-40,v->goalDraft=v);
            edit(challengeDraft,left,172,width-40,v->challengeDraft=v);
            button(text("save_defaults"),left,199,Math.min(250,width-110),()->{
                JsonObject request=request(); request.addProperty("task",taskDraft); request.addProperty("goal",goalDraft); request.addProperty("challenge",challengeDraft);
                sender.accept("defaults",request);
            }); return;
        }
        searchBox=edit(searchDraft,left,57,Math.max(55,half-87),v->searchDraft=v);
        button(text("search"),half-61,57,52,()->page(0));
        JsonArray rows=data.getAsJsonArray("rows");
        scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visible)));
        for (int index=scroll;index<Math.min(rows.size(),scroll+visible);index++) {
            JsonObject row=rows.get(index).getAsJsonObject();
            Component name=label(row); String id=row.get("id").getAsString();
            String label=font.plainSubstrByWidth(name.getString(),Math.max(20,half-92))+"  "+row.get("amount").getAsString()+" €";
            Button b=button(Component.literal(label),left,84+(index-scroll)*24,half-left-9,()->{
                selected=row; amountDraft=row.get("amount").getAsString(); rebuildWidgets();
            });
            b.setTooltip(Tooltip.create(name.copy().append("\n"+id)));
        }
        if (selected!=null) {
            amountBox=edit(amountDraft,right,143,Math.max(70,width-right-20),v->amountDraft=v);
            button(text("save"),right,170,Math.max(70,width-right-20),()->save("save"));
            button(text("reset"),right,194,Math.max(70,width-right-20),()->save("reset"));
        }
        int page=data.get("page").getAsInt(),total=data.get("total").getAsInt(),size=data.get("size").getAsInt();
        button(Component.literal("←"),left,height-26,30,()->page(page-1)).active=page>0;
        button(Component.literal("→"),left+36,height-26,30,()->page(page+1)).active=(page+1)*size<total;
    }
    private static Component label(JsonObject row) {
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE,row.get("title")).getOrThrow();
    }
    private JsonObject request() {
        JsonObject request=new JsonObject(); request.addProperty("search",searchDraft);
        request.addProperty("page",data.get("page").getAsInt()); request.addProperty("size",8); return request;
    }
    private void page(int page) { JsonObject request=request(); request.addProperty("page",Math.max(0,page)); sender.accept("list",request); }
    private void save(String operation) {
        JsonObject request=request(); request.addProperty("id",selected.get("id").getAsString()); request.addProperty("value",amountDraft); sender.accept(operation,request);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if ((key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER) && searchBox!=null && searchBox.isFocused()) { page(0); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if (!defaultsTab && x<half && y>=84 && y<height-50) {
            int next=Math.max(0,Math.min(Math.max(0,data.getAsJsonArray("rows").size()-visible),scroll+(vertical<0?1:-1)));
            if (next!=scroll) { scroll=next; rebuildWidgets(); return true; }
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        g.fill(0,0,width,height,0xff17201c); g.fill(10,8,width-10,height-8,0xff7b5838);
        g.fill(13,11,width-13,height-11,0xffe6d6af); g.fill(half-1,80,half,height-53,0xffb59c70);
        g.drawString(font,title,left,15,0xff3b2a18,false);
        if (defaultsTab) {
            g.drawString(font,text("task"),left,71,0xff3b2a18,false);
            g.drawString(font,text("goal"),left,115,0xff3b2a18,false);
            g.drawString(font,text("challenge"),left,159,0xff3b2a18,false);
        } else if (selected!=null) {
            g.drawString(font,font.plainSubstrByWidth(label(selected).getString(),width-right-20),right,87,0xff294b39,false);
            g.drawString(font,font.plainSubstrByWidth(selected.get("id").getAsString(),width-right-20),right,102,0xff705d40,false);
            g.drawString(font,text("value"),right,128,0xff3b2a18,false);
            g.drawString(font,text(selected.get("override").getAsBoolean()?"custom":"automatic"),right,115,0xff705d40,false);
        } else g.drawWordWrap(font,text("choose"),right,90,Math.max(50,width-right-20),0xff3b2a18);
        if (!defaultsTab) {
            g.drawString(font,text("page",data.get("page").getAsInt()+1,Math.max(1,(data.get("total").getAsInt()+7)/8),data.get("total").getAsInt()),left+77,height-21,0xff3b2a18,false);
            if (data.getAsJsonArray("rows").size()>visible) {
                int track=Math.max(10,height-144),thumb=Math.max(8,track*visible/data.getAsJsonArray("rows").size());
                int y=84+(track-thumb)*scroll/Math.max(1,data.getAsJsonArray("rows").size()-visible);
                g.fill(half-6,84,half-3,84+track,0xffc0aa7a); g.fill(half-6,y,half-3,y+thumb,0xff56704e);
            }
        }
        String error=data.get("error").getAsString();
        Component status=!error.isEmpty()?Component.translatable(error):text(data.get("chronicles").getAsBoolean()?"future_only":"wallet_missing");
        // Footer is bounded; its full wording remains available in the control tooltip.
        int statusY=height<270 && defaultsTab?57:height-47;
        int statusWidth=height<270 && !defaultsTab?Math.max(50,half-left-10):width-40;
        g.drawString(font,font.plainSubstrByWidth(status.getString(),statusWidth),left,statusY,error.isEmpty()?0xff705d40:0xffa02c27,false);
        super.render(g,mouseX,mouseY,partial);
    }
    @Override public boolean isPauseScreen() { return false; }
    // Screen.render() renders its default blurred background before the widgets.
    // Our parchment and labels were already drawn, so that background must be empty.
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial) {}
}
