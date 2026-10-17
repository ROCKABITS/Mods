package pt.realmnotifications.visualtest;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.realmnotifications.api.NotificationsApi;
import pt.realmnotifications.client.*;

/** Real client, real integrated server and real optional S2C transport; demonstration event text. */
@EventBusSubscriber(modid="realmnotifications",value=Dist.CLIENT)
public final class NotificationVisualQA {
    private static final Path OUT=Path.of(System.getProperty("notifications.qaOutput","qa-results/notifications-client"));
    private static final long START=System.currentTimeMillis();
    private static final List<String> CHECKS=new ArrayList<>(),CAPTURES=new ArrayList<>();
    private static volatile Throwable serverFailure;
    private static volatile boolean ready,sent;
    private static int step,lastStep=-1;
    private static long next,stepStart;
    private static boolean done;
    private NotificationVisualQA() {}
    private static void check(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
    private static boolean click(String key) {
        var s=Minecraft.getInstance().screen;if(s==null)return false;
        String text=Component.translatable(key).getString();
        for(var child:List.copyOf(s.children()))if(child instanceof Button b&&b.active&&b.getMessage().getString().equals(text)) {b.onPress();return true;}
        return false;
    }
    private static void server(Runnable action) {
        var server=Minecraft.getInstance().getSingleplayerServer();check(server!=null,"No integrated server");
        server.execute(()->{try{action.run();}catch(Throwable failure){serverFailure=failure;}});
    }
    private static void quietCompanionCreation() {
        // Companion character creation is unrelated to this presentation fixture. No release hook.
        try {
            var c=Class.forName("pt.cronicasrp.client.ClientState");var field=c.getDeclaredField("pending");field.setAccessible(true);field.setInt(null,0);
            var flow=Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset");flow.setAccessible(true);flow.invoke(null);
        }catch(ClassNotFoundException ignored) {}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void prepare() {
        UUID id=Minecraft.getInstance().player.getUUID();
        server(()->{
            var server=Minecraft.getInstance().getSingleplayerServer();var player=server.getPlayerList().getPlayer(id);var level=player.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(1000);
            for(int x=-7;x<=7;x++)for(int z=-4;z<=10;z++) {
                level.setBlock(new BlockPos(x,160,z),Blocks.SPRUCE_PLANKS.defaultBlockState(),3);
                for(int y=161;y<168;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            for(int x=-7;x<=7;x++)for(int y=161;y<=166;y++) {
                var block=y==161?Blocks.STONE_BRICKS:y==166||x%4==0?Blocks.STRIPPED_SPRUCE_LOG:Blocks.OAK_PLANKS;
                level.setBlock(new BlockPos(x,y,-4),block.defaultBlockState(),3);
            }
            for(int x:new int[]{-6,-5,5,6})for(int y=161;y<=164;y++)level.setBlock(new BlockPos(x,y,-3),Blocks.BOOKSHELF.defaultBlockState(),3);
            for(int x:new int[]{-3,3}) {
                level.setBlock(new BlockPos(x,161,-3),Blocks.BARREL.defaultBlockState(),3);
                level.setBlock(new BlockPos(x,162,-3),Blocks.LANTERN.defaultBlockState(),3);
            }
            for(int x=-2;x<=2;x++)for(int z=0;z<=7;z++)level.setBlock(new BlockPos(x,161,z),Blocks.RED_CARPET.defaultBlockState(),3);
            var board=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("contracts","contract_board")).defaultBlockState();
            if(!board.isAir()) {
                if(board.hasProperty(BlockStateProperties.HORIZONTAL_FACING))board=board.setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
                level.setBlock(new BlockPos(0,163,-3),board,3);
            }
            level.setBlock(new BlockPos(-1,162,-3),Blocks.CRAFTING_TABLE.defaultBlockState(),3);
            level.setBlock(new BlockPos(1,162,-3),Blocks.CARTOGRAPHY_TABLE.defaultBlockState(),3);
            player.connection.teleport(.5,161,6.5,180,-4);ready=true;
        });
    }
    private static void sendDemo() {
        sent=false;UUID id=Minecraft.getInstance().player.getUUID();
        server(()->{
            var player=Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayer(id);
            check(NotificationsApi.send(player,"reward",Component.literal("Advancement complete"),Component.literal("+50 € for completing Diamonds!")),"Real reward S2C was not sent");
            check(NotificationsApi.send(player,"contract",Component.literal("A task well done"),Component.literal("Your delivery is complete. Collect your reward at the board.")),"Real contract S2C was not sent");
            check(NotificationsApi.send(player,"market",Component.literal("News from your market stall"),Component.literal("Oak logs sold. 19 € are waiting at your stall.")),"Real market S2C was not sent");
            sent=true;
        });
    }
    private static void bounds(NotificationScreen screen) {
        for(var child:screen.children())if(child instanceof AbstractWidget w)
            check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=320&&w.getY()+w.getHeight()<=240,"Compact widget outside320x240: "+w.getMessage().getString());
    }
    private static void verifyCompactAndPreferences() {
        var mc=Minecraft.getInstance();var p=NotificationsClient.preferences();
        p.sound=false;p.seconds=12;p.position=NotificationPreferences.Position.TOP_LEFT;p.enabled=true;p.history=true;
        check(p.save(),"Client preferences could not save");var loaded=NotificationPreferences.load();
        check(!loaded.sound&&loaded.seconds==12&&loaded.position==p.position,"Preferences did not round-trip");
        for(var position:NotificationPreferences.Position.values()) {
            int w=CardLayout.width(320),x=CardLayout.x(320,w,position.name());check(x>=0&&x+w<=320,"Anchor outside compact HUD");
            for(int n=0;n<CardLayout.visible(240);n++){int y=CardLayout.y(240,n,position.name());check(y>=0&&y+CardLayout.HEIGHT<=240,"Stack outside compact HUD");}
        }
        var s=new NotificationScreen(null,false);mc.setScreen(s);s.resize(mc,320,240);bounds(s);
        Button soundToggle=null;
        for(var child:s.children())if(child instanceof Button b&&b.getMessage().getString().equals(Component.translatable("gui.realmnotifications.off").getString())) {soundToggle=b;break;}
        check(soundToggle!=null,"Missing muted sound toggle");soundToggle.onPress();check(p.sound,"Native sound toggle did not enable sound");
        soundToggle.onPress();check(!p.sound&&!NotificationPreferences.load().sound,"Native sound toggle did not persist mute");
        check(click("gui.realmnotifications.history"),"Missing journal tab");bounds(s);
        s.mouseScrolled(40,120,0,-20);
        check(click("gui.realmnotifications.clear"),"Missing clear journal button");check(NotificationsClient.history().isEmpty(),"Journal clear failed");
        NotificationsClient.resetSession();
        check(NotificationsClient.history().isEmpty()&&NotificationsClient.pendingCount()==0&&NotificationsClient.visibleCount()==0,"Session reset retained private data");
        CHECKS.add("Preferences round-trip, sound disabled, all5 anchors,320x240 widget bounds, journal scrolling/clear and private session reset passed.");
        mc.setScreen(null);
    }
    private static void stressAndRestore() {
        var mc=Minecraft.getInstance();NotificationsClient.resetSession();
        for(int i=0;i<140;i++)NotificationsClient.receive("info",Component.literal("Realm dispatch "+i),Component.literal("A journal entry from the realm. ".repeat(13)));
        check(NotificationsClient.pendingCount()==32&&NotificationsClient.history().size()==100,"Queue/history limits failed");
        // Production render drains to3, then shrinks to1 with the queue filled again.
        var target=mc.getMainRenderTarget();var graphics=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
        NotificationsClient.render(graphics,800,450);
        for(int i=0;i<32;i++)NotificationsClient.receive("info",Component.literal("Queued dispatch "+i),Component.literal("Queued"));
        NotificationsClient.render(graphics,320,180);graphics.flush();
        check(NotificationsClient.visibleCount()==1&&NotificationsClient.pendingCount()<=32,"Shrinking viewport broke queue limit");
        check(NotificationsClient.soundsPlayed()==0,"Sound played with disabled preference");
        var history=new NotificationScreen(null,true);mc.setScreen(history);history.resize(mc,320,240);bounds(history);
        history.mouseScrolled(50,120,0,-1000);history.render(graphics,0,0,0);graphics.flush();
        CHECKS.add("Compact native journal rendered and scrolled all100 entries, including long wrapped bodies.");
        mc.setScreen(null);
        NotificationsClient.resetSession();sendDemo();
        CHECKS.add("Native production renderer enforced visible/32pending/100history limits, including3-to1 resize with fullqueue; muted mode played no sound.");
    }
    private static void capture(String name)throws Exception {
        Files.createDirectories(OUT);
        try(var screenshot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            check(screenshot.getWidth()==1600&&screenshot.getHeight()==900,"Framebuffer size mismatch");screenshot.writeToFile(OUT.resolve(name));
        }CAPTURES.add(name);
    }
    private static void complete(boolean success,String message) {
        done=true;
        try {Files.createDirectories(OUT);Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
            "success",success,"message",message,"step",step,"checks",CHECKS,"screenshots",CAPTURES,"actual_minecraft_client",true,"actual_integrated_server",true,"real_s2c_transport",true,"demonstration_event_text",true)));}
        catch(Exception failure){failure.printStackTrace();}
        System.out.println(success?"NOTIFICATIONS_CLIENT_PASSED":"NOTIFICATIONS_CLIENT_FAILED "+message);Minecraft.getInstance().stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(done)return;var mc=Minecraft.getInstance();long now=System.currentTimeMillis();
        try {
            if(serverFailure!=null)throw new IllegalStateException("Server fixture failed",serverFailure);
            check(now-START<420000,"Visual QA timed out");if(now<next||mc.getOverlay()!=null)return;next=now+500;
            if(lastStep!=step){lastStep=step;stepStart=now;}else check(now-stepStart<90000,"Visual QA stalled at"+step);
            switch(step) {
                case 0->{
                    if(mc.screen instanceof TitleScreen){mc.getWindow().setWindowed(1600,900);mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);mc.resizeDisplay();CreateWorldScreen.openFresh(mc,mc.screen);step++;}
                    else if(mc.screen!=null&&mc.screen.getClass().getSimpleName().contains("Accessibility"))click("gui.continue");
                }
                case 1->{if(mc.screen instanceof CreateWorldScreen world){world.getUiState().setName("Realm Notifications presentation QA");world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);world.getUiState().setAllowCommands(true);world.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);check(click("selectWorld.create"),"Missing createworld button");step++;}}
                case 2->{if(mc.level!=null&&mc.player!=null&&mc.getSingleplayerServer()!=null){prepare();step++;}}
                case 3->{if(!ready)return;quietCompanionCreation();mc.setScreen(null);mc.gui.getChat().clearMessages(true);verifyCompactAndPreferences();sendDemo();next=now+1800;step++;}
                case 4->{
                    if(!sent||NotificationsClient.history().size()<3)return;quietCompanionCreation();mc.setScreen(null);
                    check(NotificationsClient.visibleCount()==3,"Three real S2C cards did not reach production HUD");
                    CHECKS.add("Three genuine server API calls travelled over optional S2C packets into the real integrated-world HUD. Financial text is demonstration data; no money is minted.");
                    next=now+1200;step++;
                }
                case 5->{capture("realm-notifications-hud.png");NotificationsClient.preferences().position=NotificationPreferences.Position.TOP_CENTER;NotificationsClient.clearCards();sendDemo();next=now+1500;step++;}
                case 6->{if(!sent)return;capture("realm-notifications-center.png");stressAndRestore();next=now+1200;step++;}
                case 7->{if(!sent)return;mc.setScreen(new NotificationScreen(null,false));next=now+1000;step++;}
                case 8->{capture("realm-notifications-preferences.png");check(click("gui.realmnotifications.history"),"Missing history tab");next=now+1000;step++;}
                case 9->{capture("realm-notifications-journal.png");complete(true,"Actual integrated-world HUD, realS2C, original pixel artwork, native preferences/journal and compactqueue/sound/session checks passed.");}
                default->throw new IllegalStateException("Unexpected stage");
            }
        }catch(Throwable failure){failure.printStackTrace();complete(false,failure.toString());}
    }
}
