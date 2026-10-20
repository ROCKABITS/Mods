package pt.contracts.visualtest;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.client.*;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.FishingHook;
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
import pt.contracts.*;
import pt.contracts.compat.ChroniclesBridge;
import pt.contracts.client.*;
import pt.contracts.daily.*;
import pt.contracts.daily.DailyRules.*;
import pt.contracts.daily.DailyLedger.*;

/** Actual client and integrated host, actual C2S widgets, survival events and durable financial receipts. */
@EventBusSubscriber(modid="contracts",value=Dist.CLIENT)
public final class DailyVisualQA {
    private static final Path OUT=Path.of(System.getProperty("daily.qaOutput","qa-results/daily-client"));
    private static final long START=System.currentTimeMillis();
    private static final List<String> CAPTURES=new ArrayList<>(),CHECKS=new ArrayList<>();
    private static final List<String> GUI_REQUESTS=new ArrayList<>();
    private static volatile Throwable failure;
    private static volatile boolean ready;
    private static volatile String frozen,worldFolder,questIds;
    private static volatile long expectedWallet,totalNet,totalTax,initialBank;
    private static volatile UUID recipient;
    private static int step,lastStep=-1,accepted,genericBaseline;
    private static long next,stageStart,movingUntil;
    private static Vec3 moveStart;
    private static boolean easyShot,mediumShot,simpleShot,hardCounting,hardShot,allShot,muted,skipped,controlsFree,done;
    private static String firstEasy,secondEasy,firstMedium;
    private static int soundBaseline;
    private static boolean publishedLan;
    private static DailyScreen preferencesParent;
    private static JsonObject preferencesData;
    private static void publishLan() throws Exception {
        var host=mc().getSingleplayerServer();
        if(!host.isPublished()){
            int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
            check(host.publishServer(GameType.SURVIVAL,true,port),"Actual LAN publication failed");
        }
        check(host.isPublished(),"Integrated server is not open to LAN");publishedLan=true;
    }
    private static final BlockPos SMALL=new BlockPos(-5,161,-2),TALL=new BlockPos(-1,161,-2),WIDE=new BlockPos(4,161,-2),ORE=new BlockPos(-6,161,2),CROP=new BlockPos(-4,161,2);
    private DailyVisualQA(){}
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static ServerPlayer player(){return Objects.requireNonNull(mc().getSingleplayerServer().getPlayerList().getPlayer(recipient));}
    private static void server(Runnable task){ready=false;check(mc().getSingleplayerServer()!=null,"No integrated host");mc().getSingleplayerServer().execute(()->{try{task.run();ready=true;}catch(Throwable error){failure=error;}});}
    private static Object privateField(Object target,String name)throws Exception{var field=target instanceof Class<?> type?type.getDeclaredField(name):target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target instanceof Class<?>?null:target);}
    private static JsonObject screenData()throws Exception{if(mc().screen instanceof DailyScreen s)return s.data();if(mc().screen instanceof DailyAdminScreen)return (JsonObject)privateField(mc().screen,"data");throw new IllegalStateException("Daily UI not open");}
    @SuppressWarnings("unchecked") private static void field(String name,String value)throws Exception{var fields=(Map<String,EditBox>)privateField(mc().screen,"fields");check(fields.containsKey(name),"Missing native field "+name);fields.get(name).setValue(value);}
    private static boolean click(String translation){if(mc().screen==null)return false;String label=Component.translatable(translation).getString();for(var child:List.copyOf(mc().screen.children()))if(child instanceof AbstractButton button&&button.active&&button.getMessage().getString().equals(label)){GUI_REQUESTS.add(translation);button.onPress();return true;}return false;}
    private static void dailyClick(String key){check(click("daily.contracts."+key),"Missing clickable daily control "+key);}
    private static JsonObject active()throws Exception{return (JsonObject)privateField(DailyCinematic.class,"active");}
    private static String difficulty()throws Exception{JsonObject e=active();return e==null?"":DailyScreen.str(e,"difficulty");}
    private static String activeId()throws Exception{JsonObject e=active();return e==null?"":DailyScreen.str(e,"id");}
    private static int genericNotices(){try{return ((List<?>)Class.forName("pt.realmnotifications.client.NotificationsClient").getMethod("history").invoke(null)).size();}catch(Exception absent){return 0;}}
    private static void quietCreation(){try{var type=Class.forName("pt.cronicasrp.client.ClientState");var pending=type.getDeclaredField("pending");pending.setAccessible(true);pending.setInt(null,0);var reset=Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(null);}catch(Exception ignored){}}
    @SuppressWarnings({"unchecked","rawtypes"}) private static void profile(ServerPlayer p)throws Exception{
        AttachmentType attachment=(AttachmentType)((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();
        Object value=Class.forName("pt.cronicasrp.domain.CharacterProfile").getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,"Aetherion",28,"origins:human","Visual QA biography","Visual QA signature","Comum",100000L,100000L,"");p.setData(attachment,value);DailyWallet.sync(p);
        Object data=Class.forName("pt.realmrewards.data.RewardData").getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,p.getServer());Object ledger=data.getClass().getField("ledger").get(data);ledger.getClass().getMethod("baseline",UUID.class,Collection.class).invoke(ledger,p.getUUID(),p.getServer().getAdvancements().getAllAdvancements().stream().map(a->a.id().toString()).toList());Object account=ledger.getClass().getMethod("account",UUID.class).invoke(ledger,p.getUUID());long pending=(long)account.getClass().getMethod("pending").invoke(account);if(pending>0)ledger.getClass().getMethod("paid",UUID.class,long.class).invoke(ledger,p.getUUID(),pending);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static long bank()throws Exception{Object data=Class.forName("pt.crownledger.data.TreasuryData").getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,player().getServer());Object treasury=data.getClass().getField("treasury").get(data);Class<Enum> currency=(Class<Enum>)Class.forName("pt.crownledger.domain.Treasury$Currency");return(long)treasury.getClass().getMethod("balance",currency).invoke(treasury,Enum.valueOf(currency,"CHRONICLES"));}
    private static void place(ServerPlayer p,net.minecraft.world.level.block.Block board,BlockPos pos){p.setYRot(180);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(board));var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);check(p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Native board placement failed");}
    private static void prepare(){recipient=mc().player.getUUID();server(()->{try{
        var p=player();var level=p.serverLevel();var host=p.getServer();publishLan();profile(p);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
        level.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false,host);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,host);level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,host);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,host);level.setDayTime(1000);
        for(int x=-12;x<=12;x++)for(int z=-7;z<=11;z++){level.setBlock(new BlockPos(x,160,z),Blocks.SPRUCE_PLANKS.defaultBlockState(),3);for(int y=161;y<=170;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        for(int x=-11;x<=11;x++)for(int y=161;y<167;y++)level.setBlock(new BlockPos(x,y,-4),(y==161?Blocks.STONE_BRICKS:y==166||x%4==0?Blocks.STRIPPED_SPRUCE_LOG:Blocks.OAK_PLANKS).defaultBlockState(),3);
        for(int x:new int[]{-10,10}){for(int y=161;y<165;y++)level.setBlock(new BlockPos(x,y,-3),Blocks.BOOKSHELF.defaultBlockState(),3);level.setBlock(new BlockPos(x,165,-3),Blocks.LANTERN.defaultBlockState(),3);}
        for(int x:new int[]{-8,2,8})level.setBlock(new BlockPos(x,161,-3),Blocks.LANTERN.defaultBlockState(),3);
        for(int x=-2;x<=2;x++)for(int z=2;z<8;z++)level.setBlock(new BlockPos(x,161,z),Blocks.RED_CARPET.defaultBlockState(),3);
        p.setPos(0,161,6);place(p,ContractsMod.BOARD.get(),SMALL);place(p,ContractsMod.TALL_BOARD.get(),TALL);place(p,ContractsMod.WIDE_BOARD.get(),WIDE);
        level.setBlock(ORE,Blocks.COAL_ORE.defaultBlockState(),3);level.setBlock(CROP.below(),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(CROP,((CropBlock)Blocks.WHEAT).getStateForAge(7),3);
        for(int x=6;x<=10;x++)for(int z=3;z<=7;z++)for(int y=157;y<=160;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),3);
        for(var advancement:host.getAdvancements().getAllAdvancements())for(String criterion:advancement.value().criteria().keySet())p.getAdvancements().getOrStartProgress(advancement).grantProgress(criterion);
        p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.DIAMOND_PICKAXE));p.getInventory().setItem(1,new ItemStack(Items.DIAMOND_SWORD));p.getInventory().setItem(2,new ItemStack(Items.FISHING_ROD));p.getInventory().setItem(5,new ItemStack(Items.DIAMOND,8));p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));
        DailyData data=DailyData.get(host);data.ledger.players.remove(p.getUUID());data.ledger.rewards.entrySet().removeIf(e->e.getValue().player.equals(p.getUUID()));data.ledger.salt=new UUID(4242,9090);data.ledger.settings=new Settings();data.ledger.initialized=true;
        List<Template> templates=new ArrayList<>();Map<String,Integer> wanted=Map.of("mining_coal_easy",1,"farming_wheat_easy",1,"combat_zombie_medium",1,"fishing_fish_medium",1,"delivery_diamonds_hard",8);
        for(Template t:DailyCatalog.all(data.ledger))if(wanted.containsKey(t.id().substring(t.id().indexOf(':')+1)))templates.add(new Template(t.id(),t.title(),t.category(),t.difficulty(),t.target(),wanted.get(t.id().substring(t.id().indexOf(':')+1)),t.reward(),t.weight(),t.mods(),t.dimensions(),t.biomes(),true,t.validation()));
        check(templates.size()==5,"Controlled native catalogue missing");var round=data.ledger.generate(p.getUUID(),System.currentTimeMillis(),templates,"CHRONICLES");frozen=DailyLedger.JSON.toJson(round);questIds=round.quests.stream().map(q->q.id.toString()).sorted().toList().toString();data.ledger.placed.remove(DailyEvents.position(level,ORE));data.ledger.consumed.remove(DailyEvents.position(level,ORE));data.persist(host);initialBank=bank();worldFolder=host.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();p.connection.teleport(.5,161,7.5,180,0);
    }catch(Exception e){throw new IllegalStateException(e);}});}
    private static void teleport(BlockPos board){server(()->player().connection.teleport(board.getX()+.5,board.getY(),board.getZ()+2.5,180,0));}
    private static void interact(BlockPos board){mc().setScreen(null);check(mc().gameMode!=null,"No client game mode");mc().gameMode.useItemOn(mc().player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(board),Direction.SOUTH,board,false));GUI_REQUESTS.add("actual_mural_use:"+board);}
    private static void easyActions(){server(()->{var p=player();p.connection.teleport(-5,161,3.5,180,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));check(p.gameMode.destroyBlock(ORE),"Real ore break failed");check(p.gameMode.destroyBlock(CROP),"Real crop harvest failed");});}
    private static void mediumActions(){server(()->{try{var p=player();var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.spawn(p.serverLevel(),p.blockPosition().offset(2,0,0),MobSpawnType.NATURAL);check(zombie!=null&&DailyEvents.natural(zombie),"Natural zombie fixture failed");zombie.setNoAi(true);zombie.setHealth(1);zombie.hurt(p.damageSources().playerAttack(p),50);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FISHING_ROD));var nibble=FishingHook.class.getDeclaredField("nibble");nibble.setAccessible(true);for(int i=0;i<12;i++){var hook=new FishingHook(p,p.serverLevel(),0,0);hook.setPos(8,160,5);p.serverLevel().addFreshEntity(hook);nibble.setInt(hook,10);hook.retrieve(p.getMainHandItem());}}catch(Exception e){throw new IllegalStateException(e);}});}
    private static void capture(String name)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(mc().getMainRenderTarget())){check(image.getWidth()==1600&&image.getHeight()==900,"Wrong framebuffer dimensions");image.writeToFile(OUT.resolve(name));}CAPTURES.add(name);}
    private static void bounds(Screen screen){mc().setScreen(screen);screen.resize(mc(),320,240);for(var child:screen.children())if(child instanceof AbstractWidget w)check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=320&&w.getY()+w.getHeight()<=240,"Compact control outside screen: "+w.getMessage().getString());}
    private static void verifyServer(boolean reentry){server(()->{try{
        publishLan();var p=player();var data=DailyData.get(p.getServer());var round=data.ledger.account(p.getUUID()).round;check(round.quests.stream().allMatch(q->q.state==State.PAID)&&round.specialConfirmed,"Five quests did not settle");var rewards=data.ledger.rewards.values().stream().filter(r->r.player.equals(p.getUUID())).toList();check(rewards.size()==6&&rewards.stream().allMatch(r->r.paid&&r.notified),"Paid animations did not acknowledge over C2S");long net=rewards.stream().mapToLong(r->r.net).sum(),tax=rewards.stream().mapToLong(r->r.tax).sum();check(round.grossCompleted<=30000&&round.cap==30000,"Generated cap changed after admin settings");check(ChroniclesBridge.balance(p)==100000+net&&bank()==initialBank+tax,"Actual wallet/Crown tax does not conserve completed rewards");check(ChroniclesBridge.reputation(p)==3,"Daily reputation cap failed");check(DailyWallet.current(p).getCompound("progress").getInt("missions")==5,"Chronicles mission counter did not advance five times");
        CompoundTag proof=new CompoundTag();proof.putUUID("id",round.specialId);proof.putUUID("player",p.getUUID());proof.putBoolean("delivery",false);check(DailyRewardJournal.savedReceipt(p,proof)&&DailyRewardJournal.savedHostReceipt(p,proof),"LAN owner receipt missing from UUID.dat or level.dat");check(questIds.equals(round.quests.stream().map(q->q.id.toString()).sorted().toList().toString()),"Reconnect rerolled the daily set");expectedWallet=ChroniclesBridge.balance(p);totalNet=net;totalTax=tax;if(!reentry)p.getServer().saveEverything(true,true,true);
    }catch(Exception e){throw new IllegalStateException(e);}});}
    private static void complete(boolean success,String message){done=true;mc().options.keyUp.setDown(false);try{Files.createDirectories(OUT);Map<String,Object> result=new LinkedHashMap<>();result.put("success",success);result.put("message",message);result.put("step",step);result.put("screenshots",CAPTURES);result.put("checks",CHECKS);result.put("actual_minecraft_client",true);result.put("actual_integrated_server",true);result.put("real_c2s_widgets",true);result.put("real_s2c_cinematics",true);result.put("real_chronicles_wallet",true);result.put("real_crown_tax",true);result.put("survival_events",true);result.put("synthetic_financial_snapshots",false);result.put("controlled_quest_quantities",true);result.put("all_three_physical_boards",true);result.put("actual_world_close_and_reopen",step>=31);result.put("lan_owner_dual_save",step>=24);result.put("actual_lan_publication",publishedLan);result.put("authenticated_two_clients",false);result.put("actual_realm_admin",false);result.put("controls_free",controlsFree);result.put("manual_skip",skipped);result.put("muted_real_completion",muted);result.put("net_cents",totalNet);result.put("tax_cents",totalTax);result.put("wallet_cents",expectedWallet);result.put("native_gui_actions",GUI_REQUESTS);Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception error){error.printStackTrace();}System.out.println(success?"DAILY_CLIENT_PASSED":"DAILY_CLIENT_FAILED "+message);mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(done)return;long now=System.currentTimeMillis();try{
            if(mc().screen!=null)GLFW.glfwSetCursorPos(mc().getWindow().getWindow(),mc().getWindow().getScreenWidth()-3,mc().getWindow().getScreenHeight()-3);
            if(failure!=null)throw new IllegalStateException("Integrated host fixture failed",failure);check(now-START<900000,"Daily visual QA timed out");if(now<next||mc().getOverlay()!=null)return;next=now+180;
            if(lastStep!=step){lastStep=step;stageStart=now;System.out.println("DAILY_QA_STAGE "+step);}else check(now-stageStart<120000,"Daily visual stage stalled "+step+" "+(mc().screen==null?"HUD":mc().screen.getClass().getSimpleName()));
            if(mc().player!=null)quietCreation();
            switch(step){
                case 0->{if(mc().screen instanceof TitleScreen){mc().getWindow().setWindowed(1600,900);mc().options.pauseOnLostFocus=false;mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc().options.guiScale().set(2);mc().options.renderDistance().set(4);mc().options.simulationDistance().set(4);mc().resizeDisplay();CreateWorldScreen.openFresh(mc(),mc().screen);step++;}else if(mc().screen!=null&&mc().screen.getClass().getSimpleName().contains("Accessibility"))click("gui.continue");}
                case 1->{if(mc().screen instanceof CreateWorldScreen world){world.getUiState().setName("Contracts Daily Quests visual QA");world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);world.getUiState().setAllowCommands(true);world.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);check(click("selectWorld.create"),"Missing create world button");step++;}}
                case 2->{if(mc().player!=null&&mc().level!=null&&mc().getSingleplayerServer()!=null){prepare();step++;}}
                case 3->{if(!ready)return;mc().setScreen(null);mc().getToasts().clear();DailyCinematic.reset();var p=DailyCinematic.preferences();p.enabled=true;p.volume=.55f;p.duration=5;p.simplified=false;p.reducedParticles=false;p.seen.clear();p.save();step++;next=now+4000;}
                case 4->{if(!ready)return;capture("daily-three-boards.png");teleport(SMALL);step=40;next=now+800;}
                case 40->{if(!ready)return;interact(SMALL);step=5;}
                case 5->{if(mc().screen instanceof pt.contracts.client.ContractsScreen){check(click("gui.contracts.daily"),"Daily category missing on the small board");step++;}}
                case 6->{if(mc().screen instanceof DailyScreen){capture("daily-available.png");dailyClick("government");step++;}}
                case 7->{if(mc().screen instanceof DailyAdminScreen){field("taxBps","10");field("bonus.MINING","10");capture("daily-government.png");dailyClick("save");step++;}}
                case 8->{if(DailyScreen.number(screenData().getAsJsonObject("settings"),"taxBps")!=1000)return;server(()->check(frozen.equals(DailyLedger.JSON.toJson(DailyData.get(player().getServer()).ledger.account(recipient).round)),"Policy changed generated quest values"));dailyClick("back");step++;next=now+500;}
                case 9->{if(!ready||!(mc().screen instanceof DailyScreen))return;dailyClick("admin");step++;}
                case 10->{if(!(mc().screen instanceof DailyAdminScreen))return;field("dailyCap","350");capture("daily-administration.png");dailyClick("save");step++;}
                case 11->{if(DailyScreen.number(screenData().getAsJsonObject("settings"),"dailyCap")!=35000)return;dailyClick("catalog");((EditBox)privateField(mc().screen,"searchField")).setValue("mining_coal_easy");dailyClick("search");step++;}
                case 12->{if(DailyScreen.number(screenData(),"catalogTotal")!=1)return;dailyClick("edit");field("quantity","17");step=120;next=now+300;}
                case 120->{capture("daily-template-editor.png");dailyClick("save");step=13;}
                case 13->{if(!DailyScreen.str(screenData(),"message").endsWith("template_saved"))return;server(()->check(frozen.equals(DailyLedger.JSON.toJson(DailyData.get(player().getServer()).ledger.account(recipient).round)),"Catalogue edit changed an existing quest"));dailyClick("back");step++;next=now+500;}
                case 14->{if(!ready||!(mc().screen instanceof DailyScreen))return;JsonArray quests=screenData().getAsJsonObject("round").getAsJsonArray("quests");accepted=(int)quests.asList().stream().filter(q->q.getAsJsonObject().get("state").getAsString().equals("ACTIVE")).count();if(accepted<5){if(click("daily.contracts.accept"))next=now+300;return;}capture("daily-accepted.png");mc().setScreen(null);genericBaseline=genericNotices();easyActions();step++;}
                case 15->{if(!ready||!DailyCinematic.active())return;if(firstEasy==null){firstEasy=activeId();moveStart=mc().player.position();mc().options.keyUp.setDown(true);mc().player.setYRot(mc().player.getYRot()+9);movingUntil=now+500;}if(movingUntil>0&&now>=movingUntil){mc().options.keyUp.setDown(false);controlsFree=mc().screen==null&&mc().player.position().distanceTo(moveStart)>.05;movingUntil=0;}if(activeId().equals(firstEasy)&&DailyCinematic.age()>2.5&&!easyShot){capture("daily-cinematic-easy.png");easyShot=true;}if(!activeId().equals(firstEasy)&&difficulty().equals("EASY")){check(easyShot,"First easy celebration was not captured");secondEasy=activeId();soundBaseline=DailyCinematic.soundsPlayed();DailyCinematic.preferences().volume=0;step++;}}
                case 16->{if(!activeId().equals(secondEasy))throw new IllegalStateException("Second celebration ended before mute check");if(DailyCinematic.age()<.8)return;muted=DailyCinematic.soundsPlayed()==soundBaseline;check(muted,"Muted completion requested a sound");KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_BACKSPACE));skipped=true;DailyCinematic.preferences().volume=.55f;mediumActions();step++;}
                case 17->{if(!ready||!difficulty().equals("MEDIUM"))return;if(firstMedium==null)firstMedium=activeId();if(activeId().equals(firstMedium)&&DailyCinematic.age()>2.7&&!mediumShot){capture("daily-cinematic-medium.png");mediumShot=true;}if(!activeId().equals(firstMedium)){DailyCinematic.preferences().simplified=true;DailyCinematic.preferences().reducedParticles=true;step++;}}
                case 18->{if(!difficulty().equals("MEDIUM")||DailyCinematic.age()<2.65)return;capture("daily-cinematic-simplified.png");simpleShot=true;teleport(WIDE);step++;next=now+600;}
                case 19->{if(!ready)return;interact(WIDE);step++;}
                case 20->{if(mc().screen instanceof pt.contracts.client.ContractsScreen){check(click("gui.contracts.daily"),"Daily category missing on the wide board");step++;}}
                case 21->{if(!(mc().screen instanceof DailyScreen))return;capture("daily-four-complete.png");dailyClick("deliver");step++;}
                case 22->{if(!(mc().screen instanceof DailyScreen))return;boolean paid=screenData().getAsJsonObject("round").getAsJsonArray("quests").asList().stream().allMatch(q->q.getAsJsonObject().get("state").getAsString().equals("PAID"));if(!paid)return;capture("daily-five-complete.png");mc().setScreen(null);DailyCinematic.preferences().simplified=false;DailyCinematic.preferences().reducedParticles=false;step++;}
                case 23->{if(difficulty().equals("HARD")){if(DailyCinematic.age()>2&&!hardCounting){capture("daily-cinematic-hard-counting.png");hardCounting=true;}if(DailyCinematic.age()>3.3&&!hardShot){capture("daily-cinematic-hard.png");hardShot=true;}}if(difficulty().equals("ALL")&&DailyCinematic.age()>3.4&&!allShot){capture("daily-cinematic-all.png");allShot=true;}if(allShot&&!DailyCinematic.active()&&DailyCinematic.pending()==0){check(easyShot&&mediumShot&&simpleShot&&hardShot,"Missing real completion frames");check(controlsFree,"Cinematic blocked normal movement");check(genericNotices()==genericBaseline,"Completion duplicated the generic HUD notice");verifyServer(false);step++;}}
                case 24->{if(!ready)return;CHECKS.add("Five real accepted quests completed through mining, crop harvest, natural combat, vanilla fishing and consumed delivery; five individual receipts and one zero-bonus finale settled once in the actual Chronicles wallet and Crown treasury. Both LAN owner files contain the receipt.");teleport(TALL);step++;next=now+600;}
                case 25->{if(!ready)return;interact(TALL);step++;}
                case 26->{if(mc().screen instanceof pt.contracts.client.ContractsScreen){check(click("gui.contracts.daily"),"Daily category missing on the tall board");step++;}}
                case 27->{if(!(mc().screen instanceof DailyScreen))return;preferencesParent=(DailyScreen)mc().screen;preferencesData=preferencesParent.data();dailyClick("visuals");step=270;next=now+300;}
                case 270->{var parent=preferencesParent;JsonObject data=preferencesData;check(mc().screen instanceof DailyVisualScreen,"Visual preferences did not open");capture("daily-visual-preferences.png");check(click("daily.contracts.on"),"Animation toggle missing");check(!DailyVisualPreferences.load().enabled,"Animation toggle did not persist");check(click("daily.contracts.off"),"Animation re-enable missing");bounds(new DailyScreen(data));var admin=new DailyAdminScreen(parent,data,true);mc().setScreen(admin);bounds(admin);var gov=new DailyAdminScreen(parent,data,false);mc().setScreen(gov);bounds(gov);var prefs=new DailyVisualScreen(parent);mc().setScreen(prefs);bounds(prefs);mc().setScreen(null);CHECKS.add("All three physical boards used actual client interaction packets. Native menu fields and preferences fit320x240; preferences persisted. Real celebrations queued, one muted completion played no sound, manual skip preserved payment, and movement/camera controls stayed available.");mc().level.disconnect();mc().disconnect(new TitleScreen());step=28;next=now+1500;}
                case 28->{if(mc().level!=null||mc().getSingleplayerServer()!=null)return;mc().createWorldOpenFlows().openWorld(worldFolder,()->mc().setScreen(new TitleScreen()));step++;}
                case 29->{if(mc().player==null||mc().level==null||mc().getSingleplayerServer()==null)return;verifyServer(true);step++;next=now+1500;}
                case 30->{if(!ready)return;check(!DailyCinematic.active()&&DailyCinematic.pending()==0,"Rejoining replayed acknowledged animations");teleport(SMALL);step++;next=now+600;}
                case 31->{if(!ready)return;interact(SMALL);step++;}
                case 32->{if(mc().screen instanceof pt.contracts.client.ContractsScreen){check(click("gui.contracts.daily"),"Rejoined board did not open daily quests");step++;}}
                case 33->{if(!(mc().screen instanceof DailyScreen))return;capture("daily-after-world-reopen.png");CHECKS.add("The actual integrated world closed and reopened. UUID, five quest IDs, paid money, public tax, reputation, mission counter and animation acknowledgements survived; no reroll or duplicate payout occurred.");complete(true,"Actual client HUD, C2S widgets, survival events, wallet, public tax, animations, controls and world reopen passed.");}
            }
        }catch(Throwable error){error.printStackTrace();complete(false,error.toString());}
    }
}
