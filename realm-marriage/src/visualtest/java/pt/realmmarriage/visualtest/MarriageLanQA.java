package pt.realmmarriage.visualtest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.gui.screens.multiplayer.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import pt.realmmarriage.api.*;
import pt.realmmarriage.block.*;
import pt.realmmarriage.compat.WalletBridge;
import pt.realmmarriage.client.*;
import pt.realmmarriage.data.MarriageData;
import pt.realmmarriage.domain.MarriageLedger;
import pt.realmmarriage.domain.MarriageLedger.*;
import pt.realmmarriage.service.*;

/** Four actual Minecraft processes over a published integrated TCP server, using offline test identities. */
@EventBusSubscriber(modid="realmmarriage",value=Dist.CLIENT)
public final class MarriageLanQA {
    private static final String ROLE=System.getProperty("marriage.role","host");
    private static final Path OUT=Path.of(System.getProperty("marriage.qaOutput","qa-results/marriage-client")).toAbsolutePath();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<Process> CHILDREN=new ArrayList<>();
    private static final List<String> SHOTS=new ArrayList<>(),ACTIONS=new ArrayList<>(),CHECKS=new ArrayList<>();
    private static final Map<String,UUID> PLAYERS=new HashMap<>();
    private static final BlockPos ALTAR=new BlockPos(0,161,0);
    private static final long START=System.currentTimeMillis();
    private static volatile boolean conditionPending,conditionDone,conditionValue;
    private static volatile Throwable failure;private static volatile boolean ready;
    private static volatile String folder;private static volatile UUID marriage;
    private static long next,stageAt,commandId,captureAt;private static int stage,lastStage=-1,localStep;
    private static long currentCommand=-1,commandStarted,commandRevision,diagnosticId=-1;private static boolean done,booted,controlsFree,altarShotReady;
    private static JsonObject command;
    private static long initialBank;private static int port,certificateSlot=-1;private static Vec3 moveStart;private static long movingUntil;
    private MarriageLanQA(){}
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static void write(Path path,Object data)throws Exception{Files.createDirectories(path.getParent());Path tmp=Files.createTempFile(path.getParent(),"marriage-qa-",".tmp");Files.writeString(tmp,JSON.toJson(data));Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    private static JsonObject read(Path path)throws Exception{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    private static void server(Runnable task){ready=false;mc().getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable t){failure=t;}});}
    private static boolean serverCondition(BooleanSupplier test){if(conditionPending)return false;if(conditionDone){conditionDone=false;if(conditionValue)return true;}conditionPending=true;mc().getSingleplayerServer().execute(()->{try{conditionValue=test.getAsBoolean();}catch(Throwable t){failure=t;}finally{conditionPending=false;conditionDone=true;}});return false;}
    private static ServerPlayer p(String role){return Objects.requireNonNull(mc().getSingleplayerServer().getPlayerList().getPlayer(PLAYERS.get(role)));}
    private static Union union(){return MarriageData.get(mc().getSingleplayerServer()).ledger.union(marriage);}
    private static void assertStage(Phase phase){server(()->check(union().phase==phase,"Expected "+phase+", received "+union().phase+" "+union().error));}
    private static Object get(Object target,String key)throws Exception{var f=target.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(target);}
    private static boolean click(String key){if(mc().screen==null)return false;String text=Component.translatable(key).getString();for(var child:List.copyOf(mc().screen.children()))if(child instanceof AbstractButton b&&b.active&&b.getMessage().getString().equals(text)){b.onPress();ACTIONS.add(key);return true;}return false;}
    private static boolean clickKey(String key){return click("marriage."+key);}
    private static void screenshot(String name)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){check(image.getWidth()==1600&&image.getHeight()==900,"Unexpected framebuffer");image.writeToFile(OUT.resolve(name));}SHOTS.add(name);}
    private static void quiet(){try{var f=Class.forName("pt.cronicasrp.client.ClientState").getDeclaredField("pending");f.setAccessible(true);f.setInt(null,0);var reset=Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(null);}catch(Exception ignored){}}
    private static void setupClient(){mc().getWindow().setWindowed(1600,900);mc().options.pauseOnLostFocus=false;mc().options.guiScale().set(2);mc().options.renderDistance().set(3);mc().options.simulationDistance().set(5);mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc().resizeDisplay();MarriageClient.preferences().volume=.4f;}
    @SuppressWarnings("unchecked") private static void profile(ServerPlayer p,long amount,String name)throws Exception{AttachmentType<Object> attachment=(AttachmentType<Object>)((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();Object value=Class.forName("pt.cronicasrp.domain.CharacterProfile").getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,name,28,"origins:human","Private wedding QA biography","Private wedding QA signature","Comum",amount,amount,"");p.setData(attachment,value);WalletBridge.sync(p);
        Object rewards=Class.forName("pt.realmrewards.data.RewardData").getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,p.getServer());Object ledger=rewards.getClass().getField("ledger").get(rewards);ledger.getClass().getMethod("baseline",UUID.class,Collection.class).invoke(ledger,p.getUUID(),p.getServer().getAdvancements().getAllAdvancements().stream().map(a->a.id().toString()).toList());Object account=ledger.getClass().getMethod("account",UUID.class).invoke(ledger,p.getUUID());long pending=(long)account.getClass().getMethod("pending").invoke(account);if(pending>0)ledger.getClass().getMethod("paid",UUID.class,long.class).invoke(ledger,p.getUUID(),pending);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static long bank()throws Exception{Object d=Class.forName("pt.crownledger.data.TreasuryData").getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,mc().getSingleplayerServer());Object t=d.getClass().getField("treasury").get(d);Class<Enum> currency=(Class<Enum>)Class.forName("pt.crownledger.domain.Treasury$Currency");return(long)t.getClass().getMethod("balance",currency).invoke(t,Enum.valueOf(currency,"CHRONICLES"));}
    private static void publish()throws Exception{var host=mc().getSingleplayerServer();host.setUsesAuthentication(false);if(!host.isPublished()){try(var s=new java.net.ServerSocket(0)){port=s.getLocalPort();}check(host.publishServer(GameType.SURVIVAL,false,port),"LAN publication failed");}write(OUT.resolve("join.json"),Map.of("port",port));}
    private static void connect()throws Exception{int port=read(OUT.resolve("join.json")).get("port").getAsInt();String address="127.0.0.1:"+port;ConnectScreen.startConnecting(new TitleScreen(),mc(),ServerAddress.parseString(address),new ServerData("Marriage LAN QA",address,ServerData.Type.LAN),false,null);}
    private static List<String> arguments()throws Exception{
        List<String> result=new ArrayList<>(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        result.add("-cp");result.add(System.getProperty("java.class.path"));result.add("net.neoforged.devlaunch.Main");
        Path program=OUT.getParent().getParent().resolve("build/moddev/clientRunProgramArgs.txt");
        for(String line:Files.readAllLines(program)){line=line.strip();if(!line.isEmpty()&&!line.startsWith("#"))result.add(line.startsWith("\"")&&line.endsWith("\"")?line.substring(1,line.length()-1):line);}
        return result;
    }
    private static void option(List<String> args,String name,String value){int i=args.indexOf(name);if(i<0){args.add(name);args.add(value);}else args.set(i+1,value);}
    private static void children()throws Exception{List<String> original=arguments();for(String role:List.of("spouse","priest","guest")){List<String> args=new ArrayList<>(original);args.add(0,"-Dmarriage.role="+role);String username="Marriage"+role;option(args,"--username",username);option(args,"--uuid",UUID.nameUUIDFromBytes(("OfflinePlayer:"+username).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-",""));option(args,"--accessToken","0");Path run=OUT.resolve("runs/"+role);Files.createDirectories(run);option(args,"--gameDir",run.toString());List<String> launch=new ArrayList<>();launch.add(Path.of(System.getProperty("java.home"),"bin","java").toString());launch.addAll(args);ProcessBuilder builder=new ProcessBuilder(launch).directory(run.toFile()).redirectErrorStream(true).redirectOutput(OUT.resolve(role+"-client.log").toFile());CHILDREN.add(builder.start());System.out.println("MARRIAGE_CHILD_STARTED "+role);}}
    private static void prepareWorld(){PLAYERS.put("host",mc().player.getUUID());server(()->{try{var player=p("host");var level=player.serverLevel();var host=player.getServer();level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false,host);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,host);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,host);level.setDayTime(1000);for(int x=-12;x<=12;x++)for(int z=-8;z<=12;z++){level.setBlock(new BlockPos(x,160,z),Blocks.SPRUCE_PLANKS.defaultBlockState(),3);for(int y=161;y<174;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}for(int x=-11;x<=11;x++)for(int y=161;y<=169;y++)level.setBlock(new BlockPos(x,y,-5),(x%5==0||y==169?Blocks.STRIPPED_DARK_OAK_LOG:Blocks.STONE_BRICKS).defaultBlockState(),3);for(int x:new int[]{-8,-4,4,8}){for(int y=162;y<=168;y++)level.setBlock(new BlockPos(x,y,-4),Blocks.WHITE_STAINED_GLASS.defaultBlockState(),3);level.setBlock(new BlockPos(x,161,-3),Blocks.ROSE_BUSH.defaultBlockState(),3);}for(int x=-1;x<=1;x++)for(int z=3;z<=10;z++)level.setBlock(new BlockPos(x,161,z),Blocks.RED_CARPET.defaultBlockState(),3);for(int x:new int[]{-5,5})for(int z:new int[]{3,6,9}){level.setBlock(new BlockPos(x,161,z),Blocks.DARK_OAK_STAIRS.defaultBlockState(),3);level.setBlock(new BlockPos(x,162,z+1),Blocks.DARK_OAK_SLAB.defaultBlockState(),3);}for(int x:new int[]{-10,10})level.setBlock(new BlockPos(x,161,0),Blocks.LANTERN.defaultBlockState(),3);player.setYRot(180);player.setPos(0,161,4);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BlockRegistry.ALTAR_ITEM.get()));var hit=new BlockHitResult(Vec3.atCenterOf(ALTAR.below()).add(0,.5,0),Direction.UP,ALTAR.below(),false);check(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Actual altar placement failed");check(BlockRegistry.ALTAR.get().validStructure(level,ALTAR),"Incomplete altar");folder=host.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();publish();}catch(Exception e){throw new IllegalStateException(e);}});}
    private static void preparePlayers(){server(()->{try{for(ServerPlayer player:mc().getSingleplayerServer().getPlayerList().getPlayers())for(String role:List.of("spouse","priest","guest"))if(player.getGameProfile().getName().equals("Marriage"+role))PLAYERS.put(role,player.getUUID());check(PLAYERS.size()==4,"Four actual players did not join");int i=0;for(String role:List.of("host","spouse","priest","guest")){var p=p(role);profile(p,role.equals("host")?50000:role.equals("spouse")?30001:10000,Map.of("host","Aetherion","spouse","Selene","priest","Arrozeira","guest","Liora").get(role));p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(BlockRegistry.ENGAGEMENT_RING.get()));p.setGameMode(GameType.SURVIVAL);if(role.equals("host"))mc().getSingleplayerServer().getPlayerList().op(p.getGameProfile());p.connection.teleport(role.equals("host")?-.7:role.equals("spouse")?.7:role.equals("priest")?2.5:-2.5,161,role.equals("guest")?4:3,180,0);MarriageService.data(p);if(!role.equals("host"))check(!p.hasPermissions(2),"Celebrant and guests must not be administrators");i++;}initialBank=bank();mc().getSingleplayerServer().saveEverything(true,true,true);}catch(Exception e){throw new IllegalStateException(e);}});}
    private static void issue(String role,String op)throws Exception{issue(role,op,"");}
    private static void issue(String role,String op,String value)throws Exception{commandId++;write(OUT.resolve(role+"-command.json"),Map.of("id",commandId,"op",op,"value",value));}
    private static boolean acknowledged(String role)throws Exception{Path p=OUT.resolve(role+"-ack.json");if(!Files.exists(p))return false;JsonObject r=read(p);if(r.has("error"))throw new IllegalStateException(role+": "+r.get("error").getAsString());return r.get("id").getAsLong()==commandId;}
    private static void openAltar(){mc().setScreen(null);mc().gameMode.useItemOn(mc().player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ALTAR),Direction.SOUTH,ALTAR,false));}
    private static boolean page(String tab){if(!(mc().screen instanceof MarriageScreen screen)||!MarriageScreen.bool(screen.data(),"physical")){openAltar();return false;}return clickKey(tab);}
    @SuppressWarnings("unchecked") private static void form(String key,String value)throws Exception{Map<String,String> form=(Map<String,String>)get(mc().screen,"form");Map<String,EditBox> fields=(Map<String,EditBox>)get(mc().screen,"fields");if(fields.containsKey(key))fields.get(key).setValue(value);else form.put(key,value);}
    private static void command()throws Exception{Path path=OUT.resolve(ROLE+"-command.json");if(!Files.exists(path))return;JsonObject in=read(path);long id=in.get("id").getAsLong();if(id!=currentCommand){currentCommand=id;command=in;commandStarted=System.currentTimeMillis();localStep=0;}else if(localStep==999)return;if(System.currentTimeMillis()-commandStarted>15000&&diagnosticId!=id){diagnosticId=id;write(OUT.resolve(ROLE+"-stalled.json"),Map.of("command",command,"screen",mc().screen==null?"none":mc().screen.getClass().getName(),"view",mc().screen instanceof MarriageScreen screen?screen.data():new JsonObject()));screenshot(ROLE+"-stalled.png");}check(System.currentTimeMillis()-commandStarted<90000,"Command stalled: "+command);String op=command.get("op").getAsString(),value=command.get("value").getAsString();boolean success=false;
        if(localStep==997){if(!(mc().screen instanceof MarriageScreen screen)|| (boolean)get(screen,"waiting"))return;String message=MarriageScreen.str(screen.data(),"message");if(message.equals("marriage.error.stale")){localStep=0;return;}check(message.isBlank(),"Native action "+op+": "+message);if(MarriageScreen.number(screen.data(),"revision")<=commandRevision)return;write(OUT.resolve(ROLE+"-ack.json"),Map.of("id",id,"op",op));localStep=999;ACTIONS.add(op);return;}
        if(op.equals("disconnect")){if(mc().level!=null){mc().level.disconnect();mc().disconnect(new TitleScreen());}success=mc().level==null;}
        else if(op.equals("reconnect")){if(localStep==0){connect();localStep++;}success=mc().player!=null&&mc().level!=null;}
        else if(mc().player!=null&&mc().level!=null)switch(op){
            case "proposal"->{if(localStep==0){mc().setScreen(null);UUID target=UUID.fromString(value);var entity=mc().level.getPlayerByUUID(target);if(entity!=null){mc().gameMode.interact(mc().player,entity,InteractionHand.MAIN_HAND);success=true;}}}
            case "answer"->success=clickKey("accept");
            case "fee","configure","agree","invitation","start","next","continue","resume","speaker.objector","speaker.couple","ask","vow","witness","seal","documents","withdraw","deposit","consolidate","credits","divorce"->{String tab=Set.of("fee").contains(op)?"admin":op.equals("configure")?"terms":Set.of("withdraw","deposit","consolidate","credits").contains(op)?"account":Set.of("start","next","continue","resume","speaker.objector","speaker.couple","ask","vow","witness","seal").contains(op)?"ceremony":"home";
                if(localStep==0){if(page(tab))localStep++;return;}if(!(mc().screen instanceof MarriageScreen))return;commandRevision=MarriageScreen.number(((MarriageScreen)mc().screen).data(),"revision");
                if(op.equals("configure")){JsonObject setup=JsonParser.parseString(value).getAsJsonObject();form("priest",setup.get("priest").getAsString());form("witness0",setup.get("witness").getAsString());form("witness1","");form("mode","JOINT");form("shareA","50.00");success=clickKey("save");}
                else if(op.equals("fee")){form("fee",value);success=clickKey("save");}
                else if(Set.of("withdraw","deposit","credits").contains(op)){if(op.equals("credits")){var data=((MarriageScreen)mc().screen).data();value=new java.math.BigDecimal(MarriageScreen.number(data,"credits")).movePointLeft(2).toPlainString();}form("amount",value);success=clickKey(op.equals("credits")?"claim_credits":op);}
                else if(op.equals("divorce")){if(localStep==1){if(clickKey("divorce"))localStep++;return;}success=clickKey("confirm");}
                else success=clickKey(Map.of("invitation","accept","vow","i_do","witness","sign","documents","claim_documents").getOrDefault(op,op));
            }
            case "G"->{mc().setScreen(null);KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));success=true;}
            case "reason"->{if(mc().screen!=null&&mc().screen.getClass().getSimpleName().equals("ObjectionScreen")){((EditBox)get(mc().screen,"reason")).setValue(value);success=clickKey("send");}}
            case "certificate"->{if(localStep==0){mc().setScreen(null);certificateSlot=-1;for(int i=0;i<9;i++)if(mc().player.getInventory().getItem(i).is(BlockRegistry.CERTIFICATE.get()))certificateSlot=i;check(certificateSlot>=0,"Certificate is not in the hotbar");mc().player.getInventory().selected=certificateSlot;mc().options.keyShift.setDown(true);localStep++;return;}if(localStep==1){mc().gameMode.useItem(mc().player,InteractionHand.MAIN_HAND);localStep++;return;}success=mc().screen!=null&&mc().screen.getClass().getSimpleName().equals("IdentityScreen");if(success){screenshot(ROLE+"-identity-certificate.png");mc().options.keyShift.setDown(false);}}
            case "close"->{if(mc().screen!=null)mc().screen.onClose();success=mc().screen==null;}
            case "shot"->{if(localStep==0){if(mc().screen!=null)mc().screen.onClose();localStep++;return;}if(System.currentTimeMillis()-commandStarted<900)return;screenshot(value);success=true;}
            default->throw new IllegalStateException("Unknown QA command "+op);
        }
        if(success&&Set.of("fee","configure","agree","invitation","start","next","continue","resume","speaker.objector","speaker.couple","ask","vow","witness","seal","documents","withdraw","deposit","consolidate","credits","divorce").contains(op)){localStep=997;return;}
        if(success){write(OUT.resolve(ROLE+"-ack.json"),Map.of("id",id,"op",op));localStep=999;ACTIONS.add(op);}
    }
    private static void child(long now)throws Exception{if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))click("gui.continue");if(!booted&&mc().screen instanceof TitleScreen&&Files.exists(OUT.resolve("join.json"))){setupClient();booted=true;connect();}if(mc().player!=null)write(OUT.resolve(ROLE+"-connected.json"),Map.of("id",mc().player.getUUID().toString()));command();}
    private static boolean compactAndPreferences;
    private static void verifyCompactAndPreferences()throws Exception{
        MarriageScreen screen=(MarriageScreen)mc().screen;
        for(int[] size:List.of(new int[]{320,240},new int[]{480,270},new int[]{800,450})){
            screen.resize(mc(),size[0],size[1]);
            for(String tab:List.of("home","ceremony","terms","account","journal","effects","admin")){
                check(clickKey(tab),"Missing compact tab "+tab);
                for(int page=0;page<5;page++){
                    for(var child:screen.children())if(child instanceof AbstractWidget w)check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=size[0]&&w.getY()+w.getHeight()<=size[1],"Compact control escaped the screen");
                    screen.mouseScrolled(0,0,0,-1);
                }
            }
        }
        screen.resize(mc(),mc().getWindow().getGuiScaledWidth(),mc().getWindow().getGuiScaledHeight());check(clickKey("effects"),"Missing preferences");
        check(clickKey("on")&&!MarriageClient.preferences().enabled,"Animation toggle did not persist");
        check(clickKey("off")&&MarriageClient.preferences().enabled,"Animation toggle did not restore");
        check(clickKey("normal")&&MarriageClient.preferences().reduced,"Particle toggle failed");
        check(clickKey("reduced")&&!MarriageClient.preferences().reduced,"Particle toggle did not restore");
        JsonObject preferences=read(Path.of("config/realm-marriage-visuals.json"));check(preferences.get("enabled").getAsBoolean()&&!preferences.get("reduced").getAsBoolean(),"Preferences were not saved to disk");compactAndPreferences=true;
    }
    private static void done(boolean ok,String message){done=true;mc().options.keyUp.setDown(false);try{
        Map<String,Object> result=new LinkedHashMap<>();result.put("success",ok);result.put("message",message);result.put("stage",stage);result.put("actual_minecraft_client",true);result.put("actual_tcp_lan",stage>=5);result.put("actual_client_processes",1+CHILDREN.size());result.put("authenticated_clients",false);result.put("non_admin_celebrant_and_guests",stage>=5);result.put("synthetic_financial_snapshots",false);result.put("controlled_initial_funds",stage>=5);result.put("real_chronicles_wallet",stage>=42);result.put("real_crown_fee",stage>=42);result.put("real_identity_certificate",stage>=46);result.put("real_disconnect_during_ceremony",stage>=22);result.put("real_world_close_and_reopen",stage>=64);result.put("real_guest_key_objection",stage>=29);result.put("real_both_individual_vows",stage>=40);result.put("real_witness_consent",stage>=40);result.put("controls_free",controlsFree);result.put("compact_controls_and_local_preferences",compactAndPreferences);result.put("screenshots",SHOTS);result.put("checks",CHECKS);result.put("actions",ACTIONS);write(OUT.resolve(ROLE.equals("host")?"result.json":ROLE+"-failure.json"),result);
    }catch(Exception e){e.printStackTrace();}System.out.println(ok?"MARRIAGE_LAN_CLIENT_PASSED":"MARRIAGE_LAN_CLIENT_FAILED "+ROLE+" "+message);for(Process child:CHILDREN)child.destroy();mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(done)return;long now=System.currentTimeMillis();try{if(failure!=null)throw new IllegalStateException("Host verification failed",failure);check(now-START<1200000,"LAN test timed out");if(mc().player!=null)quiet();if(mc().screen!=null)GLFW.glfwSetCursorPos(mc().getWindow().getWindow(),1,1);if(now<next||mc().getOverlay()!=null)return;next=now+250;if(!ROLE.equals("host")){child(now);return;}if(stage>=4)for(String role:List.of("spouse","priest","guest"))if(Files.exists(OUT.resolve(role+"-failure.json")))throw new IllegalStateException(Files.readString(OUT.resolve(role+"-failure.json")));if(stage>=5)command();if(lastStage!=stage){lastStage=stage;stageAt=now;System.out.println("MARRIAGE_LAN_STAGE "+stage);}else check(now-stageAt<(stage==4?300000:150000),"Host stage stalled "+stage);
        switch(stage){
            case 0->{if(mc().screen instanceof TitleScreen){setupClient();Files.createDirectories(OUT);for(String role:List.of("host","spouse","priest","guest"))for(String suffix:List.of("-command.json","-ack.json","-failure.json","-connected.json"))Files.deleteIfExists(OUT.resolve(role+suffix));Files.deleteIfExists(OUT.resolve("result.json"));CreateWorldScreen.openFresh(mc(),mc().screen);stage++;}else if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))click("gui.continue");}
            case 1->{if(mc().screen instanceof CreateWorldScreen screen){screen.getUiState().setName("Realm Marriage LAN QA");screen.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);screen.getUiState().setAllowCommands(true);screen.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);check(click("selectWorld.create"),"Cannot create QA world");stage++;}}
            case 2->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){prepareWorld();stage++;}}
            case 3->{if(!ready)return;children();stage++;}
            case 4->{if(mc().getSingleplayerServer().getPlayerList().getPlayerCount()<4)return;preparePlayers();stage++;next=now+3000;}
            case 5->{if(!ready)return;if(!altarShotReady){altarShotReady=true;mc().setScreen(null);mc().getToasts().clear();mc().player.setYRot(-165);mc().player.setXRot(10);mc().options.hideGui=true;next=now+500;return;}screenshot("marriage-altar.png");mc().options.hideGui=false;issue("host","proposal",PLAYERS.get("spouse").toString());stage++;}
            case 6->{if(!acknowledged("host"))return;issue("spouse","answer");stage++;}
            case 7->{if(!acknowledged("spouse"))return;server(()->{Union u=MarriageData.get(mc().getSingleplayerServer()).ledger.current(PLAYERS.get("host"));check(u!=null&&u.phase==Phase.ENGAGED,"Proposal not consented");marriage=u.id;});stage++;}
            case 8->{if(!ready)return;issue("host","fee","50.00");stage++;}
            case 9->{if(!acknowledged("host"))return;server(()->check(MarriageData.get(mc().getSingleplayerServer()).ledger.settings.fee==5000,"Native fee GUI did not apply"));stage++;}
            case 10->{if(!ready)return;issue("host","configure",JSON.toJson(Map.of("priest",PLAYERS.get("priest").toString(),"witness",PLAYERS.get("guest").toString())));stage++;}
            case 11->{if(!acknowledged("host"))return;server(()->check(union().mode==Mode.JOINT&&union().priest.equals(PLAYERS.get("priest"))&&union().witnesses.equals(List.of(PLAYERS.get("guest"))),"Agreement GUI did not save"));stage++;}
            case 12->{if(!ready)return;screenshot("marriage-agreement.png");issue("host","agree");stage++;}
            case 13->{if(!acknowledged("host"))return;issue("spouse","agree");stage++;}
            case 14->{if(!acknowledged("spouse"))return;issue("priest","invitation");stage++;}
            case 15->{if(!acknowledged("priest"))return;issue("guest","invitation");stage++;}
            case 16->{if(!acknowledged("guest"))return;issue("priest","start");stage++;}
            case 17->{if(!acknowledged("priest"))return;assertStage(Phase.OPENING);issue("host","close");captureAt=now+1400;stage++;}
            case 18->{if(!ready||!acknowledged("host")||now<captureAt)return;check(mc().screen==null,"Opening HUD hidden by an unexpected menu");screenshot("marriage-opening.png");issue("priest","next");stage++;}
            case 19->{if(!acknowledged("priest"))return;assertStage(Phase.INTRODUCTION);issue("spouse","disconnect");stage++;}
            case 20->{if(!ready||!acknowledged("spouse"))return;if(!serverCondition(()->union().phase==Phase.SUSPENDED&&mc().getSingleplayerServer().getPlayerList().getPlayer(PLAYERS.get("spouse"))==null))return;server(()->{check(union().phase==Phase.SUSPENDED,"Disconnect did not suspend");check(WalletBridge.balance(p("host"))==50000&&union().balance==0,"Disconnect lost funds");});stage++;}
            case 21->{if(!ready)return;issue("spouse","reconnect");stage++;}
            case 22->{if(!acknowledged("spouse"))return;issue("host","continue");stage++;}
            case 23->{if(!acknowledged("host"))return;issue("spouse","continue");stage++;}
            case 24->{if(!acknowledged("spouse"))return;issue("priest","resume");stage++;}
            case 25->{if(!acknowledged("priest"))return;issue("priest","next");stage++;}
            case 26->{if(!acknowledged("priest"))return;issue("guest","shot","marriage-guest-countdown.png");stage++;}
            case 27->{if(!acknowledged("guest"))return;issue("guest","G");stage++;}
            case 28->{if(!acknowledged("guest"))return;server(()->check(union().phase==Phase.SUSPENDED&&PLAYERS.get("guest").equals(union().objector),"Actual guest G did not interrupt"));stage++;}
            case 29->{if(!ready)return;if(mc().screen!=null)mc().screen.onClose();issue("guest","reason","Uma última palavra para felicitar os noivos.");stage++;}
            case 30->{if(!acknowledged("guest"))return;screenshot("marriage-objection.png");issue("priest","speaker.objector");stage++;}
            case 31->{if(!acknowledged("priest"))return;issue("priest","speaker.couple");stage++;}
            case 32->{if(!acknowledged("priest"))return;issue("host","continue");stage++;}
            case 33->{if(!acknowledged("host"))return;issue("spouse","continue");stage++;}
            case 34->{if(!acknowledged("spouse"))return;issue("priest","resume");stage++;}
            case 35->{if(!acknowledged("priest"))return;issue("priest","ask");stage++;}
            case 36->{if(!acknowledged("priest"))return;issue("host","vow");stage++;}
            case 37->{if(!acknowledged("host"))return;screenshot("marriage-vows.png");issue("priest","ask");stage++;}
            case 38->{if(!acknowledged("priest"))return;issue("spouse","vow");stage++;}
            case 39->{if(!acknowledged("spouse"))return;issue("guest","witness");stage++;}
            case 40->{if(!acknowledged("guest"))return;issue("priest","seal");stage++;}
            case 41->{if(!acknowledged("priest"))return;server(()->{try{check(union().phase==Phase.MARRIED,"Wedding did not settle: "+union().error);check(union().balance==75001&&WalletBridge.balance(p("host"))==0&&WalletBridge.balance(p("spouse"))==0&&bank()==initialBank+5000,"Joint wallets or public fee do not conserve money");}catch(Exception e){throw new IllegalStateException(e);}});issue("host","close");captureAt=now+1400;stage++;}
            case 42->{if(!ready||!acknowledged("host")||now<captureAt)return;check(mc().screen==null,"Celebration HUD hidden by an unexpected menu");screenshot("marriage-celebration.png");moveStart=mc().player.position();mc().options.keyUp.setDown(true);mc().player.setYRot(mc().player.getYRot()+12);movingUntil=now+500;stage++;}
            case 43->{if(now<movingUntil)return;mc().options.keyUp.setDown(false);controlsFree=mc().screen==null&&mc().player.position().distanceTo(moveStart)>.05;check(controlsFree,"Ceremony blocked movement");issue("host","documents");stage++;}
            case 44->{if(!acknowledged("host"))return;issue("spouse","documents");stage++;}
            case 45->{if(!acknowledged("spouse"))return;issue("host","certificate");stage++;}
            case 46->{if(!acknowledged("host"))return;issue("host","withdraw","10.00");stage++;}
            case 47->{if(!acknowledged("host"))return;server(()->check(WalletBridge.balance(p("host"))==1000&&union().balance==74001,"Withdrawal incorrect"));stage++;}
            case 48->{if(!ready)return;issue("host","deposit","5.00");stage++;}
            case 49->{if(!acknowledged("host"))return;issue("host","consolidate");stage++;}
            case 50->{if(!acknowledged("host"))return;server(()->check(WalletBridge.balance(p("host"))==0&&union().balance==75001,"Deposit/consolidation duplicated money"));issue("spouse","disconnect");stage++;}
            case 51->{if(!ready||!acknowledged("spouse"))return;if(!serverCondition(()->mc().getSingleplayerServer().getPlayerList().getPlayer(PLAYERS.get("spouse"))==null))return;screenshot("marriage-shared-account.png");issue("host","divorce");stage++;}
            case 52->{if(!acknowledged("host"))return;server(()->{var d=MarriageData.get(mc().getSingleplayerServer());check(union().phase==Phase.DIVORCED&&d.ledger.citizen(PLAYERS.get("host")).credit()==37500&&d.ledger.citizen(PLAYERS.get("spouse")).credit()==37501,"Offline divorce split incorrect");});issue("host","credits");stage++;}
            case 53->{if(!ready||!acknowledged("host"))return;issue("spouse","reconnect");stage++;}
            case 54->{if(!acknowledged("spouse"))return;issue("spouse","credits");stage++;}
            case 55->{if(!acknowledged("spouse"))return;server(()->{try{check(WalletBridge.balance(p("host"))==37500&&WalletBridge.balance(p("spouse"))==37501&&bank()==initialBank+5000,"Divorce money failed conservation");var data=MarriageData.get(mc().getSingleplayerServer());check(data.ledger.citizen(PLAYERS.get("host")).credit()==0&&data.ledger.citizen(PLAYERS.get("spouse")).credit()==0,"Credits collected twice");mc().getSingleplayerServer().saveEverything(true,true,true);}catch(Exception e){throw new IllegalStateException(e);}});issue("priest","disconnect");stage++;}
            case 56->{if(!ready||!acknowledged("priest"))return;if(!serverCondition(()->mc().getSingleplayerServer().getPlayerList().getPlayer(PLAYERS.get("priest"))==null))return;issue("guest","disconnect");stage++;}
            case 57->{if(!acknowledged("guest"))return;if(!serverCondition(()->mc().getSingleplayerServer().getPlayerList().getPlayer(PLAYERS.get("guest"))==null))return;issue("spouse","disconnect");stage++;}
            case 58->{if(!acknowledged("spouse"))return;if(!serverCondition(()->mc().getSingleplayerServer().getPlayerList().getPlayer(PLAYERS.get("spouse"))==null))return;mc().level.disconnect();mc().disconnect(new TitleScreen());stage++;next=now+1500;}
            case 59->{if(mc().level!=null||mc().getSingleplayerServer()!=null)return;mc().createWorldOpenFlows().openWorld(folder,()->mc().setScreen(new TitleScreen()));stage++;}
            case 60->{if(mc().player==null||mc().getSingleplayerServer()==null)return;server(()->{try{publish();check(union().phase==Phase.DIVORCED&&WalletBridge.balance(p("host"))==37500&&bank()==initialBank+5000,"World reopen lost or repeated money");}catch(Exception e){throw new IllegalStateException(e);}});stage++;}
            case 61->{if(!ready)return;issue("spouse","reconnect");stage++;}
            case 62->{if(!acknowledged("spouse"))return;server(()->check(WalletBridge.balance(p("spouse"))==37501,"Spouse wallet did not survive world restart"));stage++;}
            case 63->{if(!ready)return;openAltar();stage++;next=now+1000;}
            case 64->{if(!(mc().screen instanceof MarriageScreen))return;screenshot("marriage-after-world-reopen.png");verifyCompactAndPreferences();CHECKS.add("Four actual Minecraft processes used a real TCP LAN server with offline test identities, native C2S menus, three consenting participants and an invited witness.");CHECKS.add("An actual spouse disconnected mid-ceremony. The wedding paused without charging, resumed with both consents, accepted one guest G objection, and required both individual vows and the witness.");CHECKS.add("Real wallets 50000 + 30001 cents became 75001 shared + 5000 Crown cents. Actual withdrawal/deposit/consolidation conserved money. Unilateral divorce while the spouse was offline reserved 37500/37501, collected once and survived real world reopen.");done(true,"Four-client actual TCP LAN wedding, objection, disconnect, wallets, divorce, Identity certificate and world reopen passed.");}
        }
    }catch(Throwable t){t.printStackTrace();done(false,t.toString());}}
}
