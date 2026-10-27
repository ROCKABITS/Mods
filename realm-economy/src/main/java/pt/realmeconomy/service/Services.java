package pt.realmeconomy.service;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.*;
import net.minecraft.stats.Stats;
import net.minecraft.advancements.CriteriaTriggers;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import pt.realmeconomy.api.EconomyPermissions;
import pt.realmeconomy.compat.*;
import pt.realmeconomy.data.EconomyData;
import pt.realmeconomy.domain.*;
import pt.realmeconomy.menu.ServicesMenu;
import pt.realmeconomy.network.TradeNetwork;

/** All service requests are physical, quoted and bound to exact server-side items and policy. */
public final class Services {
 private static final Map<UUID,Session> SESSIONS=new HashMap<>();
 private static final class Session {
  final BlockPos pos;final String dimension,context;UUID nonce=UUID.randomUUID();String quote="";NativeQuote plan;String problem="";
  int[] slots={-1,-1,-1,-1};String name="";boolean rename;int choice,secondary;
  Session(ServerPlayer p,BlockPos pos,String context){this.pos=pos.immutable();dimension=p.level().dimension().location().toString();this.context=context;}
 }
 private Services(){}
 private static IllegalStateException error(String key){return new IllegalStateException("error.realmeconomy."+key);}
 public static String context(net.minecraft.world.level.block.state.BlockState state){
  if(state.is(pt.realmeconomy.block.EconomyBlocks.DESK.get()))return "DESK";
  if(state.is(BlockTags.ANVIL))return "ANVIL_REPAIR";if(state.is(Blocks.ENCHANTING_TABLE))return "ENCHANT";if(state.is(Blocks.SMITHING_TABLE))return "SMITHING";if(state.is(Blocks.BREWING_STAND))return "BREWING";if(state.is(Blocks.BEACON))return "BEACON";return "";
 }
 public static void interact(PlayerInteractEvent.RightClickBlock event){
  if(event.getLevel().isClientSide||!(event.getEntity() instanceof ServerPlayer p)||p.isSecondaryUseActive()&&!p.getMainHandItem().isEmpty())return;
  String context=context(event.getLevel().getBlockState(event.getPos()));if(context.isEmpty()||!context.equals("DESK")&&!ServiceGates.enabled(event.getLevel(),ServiceKind.valueOf(context),event.getPos()))return;
  event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
  try{open(p,event.getPos());}catch(RuntimeException|LinkageError fail){p.displayClientMessage(Component.translatable(message(fail)),false);}
 }
 public static void open(ServerPlayer p,BlockPos pos){
  String c=context(p.level().getBlockState(pos));if(c.isEmpty()||p.isSpectator()||!p.isAlive()||p.distanceToSqr(pos.getCenter())>64||!c.equals("DESK")&&!ServiceGates.enabled(p.level(),ServiceKind.valueOf(c),pos))throw error("distance");
  TradeJournal.recover(p);EconomyData.get(p.getServer()).requireAvailable();p.closeContainer();SESSIONS.put(p.getUUID(),new Session(p,pos,c));p.openMenu(new SimpleMenuProvider((id,inventory,player)->new ServicesMenu(id,inventory),Component.translatable("service.realmeconomy."+(c.equals("ANVIL_REPAIR")?"anvil":c.toLowerCase(java.util.Locale.ROOT)))));send(p,"");
 }
 public static boolean valid(ServerPlayer p){var s=SESSIONS.get(p.getUUID());return s!=null&&p.isAlive()&&!p.isSpectator()&&p.level().dimension().location().toString().equals(s.dimension)&&p.distanceToSqr(s.pos.getCenter())<=64&&context(p.level().getBlockState(s.pos)).equals(s.context)&&(s.context.equals("DESK")||ServiceGates.enabled(p.level(),ServiceKind.valueOf(s.context),s.pos));}
 public static void close(ServerPlayer p){SESSIONS.remove(p.getUUID());}
 public static void stop(MinecraftServer server){SESSIONS.clear();}
 private static Session session(ServerPlayer p){if(!valid(p)||!(p.containerMenu instanceof ServicesMenu))throw error("distance");return SESSIONS.get(p.getUUID());}
 public static boolean governor(ServerPlayer p){return TradingService.manager(p);}
 private static String fingerprint(ServerPlayer p,Session s){
  try{
   var data=EconomyData.get(p.getServer());String state=s.context+":"+s.pos+":"+p.level().getBlockState(s.pos)+":"+TradeItems.inventory(p)+":"+p.getEnchantmentSeed()+":"+data.rulesSnapshot()+":"+data.beaconSeconds()+":"+Arrays.toString(s.slots)+":"+s.name+":"+s.rename+":"+s.choice+":"+s.secondary;
   if(s.context.equals("ENCHANT"))for(var off:EnchantingTableBlock.BOOKSHELF_OFFSETS){state+=p.level().getBlockState(s.pos.offset(off)).toString()+":"+p.level().getBlockState(s.pos.offset(off.getX()/2,off.getY(),off.getZ()/2));}
   if(s.context.equals("BREWING"))state+=data.entry("fuel",p.getUUID().toString()).toString();
   if(s.context.equals("BEACON"))state+=data.entry("beacons",BeaconService.key(p.level(),s.pos)).toString()+":"+BeaconService.layers(p.level(),s.pos)+":"+BeaconService.clearBeam(p.level(),s.pos);
   return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));
  }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 private static void calculate(ServerPlayer p,Session s){
  s.plan=null;s.problem="";s.quote=fingerprint(p,s);if(s.context.equals("DESK"))return;
  try{
   if(s.context.equals("BEACON")){
    int layers=BeaconService.layers(p.level(),s.pos);if(!BeaconService.unlocked(s.choice,layers)||s.secondary<0||s.secondary>2||s.secondary>0&&layers<4||!BeaconService.clearBeam(p.level(),s.pos))throw error("pyramid");
    var current=EconomyData.get(p.getServer()).entry("beacons",BeaconService.key(p.level(),s.pos));if(current.getLong("until")>System.currentTimeMillis()&&(current.getInt("primary")!=s.choice||current.getInt("secondary")!=s.secondary))throw error("beacon_active");
    var entry=new CompoundTag();entry.putUUID("owner",p.getUUID());entry.putInt("primary",s.choice);entry.putInt("secondary",s.secondary);entry.putLong("until",0);var changes=new ListTag();NativeQuote.mutation(changes,"beacons",BeaconService.key(p.level(),s.pos),entry);
    s.plan=new NativeQuote(ServiceKind.BEACON,1,new ItemStack(Items.BEACON),TradeItems.inventory(p),changes,p.getEnchantmentSeed(),Integer.toString(EconomyData.get(p.getServer()).beaconSeconds()));
   }else s.plan=NativeQuote.calculate(p,s.pos,ServiceKind.valueOf(s.context),s.slots,s.name,s.rename,s.choice);
  }catch(RuntimeException|LinkageError invalid){s.problem=message(invalid);}
 }
 private static void exact(JsonObject request,String...fields){if(!request.keySet().equals(Set.of(fields)))throw error("invalid_request");}
 public static void action(ServerPlayer p,String operation,String json){
  try{
   var s=session(p);if(json.length()>2048)throw error("invalid_request");var request=JsonParser.parseString(json).getAsJsonObject();
   if(operation.equals("svcRefresh")){exact(request);calculate(p,s);send(p,"");return;}
   if(!request.has("nonce")||!UUID.fromString(request.get("nonce").getAsString()).equals(s.nonce)||!request.has("quote")||!request.get("quote").getAsString().equals(s.quote))throw error("stale");
   s.nonce=UUID.randomUUID();EconomyData data=EconomyData.get(p.getServer());data.requireAvailable();
   switch(operation){
    case "svcPreview"->{exact(request,"nonce","quote","slots","name","rename","choice","secondary");var a=request.getAsJsonArray("slots");if(a.size()!=4)throw error("invalid_request");for(int i=0;i<4;i++){int slot=a.get(i).getAsBigDecimal().intValueExact();if(slot< -1||slot>=36)throw error("invalid_request");s.slots[i]=slot;}s.name=request.get("name").getAsString();if(s.name.length()>50||s.name.codePoints().anyMatch(Character::isISOControl))throw error("invalid_request");s.rename=request.get("rename").getAsBoolean();s.choice=request.get("choice").getAsBigDecimal().intValueExact();s.secondary=request.get("secondary").getAsBigDecimal().intValueExact();if(s.choice<0||s.choice>4||s.secondary<0||s.secondary>2)throw error("invalid_request");}
    case "svcConfirm"->{exact(request,"nonce","quote");if(s.plan==null||!s.quote.equals(fingerprint(p,s)))throw error("stale");var quote=s.plan;var price=data.rule(quote.kind()).quote(quote.units(),CrownBridge.installed());var changes=quote.mutations().copy();long now=System.currentTimeMillis();
     for(var raw:changes){var change=(CompoundTag)raw;var value=change.getCompound("value");if(change.getString("map").equals("jobs"))value.putLong("readyAt",Math.addExact(now,20000));if(change.getString("map").equals("beacons")){long previous=data.entry("beacons",change.getString("key")).getLong("until");long until=Math.addExact(Math.max(now,previous),data.beaconSeconds()*1000L);if(until-now>86400_000L)throw error("beacon_limit");value.putLong("until",until);}}
     var oldInputs=new ArrayList<ItemStack>();for(int i:s.slots)oldInputs.add(i>=0?p.getInventory().getItem(i).copy():ItemStack.EMPTY);
     commit(p,quote.kind(),price,quote.inventory(),changes,quote.kind()==ServiceKind.ENCHANT?quote.seed():null,true);
     try{effects(p,s,quote,oldInputs);NotificationsBridge.send(p,quote.kind()==ServiceKind.BREWING?"notification.realmeconomy.brewing":"notification.realmeconomy.paid",price.total());}catch(RuntimeException|LinkageError secondary){com.mojang.logging.LogUtils.getLogger().warn("Economic receipt committed; secondary feedback failed",secondary);}TradingService.flush(p.getServer());
    }
    case "svcClaim"->{exact(request,"nonce","quote","job");UUID id=UUID.fromString(request.get("job").getAsString());var job=data.entry("jobs",id.toString());if(!job.hasUUID("owner")||!job.getUUID("owner").equals(p.getUUID())||job.getLong("readyAt")>System.currentTimeMillis())throw error("not_ready");var plan=new InventoryPlan(p);for(var raw:job.getList("items",Tag.TAG_COMPOUND))plan.add(ItemStack.parse(p.registryAccess(),(CompoundTag)raw).orElseThrow());var mutations=new ListTag();var remove=new CompoundTag();remove.putString("map","jobs");remove.putString("key",id.toString());remove.putBoolean("remove",true);mutations.add(remove);commit(p,ServiceKind.BREWING,new TradePrice(0,0,0,0),plan.encode(),mutations,null,false);try{for(var raw:job.getList("items",Tag.TAG_COMPOUND)){var item=ItemStack.parse(p.registryAccess(),(CompoundTag)raw).orElseThrow();var contents=item.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,net.minecraft.world.item.alchemy.PotionContents.EMPTY);contents.potion().ifPresent(potion->CriteriaTriggers.BREWED_POTION.trigger(p,potion));}NotificationsBridge.send(p,"notification.realmeconomy.collected",0);}catch(RuntimeException|LinkageError secondary){com.mojang.logging.LogUtils.getLogger().warn("Brewing receipt committed; secondary achievements failed",secondary);}}
    case "svcPolicy"->{exact(request,"nonce","quote","service","value","tax");if(!s.context.equals("DESK")||!governor(p))throw error("permission");var kind=ServiceKind.valueOf(request.get("service").getAsString());var rule=data.rule(kind);data.changeRule(p.getUUID(),publicName(p),kind,rule.price(request.get("value").getAsBigDecimal().longValueExact(),request.get("tax").getAsBigDecimal().intValueExact()));TradingService.flush(p.getServer());}
    case "svcAdmin"->{exact(request,"nonce","quote","service","minimum","maximum","cap","enabled");if(!s.context.equals("DESK")||!EconomyPermissions.canAdminister(p))throw error("permission");var kind=ServiceKind.valueOf(request.get("service").getAsString());long min=request.get("minimum").getAsBigDecimal().longValueExact(),max=request.get("maximum").getAsBigDecimal().longValueExact();if(kind==ServiceKind.VILLAGER&&min<1)throw error("tax_limit");var next=data.rule(kind).limits(min,max,request.get("cap").getAsBigDecimal().intValueExact(),request.get("enabled").getAsBoolean());var changes=new java.util.EnumMap<ServiceKind,EconomyRule>(ServiceKind.class);changes.put(kind,next);if(kind.name().startsWith("ANVIL")){for(var anvil:List.of(ServiceKind.ANVIL_REPAIR,ServiceKind.ANVIL_COMBINE,ServiceKind.ANVIL_RENAME)){var old=data.rule(anvil);if(anvil!=kind)changes.put(anvil,new EconomyRule(old.value(),old.minimum(),old.maximum(),old.tax(),old.taxCap(),next.enabled()));}}data.changeRules(p.getUUID(),publicName(p),changes);TradingService.flush(p.getServer());}
    case "svcPeriod"->{exact(request,"nonce","quote","seconds");if(!s.context.equals("DESK")||!EconomyPermissions.canAdminister(p))throw error("permission");data.beaconSeconds(p.getUUID(),publicName(p),request.get("seconds").getAsBigDecimal().intValueExact());}
    case "svcStorage"->{exact(request,"nonce","quote");if(!s.context.equals("BREWING"))throw error("invalid_request");var stand=p.level().getBlockEntity(s.pos);if(!(stand instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity provider))throw error("distance");p.openMenu(provider);return;}
    default->throw error("invalid_request");
   }
   calculate(p,s);send(p,"");
  }catch(RuntimeException|LinkageError failed){if(valid(p)&&p.containerMenu instanceof ServicesMenu){var s=SESSIONS.get(p.getUUID());calculate(p,s);send(p,(operation.equals("svcPolicy")||operation.equals("svcAdmin")||operation.equals("svcPeriod"))&&(failed instanceof IllegalArgumentException||failed instanceof ArithmeticException)?"error.realmeconomy.bounds":message(failed));}else p.closeContainer();}
 }
 private static String publicName(ServerPlayer p){return ChroniclesBridge.available(p)?ChroniclesBridge.publicName(p):p.getGameProfile().getName();}
 private static void commit(ServerPlayer p,ServiceKind kind,TradePrice price,ListTag inventory,ListTag mutations,Integer seed,boolean needsWallet){
  if(needsWallet&&!ChroniclesBridge.available(p))throw error("chronicles_unavailable");boolean wallet=ChroniclesBridge.available(p);long before=wallet?ChroniclesBridge.balance(p):-1,after=wallet?Math.subtractExact(before,price.total()):-1;if(wallet&&after<0)throw error("insufficient_funds");UUID id=UUID.randomUUID();if(price.tax()>0)CrownBridge.preflightService(p.getServer(),id,p.getUUID(),price.tax(),kind.name());
  var intent=new CompoundTag();intent.putUUID("id",id);intent.putUUID("player",p.getUUID());intent.putLong("before",before);intent.putLong("after",after);intent.putLong("base",price.base());intent.putLong("tax",price.tax());intent.putInt("rate",price.rate());intent.putLong("at",System.currentTimeMillis());intent.putString("service",kind.name());intent.put("inventoryBefore",TradeItems.inventory(p));intent.put("inventoryAfter",inventory);intent.put("mutations",mutations);if(seed!=null){intent.putInt("seedBefore",p.getEnchantmentSeed());intent.putInt("seedAfter",seed);}TradeJournal.commit(p,intent);
 }
 private static void effects(ServerPlayer p,Session s,NativeQuote q,List<ItemStack> old){
  if(q.kind().name().startsWith("ANVIL")){var state=p.level().getBlockState(s.pos);float chance=net.neoforged.neoforge.common.CommonHooks.onAnvilRepair(p,q.result(),old.get(0),old.get(1));if(!p.getAbilities().instabuild&&p.getRandom().nextFloat()<chance){var next=AnvilBlock.damage(state);if(next==null){p.level().removeBlock(s.pos,false);p.level().levelEvent(1029,s.pos,0);}else p.level().setBlock(s.pos,next,2);}p.level().levelEvent(1030,s.pos,0);}
  if(q.kind()==ServiceKind.ENCHANT){var enchantments=net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(q.result());var applied=new java.util.ArrayList<net.minecraft.world.item.enchantment.EnchantmentInstance>();for(var enchantment:enchantments.entrySet())applied.add(new net.minecraft.world.item.enchantment.EnchantmentInstance(enchantment.getKey(),enchantment.getIntValue()));net.neoforged.neoforge.common.CommonHooks.onPlayerEnchantItem(p,q.result(),applied);p.awardStat(Stats.ENCHANT_ITEM);CriteriaTriggers.ENCHANTED_ITEM.trigger(p,q.result(),s.choice+1);p.level().playSound(null,s.pos,net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);}
  if(q.kind()==ServiceKind.SMITHING){q.result().onCraftedBy(p.level(),p,q.result().getCount());p.level().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(q.detail())).ifPresent(recipe->{p.awardRecipes(java.util.List.of(recipe));p.triggerRecipeCrafted(recipe,old.subList(0,3));});p.level().levelEvent(1044,s.pos,0);}
 }
 private static void item(JsonObject out,String key,ItemStack stack,ServerPlayer p){out.add(key,ItemStack.OPTIONAL_CODEC.encodeStart(p.registryAccess().createSerializationContext(JsonOps.INSTANCE),stack).getOrThrow());}
 public static JsonObject snapshot(ServerPlayer p){
  var s=session(p);if(s.quote.isEmpty())calculate(p,s);var data=EconomyData.get(p.getServer());var out=new JsonObject();out.addProperty("screen","services");out.addProperty("container",p.containerMenu.containerId);out.addProperty("context",s.context);out.addProperty("nonce",s.nonce.toString());out.addProperty("quote",s.quote);out.addProperty("balance",ChroniclesBridge.available(p)?ChroniclesBridge.balance(p):-1);out.addProperty("manager",governor(p));out.addProperty("administrator",EconomyPermissions.canAdminister(p));out.addProperty("adminProvider",EconomyPermissions.providerReady());out.addProperty("treasury",CrownBridge.installed());out.addProperty("beaconSeconds",data.beaconSeconds());out.addProperty("fuel",data.entry("fuel",p.getUUID().toString()).getInt("units"));out.addProperty("seconds",System.currentTimeMillis());
  var inventory=new JsonArray();for(int i=0;i<36;i++){var row=new JsonObject();row.addProperty("slot",i);item(row,"item",p.getInventory().getItem(i),p);inventory.add(row);}out.add("inventory",inventory);var selected=new JsonArray();for(int i:s.slots)selected.add(i);out.add("slots",selected);out.addProperty("choice",s.choice);out.addProperty("secondary",s.secondary);out.addProperty("name",s.name);out.addProperty("rename",s.rename);
  var rules=new JsonArray();for(var kind:ServiceKind.values()){var r=data.rule(kind);var row=new JsonObject();row.addProperty("service",kind.name());row.addProperty("value",r.value());row.addProperty("minimum",r.minimum());row.addProperty("maximum",r.maximum());row.addProperty("tax",CrownBridge.installed()?r.tax():0);row.addProperty("approvedTax",r.tax());row.addProperty("cap",r.taxCap());row.addProperty("enabled",r.enabled());rules.add(row);}out.add("rules",rules);
  out.addProperty("canConfirm",false);out.addProperty("problem",s.problem);
  if(s.plan!=null){var price=data.rule(s.plan.kind()).quote(s.plan.units(),CrownBridge.installed());out.addProperty("service",s.plan.kind().name());out.addProperty("units",s.plan.units());out.addProperty("rate",price.rate());out.addProperty("base",price.base());out.addProperty("tax",price.tax());out.addProperty("total",price.total());out.addProperty("detail",s.plan.detail());item(out,"result",s.context.equals("ENCHANT")?p.getInventory().getItem(s.slots[0]):s.plan.result(),p);out.addProperty("canConfirm",ChroniclesBridge.available(p)&&ChroniclesBridge.balance(p)>=price.total());}
  var jobs=new JsonArray();var storedJobs=data.collection("jobs");for(String key:storedJobs.getAllKeys()){var j=storedJobs.getCompound(key);if(!j.hasUUID("owner")||!j.getUUID("owner").equals(p.getUUID()))continue;var row=new JsonObject();row.addProperty("id",key);row.addProperty("remaining",Math.max(0,(j.getLong("readyAt")-System.currentTimeMillis()+999)/1000));row.addProperty("ready",j.getLong("readyAt")<=System.currentTimeMillis());row.addProperty("count",j.getList("items",Tag.TAG_COMPOUND).size());item(row,"item",ItemStack.parse(p.registryAccess(),j.getList("items",Tag.TAG_COMPOUND).getCompound(0)).orElseThrow(),p);jobs.add(row);}out.add("jobs",jobs);
  var audit=new JsonArray();if(governor(p))for(var raw:data.audit()){var a=(CompoundTag)raw;var row=new JsonObject();for(String key:List.of("name","service","old","next"))row.addProperty(key,a.getString(key));row.addProperty("at",a.getLong("at"));audit.add(row);}out.add("audit",audit);
  if(s.context.equals("BEACON")){var beacon=data.entry("beacons",BeaconService.key(p.level(),s.pos));out.addProperty("layers",BeaconService.layers(p.level(),s.pos));out.addProperty("remaining",Math.max(0,(beacon.getLong("until")-System.currentTimeMillis()+999)/1000));}
  return out;
 }
 private static void send(ServerPlayer p,String error){var json=snapshot(p);json.addProperty("error",error);TradeNetwork.send(p,json);}
 private static String message(Throwable failed){String value=failed.getMessage();return value!=null&&value.startsWith("error.realmeconomy.")?value:"error.realmeconomy.recipe";}
 public static void tick(MinecraftServer server){
  for(var p:server.getPlayerList().getPlayers())if(SESSIONS.containsKey(p.getUUID())&&!valid(p))p.closeContainer();
  if(server.getTickCount()%20!=0)return;
  try{var data=EconomyData.get(server);data.requireAvailable();int count=0;for(String map:List.of("jobs","beacons")){var entries=data.collection(map);for(String key:entries.getAllKeys()){if(count>=10)return;var item=entries.getCompound(key);long ready=map.equals("jobs")?item.getLong("readyAt"):item.getLong("until");if(item.getBoolean("notified")||ready>System.currentTimeMillis()||!item.hasUUID("owner"))continue;var player=server.getPlayerList().getPlayer(item.getUUID("owner"));if(player==null)continue;data.markNotice(map,key);NotificationsBridge.send(player,map.equals("jobs")?"notification.realmeconomy.ready":"notification.realmeconomy.expired",0);count++;}}}catch(RuntimeException|LinkageError frozen){}
 }
}
