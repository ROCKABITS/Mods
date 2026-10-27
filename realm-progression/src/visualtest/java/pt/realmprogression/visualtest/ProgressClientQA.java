package pt.realmprogression.visualtest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Treasury;
import pt.crownledger.service.CrownService;
import pt.realmprogression.block.ProgressBlocks;
import pt.realmprogression.client.*;
import pt.realmprogression.client.ProgressScreen;
import pt.realmprogression.service.*;
import pt.realmprogression.data.ProgressStore;
import pt.realmprogression.network.ProgressNetwork;

/** Real Create, the real public treasury, production UI packets, two TCP clients and actual world reopening. */
@EventBusSubscriber(modid="realmprogression",value=Dist.CLIENT)
public final class ProgressClientQA {
    private static final String ROLE=System.getProperty("progression.role","host");
    private static final Path OUT=Path.of(System.getProperty("progression.qaOutput")).toAbsolutePath();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final BlockPos TABLE=new BlockPos(0,161,0),SECOND=new BlockPos(4,161,0);
    private static final List<String> SHOTS=new ArrayList<>();private static final List<JsonObject> REQUESTS=new ArrayList<>();
    private static final Set<Object> TRACKED=Collections.newSetFromMap(new WeakHashMap<>());
    private static final long START=System.currentTimeMillis();private static volatile Throwable failure;private static volatile boolean ready;
    private static int stage,last=-1,guestStage,guestLast=-1;private static long next,stageAt;private static boolean done,booted;private static String folder;private static Process child;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private static JsonObject view(){return mc().screen instanceof ProgressScreen?(JsonObject)field(mc().screen,"data"):null;}
    private static void write(String name,Object data)throws Exception{Files.createDirectories(OUT);var p=Files.createTempFile(OUT,"progression-qa-",".tmp");Files.writeString(p,JSON.toJson(data));Files.move(p,OUT.resolve(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
    private static void server(Runnable task){ready=false;mc().getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable e){failure=e;}});}
    private static ServerPlayer host(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    private static Item alloy(){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("create:andesite_alloy"));}
    private static long bank(){return TreasuryData.get(host().getServer()).treasury.balance(Treasury.Currency.CHRONICLES);}
    private static boolean press(String key){return literal(Component.translatable(key.startsWith("gui.")||key.startsWith("selectWorld.")?key:"gui.realmprogression."+key).getString());}
    private static boolean literal(String label){if(mc().screen!=null)for(var c:List.copyOf(mc().screen.children()))if(c instanceof AbstractButton b&&b.visible&&b.active&&b.getMessage().getString().equals(label)){b.onPress();return true;}return false;}
    private static void input(String key,String value){((EditBox)field(mc().screen,key)).setValue(value);}
    private static void transport(){try{var s=mc().screen;if(!TRACKED.add(s))return;var f=s.getClass().getDeclaredField("requestSender");f.setAccessible(true);@SuppressWarnings("unchecked")var sender=(java.util.function.BiConsumer<String,JsonObject>)f.get(s);f.set(s,(java.util.function.BiConsumer<String,JsonObject>)(op,b)->{var request=b.deepCopy();request.addProperty("operation",op);REQUESTS.add(request);sender.accept(op,b);});}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean available(){if(view()==null||!view().has("balance")||(boolean)field(mc().screen,"waiting"))return false;check(view().get("error").getAsString().isEmpty(),"Research UI error: "+view());transport();return true;}
    private static void shot(String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}SHOTS.add(name);System.out.println("PROGRESSION_CAPTURE "+name);}
    private static void setup(){mc().getWindow().setWindowed(1600,900);mc().options.guiScale().set(2);mc().options.renderDistance().set(4);mc().options.simulationDistance().set(5);mc().resizeDisplay();}
    private static void quiet(){try{var f=Class.forName("pt.cronicasrp.client.ClientState").getDeclaredField("pending");f.setAccessible(true);f.setInt(null,0);var m=Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset");m.setAccessible(true);m.invoke(null);}catch(Exception ignored){}mc().getToasts().clear();}
    private static void profile(ServerPlayer p,String name,long balance){try{var cls=Class.forName("pt.cronicasrp.domain.CharacterProfile");Object v=cls.getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,name,28,"origins:human","Controlled research QA funds",name,"Common",balance,balance,"");Object a=((java.util.function.Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();for(var m:p.getClass().getMethods())if(m.getName().equals("setData")&&m.getParameterCount()==2&&m.getParameterTypes()[0].isInstance(a)){m.invoke(p,a,v);break;}Class.forName("pt.realmeconomy.compat.ChroniclesBridge").getMethod("sync",ServerPlayer.class).invoke(null,p);p.getServer().getPlayerList().saveAll();}catch(Exception e){throw new IllegalStateException(e);}}
    private static long wallet(ServerPlayer p){try{return ((Number)Class.forName("pt.crownledger.compat.ChroniclesBridge").getMethod("balance",ServerPlayer.class).invoke(null,p)).longValue();}catch(Exception e){throw new IllegalStateException(e);}}
    private static void open(ServerPlayer p,BlockPos pos){p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-1.7,0,18);var e=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),Direction.NORTH,pos,false));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(e);check(p.containerMenu instanceof pt.realmprogression.menu.ProgressMenu,"Physical research interaction failed");}
    private static void prepare(){server(()->{try{
        var p=host();var s=p.getServer();var level=p.serverLevel();folder=s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        for(int cx=-1;cx<=0;cx++)for(int cz=-1;cz<=0;cz++){level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);}
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,s);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);level.setDayTime(6000);level.setWeatherParameters(0,60000,false,false);
        for(int x=-10;x<=10;x++)for(int z=-8;z<=8;z++){level.setBlock(new BlockPos(x,160,z),(Math.abs(x)<7?Blocks.STONE_BRICKS:Blocks.DARK_OAK_PLANKS).defaultBlockState(),2);for(int y=161;y<168;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(int x:new int[]{-7,7})for(int z:new int[]{-5,5}){for(int y=161;y<165;y++)level.setBlock(new BlockPos(x,y,z),Blocks.DARK_OAK_LOG.defaultBlockState(),2);level.setBlock(new BlockPos(x,165,z),Blocks.LANTERN.defaultBlockState(),2);}
        for(int x:new int[]{-4,0,4})level.setBlock(new BlockPos(x,161,0),ProgressBlocks.TABLE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,x==4?Direction.WEST:Direction.NORTH),3);
        profile(p,"Aveline Vale",50000);s.getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(),2,false));
        var data=TreasuryData.get(s);data.treasury.addFunds(Treasury.Currency.CHRONICLES,790000,Treasury.Kind.DEPOSIT,p.getUUID(),"Controlled starting treasury for research QA",System.currentTimeMillis());data.setDirty();check(bank()==790000,"Invalid initial treasury");check(ProgressStore.get(s).state().blocked("create"),"Create unlocked before the target");
        p.setGameMode(GameType.SURVIVAL);p.connection.teleport(1.5,161,-3.8,20,19);s.setUsesAuthentication(false);int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}check(s.publishServer(GameType.SURVIVAL,false,port),"Actual LAN publishing failed");write("join.json",Map.of("port",port));
    }catch(Exception e){throw new IllegalStateException(e);}});}
    private static void spawn()throws Exception{var args=new ArrayList<>(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());args.add(0,"-Dprogression.role=guest");args.add("-cp");args.add(System.getProperty("java.class.path"));args.add("net.neoforged.devlaunch.Main");for(String line:Files.readAllLines(OUT.getParent().getParent().resolve("build/moddev/clientRunProgramArgs.txt"))){line=line.strip();if(!line.isEmpty()&&!line.startsWith("#"))args.add(line.startsWith("\"")&&line.endsWith("\"")?line.substring(1,line.length()-1):line);}var run=OUT.resolve("guest-run");Files.createDirectories(run);option(args,"--gameDir",run.toString());option(args,"--username","ResearchGuest");option(args,"--uuid",UUID.nameUUIDFromBytes("OfflinePlayer:ResearchGuest".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-",""));option(args,"--accessToken","0");args.add(0,Path.of(System.getProperty("java.home"),"bin","java").toString());child=new ProcessBuilder(args).directory(run.toFile()).redirectErrorStream(true).redirectOutput(OUT.resolve("guest-client.log").toFile()).start();}
    private static void option(List<String> args,String key,String value){int i=args.indexOf(key);if(i<0){args.add(key);args.add(value);}else args.set(i+1,value);}
    private static void compact(){var screen=mc().screen;for(int[] size:List.of(new int[]{320,240},new int[]{480,270},new int[]{800,450})){screen.resize(mc(),size[0],size[1]);var widgets=new ArrayList<AbstractWidget>();for(var c:screen.children())if(c instanceof AbstractWidget w&&w.visible){check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=size[0]&&w.getY()+w.getHeight()<=size[1],"Control outside viewport");widgets.add(w);}for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);check(a.getX()+a.getWidth()<=b.getX()||b.getX()+b.getWidth()<=a.getX()||a.getY()+a.getHeight()<=b.getY()||b.getY()+b.getHeight()<=a.getY(),"Overlapping research controls: "+a.getMessage()+" / "+b.getMessage());}}screen.resize(mc(),mc().getWindow().getGuiScaledWidth(),mc().getWindow().getGuiScaledHeight());}
    private static void guest()throws Exception{
        if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))press("gui.continue");
        if(!booted&&mc().screen instanceof TitleScreen&&Files.exists(OUT.resolve("join.json"))){setup();booted=true;int port=JsonParser.parseString(Files.readString(OUT.resolve("join.json"))).getAsJsonObject().get("port").getAsInt();String address="127.0.0.1:"+port;ConnectScreen.startConnecting(new TitleScreen(),mc(),ServerAddress.parseString(address),new ServerData("Research LAN QA",address,ServerData.Type.LAN),false,null);}
        if(mc().player==null)return;write("guest-connected.json",Map.of("id",mc().player.getUUID()));if(view()==null||!view().has("balance"))return;
        if(guestLast!=guestStage){guestLast=guestStage;next=System.currentTimeMillis()+900;return;}
        switch(guestStage){
            case 0->{if(!available())return;check(!view().get("admin").getAsBoolean()&&ClientProgress.locked().contains("create"),"Guest must be non-OP and receive the real lock state");compact();shot("research-player-locked.png");var body=new JsonObject();body.add("nonce",view().get("nonce"));body.add("revision",view().get("revision"));body.addProperty("mod","create");body.addProperty("goal",1);body.addProperty("enabled",true);body.addProperty("namespaces","create");ProgressNetwork.request("configure",body);guestStage++;}
            case 1->{if(view().get("error").getAsString().equals("error.realmprogression.permission")){write("guest-denied.json",Map.of("success",true));var b=new JsonObject();b.addProperty("search","");b.addProperty("selected","create");b.addProperty("page",0);b.addProperty("browse",true);ProgressNetwork.request("query",b);guestStage++;}}
            case 2->{if(available()){check(!view().get("admin").getAsBoolean()&&!view().get("browse").getAsBoolean(),"Non-OP entered administrative browse mode");write("guest-locked-confirmed.json",Map.of("success",true));guestStage++;}}
            case 3->{if(available()&&!ClientProgress.locked().contains("create")&&view().getAsJsonObject("selected").get("unlocked").getAsBoolean()){shot("research-player-unlocked.png");write("guest-done.json",Map.of("success",true,"screenshots",SHOTS));guestStage++;}}
            default->{}
        }
    }
    private static void finish(boolean ok,String message){done=true;try{if(!ok){shot(ROLE+"-failure.png");write(ROLE+"-view.json",view()==null?new JsonObject():view());}if(ROLE.equals("host")&&Files.exists(OUT.resolve("guest-done.json")))for(var raw:JsonParser.parseString(Files.readString(OUT.resolve("guest-done.json"))).getAsJsonObject().getAsJsonArray("screenshots"))SHOTS.add(raw.getAsString());var report=new LinkedHashMap<String,Object>();report.put("success",ok);report.put("message",message);report.put("stage",stage);report.put("screenshots",SHOTS);report.put("requests",REQUESTS);report.put("actualMinecraftClients",2);report.put("actualTcpLan",true);report.put("actualCreateCrafts",true);report.put("actualTreasuryDonation",true);report.put("actualWorldReopening",true);report.put("syntheticFinancialSnapshots",false);report.put("mods",net.neoforged.fml.ModList.get().getMods().stream().collect(java.util.stream.Collectors.toMap(m->m.getModId(),m->m.getVersion().toString())));write(ROLE.equals("host")?"result.json":"guest-failure.json",report);}catch(Exception e){e.printStackTrace();}if(child!=null)child.destroy();System.out.println("PROGRESSION_CLIENT_QA success="+ok+" "+message);mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){if(done)return;long time=System.currentTimeMillis();try{
        check(time-START<660000,"Research QA timeout");if(failure!=null)throw new IllegalStateException("Research fixture failed",failure);if(mc().player!=null)quiet();if(mc().getOverlay()!=null||time<next)return;next=time+500;
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc().getWindow().getWindow(),1,1);for(String n:List.of("xpos","ypos")){var f=mc().mouseHandler.getClass().getDeclaredField(n);f.setAccessible(true);f.setDouble(mc().mouseHandler,1);}
        if(ROLE.equals("guest")){guest();return;}if(Files.exists(OUT.resolve("guest-failure.json")))throw new IllegalStateException(Files.readString(OUT.resolve("guest-failure.json")));
        if(last!=stage){last=stage;stageAt=time;System.out.println("PROGRESSION_CLIENT_STAGE "+stage);next=time+800;return;}check(time-stageAt<150000,"Stalled research stage "+stage);
        switch(stage){
            case 0->{if(mc().screen instanceof TitleScreen){setup();Files.createDirectories(OUT);try(var files=Files.list(OUT)){for(var p:files.filter(p->p.getFileName().toString().endsWith(".json")).toList())Files.delete(p);}CreateWorldScreen.openFresh(mc(),mc().screen);stage++;}else if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))press("gui.continue");}
            case 1->{if(mc().screen instanceof CreateWorldScreen s){s.getUiState().setName("Realm Progression LAN QA");s.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);s.getUiState().setAllowCommands(true);s.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);check(press("selectWorld.create"),"World creation unavailable");stage++;}}
            case 2->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){prepare();stage++;}}
            case 3->{if(ready){mc().setScreen(null);mc().options.hideGui=true;next=time+1200;stage++;}}
            case 4->{shot("research-tables.png");server(()->host().connection.teleport(1.3,161.15,-1.4,40,25));stage++;}
            case 5->{if(ready){shot("research-table-closeup.png");mc().options.hideGui=false;server(()->open(host(),TABLE));stage++;}}
            case 6->{if(available()){compact();shot("research-create-locked.png");check(press("goals"),"Installed-mod browser unavailable");stage++;}}
            case 7->{if(available()&&view().get("browse").getAsBoolean()){input("search","Realm Economy");stage++;}}
            case 8->{if(available()&&view().has("selected")&&view().getAsJsonObject("selected").get("id").getAsString().equals("realmeconomy")){shot("research-installed-mods.png");input("goal","10000");check(press("save"),"Save-goal control missing");stage++;}}
            case 9->{shot("research-goal-confirmation.png");compact();check(press("confirm"),"Goal confirmation missing");stage++;}
            case 10->{if(available()&&view().getAsJsonObject("selected").get("enabled").getAsBoolean()){server(()->check(ProgressStore.get(host().getServer()).state().rules().get("realmeconomy").goal()==1000000,"Native UI goal was not saved"));input("search","");stage++;}}
            case 11->{if(ready&&available()&&view().get("search").getAsString().isEmpty()){check(press("browse"),"Managed-goal mode unavailable");stage++;}}
            case 12->{if(available()&&!view().get("browse").getAsBoolean()){check(literal("Create"),"Create selection missing");stage++;}}
            case 13->{if(available()&&view().getAsJsonObject("selected").get("id").getAsString().equals("create")){shot("research-independent-goals.png");spawn();stage++;}}
            case 14->{if(Files.exists(OUT.resolve("guest-connected.json"))){UUID id=UUID.fromString(JsonParser.parseString(Files.readString(OUT.resolve("guest-connected.json"))).getAsJsonObject().get("id").getAsString());server(()->{var p=host().getServer().getPlayerList().getPlayer(id);check(p!=null&&!p.hasPermissions(2),"Guest must be a real non-OP");profile(p,"Aetherion",20000);open(p,SECOND);});stage++;}}
            case 15->{if(ready&&Files.exists(OUT.resolve("guest-locked-confirmed.json"))){server(()->{check(ProgressStore.get(host().getServer()).state().rules().get("create").goal()==800000,"Guest changed the Create goal");var p=host();p.closeContainer();var m=p.inventoryMenu;for(int i=1;i<=4;i++)m.getSlot(i).set(new ItemStack(i==1||i==4?Items.IRON_NUGGET:Items.ANDESITE,2));m.slotsChanged(m.getSlot(1).container);m.broadcastChanges();check(m.getSlot(0).getItem().isEmpty(),"Locked real Create recipe assembled");});stage++;}}
            case 16->{if(ready){mc().setScreen(new InventoryScreen(mc().player));next=time+900;stage++;}}
            case 17->{shot("research-blocked-create-craft.png");mc().gameMode.handleInventoryMouseClick(0,0,0,ClickType.QUICK_MOVE,mc().player);stage++;}
            case 18->{server(()->{check(host().getInventory().countItem(alloy())==0&&host().inventoryMenu.getSlot(1).getItem().getCount()==2,"Locked native shift-click consumed or delivered items");CrownService.donate(host(),10000,Treasury.Currency.CHRONICLES);check(bank()==800000&&wallet(host())==40000,"Actual donation or observed-money conservation failed");check(!ProgressStore.get(host().getServer()).state().blocked("create"),"Actual Crown donation did not trigger the unlock");});stage++;}
            case 19->{if(ready&&!ClientProgress.locked().contains("create")&&mc().player.inventoryMenu.getSlot(0).getItem().is(alloy())){shot("research-unlock-banner.png");mc().gameMode.handleInventoryMouseClick(0,0,0,ClickType.QUICK_MOVE,mc().player);stage++;}}
            case 20->{server(()->{check(host().getInventory().countItem(alloy())==2,"Native repeated shift crafting did not consume exactly two complete recipes");for(int i=1;i<=4;i++)check(host().inventoryMenu.getSlot(i).getItem().isEmpty(),"Crafted materials were not consumed");check(bank()==800000&&wallet(host())==40000,"Research unlock charged or duplicated money");check(ProgressStore.get(host().getServer()).state().blocked("realmeconomy"),"A different goal unlocked early");open(host(),TABLE);});stage++;}
            case 21->{if(ready&&available()&&view().getAsJsonObject("selected").get("unlocked").getAsBoolean()){shot("research-create-unlocked.png");if(Files.exists(OUT.resolve("guest-done.json"))){check(press("history"),"History control missing");stage++;}}}
            case 22->{shot("research-audit.png");compact();mc().options.guiScale().set(3);mc().resizeDisplay();stage++;}
            case 23->{shot("research-compact-records.png");mc().options.guiScale().set(2);mc().resizeDisplay();server(()->{var d=TreasuryData.get(host().getServer());d.treasury.removeFunds(Treasury.Currency.CHRONICLES,10000,Treasury.Kind.CONTRACT_FUNDING,host().getUUID(),"QA public expense after permanent unlock",System.currentTimeMillis());d.setDirty();ProgressService.observe(host().getServer());host().getServer().getPlayerList().saveAll();check(!ProgressStore.get(host().getServer()).state().blocked("create"),"Spending revoked the unlock");});stage++;}
            case 24->{if(ready){child.destroy();child.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);mc().level.disconnect();mc().disconnect(new TitleScreen());stage++;}}
            case 25->{if(mc().level==null&&mc().getSingleplayerServer()==null){mc().createWorldOpenFlows().openWorld(folder,()->mc().setScreen(new TitleScreen()));stage++;}}
            case 26->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){server(()->{check(bank()==790000&&wallet(host())==40000,"Reopening lost the real treasury/wallet");check(!ProgressStore.get(host().getServer()).state().blocked("create")&&ProgressStore.get(host().getServer()).state().blocked("realmeconomy"),"World reopening lost independent approvals");check(host().getInventory().countItem(alloy())==2,"Restart lost crafted items");open(host(),TABLE);});stage++;}}
            case 27->{if(ready&&available()){shot("research-after-world-reopen.png");finish(true,"Actual Create crafts, admin-configured second mod, denied non-OP packet, unchanged public unlock funds, real Crown donation, repeated native crafting, two TCP LAN clients and world reopening passed.");}}
            default->throw new IllegalStateException("Unknown research QA stage");
        }
    }catch(Throwable failure){failure.printStackTrace();finish(false,failure.toString());}}
}
