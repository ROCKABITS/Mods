package pt.realmfitness.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import pt.realmfitness.FitnessNetwork;

public final class FitnessClient implements net.fabricmc.api.ClientModInitializer {
    public static final KeyMapping KEY=new KeyMapping("key.realmfitness.profile",GLFW.GLFW_KEY_K,"key.categories.realmfitness");
    public static void receive(String json){
        JsonObject d=JsonParser.parseString(json).getAsJsonObject();var mc=Minecraft.getInstance();
        switch(str(d,"type")){
            case "profile" -> {if(mc.screen instanceof AthleteScreen s)s.accept(d);else mc.setScreen(new AthleteScreen(d));}
            case "game" -> {if(TrainingHud.active()!=null)TrainingHud.active().accept(d);else if(d.get("open").getAsBoolean())TrainingHud.start(d);}
            case "cancel" -> {TrainingHud.clear();}
            case "done" -> TrainingHud.finish(d);
            default -> {}
        }
    }
    public static String str(JsonObject d,String key){return d.has(key)&&!d.get(key).isJsonNull()?d.get(key).getAsString():"";}
    public static int num(JsonObject d,String key){return d.has(key)?d.get(key).getAsInt():0;}
    public static long time(JsonObject d,String key){return d.has(key)?d.get(key).getAsLong():0;}
    public static JsonArray array(JsonObject d,String key){return d.has(key)?d.getAsJsonArray(key):new JsonArray();}
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(KEY);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(FitnessNetwork.SNAPSHOT,(client,handler,buffer,sender)->{String json=buffer.readUtf(196608);client.execute(()->receive(json));});
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->TrainingHud.clear());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{while(KEY.consumeClick())if(client.player!=null&&TrainingHud.active()==null)FitnessNetwork.request("open",Map.of());});
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics,delta)->TrainingHud.draw(graphics));
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(pt.realmfitness.FitnessMod.MOUNT.get(),GymMountRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(pt.realmfitness.FitnessMod.BOARD_ENTITY.get(),RecordBoardRenderer::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(net.minecraft.client.renderer.RenderType.cutout(),pt.realmfitness.FitnessMod.MACHINES.values().stream().map(r->r.get()).toArray(net.minecraft.world.level.block.Block[]::new));
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context->{
            for(var e:pt.realmfitness.domain.Exercise.values())for(var group:switch(e){
                case BENCH,SQUAT->List.of("bar");case BOX->List.of("bag");case RUN->List.of("belt");case ROW->List.of("seat","handle","cable","flywheel");case LEG_PRESS->List.of("sled");case TRICEPS->List.of("stack","front_cable","stack_cable","clip","rope_left","rope_right");default->List.<String>of();
            })context.addModels(new net.minecraft.resources.ResourceLocation("realmfitness","block/"+e.id()+"_"+group));
        });
        if(Boolean.getBoolean("fitness.visualTests"))try{Class.forName("pt.realmfitness.visualtest.FitnessVisualQA").getMethod("register").invoke(null);}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot start visual validation",error);}
    }
}
