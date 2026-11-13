package pt.realmfitness.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import pt.realmfitness.FitnessNetwork;

@EventBusSubscriber(modid="realmfitness",value=Dist.CLIENT)
public final class FitnessClient {
    public static final KeyMapping KEY=new KeyMapping("key.realmfitness.profile",GLFW.GLFW_KEY_K,"key.categories.realmfitness");
    public record Motion(String exercise,int tick,long received){}
    public static final Map<UUID,Motion> MOTIONS=new HashMap<>();
    private FitnessClient(){}
    public static void receive(String json){
        JsonObject d=JsonParser.parseString(json).getAsJsonObject();var mc=Minecraft.getInstance();
        switch(str(d,"type")){
            case "profile" -> {if(mc.screen instanceof AthleteScreen s)s.accept(d);else mc.setScreen(new AthleteScreen(d));}
            case "game" -> {if(mc.screen instanceof TrainingScreen s)s.accept(d);else if(d.get("open").getAsBoolean())mc.setScreen(new TrainingScreen(d));}
            case "cancel" -> {if(mc.screen instanceof TrainingScreen)mc.setScreen(null);}
            case "pose" -> {UUID id=UUID.fromString(str(d,"player"));if(d.get("end").getAsBoolean())MOTIONS.remove(id);else MOTIONS.put(id,new Motion(str(d,"exercise"),num(d,"tick"),System.nanoTime()));}
            default -> {}
        }
    }
    public static String str(JsonObject d,String key){return d.has(key)&&!d.get(key).isJsonNull()?d.get(key).getAsString():"";}
    public static int num(JsonObject d,String key){return d.has(key)?d.get(key).getAsInt():0;}
    public static long time(JsonObject d,String key){return d.has(key)?d.get(key).getAsLong():0;}
    public static JsonArray array(JsonObject d,String key){return d.has(key)?d.getAsJsonArray(key):new JsonArray();}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){var mc=Minecraft.getInstance();while(KEY.consumeClick())if(mc.player!=null&&!(mc.screen instanceof TrainingScreen))FitnessNetwork.request("open",Map.of());MOTIONS.values().removeIf(m->System.nanoTime()-m.received>2_000_000_000L);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){MOTIONS.clear();}
    @EventBusSubscriber(modid="realmfitness",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(KEY);}
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(pt.realmfitness.FitnessMod.BOARD_ENTITY.get(),RecordBoardRenderer::new);}
    }
}
