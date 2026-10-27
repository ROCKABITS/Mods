package pt.realmeconomy.colonyvisualtest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import pt.realmeconomy.block.EconomyBlocks;
import pt.realmeconomy.client.ColonyScreen;
import pt.realmeconomy.colony.*;
import pt.realmeconomy.compat.ChroniclesBridge;
import pt.realmeconomy.data.EconomyData;
import pt.realmeconomy.service.TradeJournal;
import pt.realmeconomy.service.TradeItems;

/** Actual TCP LAN clients, native colonies and RP wallets. Fixtures only seed a demonstrable world. */
@EventBusSubscriber(modid="realmeconomy",value=Dist.CLIENT)
public final class ColonyClientQA {
 private static final String ROLE=System.getProperty("colony.role","host");
 private static final Path OUT=Path.of(System.getProperty("economy.colonyQAOutput")).toAbsolutePath();
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 private static final BlockPos DESK=new BlockPos(0,161,0),SECOND=new BlockPos(4,161,0);
 private static final List<String> SHOTS=new ArrayList<>();private static final List<JsonObject> REQUESTS=new ArrayList<>();
 private static final Set<Object> TRACKED=Collections.newSetFromMap(new WeakHashMap<>());
 private static final long START=System.currentTimeMillis();private static volatile Throwable failure;private static volatile boolean ready;
 private static int stage,guestStep,last=-1,guestLast=-1,colonyId;private static long next,stageAt,exportCents,expectedBank;private static UUID guest;
 private static boolean done,booted;private static String folder;private static Process child;
 private static Minecraft mc(){return Minecraft.getInstance();}
 private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static void write(String name,Object value)throws Exception{Files.createDirectories(OUT);Path p=Files.createTempFile(OUT,"colony-qa-",".tmp");Files.writeString(p,JSON.toJson(value));Files.move(p,OUT.resolve(name),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
 private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
 private static void server(Runnable task){ready=false;mc().getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable e){failure=e;}});}
 private static ServerPlayer host(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
 private static IColony colony(){return IColonyManager.getInstance().getColonyByWorld(colonyId,host().level());}
 private static String key(){return ColonyAccess.key(colony());}
 private static ColonyLedger.Account account(){return EconomyData.get(host().getServer()).colonies().account(key());}
 private static JsonObject view(){return mc().screen instanceof ColonyScreen?(JsonObject)field(mc().screen,"data"):null;}
 private static boolean available(){if(view()==null||!view().has("balance")||(Boolean)field(mc().screen,"waiting"))return false;check(!view().has("error")||view().get("error").getAsString().isEmpty(),"Native cashier error: "+view());transport();return true;}
 private static boolean press(String key){String label=Component.translatable(key.startsWith("gui.")||key.startsWith("selectWorld.")?key:"gui.realmeconomy.colony_"+key).getString();if(mc().screen!=null)for(var c:List.copyOf(mc().screen.children()))if(c instanceof AbstractButton b&&b.visible&&b.active&&b.getMessage().getString().equals(label)){b.onPress();return true;}return false;}
 private static void input(String name,String value){((EditBox)field(mc().screen,name)).setValue(value);}
 private static void transport(){try{var screen=mc().screen;if(!TRACKED.add(screen))return;var f=screen.getClass().getDeclaredField("requestSender");f.setAccessible(true);@SuppressWarnings("unchecked")var original=(java.util.function.BiConsumer<String,JsonObject>)f.get(screen);f.set(screen,(java.util.function.BiConsumer<String,JsonObject>)(op,body)->{var copy=body.deepCopy();copy.addProperty("operation",op);REQUESTS.add(copy);original.accept(op,body);});}catch(Exception e){throw new IllegalStateException(e);}}
 private static void screenshot(String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){Files.createDirectories(OUT);image.writeToFile(OUT.resolve(name));}SHOTS.add(name);System.out.println("COLONY_CLIENT_CAPTURE "+name);}
 private static void setup(){mc().getWindow().setWindowed(1600,900);mc().options.guiScale().set(2);mc().options.renderDistance().set(4);mc().options.simulationDistance().set(5);mc().resizeDisplay();}
 private static void quiet(){try{var f=Class.forName("pt.cronicasrp.client.ClientState").getDeclaredField("pending");f.setAccessible(true);f.setInt(null,0);var m=Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset");m.setAccessible(true);m.invoke(null);}catch(Exception ignored){}mc().getToasts().clear();}
 private static void profile(ServerPlayer p,String name,long balance){try{var type=Class.forName("pt.cronicasrp.domain.CharacterProfile");var value=type.getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,name,28,"origins:human","Controlled colony QA funds",name,"Common",balance,balance,"");Object a=((java.util.function.Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();for(var m:p.getClass().getMethods())if(m.getName().equals("setData")&&m.getParameterCount()==2&&m.getParameterTypes()[0].isInstance(a)){m.invoke(p,a,value);break;}ChroniclesBridge.sync(p);p.getServer().getPlayerList().saveAll();}catch(Exception e){throw new IllegalStateException(e);}}
 private static void open(ServerPlayer p,BlockPos pos){p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-1.8,0,14);var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),Direction.NORTH,pos,false));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);check(p.containerMenu instanceof pt.realmeconomy.menu.ColonyMenu,"Physical cashier event failed");}
 private static void prepare(){server(()->{try{var p=host();var level=p.serverLevel();var srv=p.getServer();folder=srv.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();for(int cx=-1;cx<=0;cx++)for(int cz=-1;cz<=0;cz++){level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);}level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,srv);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,srv);level.setDayTime(6000);level.setWeatherParameters(0,60000,false,false);
  for(int x=-10;x<=10;x++)for(int z=-8;z<=8;z++){level.setBlock(new BlockPos(x,160,z),(Math.abs(x)<7?Blocks.STONE_BRICKS:Blocks.DARK_OAK_PLANKS).defaultBlockState(),2);for(int y=161;y<167;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  for(int x:new int[]{-7,7})for(int z:new int[]{-5,5}){for(int y=161;y<165;y++)level.setBlock(new BlockPos(x,y,z),Blocks.DARK_OAK_LOG.defaultBlockState(),2);level.setBlock(new BlockPos(x,165,z),Blocks.LANTERN.defaultBlockState(),2);}
  for(int x:new int[]{-4,0,4})level.setBlock(new BlockPos(x,161,0),EconomyBlocks.COLONY_CHEST.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,x==4?Direction.WEST:Direction.NORTH),3);
  profile(p,"Aveline Vale",100000);var c=Objects.requireNonNull(IColonyManager.getInstance().createColony(level,DESK,p,"Aetheria · Colonial Treasury","Medieval Oak"));colonyId=c.getID();ColonyAccess.ensure(c);
  // A native builder hut and native building order, with a frozen fee generated by the production mixin.
  BlockPos bp=DESK.offset(8,0,2);level.setBlock(bp,ModBuildings.builder.get().getBuildingBlock().defaultBlockState(),3);var tile=(com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding)level.getBlockEntity(bp);var building=c.getServerBuildingManager().addNewBuilding(tile,level);
  check(building!=null,"Real builder hut did not register");var ctor=Arrays.stream(WorkOrderBuilding.class.getDeclaredConstructors()).filter(v->v.getParameterCount()==8).findFirst().orElseThrow();ctor.setAccessible(true);Object rotation=Class.forName("com.ldtteam.structurize.api.RotationMirror").getField("NONE").get(null);var order=(WorkOrderBuilding)ctor.newInstance("Medieval Oak","fundamentals/builder1.blueprint","block.minecolonies.blockhutbuilder",WorkOrderType.BUILD,bp,rotation,0,1);for(String name:List.of("customName","customParentName","parentTranslationKey")){var f=WorkOrderBuilding.class.getDeclaredField(name);f.setAccessible(true);f.set(order,name.equals("customName")?"Workshop of Aetheria":"");}order.setID(1);order.setColony(c);c.getWorkManager().getWorkOrders().put(1,order);order.onAdded(c,false);
  var visitor=(IVisitorData)c.getVisitorManager().createAndRegisterCivilianData();visitor.setName("Travelling Carpenter");visitor.setRecruitCosts(new ItemStack(Items.BREAD));visitor.setLastPosition(DESK);ColonyAccess.refreshVisitors(c);
  var first=account().orders().getFirst();exportCents=first.cents();var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(first.item()));int remaining=first.quantity(),slot=0;while(remaining>0){int n=Math.min(64,remaining);p.getInventory().setItem(slot++,new ItemStack(item,n));remaining-=n;}
  p.setGameMode(GameType.SURVIVAL);p.connection.teleport(1.5,161,-3.8,20,19);check(!p.hasPermissions(2),"Host cashier must be non-OP");srv.setUsesAuthentication(false);int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}check(srv.publishServer(GameType.SURVIVAL,false,port),"Actual LAN publishing failed");write("join.json",Map.of("port",port));
 }catch(Exception e){throw new IllegalStateException(e);}});}
 private static void spawn()throws Exception{var args=new ArrayList<>(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());args.add(0,"-Dcolony.role=guest");args.add("-cp");args.add(System.getProperty("java.class.path"));args.add("net.neoforged.devlaunch.Main");for(String line:Files.readAllLines(OUT.getParent().getParent().resolve("build/moddev/clientRunProgramArgs.txt"))){line=line.strip();if(!line.isEmpty()&&!line.startsWith("#"))args.add(line.startsWith("\"")&&line.endsWith("\"")?line.substring(1,line.length()-1):line);}Path run=OUT.resolve("guest-run");Files.createDirectories(run);option(args,"--gameDir",run.toString());option(args,"--username","ColonyGuest");option(args,"--uuid",UUID.nameUUIDFromBytes("OfflinePlayer:ColonyGuest".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-",""));option(args,"--accessToken","0");args.add(0,Path.of(System.getProperty("java.home"),"bin","java").toString());child=new ProcessBuilder(args).directory(run.toFile()).redirectErrorStream(true).redirectOutput(OUT.resolve("guest-client.log").toFile()).start();}
 private static void option(List<String> args,String name,String value){int i=args.indexOf(name);if(i<0){args.add(name);args.add(value);}else args.set(i+1,value);}
 private static void compact(){var screen=mc().screen;for(int[] size:List.of(new int[]{320,240},new int[]{480,270},new int[]{800,450})){screen.resize(mc(),size[0],size[1]);var controls=new ArrayList<AbstractWidget>();for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible){check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=size[0]&&w.getY()+w.getHeight()<=size[1],"Cashier control outside viewport");controls.add(w);}for(int i=0;i<controls.size();i++)for(int j=i+1;j<controls.size();j++){var a=controls.get(i);var b=controls.get(j);check(a.getX()+a.getWidth()<=b.getX()||b.getX()+b.getWidth()<=a.getX()||a.getY()+a.getHeight()<=b.getY()||b.getY()+b.getHeight()<=a.getY(),"Overlapping cashier controls: "+a.getMessage().getString()+" / "+b.getMessage().getString());}}screen.resize(mc(),mc().getWindow().getGuiScaledWidth(),mc().getWindow().getGuiScaledHeight());}
 private static void receipt(long expected)throws Exception{var p=host();var receipt=p.getPersistentData().getCompound(TradeJournal.RECEIPT_KEY);for(var path:List.of(p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat"),p.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE))){var root=NbtIo.readCompressed(path,NbtAccounter.create(104857600));if(root.contains("Data"))root=root.getCompound("Data").getCompound("Player");check(root.getCompound("neoforge:attachments").getCompound("cronicasrp:profile").getLong("balance")==expected,"Host wallet missing in a native restart file");check(root.getCompound("NeoForgeData").getCompound(TradeJournal.RECEIPT_KEY).equals(receipt),"Host receipt lost in a restart file");check(TradeItems.savedInventory(p,root).equals(TradeItems.inventory(p)),"Host inventory differs in restart files");}}
 private static void guest()throws Exception{
  if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))press("gui.continue");
  if(!booted&&mc().screen instanceof TitleScreen&&Files.exists(OUT.resolve("join.json"))){setup();booted=true;int port=JsonParser.parseString(Files.readString(OUT.resolve("join.json"))).getAsJsonObject().get("port").getAsInt();String address="127.0.0.1:"+port;ConnectScreen.startConnecting(new TitleScreen(),mc(),ServerAddress.parseString(address),new ServerData("Colony LAN QA",address,ServerData.Type.LAN),false,null);}
  if(mc().player==null)return;write("guest-connected.json",Map.of("id",mc().player.getUUID()));if(!available())return;if(guestLast!=guestStep){guestLast=guestStep;next=System.currentTimeMillis()+700;return;}
  switch(guestStep){case 0->{check(!view().get("manager").getAsBoolean(),"Friend unexpectedly manages finances");compact();input("amount","50");check(press("deposit"),"Friend deposit unavailable");guestStep++;}case 1->{screenshot("colony-member-confirmation.png");check(press("confirm"),"Friend confirmation unavailable");guestStep++;}case 2->{if(view().get("wallet").getAsLong()!=15000)return;check(!press("withdraw"),"Friend withdrawal control is enabled");screenshot("colony-member-account.png");write("guest-done.json",Map.of("success",true,"screenshots",SHOTS,"requests",REQUESTS));guestStep++;}default->{}}
 }
 private static void finish(boolean ok,String message){done=true;try{if(!ok){screenshot(ROLE+"-failure.png");write(ROLE+"-view.json",view()==null?new JsonObject():view());}if(ROLE.equals("host")&&Files.exists(OUT.resolve("guest-done.json")))for(var image:JsonParser.parseString(Files.readString(OUT.resolve("guest-done.json"))).getAsJsonObject().getAsJsonArray("screenshots"))SHOTS.add(image.getAsString());write(ROLE.equals("host")?"result.json":"guest-failure.json",Map.of("success",ok,"message",message,"stage",stage,"screenshots",SHOTS,"requests",REQUESTS,"actualMinecraftClients",2,"actualTcpLan",true,"actualChroniclesWallet",true,"syntheticFinancialSnapshots",false,"mods",net.neoforged.fml.ModList.get().getMods().stream().collect(java.util.stream.Collectors.toMap(m->m.getModId(),m->m.getVersion().toString()))));}catch(Exception e){e.printStackTrace();}if(child!=null)child.destroy();System.out.println("COLONY_CLIENT_QA success="+ok+" "+message);mc().stop();}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(done)return;long time=System.currentTimeMillis();try{check(time-START<600000,"Colony QA timeout");if(failure!=null)throw new IllegalStateException("Colony fixture failed",failure);if(mc().player!=null)quiet();if(mc().getOverlay()!=null||time<next)return;next=time+500;org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc().getWindow().getWindow(),1,1);for(String mouse:List.of("xpos","ypos")){var f=mc().mouseHandler.getClass().getDeclaredField(mouse);f.setAccessible(true);f.setDouble(mc().mouseHandler,1);}if(ROLE.equals("guest")){guest();return;}if(Files.exists(OUT.resolve("guest-failure.json")))throw new IllegalStateException(Files.readString(OUT.resolve("guest-failure.json")));if(last!=stage){last=stage;stageAt=time;System.out.println("COLONY_CLIENT_STAGE "+stage);next=time+750;return;}check(time-stageAt<120000,"Stalled colony QA stage "+stage);
  switch(stage){
   case 0->{if(mc().screen instanceof TitleScreen){setup();Files.createDirectories(OUT);for(var p:Files.list(OUT).filter(p->p.getFileName().toString().endsWith(".json")).toList())Files.delete(p);CreateWorldScreen.openFresh(mc(),mc().screen);stage++;}else if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))press("gui.continue");}
   case 1->{if(mc().screen instanceof CreateWorldScreen s){s.getUiState().setName("Realm Economy Colony LAN QA");s.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);s.getUiState().setAllowCommands(false);s.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);check(press("selectWorld.create"),"World creation unavailable");stage++;}}
   case 2->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){prepare();stage++;}}
   case 3->{if(ready){mc().setScreen(null);mc().options.hideGui=true;next=time+1800;stage++;}}
   case 4->{screenshot("colony-coffers.png");server(()->host().connection.teleport(1.3,161.15,-1.4,40,25));next=time+900;stage++;}
   case 5->{if(ready){screenshot("colony-coffer-closeup.png");mc().options.hideGui=false;server(()->open(host(),DESK));stage++;}}
   case 6->{if(available()){compact();screenshot("colony-account.png");input("amount","200");check(press("deposit"),"Owner deposit unavailable");stage++;}}
   case 7->{screenshot("colony-deposit-confirmation.png");check(press("confirm"),"Owner confirmation unavailable");stage++;}
   case 8->{if(available()&&view().get("balance").getAsLong()==20000){spawn();stage++;}}
   case 9->{if(Files.exists(OUT.resolve("guest-connected.json"))){guest=UUID.fromString(JsonParser.parseString(Files.readString(OUT.resolve("guest-connected.json"))).getAsJsonObject().get("id").getAsString());server(()->{var p=host().getServer().getPlayerList().getPlayer(guest);check(p!=null&&!p.hasPermissions(2),"Guest must be real non-OP");profile(p,"Aetherion",20000);colony().getPermissions().addPlayer(guest,p.getGameProfile().getName(),colony().getPermissions().getRankFriend());open(p,SECOND);});stage++;}}
   case 10->{if(Files.exists(OUT.resolve("guest-done.json"))){server(()->{check(account().balance()==25000,"Actual LAN deposit did not reach the shared account");open(host(),DESK);});stage++;}}
   case 11->{if(ready&&available()){check(press("exports"),"Exports tab missing");compact();stage++;}}
   case 12->{screenshot("colony-exports.png");check(press("deliver"),"Real export delivery missing");stage++;}
   case 13->{screenshot("colony-export-confirmation.png");check(press("confirm"),"Export confirmation missing");stage++;}
   case 14->{if(available()&&view().get("balance").getAsLong()==25000+exportCents){check(view().get("wallet").getAsLong()==80000,"Export paid the wrong wallet");check(press("invoices"),"Expenses tab missing");compact();stage++;}}
   case 15->{screenshot("colony-expenses.png");check(press("pay"),"Expense approval missing");stage++;}
   case 16->{screenshot("colony-expense-confirmation.png");check(press("confirm"),"Expense confirmation missing");stage++;}
   case 17->{if(available()&&view().get("balance").getAsLong()==22500+exportCents){check(press("pay"),"Second expense approval missing");stage++;}}
   case 18->{check(press("confirm"),"Second expense confirmation missing");stage++;}
   case 19->{if(available()&&view().get("balance").getAsLong()==20000+exportCents){check(press("history"),"History tab missing");compact();stage++;}}
   case 20->{screenshot("colony-records.png");check(press("account"),"Account tab missing");input("amount","50");check(press("withdraw"),"Owner withdrawal missing");stage++;}
   case 21->{check(press("confirm"),"Withdrawal confirmation missing");stage++;}
   case 22->{if(available()&&view().get("wallet").getAsLong()==85000){expectedBank=15000+exportCents;check(view().get("balance").getAsLong()==expectedBank,"Actual withdrawal lost conservation");mc().options.guiScale().set(3);mc().resizeDisplay();next=time+700;stage++;}}
   case 23->{screenshot("colony-compact-account.png");mc().options.guiScale().set(2);mc().resizeDisplay();server(()->{try{receipt(85000);check(account().orders().stream().filter(ColonyLedger.Order::delivered).count()==1,"Export state lost");check(account().invoices().values().stream().filter(ColonyLedger.Invoice::paid).count()==2,"Native fee approvals lost");}catch(Exception e){throw new IllegalStateException(e);}});stage++;}
   case 24->{if(ready){child.destroy();child.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);mc().level.disconnect();mc().disconnect(new TitleScreen());stage++;}}
   case 25->{if(mc().level==null&&mc().getSingleplayerServer()==null){mc().createWorldOpenFlows().openWorld(folder,()->mc().setScreen(new TitleScreen()));stage++;}}
   case 26->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){server(()->{check(account().balance()==expectedBank&&ChroniclesBridge.balance(host())==85000,"World reopening lost balances");check(account().orders().stream().filter(ColonyLedger.Order::delivered).count()==1,"World reopening repeated exports");host().serverLevel().removeBlock(DESK,false);open(host(),SECOND);});stage++;}}
   case 27->{if(ready&&available()){screenshot("colony-after-world-reopen.png");check(press("history"),"Reopened history missing");stage++;}}
   case 28->{screenshot("colony-reopened-records.png");server(()->{var p=host();p.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(),2,false));open(p,SECOND);});stage++;}
   case 29->{if(ready&&available()&&view().get("administrator").getAsBoolean()){check(press("admin"),"Native configuration tab unavailable");compact();stage++;}}
   case 30->{screenshot("colony-admin-settings.png");@SuppressWarnings("unchecked")var fields=(Map<String,EditBox>)field(mc().screen,"fields");fields.get("buildCents").setValue("30");fields.get("dailyCap").setValue("200");check(press("save"),"Native configuration save unavailable");stage++;}
   case 31->{screenshot("colony-policy-confirmation.png");check(press("confirm"),"Native configuration confirmation unavailable");stage++;}
   case 32->{if(available()&&view().getAsJsonObject("policy").get("buildCents").getAsLong()==3000){check(view().getAsJsonObject("policy").get("dailyCap").getAsLong()==20000,"Native configuration changed its submitted limit");server(()->{var d=EconomyData.get(host().getServer());check(account().invoices().values().stream().allMatch(i->i.cents()==2500),"Administrative configuration changed existing frozen invoices");check(d.audit().getCompound(0).getString("service").equals("COLONY_POLICY")&&d.audit().getCompound(0).getUUID("actor").equals(host().getUUID()),"Policy audit lost its author or kind");});stage++;}}
   case 33->{if(ready){screenshot("colony-admin-saved.png");finish(true,"Two actual LAN clients, native MineColonies accounts/rights, RP wallets, export delivery, permits, policy controls/audit, host dual saves and actual world reopening passed.");}}
   default->throw new IllegalStateException("Unknown colony QA stage");
  }
 }catch(Throwable e){e.printStackTrace();finish(false,e.toString());}}
}
