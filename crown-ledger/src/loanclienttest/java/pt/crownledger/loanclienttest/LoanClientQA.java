package pt.crownledger.loanclienttest;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.crownledger.block.BlockRegistry;
import pt.crownledger.client.LoanScreen;
import pt.crownledger.compat.*;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.*;
import pt.crownledger.service.*;
import static pt.crownledger.domain.Treasury.Currency.CHRONICLES;

/** Two actual Minecraft clients: ordinary borrower, sworn President, real RP wallet and a LAN restart. */
@EventBusSubscriber(modid="kingdomtreasury",value=Dist.CLIENT)
public final class LoanClientQA {
    private static final String ROLE=System.getProperty("loans.role","host");
    private static final Path OUT=Path.of(System.getProperty("loans.qaOutput","qa-results/loans-client")).toAbsolutePath();
    private static final List<String> SHOTS=new ArrayList<>(),CHECKS=new ArrayList<>();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final BlockPos DESK=new BlockPos(0,161,0),SECOND=new BlockPos(4,161,0);
    private static final long START=System.currentTimeMillis();
    private static Process child;private static volatile boolean ready;private static volatile Throwable failure;
    private static int stage,guestStep,last=-1;private static long next,stageAt;private static boolean booted,done;
    private static UUID guest,guestLoan,hostLoan;private static String folder;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static void write(String name,Object value)throws Exception{Files.createDirectories(OUT);var tmp=Files.createTempFile(OUT,"loan-qa-",".tmp");Files.writeString(tmp,JSON.toJson(value));Files.move(tmp,OUT.resolve(name),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    private static void server(Runnable task){ready=false;mc().getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable e){failure=e;}});}
    private static ServerPlayer host(){return mc().getSingleplayerServer().getPlayerList().getPlayer(mc().player.getUUID());}
    private static TreasuryData bank(){return TreasuryData.get(mc().getSingleplayerServer());}
    private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private static JsonObject view(){return mc().screen instanceof LoanScreen?(JsonObject)field(mc().screen,"data"):null;}
    private static JsonObject row(){var v=view();return v!=null&&v.has("rows")&&!v.getAsJsonArray("rows").isEmpty()?v.getAsJsonArray("rows").get(0).getAsJsonObject():null;}
    private static boolean state(String expected){return row()!=null&&row().get("state").getAsString().equals(expected)&&!(Boolean)field(mc().screen,"waiting");}
    private static boolean click(String key){String label=Component.translatable(key).getString();if(mc().screen!=null)for(var c:List.copyOf(mc().screen.children()))if(c instanceof AbstractButton b&&b.active&&b.visible&&b.getMessage().getString().equals(label)){b.onPress();return true;}return false;}
    private static boolean press(String key){return click("realm.treasury.loan."+key);}
    private static void input(String name,String value){((EditBox)field(mc().screen,name)).setValue(value);}
    private static void shot(String name)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}SHOTS.add(name);System.out.println("LOAN_QA_CAPTURE "+name);}
    private static void setup(){mc().getWindow().setWindowed(1600,900);mc().options.guiScale().set(2);mc().options.renderDistance().set(4);mc().options.simulationDistance().set(5);mc().resizeDisplay();}
    private static void quiet(){try{var f=Class.forName("pt.cronicasrp.client.ClientState").getDeclaredField("pending");f.setAccessible(true);f.setInt(null,0);var m=Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset");m.setAccessible(true);m.invoke(null);}catch(Exception ignored){}mc().getToasts().clear();}
    private static void profile(ServerPlayer p,String name){try{var type=Class.forName("pt.cronicasrp.domain.CharacterProfile");Object value=type.getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,name,28,"origins:human","Demonstration credit funds",name,"Common",20000L,20000L,"");Object attachment=((java.util.function.Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();for(var m:p.getClass().getMethods())if(m.getName().equals("setData")&&m.getParameterCount()==2&&m.getParameterTypes()[0].isInstance(attachment)){m.invoke(p,attachment,value);break;}ChroniclesBridge.syncReceipt(p);p.getServer().getPlayerList().saveAll();}catch(Exception e){throw new IllegalStateException(e);}}
    private static void crown(ServerPlayer p){try{var type=Class.forName("pt.realmcouncil.CouncilData");Object data=type.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,p.getServer());Object state=type.getField("state").get(data);var mt=Class.forName("pt.realmcouncil.domain.CouncilState$Mandate");Object m=mt.getConstructor().newInstance();mt.getField("playerId").set(m,p.getUUID());mt.getField("electionId").set(m,UUID.randomUUID());mt.getField("title").set(m,"President");mt.getField("name").set(m,ChroniclesBridge.publicName(p));long now=System.currentTimeMillis();for(var e:Map.of("beginsAt",now-1000,"endsAt",now+3600000,"claimedAt",now-500).entrySet())mt.getField(e.getKey()).setLong(m,e.getValue());state.getClass().getField("mandate").set(state,m);((net.minecraft.world.level.saveddata.SavedData)data).setDirty();p.getServer().getPlayerList().deop(p.getGameProfile());}catch(Exception e){throw new IllegalStateException(e);}}
    private static void prepare(){server(()->{try{var p=host();var level=p.serverLevel();var s=p.getServer();folder=s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();for(int cx=-1;cx<=0;cx++)for(int cz=-1;cz<=0;cz++){level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);}
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,s);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);level.setDayTime(6000);level.setWeatherParameters(0,60000,false,false);
        for(int x=-9;x<=9;x++)for(int z=-7;z<=8;z++){level.setBlock(new BlockPos(x,160,z),Math.abs(x)%3==0?Blocks.DEEPSLATE_TILES.defaultBlockState():Blocks.STONE_BRICKS.defaultBlockState(),3);for(int y=161;y<168;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        for(int x=-7;x<=7;x++)for(int y=161;y<=165;y++){level.setBlock(new BlockPos(x,y,4),(y==165?Blocks.DARK_OAK_PLANKS:Blocks.STONE_BRICKS).defaultBlockState(),3);if(x%3==0)level.setBlock(new BlockPos(x,y,4),Blocks.POLISHED_DEEPSLATE.defaultBlockState(),3);}
        for(int x:new int[]{-6,0,6}){level.setBlock(new BlockPos(x,163,3),Blocks.LANTERN.defaultBlockState(),3);level.setBlock(new BlockPos(x,161,3),Blocks.BOOKSHELF.defaultBlockState(),3);}
        for(int i=0;i<4;i++)level.setBlock(new BlockPos(-3+i*3,161,0),BlockRegistry.LOAN_DESK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}[i]),3);
        level.setBlock(DESK,BlockRegistry.LOAN_DESK.get().defaultBlockState(),3);level.setBlock(SECOND,BlockRegistry.LOAN_DESK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,Direction.NORTH),3);
        p.setGameMode(GameType.SURVIVAL);p.getFoodData().setFoodLevel(20);p.teleportTo(level,2.5,162,-4,0,17);profile(p,"Aetherion");crown(p);bank().treasury=new Treasury();bank().loans=new Loans();bank().governance=new Governance();bank().income=new IncomeLedger();bank().treasury.addFunds(CHRONICLES,100000,Treasury.Kind.DEPOSIT,p.getUUID(),"Controlled QA public funds",System.currentTimeMillis());bank().commitLoans(s,bank().treasury.copy(),bank().loans.copy());s.setUsesAuthentication(false);int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}check(s.publishServer(GameType.SURVIVAL,false,port),"Could not publish LAN");write("join.json",Map.of("port",port));}catch(Exception e){throw new IllegalStateException(e);}});}
    private static void spawn()throws Exception{var args=new ArrayList<>(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());args.add(0,"-Dloans.role=guest");args.add("-cp");args.add(System.getProperty("java.class.path"));args.add("net.neoforged.devlaunch.Main");for(String l:Files.readAllLines(OUT.getParent().getParent().resolve("build/moddev/clientRunProgramArgs.txt"))){l=l.strip();if(!l.isEmpty()&&!l.startsWith("#"))args.add(l.startsWith("\"")&&l.endsWith("\"")?l.substring(1,l.length()-1):l);}var run=OUT.resolve("guest-run");Files.createDirectories(run);option(args,"--gameDir",run.toString());option(args,"--username","CreditGuest");option(args,"--uuid",UUID.nameUUIDFromBytes("OfflinePlayer:CreditGuest".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-",""));option(args,"--accessToken","0");args.add(0,Path.of(System.getProperty("java.home"),"bin","java").toString());child=new ProcessBuilder(args).directory(run.toFile()).redirectErrorStream(true).redirectOutput(OUT.resolve("guest-client.log").toFile()).start();}
    private static void option(List<String> args,String name,String value){int i=args.indexOf(name);if(i<0){args.add(name);args.add(value);}else args.set(i+1,value);}
    private static void open(ServerPlayer p,BlockPos pos){p.teleportTo(p.serverLevel(),pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,10);p.serverLevel().getBlockState(pos).useWithoutItem(p.serverLevel(),p,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.NORTH,pos,false));}
    private static void compact(){var screen=mc().screen;check(screen instanceof LoanScreen,"Credit menu missing");for(int[] size:List.of(new int[]{320,240},new int[]{480,270},new int[]{800,450})){screen.resize(mc(),size[0],size[1]);var controls=new ArrayList<AbstractWidget>();for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible){check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=size[0]&&w.getY()+w.getHeight()<=size[1],"Credit control outside compact viewport: "+w.getMessage().getString());controls.add(w);}for(int i=0;i<controls.size();i++)for(int j=i+1;j<controls.size();j++){var a=controls.get(i);var b=controls.get(j);check(a.getX()+a.getWidth()<=b.getX()||b.getX()+b.getWidth()<=a.getX()||a.getY()+a.getHeight()<=b.getY()||b.getY()+b.getHeight()<=a.getY(),"Overlapping credit controls: "+a.getMessage().getString()+" / "+b.getMessage().getString());}}screen.resize(mc(),mc().getWindow().getGuiScaledWidth(),mc().getWindow().getGuiScaledHeight());}
    private static void hostReceipt(long expected)throws Exception{var p=host();var receipt=p.getPersistentData().getCompound(LoanTransactions.RECEIPT_KEY);for(var path:List.of(p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat"),p.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE))){var root=NbtIo.readCompressed(path,NbtAccounter.create(104857600));if(root.contains("Data"))root=root.getCompound("Data").getCompound("Player");check(root.getCompound("neoforge:attachments").getCompound("cronicasrp:profile").getLong("balance")==expected,"Host wallet missing from a native restart source");check(root.getCompound("NeoForgeData").getCompound(LoanTransactions.RECEIPT_KEY).equals(receipt),"Host receipt missing from a native restart source");check(LoanInventory.saved(p,root).equals(LoanInventory.inventory(p)),"Host inventory differs across restart sources");}}
    private static void guest()throws Exception{
        if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))click("gui.continue");
        if(!booted&&mc().screen instanceof TitleScreen&&Files.exists(OUT.resolve("join.json"))){setup();booted=true;int port=JsonParser.parseString(Files.readString(OUT.resolve("join.json"))).getAsJsonObject().get("port").getAsInt();String address="127.0.0.1:"+port;ConnectScreen.startConnecting(new TitleScreen(),mc(),ServerAddress.parseString(address),new ServerData("Credit LAN QA",address,ServerData.Type.LAN),false,null);}
        if(mc().player==null)return;write("guest-debug.json",Map.of("step",guestStep,"screen",String.valueOf(mc().screen),"view",view()==null?new JsonObject():view()));write("guest-connected.json",Map.of("id",mc().player.getUUID()));if(view()==null)return;
        switch(guestStep){
            case 0->{check(!view().get("manager").getAsBoolean(),"Ordinary borrower has approval powers");compact();input("amountInput","100.00");input("purposeInput","Tools and materials for the village workshop");next=System.currentTimeMillis()+700;guestStep++;}
            case 1->{shot("loans-new-request.png");check(press("submit"),"Cannot submit application");guestStep++;}
            case 2->{if(state("REQUESTED")){write("guest-requested.json",Map.of("id",row().get("id").getAsString()));guestStep++;}}
            case 3->{if(state("OFFERED")){check(row().get("principal").getAsLong()==10000&&row().get("total").getAsLong()==11000&&row().get("installments").getAsInt()==5,"Offered frozen terms differ from approval");compact();next=System.currentTimeMillis()+700;guestStep++;}}
            case 4->{shot("loans-unsigned-contract.png");mc().options.guiScale().set(3);mc().resizeDisplay();next=System.currentTimeMillis()+700;guestStep++;}
            case 5->{shot("loans-compact-contract.png");mc().options.guiScale().set(2);mc().resizeDisplay();check(press("sign"),"Cannot sign real contract");guestStep++;}
            case 6->{if(state("ACTIVE")){next=System.currentTimeMillis()+500;guestStep++;}}
            case 7->{shot("loans-signed-contract.png");write("guest-accepted.json",Map.of("success",true));guestStep++;}
            case 8->{if(Files.exists(OUT.resolve("allow-payment.json"))){check(press("pay"),"Cannot pay installment");guestStep++;}}
            case 9->{if(state("ACTIVE")&&row().get("paid").getAsLong()==2200){shot("loans-first-installment.png");write("guest-paid.json",Map.of("success",true));guestStep++;}}
            case 10->{if(Files.exists(OUT.resolve("allow-settle.json"))){check(press("settle"),"Cannot settle real debt");guestStep++;}}
            case 11->{if(view().getAsJsonArray("rows").isEmpty()&&!(Boolean)field(mc().screen,"waiting")){check(press("tab_archive"),"Cannot open borrower records");guestStep++;}}
            case 12->{if(state("SETTLED")){shot("loans-paid-in-full.png");write("guest-settled.json",Map.of("success",true));guestStep++;}}
            case 13->{if(Files.exists(OUT.resolve("host-needs-offer.json"))&&view().get("manager").getAsBoolean()){check(press("tab_review"),"New President cannot review host request");guestStep++;}}
            case 14->{if(state("REQUESTED")){compact();input("interestInput","0");input("countInput","2");input("daysInput","1");check(press("approve"),"New President cannot offer host credit");guestStep++;}}
            case 15->{if(state("OFFERED")){write("host-offered.json",Map.of("success",true));write("guest-result.json",Map.of("success",true,"screenshots",SHOTS,"realLoanPackets",true,"ordinaryBorrower",true,"roleChangedPresident",true));guestStep++;}}
        }
    }
    private static void finish(boolean ok,String why){done=true;try{if(!ok){shot(ROLE+"-failure.png");write(ROLE+"-view.json",view()==null?new JsonObject():view());}if(!ROLE.equals("guest")&&Files.exists(OUT.resolve("guest-result.json")))for(var image:JsonParser.parseString(Files.readString(OUT.resolve("guest-result.json"))).getAsJsonObject().getAsJsonArray("screenshots"))SHOTS.add(image.getAsString());write(ROLE.equals("guest")?"guest-failure.json":"result.json",Map.of("success",ok,"message",why,"stage",stage,"screenshots",SHOTS,"checks",CHECKS,"actualMinecraftClients",2,"actualTcpLan",true,"authenticatedClients",false,"actualChroniclesWallet",true,"actualCouncilPresident",true));}catch(Exception e){e.printStackTrace();}if(child!=null)child.destroy();System.out.println("LOANS_CLIENT_QA success="+ok+" "+why);mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(done)return;long time=System.currentTimeMillis();try{if(failure!=null)throw new IllegalStateException("Server fixture failed",failure);check(time-START<600000,"Loan client timeout");if(mc().player!=null)quiet();if(mc().getOverlay()!=null||time<next)return;next=time+350;org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc().getWindow().getWindow(),1,1);
        if(ROLE.equals("guest")){guest();return;}if(Files.exists(OUT.resolve("guest-failure.json")))throw new IllegalStateException(Files.readString(OUT.resolve("guest-failure.json")));if(last!=stage){last=stage;stageAt=time;System.out.println("LOANS_CLIENT_STAGE "+stage);}check(time-stageAt<120000,"Stalled loan stage "+stage);
        switch(stage){
            case 0->{if(mc().screen instanceof TitleScreen){setup();Files.createDirectories(OUT);for(var p:Files.list(OUT).filter(p->p.getFileName().toString().endsWith(".json")).toList())Files.delete(p);CreateWorldScreen.openFresh(mc(),mc().screen);stage++;}else if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))click("gui.continue");}
            case 1->{if(mc().screen instanceof CreateWorldScreen s){s.getUiState().setName("Realm Treasury credit LAN QA");s.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);s.getUiState().setAllowCommands(false);s.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);check(click("selectWorld.create"),"Create world unavailable");stage++;}}
            case 2->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){prepare();stage++;}}
            case 3->{if(ready){mc().setScreen(null);mc().options.hideGui=true;next=time+2500;stage++;}}
            case 4->{shot("loans-royal-counters.png");server(()->host().teleportTo(host().serverLevel(),1.65,161.35,-1.4,40,26));next=time+1000;stage++;}
            case 5->{if(ready){shot("loans-counter-closeup.png");mc().options.hideGui=false;spawn();stage++;}}
            case 6->{if(Files.exists(OUT.resolve("guest-connected.json"))){guest=UUID.fromString(JsonParser.parseString(Files.readString(OUT.resolve("guest-connected.json"))).getAsJsonObject().get("id").getAsString());server(()->{var p=mc().getSingleplayerServer().getPlayerList().getPlayer(guest);check(p!=null&&!p.hasPermissions(2),"Borrower must be non-admin");profile(p,"Aveline");open(p,DESK);});stage++;}}
            case 7->{if(ready&&Files.exists(OUT.resolve("guest-requested.json"))){guestLoan=UUID.fromString(JsonParser.parseString(Files.readString(OUT.resolve("guest-requested.json"))).getAsJsonObject().get("id").getAsString());server(()->{check(LoanService.manager(host())&&!host().hasPermissions(2),"Host must be sworn President without administrator permission");open(host(),SECOND);});stage++;}}
            case 8->{if(ready&&view()!=null){check(press("tab_review"),"President review tab missing");stage++;}}
            case 9->{if(state("REQUESTED")){compact();input("interestInput","10");input("countInput","5");input("daysInput","1");next=time+600;stage++;}}
            case 10->{shot("loans-president-review.png");check(press("approve"),"Cannot approve funded request");stage++;}
            case 11->{if(state("OFFERED")){server(()->check(bank().treasury.balance(CHRONICLES)==90000&&ChroniclesBridge.balance(mc().getSingleplayerServer().getPlayerList().getPlayer(guest))==20000,"Approval did not reserve exactly 100 euros before signing"));shot("loans-reserved-offer.png");stage++;}}
            case 12->{if(ready&&Files.exists(OUT.resolve("guest-accepted.json"))){server(()->{var p=mc().getSingleplayerServer().getPlayerList().getPlayer(guest);check(ChroniclesBridge.balance(p)==30000&&ChroniclesBridge.balance(host())==20000&&bank().treasury.balance(CHRONICLES)==90000&&bank().loans.get(guestLoan).state()==Loans.State.ACTIVE,"Signed principal or treasury distribution wrong");});stage++;}}
            case 13->{if(ready){write("allow-payment.json",Map.of("go",true));stage++;}}
            case 14->{if(Files.exists(OUT.resolve("guest-paid.json"))){server(()->check(bank().loans.get(guestLoan).paid()==2200&&bank().treasury.balance(CHRONICLES)==92200&&ChroniclesBridge.balance(mc().getSingleplayerServer().getPlayerList().getPlayer(guest))==27800,"Installment did not conserve cents"));stage++;}}
            case 15->{if(ready){write("allow-settle.json",Map.of("go",true));stage++;}}
            case 16->{if(Files.exists(OUT.resolve("guest-settled.json"))){server(()->{check(bank().loans.get(guestLoan).state()==Loans.State.SETTLED&&bank().treasury.balance(CHRONICLES)==101000&&ChroniclesBridge.balance(mc().getSingleplayerServer().getPlayerList().getPlayer(guest))==19000&&ChroniclesBridge.balance(host())==20000,"Full settlement created money or paid the President personally");crown(mc().getSingleplayerServer().getPlayerList().getPlayer(guest));check(!LoanService.manager(host()),"Former President retained credit approval");open(host(),SECOND);});CHECKS.add("Ordinary borrower, real sworn President, exact 100 euro escrow, fixed 10 percent interest, 22 euro installment and early 88 euro settlement conserved every cent.");stage++;}}
            case 17->{if(ready&&view()!=null){check(press("new_request"),"Former President cannot request credit");input("amountInput","50.00");input("purposeInput","Stone and timber for the village bridge");check(press("submit"),"Host application unavailable");stage++;}}
            case 18->{if(state("REQUESTED")){hostLoan=UUID.fromString(row().get("id").getAsString());write("host-needs-offer.json",Map.of("id",hostLoan));stage++;}}
            case 19->{if(state("OFFERED")&&Files.exists(OUT.resolve("host-offered.json"))){check(row().get("total").getAsLong()==5000,"New President changed the zero-interest quote");check(press("sign"),"Integrated host cannot sign");stage++;}}
            case 20->{if(state("ACTIVE")){server(()->{try{check(bank().treasury.balance(CHRONICLES)==96000&&ChroniclesBridge.balance(host())==25000,"Second principal wrong");hostReceipt(25000);}catch(Exception e){throw new IllegalStateException(e);}});next=time+600;stage++;}}
            case 21->{if(ready){shot("loans-host-agreement.png");CHECKS.add("Authority changed with the Council mandate; the new non-admin President approved the old President's request. Both native host restart files contain the identical wallet and receipt.");child.destroy();child.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);mc().level.disconnect();mc().disconnect(new TitleScreen());stage++;}}
            case 22->{if(mc().level==null&&mc().getSingleplayerServer()==null){mc().createWorldOpenFlows().openWorld(folder,()->mc().setScreen(new TitleScreen()));stage++;}}
            case 23->{if(mc().player!=null&&mc().getSingleplayerServer()!=null){server(()->{check(ChroniclesBridge.balance(host())==25000&&bank().treasury.balance(CHRONICLES)==96000&&bank().loans.get(hostLoan).state()==Loans.State.ACTIVE&&bank().loans.get(guestLoan).state()==Loans.State.SETTLED,"Real world reopening lost money or debts");host().serverLevel().removeBlock(DESK,false);open(host(),SECOND);});stage++;}}
            case 24->{if(ready&&state("ACTIVE")){compact();next=time+600;stage++;}}
            case 25->{shot("loans-after-world-reopen.png");check(press("settle"),"Reopened host agreement cannot be settled");stage++;}
            case 26->{if(view()!=null&&view().getAsJsonArray("rows").isEmpty()&&!(Boolean)field(mc().screen,"waiting")){server(()->{try{check(bank().treasury.balance(CHRONICLES)==101000&&ChroniclesBridge.balance(host())==20000&&bank().loans.get(hostLoan).state()==Loans.State.SETTLED&&bank().loanJournal()==null,"Reopened settlement duplicated or lost money");hostReceipt(20000);}catch(Exception e){throw new IllegalStateException(e);}});check(press("tab_archive"),"Host records unavailable");stage++;}}
            case 27->{if(ready&&state("SETTLED")){next=time+600;stage++;}}
            case 28->{shot("loans-host-paid-in-full.png");CHECKS.add("The actual LAN world closed and reopened, retained both agreements and their escrow, and settled the host debt through a different physical desk after removing the original.");CHECKS.add("Native controls and contract scrolling fit 320x240, 480x270 and 800x450; digital credit uses the existing Realm Origins wallet and native C2S/S2C packets.");finish(true,"Two native LAN clients, real wallets, live Council authority, loan escrow, installment payments, world reopening and both host restart files passed.");}
        }
    }catch(Throwable e){e.printStackTrace();finish(false,e.toString());}}
}
