package pt.realmeconomy.visualtest;

import com.google.gson.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pt.realmeconomy.block.EconomyBlocks;
import pt.realmeconomy.client.ServicesScreen;
import pt.realmeconomy.compat.ChroniclesBridge;
import pt.realmeconomy.data.EconomyData;
import pt.realmeconomy.service.*;

/** Additional real client checks; every economic action uses the native production controls. */
@EventBusSubscriber(modid="realmeconomy",value=Dist.CLIENT)
public final class EconomyVisualQA {
 private static final BlockPos DESK=new BlockPos(-8,161,8),ANVIL=new BlockPos(-8,161,-5),ENCHANT=new BlockPos(-4,161,-5),SMITH=new BlockPos(0,161,-5),BREW=new BlockPos(4,161,-5),BEACON=new BlockPos(8,161,-5);
 private static final Map<String,Object> PROOF=new LinkedHashMap<>();private static final Set<Object> TRACKED=Collections.newSetFromMap(new WeakHashMap<>());private static boolean active,done;private static int step,cursor,oldSeed;private static long next,quoted,activatedAt,startedAt;private static volatile boolean ready,verified;private static volatile Throwable failure;
 public static Map<String,Object> proof(){return PROOF;}
 public static void begin(){active=true;next=System.currentTimeMillis()+300;System.out.println("ECONOMY_CLIENT_STARTED");}
 private static ServerPlayer player(){var mc=Minecraft.getInstance();return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
 private static void check(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
 private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
 private static void server(Runnable task){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{task.run();}catch(Throwable e){failure=e;}});}
 private static JsonObject data(){return Minecraft.getInstance().screen instanceof ServicesScreen screen?(JsonObject)field(screen,"data"):null;}
 private static boolean available(){return ready&&data()!=null&&data().has("rules")&&!(Boolean)field(Minecraft.getInstance().screen,"waiting");}
 private static boolean click(String key){var screen=Minecraft.getInstance().screen;if(screen==null)return false;for(var child:List.copyOf(screen.children()))if(child instanceof AbstractButton b&&b.visible&&b.active&&(b.getMessage().getString().equals(key)||b.getMessage().getString().equals(Component.translatable(key).getString())||b.getMessage().getContents() instanceof TranslatableContents t&&t.getKey().equals(key))){b.onPress();return true;}return false;}
 private static void input(String name,String value){((EditBox)field(Minecraft.getInstance().screen,name)).setValue(value);}
 private static void transport(){try{var screen=Minecraft.getInstance().screen;if(!TRACKED.add(screen))return;Field f=screen.getClass().getDeclaredField("requestSender");f.setAccessible(true);@SuppressWarnings("unchecked")var original=(BiConsumer<String,JsonObject>)f.get(screen);f.set(screen,(BiConsumer<String,JsonObject>)(operation,payload)->{var copy=payload.deepCopy();copy.addProperty("operation",operation);VillagerVisualQA.recordRequest(copy);original.accept(operation,payload);});}catch(Exception e){throw new IllegalStateException(e);}}
 private static boolean select(int...slots){if(!available())return false;if(cursor>=slots.length){cursor=0;return true;}int index=cursor++;if(slots[index]<0)return false;check(click(Integer.toString(index+1)),"Real service input selector missing");var screen=Minecraft.getInstance().screen;int x=(Integer)field(screen,"x")+14+(slots[index]%9)*26,y=(Integer)field(screen,"top")+72+(slots[index]/9)*26;for(var child:screen.children())if(child instanceof AbstractButton b&&b.getMessage().getString().isEmpty()&&b.getX()==x&&b.getY()==y){b.onPress();return false;}throw new IllegalStateException("Native inventory selection missing");}
 private static void compact(){var mc=Minecraft.getInstance();var screen=mc.screen;screen.resize(mc,320,240);for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible)check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=320&&w.getY()+w.getHeight()<=240,"Service controls overflow 320x240: "+w.getMessage().getString());screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());VillagerVisualQA.recordCheck("Visible service controls fit 320x240.");}
 private static void pause(){VillagerVisualQA.quiet();Minecraft.getInstance().getToasts().clear();next=System.currentTimeMillis()+900;}
 private static void capture(String name)throws Exception{VillagerVisualQA.screenshot(name);}
 private static void open(BlockPos pos,int fixture){ready=false;verified=false;cursor=0;server(()->{var p=player();p.closeContainer();if(fixture>0){p.getInventory().clearContent();p.experienceLevel=0;p.experienceProgress=0;p.totalExperience=0;switch(fixture){case 1->{p.getInventory().setItem(0,PotionContents.createItemStack(Items.POTION,Potions.WATER));p.getInventory().setItem(1,PotionContents.createItemStack(Items.POTION,Potions.WATER));p.getInventory().setItem(2,new ItemStack(Items.NETHER_WART,2));p.getInventory().setItem(3,new ItemStack(Items.BLAZE_POWDER));}case 2->p.getInventory().setItem(0,new ItemStack(Items.IRON_SWORD));case 3->{p.getInventory().setItem(0,new ItemStack(Items.BOOK));p.getInventory().setItem(1,new ItemStack(Items.LAPIS_LAZULI,3));oldSeed=p.getEnchantmentSeed();}case 4->{p.getInventory().setItem(0,new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));var armor=new ItemStack(Items.DIAMOND_CHESTPLATE);armor.set(DataComponents.CUSTOM_NAME,Component.literal("Royal Guard"));p.getInventory().setItem(1,armor);p.getInventory().setItem(2,new ItemStack(Items.NETHERITE_INGOT));}default->{}}}p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()-1.4,0,15);Services.open(p,pos);ready=true;});}
 private static JsonObject rule(String kind){for(var v:data().getAsJsonArray("rules")){var r=v.getAsJsonObject();if(r.get("service").getAsString().equals(kind))return r;}throw new IllegalStateException("Policy missing");}
 private static void verify(long cost,Runnable extra){verified=false;server(()->{try{var p=player();var receipt=p.getPersistentData().getCompound(TradeJournal.RECEIPT_KEY);check(receipt.getLong("before")-receipt.getLong("after")==cost,"Actual service debit differs from the confirmed quote");check(EconomyData.get(p.getServer()).journal()==null,"The actual service still has a pending receipt");for(var path:List.of(p.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat"),p.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE))){var root=NbtIo.readCompressed(path,NbtAccounter.create(104857600));if(root.contains("Data"))root=root.getCompound("Data").getCompound("Player");check(root.getCompound("neoforge:attachments").getCompound("cronicasrp:profile").getLong("balance")==receipt.getLong("after"),"A service lost its actual host wallet restart source");check(TradeItems.savedInventory(p,root).equals(TradeItems.inventory(p)),"Service inventory did not reach both host restart files");check(root.getInt("XpSeed")==p.getEnchantmentSeed(),"Service enchantment seed was not persisted");}check(p.experienceLevel==0&&p.totalExperience==0,"A euro service consumed or invented XP");extra.run();verified=true;}catch(Exception e){throw new IllegalStateException(e);}});}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!active||done)return;var mc=Minecraft.getInstance();try{if(failure!=null)throw new IllegalStateException("Economic client server assertion failed",failure);if(mc.getOverlay()!=null||System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+300;
  switch(step){
   case 0->{ready=false;server(()->{var p=player();p.closeContainer();p.removeAllEffects();for(int i=0;i<4;i++)p.serverLevel().setBlock(new BlockPos(-8+i*5,161,8),EconomyBlocks.DESK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}[i]),3);p.serverLevel().setBlock(ANVIL,Blocks.ANVIL.defaultBlockState(),3);p.serverLevel().setBlock(ENCHANT,Blocks.ENCHANTING_TABLE.defaultBlockState(),3);p.serverLevel().setBlock(SMITH,Blocks.SMITHING_TABLE.defaultBlockState(),3);p.serverLevel().setBlock(BREW,Blocks.BREWING_STAND.defaultBlockState(),3);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)p.serverLevel().setBlock(BEACON.offset(x,-1,z),Blocks.IRON_BLOCK.defaultBlockState(),3);p.serverLevel().setBlock(BEACON,Blocks.BEACON.defaultBlockState(),3);p.connection.teleport(0,163.5,14,180,18);ready=true;});mc.options.hideGui=true;next=System.currentTimeMillis()+4000;step++;}
   case 1->{if(!ready)return;capture("realm-economy-desks.png");server(()->player().connection.teleport(-6.1,161.35,6.7,37.87F,27.17F));next=System.currentTimeMillis()+1300;step=41;}
   case 41->{capture("realm-economy-desk-close.png");mc.options.hideGui=false;open(DESK,0);step=2;}
   case 2->{if(!available())return;transport();compact();pause();step++;}
   case 3->{capture("realm-economy-prices.png");check(click("service.realmeconomy.anvil_rename"),"Governor service selector missing");input("priceInput","2");input("taxInput","10");check(click("gui.realmeconomy.save_policy"),"Economic approval unavailable");step++;}
   case 4->{if(!available()||rule("ANVIL_RENAME").get("value").getAsLong()!=200)return;check(rule("ANVIL_RENAME").get("approvedTax").getAsInt()==1000,"Governor service tax did not save");pause();step++;}
   case 5->{capture("realm-economy-governor.png");check(click("gui.realmeconomy.admin"),"Native administration unavailable");input("minInput","1");input("maxInput","5");input("capInput","25");check(click("gui.realmeconomy.save_limits"),"Administrator bounds unavailable");step++;}
   case 6->{if(!available()||rule("ANVIL_RENAME").get("maximum").getAsLong()!=500)return;compact();pause();step++;}
   case 7->{capture("realm-economy-admin-bounds.png");check(click("gui.realmeconomy.admin_options"),"System switch page missing");input("periodInput","10");check(click("gui.realmeconomy.save_period"),"Period approval unavailable");step++;}
   case 8->{if(!available()||data().get("beaconSeconds").getAsInt()!=10)return;compact();pause();step++;}
   case 9->{capture("realm-economy-admin-options.png");open(BREW,1);step++;}
   case 10->{if(!available())return;transport();compact();if(!select(0,1,-1,2))return;check(data().get("canConfirm").getAsBoolean(),"Actual brewing quote unavailable");pause();step++;}
   case 11->{capture("realm-economy-brewing.png");quoted=data().get("total").getAsLong();check(click("gui.realmeconomy.start_batch"),"Actual brewing payment control missing");startedAt=System.currentTimeMillis();step++;}
   case 12->{if(!available()||data().getAsJsonArray("jobs").size()!=1)return;verify(quoted,()->{var d=EconomyData.get(player().getServer());check(d.collection("fuel").getCompound(player().getUUID().toString()).getInt("units")==19,"Real blaze fuel did not retain 19 charges");PROOF.put("actual_brewing_escrow",true);});check(click("gui.realmeconomy.batches"),"Personal batch screen missing");pause();step++;}
   case 13->{if(!verified)return;capture("realm-economy-batch-progress.png");open(ANVIL,2);step++;}
   case 14->{if(!available())return;transport();compact();if(!select(0))return;input("nameInput","Royal Promise");check(click("gui.realmeconomy.rename"),"Anvil name preview control missing");step++;}
   case 15->{if(!available()||!data().get("canConfirm").getAsBoolean())return;check(data().get("base").getAsLong()==200&&data().get("tax").getAsLong()==20&&data().get("total").getAsLong()==220,"Anvil base, tax and total are wrong");pause();step++;}
   case 16->{capture("realm-economy-anvil.png");quoted=220;check(click("gui.realmeconomy.confirm_service"),"Actual anvil confirmation missing");step++;}
   case 17->{if(!available())return;verify(quoted,()->{check(player().getInventory().getItem(0).getHoverName().getString().equals("Royal Promise"),"The actual anvil name was lost");PROOF.put("actual_anvil_xp_substitution",true);});step++;}
   case 18->{if(!verified)return;open(ENCHANT,3);step++;}
   case 19->{if(!available())return;transport();compact();if(!select(0))return;check(data().get("canConfirm").getAsBoolean(),"An enchantment at zero XP was unavailable");pause();step++;}
   case 20->{capture("realm-economy-enchanting.png");quoted=data().get("total").getAsLong();check(click("gui.realmeconomy.confirm_service"),"Actual enchantment confirmation missing");step++;}
   case 21->{if(!available())return;verify(quoted,()->{check(player().getInventory().countItem(Items.ENCHANTED_BOOK)==1&&player().getInventory().countItem(Items.LAPIS_LAZULI)==2&&player().getEnchantmentSeed()!=oldSeed,"Actual lapis, enchantment or seed failed");PROOF.put("actual_enchantment_xp_substitution",true);});step++;}
   case 22->{if(!verified)return;open(SMITH,4);step++;}
   case 23->{if(!available())return;transport();compact();if(!select(0,1,2))return;check(data().get("canConfirm").getAsBoolean(),"Actual smithing quote missing");pause();step++;}
   case 24->{capture("realm-economy-smithing.png");quoted=data().get("total").getAsLong();check(click("gui.realmeconomy.confirm_service"),"Actual smithing confirmation missing");step++;}
   case 25->{if(!available())return;verify(quoted,()->{check(player().getInventory().countItem(Items.NETHERITE_CHESTPLATE)==1&&player().getInventory().countItem(Items.NETHERITE_INGOT)==0&&player().getInventory().countItem(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)==0,"Actual smithing did not consume the template and metal");PROOF.put("actual_smithing_recipe",true);});step++;}
   case 26->{if(!verified)return;open(BEACON,0);step++;}
   case 27->{if(!available())return;transport();compact();check(data().get("canConfirm").getAsBoolean(),"Actual funded beacon quote missing");pause();step++;}
   case 28->{capture("realm-economy-beacon.png");quoted=data().get("total").getAsLong();check(click("gui.realmeconomy.confirm_service"),"Actual beacon payment missing");activatedAt=System.currentTimeMillis();step++;}
   case 29->{if(!available())return;verify(quoted,()->{check(EconomyData.get(player().getServer()).collection("beacons").size()==1,"Beacon lease was not committed");});next=System.currentTimeMillis()+4300;step++;}
   case 30->{if(!verified)return;verified=false;server(()->{check(player().hasEffect(MobEffects.MOVEMENT_SPEED),"Actual paid beacon did not apply its effect");PROOF.put("actual_paid_beacon",true);verified=true;player().closeContainer();player().connection.teleport(8.5,162,-10,0,12);});mc.options.hideGui=true;next=System.currentTimeMillis()+1200;step++;}
   case 31->{if(!verified)return;capture("realm-economy-beacon-world.png");mc.options.hideGui=false;next=Math.max(System.currentTimeMillis()+300,activatedAt+11500);step++;}
   case 32->{verified=false;server(()->{check(!player().hasEffect(MobEffects.MOVEMENT_SPEED),"Expired actual beacon kept its paid effect");PROOF.put("actual_beacon_expiry",true);verified=true;});step++;}
   case 33->{if(!verified)return;open(DESK,0);step++;}
   case 34->{if(!available())return;transport();check(click("gui.realmeconomy.batches"),"Collection at the actual desk unavailable");if(data().getAsJsonArray("jobs").size()!=1||!data().getAsJsonArray("jobs").get(0).getAsJsonObject().get("ready").getAsBoolean())return;pause();step++;}
   case 35->{capture("realm-economy-batch-ready.png");check(System.currentTimeMillis()>=startedAt+20000,"The real 20-second brewing interval did not elapse");check(click("gui.realmeconomy.collect"),"Actual potion claim unavailable");step++;}
   case 36->{if(!available()||!data().getAsJsonArray("jobs").isEmpty())return;verify(0,()->{check(player().getInventory().countItem(Items.POTION)==2,"Actual brewing claim did not deliver two bottles");for(var item:player().getInventory().items)if(item.is(Items.POTION))check(item.get(DataComponents.POTION_CONTENTS).is(Potions.AWKWARD),"Actual native brewing recipe failed");PROOF.put("actual_brewing_timing_and_claim",true);});step++;}
   case 37->{if(!verified)return;check(click("gui.realmeconomy.history"),"Economic history control missing");compact();pause();step++;}
   case 38->{capture("realm-economy-audit.png");server(()->{try{var p=player();p.closeContainer();var chest=new BlockPos(0,161,4);p.serverLevel().setBlock(chest,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("kingdomtreasury:crown_chest")).defaultBlockState(),2);p.connection.teleport(.5,161,2,0,10);Class.forName("pt.crownledger.service.CrownService").getMethod("openBoard",ServerPlayer.class,BlockPos.class).invoke(null,p,chest);}catch(Exception e){throw new IllegalStateException(e);}});step++;}
   case 39->{if(mc.screen==null||!mc.screen.getClass().getName().equals("pt.crownledger.client.TreasuryScreen"))return;check(click("gui.kingdomtreasury.history"),"Public economic tax history missing");pause();step++;}
   case 40->{capture("realm-economy-crown-history.png");PROOF.put("actual_economic_policy_and_bounds",true);PROOF.put("all_services_host_dual_save",true);PROOF.put("actual_realm_admin",false);PROOF.put("realm_admin_provider_contract_tested_natively",true);VillagerVisualQA.recordCheck("Native Economy Desk, rules, bounds, service confirmations, lapis, smithing inputs, 20-second brewing claims and real timed beacon expiry passed; every service receipt reached both host restart files without charging XP.");done=true;System.out.println("ECONOMY_CLIENT_PASSED");VillagerVisualQA.complete(true,"Native Villager trading and all five economic services passed with real wallets, treasury, materials, timers and host persistence.");}
   default->throw new IllegalStateException("Unknown economy client phase");
  }
  System.out.println("ECONOMY_CLIENT_STEP "+step);
 }catch(Throwable e){done=true;e.printStackTrace();VillagerVisualQA.complete(false,e.toString());}}
}
