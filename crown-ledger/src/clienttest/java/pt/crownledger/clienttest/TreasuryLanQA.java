package pt.crownledger.clienttest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.multiplayer.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import pt.crownledger.GovernanceConfig;
import pt.crownledger.block.BlockRegistry;
import pt.crownledger.client.*;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.*;
import pt.crownledger.service.*;

/** Opt-in QA: two actual Minecraft processes, real TCP LAN, real menus and production packets. */
@EventBusSubscriber(modid="kingdomtreasury",value=Dist.CLIENT)
public final class TreasuryLanQA {
    private static final String ROLE=System.getProperty("treasury.role","host");
    private static final Path OUT=Path.of(System.getProperty("treasury.qaOutput","qa-results/treasury-lan")).toAbsolutePath();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> SHOTS=new ArrayList<>(),CHECKS=new ArrayList<>();
    private static final BlockPos CHEST=new BlockPos(0,161,0);
    private static final String FREE_TITLE="New bridge by the village";
    private static final String FREE_DESCRIPTION="Build a public bridge across the river near the village.\nUse stone pillars, wooden railings and lighting. Keep the passage open to everyone.\nThe vote asks for citizens' approval; it does not spend treasury funds.";
    private static final long START=System.currentTimeMillis();
    private static Process child;
    private static volatile Throwable failure;private static volatile boolean ready;
    private static String folder;private static int stage,lastStage=-1,guestStep;private static long next,stageAt;private static boolean booted,done;
    private static boolean freeBallotReady,freeHistoryReady;
    private static UUID guest,firstProposal;private static String seen="";private static Vec3 movedFrom;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static void check(boolean okay,String text){if(!okay)throw new IllegalStateException(text);}
    private static void write(String name,Object value)throws Exception{Files.createDirectories(OUT);Path tmp=Files.createTempFile(OUT,"treasury-qa-",".tmp");Files.writeString(tmp,JSON.toJson(value));Files.move(tmp,OUT.resolve(name),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    private static void server(Runnable task){ready=false;mc().getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable t){failure=t;}});}
    private static ServerPlayer host(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    private static TreasuryData data(){return TreasuryData.get(mc().getSingleplayerServer());}
    private static boolean press(String key){if(mc().screen==null)return false;String text=Component.translatable(key).getString();for(var c:List.copyOf(mc().screen.children()))if(c instanceof AbstractButton b&&b.active&&b.getMessage().getString().equals(text)){b.onPress();return true;}return false;}
    private static boolean click(String key){return press("realm.treasury."+key);}
    private static void shot(String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}SHOTS.add(name);}
    private static JsonObject poll(){return GovernanceClient.state.has("poll")?GovernanceClient.state.getAsJsonObject("poll"):null;}
    private static void key(){KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));}
    private static void quiet(){try{var f=Class.forName("pt.cronicasrp.client.ClientState").getDeclaredField("pending");f.setAccessible(true);f.setInt(null,0);var m=Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset");m.setAccessible(true);m.invoke(null);}catch(ReflectiveOperationException ignored){}}
    private static void setup(){mc().getWindow().setWindowed(1600,900);mc().options.guiScale().set(2);mc().options.renderDistance().set(3);mc().options.simulationDistance().set(5);mc().options.pauseOnLostFocus=false;mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc().resizeDisplay();}
    private static void openChest(){mc().setScreen(null);mc().gameMode.useItemOn(mc().player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(CHEST),Direction.NORTH,CHEST,false));}
    private static void crown(ServerPlayer p)throws Exception{
        if(!net.neoforged.fml.ModList.get().isLoaded("realmcouncil")){data().treasury.setStandaloneManager(p.getUUID(),p.getUUID(),System.currentTimeMillis());return;}
        var type=Class.forName("pt.realmcouncil.CouncilData");Object d=type.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,p.getServer());Object state=type.getField("state").get(d);
        var mt=Class.forName("pt.realmcouncil.domain.CouncilState$Mandate");Object m=mt.getConstructor().newInstance();mt.getField("playerId").set(m,p.getUUID());mt.getField("electionId").set(m,UUID.randomUUID());mt.getField("title").set(m,"President");mt.getField("name").set(m,"Aetherion");long now=System.currentTimeMillis();for(var value:Map.of("beginsAt",now-1000,"endsAt",now+3600000,"claimedAt",now-500).entrySet())mt.getField(value.getKey()).setLong(m,value.getValue());state.getClass().getField("mandate").set(state,m);((net.minecraft.world.level.saveddata.SavedData)d).setDirty();
    }
    private static void prepare(){server(()->{try{
        var p=host();var level=p.serverLevel();var s=p.getServer();folder=s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,s);level.setDayTime(1000);
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)level.setBlockAndUpdate(new BlockPos(x,160,z),Blocks.STONE_BRICKS.defaultBlockState());
        for(int x=-5;x<=5;x++)for(int y=161;y<165;y++)level.setBlockAndUpdate(new BlockPos(x,y,4),y==164?Blocks.SPRUCE_STAIRS.defaultBlockState():Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(CHEST,BlockRegistry.CROWN_CHEST.get().defaultBlockState());p.teleportTo(level,.5,161,-3,0,12);p.setGameMode(GameType.SURVIVAL);p.getFoodData().setFoodLevel(20);
        data().treasury=new Treasury();data().governance=new Governance();data().income=new IncomeLedger();crown(p);data().treasury.addFunds(Treasury.Currency.EMERALD,1000,Treasury.Kind.DEPOSIT,p.getUUID(),"Controlled QA starting funds",System.currentTimeMillis());data().setDirty();
        GovernanceConfig.VOTING_SECONDS.set(45);GovernanceConfig.COOLDOWN_SECONDS.set(0);
        s.setUsesAuthentication(false);int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}check(s.publishServer(GameType.SURVIVAL,false,port),"LAN publication failed");write("join.json",Map.of("port",port));
    }catch(Exception e){throw new IllegalStateException(e);}});}
    private static void spawn()throws Exception{
        List<String> args=new ArrayList<>(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());args.add(0,"-Dtreasury.role=guest");args.add("-cp");args.add(System.getProperty("java.class.path"));args.add("net.neoforged.devlaunch.Main");
        for(String line:Files.readAllLines(OUT.getParent().getParent().resolve("build/moddev/clientRunProgramArgs.txt"))){line=line.strip();if(!line.isEmpty()&&!line.startsWith("#"))args.add(line.startsWith("\"")&&line.endsWith("\"")?line.substring(1,line.length()-1):line);}
        Path run=OUT.resolve("guest-run");Files.createDirectories(run);option(args,"--gameDir",run.toString());option(args,"--username","TreasuryGuest");option(args,"--uuid",UUID.nameUUIDFromBytes("OfflinePlayer:TreasuryGuest".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-",""));option(args,"--accessToken","0");args.add(0,Path.of(System.getProperty("java.home"),"bin","java").toString());child=new ProcessBuilder(args).directory(run.toFile()).redirectErrorStream(true).redirectOutput(OUT.resolve("guest-client.log").toFile()).start();
    }
    private static void option(List<String> args,String name,String value){int i=args.indexOf(name);if(i<0){args.add(name);args.add(value);}else args.set(i+1,value);}
    private static void guest()throws Exception{
        if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))press("gui.continue");
        if(!booted&&mc().screen instanceof TitleScreen&&Files.exists(OUT.resolve("join.json"))){setup();booted=true;int port=JsonParser.parseString(Files.readString(OUT.resolve("join.json"))).getAsJsonObject().get("port").getAsInt();String a="127.0.0.1:"+port;ConnectScreen.startConnecting(new TitleScreen(),mc(),ServerAddress.parseString(a),new ServerData("Realm Treasury LAN QA",a,ServerData.Type.LAN),false,null);}
        if(mc().player==null)return;if(mc().screen!=null&&!(mc().screen instanceof GovernanceScreen))return;write("guest-connected.json",Map.of("id",mc().player.getUUID()));var p=poll();if(p==null)return;String id=p.get("id").getAsString();
        if(!seen.equals(id)){seen=id;guestStep=0;check(mc().screen==null,"Poll forcibly opened a guest menu: "+String.valueOf(mc().screen));check(!GovernanceClient.state.get("canPropose").getAsBoolean(),"Ordinary guest has spending controls");check(!p.has("ballots")&&!p.has("eligible")&&!p.has("yes"),"Private choices leaked");}
        if(guestStep==0){shot("guest-vote-hud.png");key();guestStep++;return;}
        if(!(mc().screen instanceof GovernanceScreen))return;
        boolean benefit=p.has("offer");
        if(guestStep==1){
            if(!benefit){check(p.get("question").getAsString().equals(FREE_TITLE)&&p.get("description").getAsString().equals(FREE_DESCRIPTION),"Guest received altered proposal text");
                check(mc().screen.children().stream().anyMatch(c->c instanceof DescriptionBox box&&box.getValue().equals(FREE_DESCRIPTION)),"Guest cannot read the full description");}
            check(click(benefit?"vote.no":"vote.yes"),"Guest vote button missing");guestStep++;return;}
        if(guestStep==2&&p.has("ownVote")){
            if(benefit){check(!p.get("ownVote").getAsBoolean(),"First guest vote did not reach server");check(click("vote.yes"),"Guest could not change vote");guestStep++;}
            else{write("guest-advisory-voted.json",Map.of("id",id));mc().screen.onClose();guestStep=4;}
        }else if(guestStep==3&&p.has("ownVote")&&p.get("ownVote").getAsBoolean()){write("guest-benefit-voted.json",Map.of("id",id));mc().screen.onClose();guestStep=4;}
    }
    private static void compact(){var screen=mc().screen;check(screen instanceof GovernanceScreen,"Missing menu for compact checks");for(int[] size:List.of(new int[]{320,240},new int[]{480,270},new int[]{800,450})){screen.resize(mc(),size[0],size[1]);for(String tab:List.of("benefits","history","vote")){click("tab."+tab);for(var c:screen.children())if(c instanceof AbstractWidget w)check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=size[0]&&w.getY()+w.getHeight()<=size[1],"Control escapes compact screen");}}screen.resize(mc(),mc().getWindow().getGuiScaledWidth(),mc().getWindow().getGuiScaledHeight());CHECKS.add("Compact controls at 320x240, 480x270 and 800x450");}
    private static void finish(boolean okay,String message){if(!okay)try{shot(ROLE+"-failure.png");write(ROLE+"-state.json",Map.of("state",GovernanceClient.state,"screen",String.valueOf(mc().screen)));}catch(Exception ignored){}done=true;mc().options.keyUp.setDown(false);try{write(ROLE.equals("host")?"result.json":"guest-failure.json",Map.of("success",okay,"message",message,"stage",stage,"actualMinecraftClients",2,"actualTcpLan",true,"authenticatedClients",false,"screenshots",SHOTS,"checks",CHECKS));}catch(Exception e){e.printStackTrace();}if(child!=null)child.destroy();System.out.println("TREASURY_LAN_QA success="+okay+" "+message);mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(done)return;long now=System.currentTimeMillis();try{
        if(failure!=null)throw new IllegalStateException("Server check failed",failure);check(now-START<600000,"QA timeout");if(mc().player!=null)quiet();if(mc().getOverlay()!=null||now<next)return;next=now+350;GLFW.glfwSetCursorPos(mc().getWindow().getWindow(),1,1);
        if(ROLE.equals("guest")){guest();return;}if(stage>=4&&Files.exists(OUT.resolve("guest-failure.json")))throw new IllegalStateException(Files.readString(OUT.resolve("guest-failure.json")));
        if(lastStage!=stage){lastStage=stage;stageAt=now;System.out.println("TREASURY_LAN_STAGE "+stage);}check(now-stageAt<120000,"Stalled stage "+stage);
        switch(stage){
            case 0->{if(mc().screen instanceof TitleScreen){setup();Files.createDirectories(OUT);for(String f:List.of("result.json","join.json","guest-connected.json","guest-failure.json","guest-benefit-voted.json","guest-advisory-voted.json"))Files.deleteIfExists(OUT.resolve(f));CreateWorldScreen.openFresh(mc(),mc().screen);stage++;}else if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))press("gui.continue");}
            case 1->{if(mc().screen instanceof CreateWorldScreen s){s.getUiState().setName("Realm Treasury LAN QA");s.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);s.getUiState().setAllowCommands(true);s.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);check(press("selectWorld.create"),"Cannot create world");stage++;}}
            case 2->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){prepare();stage++;}}
            case 3->{if(ready){spawn();stage++;}}
            case 4->{if(Files.exists(OUT.resolve("guest-connected.json"))){guest=UUID.fromString(JsonParser.parseString(Files.readString(OUT.resolve("guest-connected.json"))).getAsJsonObject().get("id").getAsString());server(()->{var g=mc().getSingleplayerServer().getPlayerList().getPlayer(guest);check(g!=null&&!g.hasPermissions(2),"Guest must be an ordinary non-admin");g.teleportTo(g.serverLevel(),3.5,161,-3,0,0);});stage++;}}
            case 5->{if(ready){openChest();stage++;}}
            case 6->{if(mc().screen instanceof TreasuryScreen){check(press("gui.kingdomtreasury.public_votes"),"Chest has no public votes button");stage++;}}
            case 7->{if(mc().screen instanceof GovernanceScreen){check(click("tab.propose"),"President cannot propose at chest");check(mc().screen.children().stream().anyMatch(c->c instanceof MultiLineEditBox),"Free vote is not the default proposal form");check(click("type.benefit"),"Benefit option missing");stage++;}}
            case 8->{shot("treasury-proposal.png");check(click("announce"),"Cannot announce quoted benefit");stage++;}
            case 9->{if(poll()!=null){firstProposal=UUID.fromString(poll().get("id").getAsString());server(()->check(data().treasury.balance(Treasury.Currency.EMERALD)==600&&data().governance.current().eligible().size()==2,"Announcement did not reserve exact funds and freeze two citizens"));mc().setScreen(null);stage++;}}
            case 10->{if(!ready)return;check(mc().screen==null,"Announcement blocked controls");movedFrom=mc().player.position();mc().options.keyUp.setDown(true);next=now+1500;stage++;}
            case 11->{mc().options.keyUp.setDown(false);check(mc().player.position().distanceTo(movedFrom)>.1,"Vote notice blocked movement");shot("treasury-vote-hud.png");CHECKS.add("Unforced HUD leaves movement active");key();stage++;}
            case 12->{if(mc().screen instanceof GovernanceScreen){check(click("vote.yes"),"F8 ballot cannot submit vote");stage++;}}
            case 13->{if(poll()!=null&&!poll().has("ownVote")&&GovernanceClient.state.has("message")&&GovernanceClient.state.get("message").getAsString().equals("realm.treasury.error.rate")){click("vote.yes");return;}if(poll()!=null&&poll().has("ownVote")&&poll().get("ownVote").getAsBoolean()&&Files.exists(OUT.resolve("guest-benefit-voted.json"))){shot("treasury-ballot.png");compact();server(()->{var g=data().governance;check(g.current().yes()==2&&g.current().ballots().size()==2,"Changed guest vote counted twice");try{var tag=NbtIo.readCompressed(mc().getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve("data/crown_ledger.dat"),NbtAccounter.create(32000000));var restored=TreasuryData.load(tag.getCompound("data"),mc().getSingleplayerServer().registryAccess());check(restored.governance.current().yes()==2&&restored.treasury.balance(Treasury.Currency.EMERALD)==600,"Durable ballots and reservation mismatch");}catch(Exception e){throw new IllegalStateException(e);}});stage++;}}
            case 14->{if(ready&&poll()==null&&GovernanceClient.state.has("active")&&!GovernanceClient.state.getAsJsonArray("active").isEmpty()){check(click("tab.benefits"),"Benefits menu missing");stage++;}}
            case 15->{shot("treasury-benefits.png");check(click("tab.history"),"History menu missing");server(()->{check(data().governance.history().getFirst().yes()==2&&data().treasury.balance(Treasury.Currency.EMERALD)==600,"Approved benefit cost or tally incorrect");GovernanceConfig.VOTING_SECONDS.set(30);});stage++;}
            case 16->{if(ready){shot("treasury-decisions.png");check(click("tab.propose"),"Next proposal menu missing");if(mc().screen.children().stream().noneMatch(c->c instanceof MultiLineEditBox))check(click("type.general"),"Free vote option missing");for(var c:mc().screen.children()){if(c instanceof EditBox field)field.setValue(FREE_TITLE);else if(c instanceof MultiLineEditBox field)field.setValue(FREE_DESCRIPTION);}stage++;}}
            case 17->{shot("treasury-free-proposal.png");check(click("announce"),"Cannot announce general consultation");stage++;}
            case 18->{if(poll()!=null&&!poll().has("offer")){
                check(poll().get("question").getAsString().equals(FREE_TITLE)&&poll().get("description").getAsString().equals(FREE_DESCRIPTION),"Title and description did not round-trip through the server");
                if(!freeBallotReady){check(click("tab.vote"),"Ballot tab missing");freeBallotReady=true;next=now+500;return;}check(mc().screen.children().stream().anyMatch(c->c instanceof DescriptionBox box&&box.getValue().equals(FREE_DESCRIPTION)),"Full description missing from ballot");
                shot("treasury-free-ballot.png");compact();check(click("vote.no"),"No vote missing");stage++;
            }}
            case 19->{if(poll()==null&&GovernanceClient.state.getAsJsonArray("history").size()==2){check(Files.exists(OUT.resolve("guest-advisory-voted.json")),"Guest did not vote in consultation");server(()->{var r=data().governance.history().getFirst();check(r.outcome()==Governance.Outcome.REJECTED&&r.yes()==1&&r.no()==1&&r.question().equals(FREE_TITLE)&&r.description().equals(FREE_DESCRIPTION)&&data().treasury.balance(Treasury.Currency.EMERALD)==600,"General tied poll spent money or passed");});stage++;}}
            case 20->{if(ready){if(!freeHistoryReady){check(click("tab.history"),"History unavailable");check(click("read"),"Archived poll reader unavailable");freeHistoryReady=true;next=now+500;return;}
                check(mc().screen.children().stream().anyMatch(c->c instanceof DescriptionBox box&&box.getValue().equals(FREE_DESCRIPTION)),"Archived description is missing");
                shot("treasury-free-history.png");CHECKS.add("Free poll title, multiline description, ballot and archived reader round-trip over actual LAN");CHECKS.add("Two real LAN voters, secret mutable ballots, exact reservation, majority benefit, tied free consultation");child.destroy();child.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);mc().level.disconnect();mc().disconnect(new TitleScreen());stage++;}}
            case 21->{if(mc().level==null&&mc().getSingleplayerServer()==null){mc().createWorldOpenFlows().openWorld(folder,()->mc().setScreen(new TitleScreen()));stage++;}}
            case 22->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){server(()->check(data().treasury.balance(Treasury.Currency.EMERALD)==600&&data().governance.history().size()==2&&data().governance.active().size()==1&&data().governance.active().getFirst().proposalId().equals(firstProposal)&&data().governance.history().getFirst().description().equals(FREE_DESCRIPTION),"World reopening lost or charged a decision again"));stage++;}}
            case 23->{if(ready){CHECKS.add("Actual LAN world closed and reopened; benefits, tally history and balance retained");finish(true,"Actual menus, packets, two-player LAN, persistence and compact controls passed");}}
        }
    }catch(Throwable t){t.printStackTrace();finish(false,t.toString());}}
}
