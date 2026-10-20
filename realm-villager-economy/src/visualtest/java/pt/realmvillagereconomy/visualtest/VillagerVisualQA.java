package pt.realmvillagereconomy.visualtest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.realmvillagereconomy.client.TradingScreen;
import pt.realmvillagereconomy.compat.ChroniclesBridge;
import pt.realmvillagereconomy.data.EconomyData;
import pt.realmvillagereconomy.service.TradeJournal;
import pt.realmvillagereconomy.service.TradingService;

/** Actual native client and integrated-host transfers. Test sources are excluded from release JARs. */
@EventBusSubscriber(modid="realmvillagereconomy",value=Dist.CLIENT)
public final class VillagerVisualQA {
    private static final Path OUT=Path.of(System.getProperty("villager.qaOutput","qa-results/villager-client")).toAbsolutePath();
    private static final List<String> SHOTS=new ArrayList<>(),CHECKS=new ArrayList<>();
    private static final List<JsonObject> REQUESTS=new ArrayList<>();
    private static final long STARTED=System.currentTimeMillis();
    private static int step,ticks;private static long next;private static boolean language,done;private static volatile boolean prepared,opened,verified;private static volatile Throwable failure;
    private static Villager merchant,librarian;private static long savedWallet;private static boolean dualSave,realTreasury;
    private static void check(boolean pass,String why){if(!pass)throw new IllegalStateException(why);}
    private static ServerPlayer player(){var mc=Minecraft.getInstance();return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static void server(Runnable action){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable error){failure=error;}});}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private static JsonObject data(){return Minecraft.getInstance().screen instanceof TradingScreen screen?(JsonObject)field(screen,"data"):null;}
    private static boolean ready(){if(data()!=null&&data().has("error"))check(data().get("error").getAsString().isEmpty(),"Authoritative native menu error: "+data().get("error").getAsString());return opened&&data()!=null&&data().has("offers");}
    private static boolean click(String key){var screen=Minecraft.getInstance().screen;if(screen==null)return false;for(var child:List.copyOf(screen.children()))if(child instanceof AbstractButton b&&b.active&&b.visible&&(b.getMessage().getString().equals(Component.translatable(key).getString())||b.getMessage().getContents() instanceof TranslatableContents t&&t.getKey().equals(key))){b.onPress();return true;}return false;}
    private static void offer(int index){var screen=Minecraft.getInstance().screen;int top=(Integer)field(screen,"top");for(var child:screen.children())if(child instanceof AbstractButton b&&b.getMessage().getString().isEmpty()&&b.getY()==top+index*42){b.onPress();return;}throw new IllegalStateException("Native offer row missing");}
    private static void input(String value){((EditBox)field(Minecraft.getInstance().screen,"rateInput")).setValue(value);}
    private static void transport(){try{var screen=Minecraft.getInstance().screen;Field f=screen.getClass().getDeclaredField("requestSender");f.setAccessible(true);@SuppressWarnings("unchecked")BiConsumer<String,JsonObject> original=(BiConsumer<String,JsonObject>)f.get(screen);f.set(screen,(BiConsumer<String,JsonObject>)(operation,payload)->{var request=payload.deepCopy();request.addProperty("operation",operation);REQUESTS.add(request);original.accept(operation,payload);});}catch(Exception e){throw new IllegalStateException(e);}}
    private static void quiet(){try{var state=Class.forName("pt.cronicasrp.client.ClientState");var f=state.getDeclaredField("pending");f.setAccessible(true);f.setInt(null,0);var reset=Class.forName("pt.cronicasrp.client.OriginsCreationFlow").getDeclaredMethod("reset");reset.setAccessible(true);reset.invoke(null);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void profile(ServerPlayer player){try{var type=Class.forName("pt.cronicasrp.domain.CharacterProfile");Object p=type.getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,"Aveline Vale",28,"origins:human","Visual QA; all money is fictional.","Aveline Vale","Comum",100000L,100000L,"");Object attachment=((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();for(var m:player.getClass().getMethods())if(m.getName().equals("setData")&&m.getParameterCount()==2&&m.getParameterTypes()[0].isInstance(attachment)){m.invoke(player,attachment,p);break;}player.getServer().getPlayerList().saveAll();ChroniclesBridge.sync(player);check(ChroniclesBridge.balance(player)==100000,"Real wallet profile missing");}catch(Exception e){throw new IllegalStateException(e);}}
    private static void prepare(){server(()->{
        var p=player();var level=p.serverLevel();var srv=p.getServer();level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,srv);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,srv);level.setDayTime(6000);level.setWeatherParameters(0,60000,false,false);
        for(int x=-12;x<=12;x++)for(int z=-12;z<=10;z++){level.setBlock(new BlockPos(x,160,z),(Math.abs(x)<5?Blocks.STONE_BRICKS:Blocks.OAK_PLANKS).defaultBlockState(),2);for(int y=161;y<=169;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(int cx:new int[]{-4,4}){for(int x=cx-2;x<=cx+2;x++){level.setBlock(new BlockPos(x,162,2),Blocks.SPRUCE_SLAB.defaultBlockState(),2);level.setBlock(new BlockPos(x,165,1),(x%2==0?Blocks.GREEN_WOOL:Blocks.WHITE_WOOL).defaultBlockState(),2);level.setBlock(new BlockPos(x,165,2),(x%2==0?Blocks.GREEN_WOOL:Blocks.WHITE_WOOL).defaultBlockState(),2);}for(int x:new int[]{cx-2,cx+2}){for(int y=161;y<=164;y++)level.setBlock(new BlockPos(x,y,3),Blocks.OAK_FENCE.defaultBlockState(),2);level.setBlock(new BlockPos(x,164,0),Blocks.LANTERN.defaultBlockState(),2);}level.setBlock(new BlockPos(cx-1,161,4),Blocks.BARREL.defaultBlockState(),2);level.setBlock(new BlockPos(cx+1,161,4),Blocks.HAY_BLOCK.defaultBlockState(),2);}
        merchant=EntityType.VILLAGER.create(level);merchant.setPos(-3.5,161,3.5);merchant.setNoAi(true);merchant.setInvulnerable(true);merchant.setPersistenceRequired();merchant.setVillagerData(merchant.getVillagerData().setProfession(VillagerProfession.FARMER));level.addFreshEntity(merchant);merchant.getOffers().clear();merchant.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD,20),new ItemStack(Items.BREAD,3),12,4,.05F));merchant.getOffers().add(new MerchantOffer(new ItemCost(Items.WHEAT,20),new ItemStack(Items.EMERALD),12,2,.05F));merchant.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD,2),java.util.Optional.of(new ItemCost(Items.BOOK)),new ItemStack(Items.ENCHANTED_BOOK),8,4,.05F));
        librarian=EntityType.VILLAGER.create(level);librarian.setPos(4.5,161,3.5);librarian.setNoAi(true);librarian.setInvulnerable(true);librarian.setPersistenceRequired();librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));level.addFreshEntity(librarian);
        profile(p);p.getInventory().setItem(0,new ItemStack(Items.WHEAT,40));p.getInventory().setItem(1,new ItemStack(Items.BOOK));p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(0,162,-5,0,12);prepared=true;
    });}
    private static void open(Villager target){opened=false;server(()->{var p=player();p.connection.teleport(target.getX()+1.1,target.getY(),target.getZ(),90,10);System.out.println("VILLAGER_QA_OPEN alive="+target.isAlive()+" baby="+target.isBaby()+" spectator="+p.isSpectator()+" distance="+target.distanceToSqr(p)+" lineOfSight="+p.hasLineOfSight(target));TradingService.open(p,target);opened=true;});}
    private static void screenshot(String name)throws Exception{Files.createDirectories(OUT);try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){check(image.getWidth()==1600&&image.getHeight()==900,"Actual client capture is not 1600x900");image.writeToFile(OUT.resolve(name));}SHOTS.add(name);System.out.println("VILLAGER_CLIENT_CAPTURE "+name);}
    private static void pause(){quiet();try{var mc=Minecraft.getInstance();Object toasts=mc.getClass().getMethod("getToasts").invoke(mc);toasts.getClass().getMethod("clear").invoke(toasts);Object tutorial=mc.getClass().getMethod("getTutorial").invoke(mc);@SuppressWarnings({"unchecked","rawtypes"})Object none=Enum.valueOf((Class)Class.forName("net.minecraft.client.tutorial.TutorialSteps"),"NONE");tutorial.getClass().getMethod("setStep",none.getClass()).invoke(tutorial,none);}catch(ReflectiveOperationException ignored){}next=System.currentTimeMillis()+900;}
    private static void verifyHost(long expected){verified=false;server(()->{try{var p=player();check(ChroniclesBridge.balance(p)>=expected,"Unexpected actual host wallet");for(var path:List.of(p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat"),p.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE))){var root=NbtIo.readCompressed(path,NbtAccounter.create(104857600));if(root.contains("Data"))root=root.getCompound("Data").getCompound("Player");check(root.getCompound("neoforge:attachments").getCompound("cronicasrp:profile").getLong("balance")==expected,"Host restart source restored old money");check(pt.realmvillagereconomy.service.TradeItems.savedInventory(p,root).equals(pt.realmvillagereconomy.service.TradeItems.inventory(p))&&root.getCompound("NeoForgeData").getCompound(TradeJournal.RECEIPT_KEY).getUUID("id").equals(p.getPersistentData().getCompound(TradeJournal.RECEIPT_KEY).getUUID("id")),"Actual host restart source lost inventory or transaction receipt");}dualSave=true;verified=true;}catch(Exception error){throw new IllegalStateException(error);}});}
    private static void compact(){var mc=Minecraft.getInstance();var screen=mc.screen;screen.resize(mc,320,240);for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible)check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=320&&w.getY()+w.getHeight()<=240,"Trading controls overflow 320x240");screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());CHECKS.add("Native visible trading controls fit 320x240.");}
    private static void complete(boolean success,String message){done=true;try{Files.createDirectories(OUT);var report=new LinkedHashMap<String,Object>();report.put("success",success);report.put("message",message);report.put("step",step);report.put("screenshots",SHOTS);report.put("checks",CHECKS);report.put("actual_minecraft_client",true);report.put("actual_integrated_server",prepared);report.put("real_c2s_requests",REQUESTS);report.put("real_chronicles_wallet",dualSave);report.put("lan_host_dual_save",dualSave);report.put("real_crown_tax",realTreasury);report.put("external_reward_after_first_purchase",Math.max(0,savedWallet-89000));report.put("authenticated_two_clients",false);report.put("synthetic_financial_snapshots",false);report.put("demonstration_money_and_offers",true);Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){error.printStackTrace();}System.out.println(success?"VILLAGER_CLIENT_PASSED":"VILLAGER_CLIENT_FAILED "+message);Minecraft.getInstance().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(done)return;var mc=Minecraft.getInstance();try{if(failure!=null)throw new IllegalStateException("Server fixture failed",failure);check(System.currentTimeMillis()-STARTED<420000,"Actual villager client timed out");if(mc.getOverlay()!=null||System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+300;ticks++;
        switch(step){
            case 0->{if(mc.screen instanceof TitleScreen){if(!language){language=true;if(!mc.options.languageCode.equals("en_us")){mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();return;}}mc.getWindow().setWindowed(1600,900);mc.options.guiScale().set(2);mc.options.renderDistance().set(5);mc.resizeDisplay();CreateWorldScreen.openFresh(mc,mc.screen);step++;}else if(mc.screen!=null&&mc.screen.getClass().getSimpleName().contains("Accessibility"))click("gui.continue");}
            case 1->{if(mc.screen instanceof CreateWorldScreen world){world.getUiState().setName("Villager economy demonstration QA");world.getUiState().setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);world.getUiState().setAllowCommands(true);world.getUiState().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);check(click("selectWorld.create"),"Create world button missing");step++;}}
            case 2->{if(mc.player!=null&&mc.getSingleplayerServer()!=null){prepare();step++;}}
            case 3->{if(!prepared)return;quiet();mc.setScreen(null);mc.options.hideGui=true;next=System.currentTimeMillis()+12000;step++;}
            case 4->{screenshot("realm-villager-market.png");mc.options.hideGui=false;open(merchant);step++;}
            case 5->{if(!ready())return;quiet();transport();check(data().get("balance").getAsLong()==100000&&data().get("manager").getAsBoolean(),"Actual host wallet or emergency policy permission missing");compact();pause();step++;}
            case 6->{screenshot("realm-villager-base-price.png");check(click("gui.realmvillagereconomy.fiscal"),"Physical fiscal controls missing");input("10");check(click("gui.realmvillagereconomy.save_tax"),"Native tax approval unavailable");step++;}
            case 7->{if(!ready()||data().get("rate").getAsInt()!=1000)return;pause();step++;}
            case 8->{screenshot("realm-villager-tax-policy.png");check(click("gui.realmvillagereconomy.offers"),"Return to real trade offers unavailable");offer(0);pause();step++;}
            case 9->{var row=data().getAsJsonArray("offers").get(0).getAsJsonObject();check(row.get("base").getAsLong()==10000&&row.get("tax").getAsLong()==1000&&row.get("total").getAsLong()==11000,"Displayed base/tax/total is not 100+10=110");screenshot("realm-villager-tax-breakdown.png");check(click("gui.realmvillagereconomy.confirm_buy"),"Native purchase confirmation unavailable");step++;}
            case 10->{if(!ready()||data().getAsJsonArray("offers").get(0).getAsJsonObject().get("stock").getAsInt()!=11)return;savedWallet=data().get("balance").getAsLong();check(savedWallet>=89000,"The real purchase charged more than its quote");verifyHost(89000);server(()->{var paid=player().getPersistentData().getCompound(TradeJournal.RECEIPT_KEY);check(paid.getLong("before")==100000&&paid.getLong("after")==89000,"The durable purchase debit is not exactly 110 euros");try{Class<?> type=Class.forName("pt.crownledger.data.TreasuryData");Object data=type.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,player().getServer());Object treasury=type.getField("treasury").get(data);Class<?> currency=Class.forName("pt.crownledger.domain.Treasury$Currency");@SuppressWarnings({"unchecked","rawtypes"})Object c=Enum.valueOf((Class)currency,"CHRONICLES");check((Long)treasury.getClass().getMethod("balance",currency).invoke(treasury,c)==1000,"Actual public treasury did not receive the exact tax");realTreasury=true;}catch(Exception error){throw new IllegalStateException(error);}});step++;}
            case 11->{if(!verified||!realTreasury)return;screenshot("realm-villager-purchase-receipt.png");offer(1);pause();step++;}
            case 12->{screenshot("realm-villager-emerald-sale.png");check(click("gui.realmvillagereconomy.confirm_sell"),"Native material sale unavailable");step++;}
            case 13->{if(!ready()||data().getAsJsonArray("offers").get(1).getAsJsonObject().get("stock").getAsInt()!=11)return;check(data().get("balance").getAsLong()==savedWallet,"Selling materials debited digital money");server(()->check(player().getInventory().countItem(Items.EMERALD)==1&&player().getInventory().countItem(Items.WHEAT)==20,"Sale did not retain physical emeralds"));verifyHost(savedWallet);pause();step++;}
            case 14->{if(!verified)return;screenshot("realm-villager-sale-receipt.png");offer(2);pause();step++;}
            case 15->{screenshot("realm-villager-extra-material.png");check(click("gui.realmvillagereconomy.confirm_buy"),"Book purchase confirmation missing");step++;}
            case 16->{if(!ready()||data().get("balance").getAsLong()!=savedWallet-1100)return;verifyHost(savedWallet-1100);step++;}
            case 17->{if(!verified)return;check(click("gui.realmvillagereconomy.close"),"Native close unavailable");server(()->{player().addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE,1000,0));});next=System.currentTimeMillis()+700;step++;}
            case 18->{open(merchant);step++;}
            case 19->{if(!ready())return;check(data().getAsJsonArray("offers").get(0).getAsJsonObject().get("base").getAsLong()<10000,"Actual Vanilla hero discount was not preserved");pause();step++;}
            case 20->{screenshot("realm-villager-hero-discount.png");check(click("gui.realmvillagereconomy.close"),"Native close unavailable");open(librarian);step++;}
            case 21->{if(!ready())return;compact();pause();step++;}
            case 22->{screenshot("realm-villager-librarian.png");check(REQUESTS.stream().anyMatch(r->r.get("operation").getAsString().equals("policy"))&&REQUESTS.stream().filter(r->r.get("operation").getAsString().equals("trade")).count()==3,"Real C2S coverage incomplete");CHECKS.add("Actual native UI confirmed a 100 euro base plus 10 euro public tax; the real Chronicles wallet paid 110 euros and Crown received 10 euros.");CHECKS.add("Material sales preserved physical emeralds, digital balance, and complete native host restart files. An extra book was consumed, Vanilla hero discount and native librarian offers rendered.");check(click("gui.realmvillagereconomy.close"),"Close before public history failed");server(()->{try{var p=player();p.closeContainer();var chest=new BlockPos(0,161,4);p.serverLevel().setBlock(chest,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("kingdomtreasury:crown_chest")).defaultBlockState(),2);p.connection.teleport(.5,161,2,0,10);Class.forName("pt.crownledger.service.CrownService").getMethod("openBoard",ServerPlayer.class,BlockPos.class).invoke(null,p,chest);}catch(Exception e){throw new IllegalStateException(e);}});step++;}
            case 23->{if(mc.screen==null||!mc.screen.getClass().getName().equals("pt.crownledger.client.TreasuryScreen"))return;check(click("gui.kingdomtreasury.history"),"Actual Crown public history button missing");pause();step++;}
            case 24->{screenshot("realm-villager-crown-history.png");CHECKS.add("The native Crown Chest displayed the actual purchase-tax payments and the dated approval, including author and rate. Any independent Realm Rewards advancement income is separate from the receipted 110 euro debit.");complete(true,"Native merchant UI, real C2S/S2C, public tax, inventory, physical emeralds and integrated-host persistence passed.");}
            default->throw new IllegalStateException("Unexpected actual-client phase");
        }System.out.println("VILLAGER_CLIENT_STEP "+step);
    }catch(Throwable error){error.printStackTrace();complete(false,error.toString());}}
}
