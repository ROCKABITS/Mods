package pt.realmeconomy.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import pt.realmeconomy.compat.ChroniclesBridge;
import pt.realmeconomy.compat.CrownBridge;
import pt.realmeconomy.compat.RealmBridge;
import pt.realmeconomy.data.EconomyConfig;
import pt.realmeconomy.data.EconomyData;
import pt.realmeconomy.domain.TradePrice;
import pt.realmeconomy.menu.EconomyMenu;
import pt.realmeconomy.network.TradeNetwork;

/** Physical, quoted trading. Prices, stock, permissions, item delivery and all money are server-owned. */
public final class TradingService {
    private static final String MERCHANT_RECEIPT="realm_villager_revision";
    private record Session(AbstractVillager merchant,UUID nonce,String quote,long expires){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<MinecraftServer,Integer> TICKS=new java.util.WeakHashMap<>();
    private TradingService(){}
    private static IllegalStateException error(String key){return new IllegalStateException("error.realmeconomy."+key);}
    public static void interact(PlayerInteractEvent.EntityInteract event){
        if(!pt.realmeconomy.service.ServiceGates.enabled(event.getLevel(),pt.realmeconomy.domain.ServiceKind.VILLAGER)||event.getLevel().isClientSide||!(event.getEntity() instanceof ServerPlayer player)||!(event.getTarget() instanceof AbstractVillager merchant)||player.isSecondaryUseActive()&&merchant instanceof Villager||player.getItemInHand(event.getHand()).is(Items.VILLAGER_SPAWN_EGG)||merchant.isBaby()||!merchant.isAlive()||merchant instanceof Villager v&&v.isSleeping())return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        try{open(player,merchant);}catch(RuntimeException|LinkageError failure){close(player);player.displayClientMessage(Component.translatable(message(failure)),false);}
    }
    public static void open(ServerPlayer player,AbstractVillager merchant){
        if(!ServiceGates.enabled(player.level(),pt.realmeconomy.domain.ServiceKind.VILLAGER)||player.isSpectator()||!merchant.isAlive()||merchant.isBaby()||merchant.level()!=player.level()||merchant.distanceToSqr(player)>16)throw error("distance");
        if(merchant.getTradingPlayer()!=null&&!merchant.getTradingPlayer().getUUID().equals(player.getUUID()))throw error("busy");
        TradeJournal.recover(player);EconomyData.get(player.getServer()).requireAvailable();player.closeContainer();
        applyOverlay(merchant);if(merchant.getOffers().isEmpty()||merchant.getOffers().size()>256)throw error("no_offers");
        prices(merchant,player);merchant.setTradingPlayer(player);
        SESSIONS.put(player.getUUID(),new Session(merchant,UUID.randomUUID(),"",System.currentTimeMillis()+300000));
        player.openMenu(new SimpleMenuProvider((id,inventory,owner)->new EconomyMenu(id,inventory),merchant.getDisplayName()));
        player.awardStat(Stats.TALKED_TO_VILLAGER);send(player,"");
    }
    public static boolean valid(ServerPlayer player){
        Session s=SESSIONS.get(player.getUUID());return s!=null&&ServiceGates.enabled(player.level(),pt.realmeconomy.domain.ServiceKind.VILLAGER)&&s.merchant().isAlive()&&s.merchant().level()==player.level()&&s.merchant().distanceToSqr(player)<=16&&s.merchant().getTradingPlayer()==player&&!player.isSpectator()&&System.currentTimeMillis()<s.expires();
    }
    public static void close(ServerPlayer player){Session s=SESSIONS.remove(player.getUUID());if(s!=null&&s.merchant().getTradingPlayer()==player)s.merchant().setTradingPlayer(null);}
    private static Session session(ServerPlayer player){if(!valid(player)||!(player.containerMenu instanceof EconomyMenu))throw error("distance");return SESSIONS.get(player.getUUID());}
    public static boolean manager(ServerPlayer player){if(pt.realmeconomy.api.EconomyPermissions.canAdminister(player))return true;try{var governor=RealmBridge.governor(player.getServer(),System.currentTimeMillis());return governor!=null&&governor.id().equals(player.getUUID());}catch(RuntimeException failure){return false;}}
    private static boolean purchase(MerchantOffer offer){return !offer.getResult().is(Items.EMERALD)&&(offer.getCostA().is(Items.EMERALD)||offer.getCostB().is(Items.EMERALD));}
    private static TradePrice price(MerchantOffer offer,MinecraftServer server){int emeralds=purchase(offer)?(offer.getCostA().is(Items.EMERALD)?offer.getCostA().getCount():0)+(offer.getCostB().is(Items.EMERALD)?offer.getCostB().getCount():0):0;var rule=EconomyData.get(server).rule(pt.realmeconomy.domain.ServiceKind.VILLAGER);return TradePrice.of(emeralds,rule.value(),rule.tax(),rule.taxCap(),CrownBridge.installed());}
    public static void action(ServerPlayer player,String operation,String json){
        try{
            Session s=session(player);if(json.length()>2048)throw error("invalid_request");JsonObject request=JsonParser.parseString(json).getAsJsonObject();
            if(operation.equals("refresh")){if(!request.keySet().isEmpty())throw error("invalid_request");renew(player,s);send(player,"");return;}
            Set<String> fields=operation.equals("trade")?Set.of("nonce","quote","offer"):operation.equals("policy")?Set.of("nonce","quote","rate"):Set.of();
            if(fields.isEmpty()||!request.keySet().equals(fields))throw error("invalid_request");
            if(!UUID.fromString(request.get("nonce").getAsString()).equals(s.nonce())||!request.get("quote").getAsString().equals(s.quote())||!s.quote().equals(quote(s.merchant(),player.getServer())))throw error("stale");
            renew(player,s);EconomyData data=EconomyData.get(player.getServer());data.requireAvailable();
            if(operation.equals("policy")){
                if(!manager(player))throw error("permission");int rate=request.get("rate").getAsBigDecimal().intValueExact();if(rate<0||rate>EconomyData.get(player.getServer()).rule(pt.realmeconomy.domain.ServiceKind.VILLAGER).taxCap())throw error("tax_limit");
                String name=ChroniclesBridge.available(player)?ChroniclesBridge.publicName(player):player.getGameProfile().getName();data.policy(player.getUUID(),name,rate,CrownBridge.installed());flush(player.getServer());
            }else trade(player,s.merchant(),request.get("offer").getAsBigDecimal().intValueExact());
            send(player,"");
        }catch(RuntimeException|LinkageError failure){if(valid(player))try{send(player,message(failure));}catch(RuntimeException|LinkageError ignored){}else player.closeContainer();}
    }
    private static void renew(ServerPlayer player,Session s){SESSIONS.put(player.getUUID(),new Session(s.merchant(),UUID.randomUUID(),s.quote(),System.currentTimeMillis()+300000));}
    private static void trade(ServerPlayer player,AbstractVillager merchant,int index){
        var offers=merchant.getOffers();if(index<0||index>=offers.size())throw error("invalid_request");var offer=offers.get(index);if(offer.isOutOfStock())throw error("sold_out");
        boolean purchase=purchase(offer);TradePrice price=price(offer,player.getServer());boolean wallet=ChroniclesBridge.loaded()&&ChroniclesBridge.available(player);if(purchase&&!wallet)throw error("chronicles_unavailable");
        long before=wallet?ChroniclesBridge.balance(player):-1,after=wallet?before-price.total():-1;if(wallet&&after<0)throw error("insufficient_funds");
        ListTag nextItems=TradeItems.plan(player,offer,purchase);UUID receipt=UUID.randomUUID();if(price.tax()>0)CrownBridge.preflight(player.getServer(),receipt,player.getUUID(),price.tax());
        var nextOffers=offers.copy();nextOffers.get(index).increaseUses();nextOffers.forEach(MerchantOffer::resetSpecialPriceDiff);
        var data=EconomyData.get(player.getServer());var previous=data.merchant(merchant.getUUID());var overlay=new CompoundTag();overlay.putLong("revision",Math.addExact(previous.getLong("revision"),1));
        overlay.put("offers",encodeOffers(merchant,nextOffers));overlay.putInt("xp",merchant instanceof Villager?Math.addExact(merchant.getVillagerXp(),offer.getXp()):0);
        if(merchant instanceof Villager villager)overlay.putInt("level",villager.getVillagerData().getLevel());
        var intent=new CompoundTag();intent.putUUID("id",receipt);intent.putUUID("player",player.getUUID());intent.putUUID("merchant",merchant.getUUID());intent.putString("dimension",merchant.level().dimension().location().toString());intent.putLong("before",before);intent.putLong("after",after);intent.putLong("base",price.base());intent.putLong("tax",price.tax());intent.putInt("rate",price.rate());intent.putLong("at",System.currentTimeMillis());intent.put("inventoryBefore",TradeItems.inventory(player));intent.put("inventoryAfter",nextItems);intent.put("overlay",overlay);
        TradeJournal.commit(player,intent);
        // Financial state is already durable. On recovery we restore stock/xp without repeating
        // advancement/XP-orb events, which cannot be atomically committed with world entities.
        try{merchant.notifyTrade(offer);merchant.getPersistentData().putLong(MERCHANT_RECEIPT,overlay.getLong("revision"));player.awardStat(Stats.TRADED_WITH_VILLAGER);}
        catch(RuntimeException|LinkageError failure){applyOverlay(merchant);}
        player.serverLevel().playSound(null,merchant.blockPosition(),merchant.getNotifyTradeSound(),net.minecraft.sounds.SoundSource.NEUTRAL,0.5F,1F);
        flush(player.getServer());
    }
    public static void restore(AbstractVillager merchant){try{applyOverlay(merchant);}catch(RuntimeException|LinkageError error){com.mojang.logging.LogUtils.getLogger().error("Villager economic state could not be restored; trading remains unavailable",error);}}
    private static void applyOverlay(AbstractVillager merchant){
        if(merchant.level().isClientSide||merchant.getServer()==null)return;var overlay=EconomyData.get(merchant.getServer()).merchant(merchant.getUUID());long revision=overlay.getLong("revision");
        if(revision==0||merchant.getPersistentData().getLong(MERCHANT_RECEIPT)>=revision)return;
        var decoded=MerchantOffer.CODEC.listOf().parse(merchant.registryAccess().createSerializationContext(NbtOps.INSTANCE),overlay.getList("offers",Tag.TAG_COMPOUND)).getOrThrow();
        var offers=merchant.getOffers();offers.clear();offers.addAll(decoded);
        if(merchant instanceof Villager villager){villager.setVillagerXp(overlay.getInt("xp"));if(overlay.contains("level"))villager.setVillagerData(villager.getVillagerData().setLevel(overlay.getInt("level")));}
        merchant.getPersistentData().putLong(MERCHANT_RECEIPT,revision);
        if(merchant.getTradingPlayer()!=null)prices(merchant,merchant.getTradingPlayer());
    }
    private static ListTag encodeOffers(AbstractVillager merchant,java.util.List<MerchantOffer> offers){return (ListTag)MerchantOffer.CODEC.listOf().encodeStart(merchant.registryAccess().createSerializationContext(NbtOps.INSTANCE),offers).getOrThrow();}
    private static void prices(AbstractVillager merchant,net.minecraft.world.entity.player.Player player){
        if(!(merchant instanceof Villager villager))return;merchant.getOffers().forEach(MerchantOffer::resetSpecialPriceDiff);
        try{Method method=Villager.class.getDeclaredMethod("updateSpecialPrices",net.minecraft.world.entity.player.Player.class);method.setAccessible(true);method.invoke(villager,player);}catch(ReflectiveOperationException failure){throw error("discounts");}
    }
    private static String quote(AbstractVillager merchant,MinecraftServer server){try{String value=encodeOffers(merchant,merchant.getOffers()).toString()+":"+EconomyData.get(server).revision()+":"+EconomyData.get(server).rate()+":"+EconomyConfig.get()+":"+CrownBridge.installed();return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static void item(JsonObject json,String field,ItemStack stack,ServerPlayer player){json.add(field,ItemStack.OPTIONAL_CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),stack).getOrThrow());}
    public static JsonObject snapshot(ServerPlayer player){
        var s=session(player);var merchant=s.merchant();var data=EconomyData.get(player.getServer());var config=EconomyConfig.get();String quote=quote(merchant,player.getServer());SESSIONS.put(player.getUUID(),new Session(merchant,s.nonce(),quote,s.expires()));
        var out=new JsonObject();out.addProperty("container",player.containerMenu.containerId);out.addProperty("nonce",s.nonce().toString());out.addProperty("quote",quote);out.addProperty("name",merchant.hasCustomName()?merchant.getDisplayName().getString():merchant.getType().getDescription().getString());out.addProperty("balance",ChroniclesBridge.available(player)?ChroniclesBridge.balance(player):-1);out.addProperty("manager",manager(player));out.addProperty("administrator",pt.realmeconomy.api.EconomyPermissions.canAdminister(player));out.addProperty("treasury",CrownBridge.installed());out.addProperty("rate",CrownBridge.installed()?Math.min(data.rate(),data.rule(pt.realmeconomy.domain.ServiceKind.VILLAGER).taxCap()):0);out.addProperty("approvedRate",data.rate());out.addProperty("maxRate",data.rule(pt.realmeconomy.domain.ServiceKind.VILLAGER).taxCap());out.addProperty("conversion",data.rule(pt.realmeconomy.domain.ServiceKind.VILLAGER).value());out.addProperty("xp",merchant.getVillagerXp());out.addProperty("level",merchant instanceof Villager v?v.getVillagerData().getLevel():1);out.addProperty("profession",merchant instanceof Villager v?v.getVillagerData().getProfession().name():"wandering_trader");
        var array=new JsonArray();int index=0;for(var offer:merchant.getOffers()){
            var row=new JsonObject();row.addProperty("index",index++);item(row,"costA",offer.getCostA(),player);item(row,"costB",offer.getCostB(),player);item(row,"result",offer.getResult(),player);row.addProperty("purchase",purchase(offer));row.addProperty("sale",offer.getResult().is(Items.EMERALD));var price=price(offer,player.getServer());row.addProperty("base",price.base());row.addProperty("tax",price.tax());row.addProperty("total",price.total());row.addProperty("stock",Math.max(0,offer.getMaxUses()-offer.getUses()));row.addProperty("canTrade",false);try{TradeItems.plan(player,offer,purchase(offer));row.addProperty("canTrade",!offer.isOutOfStock()&&(!purchase(offer)||out.get("balance").getAsLong()>=price.total()));}catch(RuntimeException ignored){}array.add(row);
        }out.add("offers",array);var history=new JsonArray();for(Tag tag:data.history().subList(0,Math.min(20,data.history().size()))){var event=(CompoundTag)tag;var row=new JsonObject();row.addProperty("name",event.getString("name"));row.addProperty("rate",event.getInt("rate"));row.addProperty("at",event.getLong("at"));history.add(row);}out.add("history",history);return out;
    }
    private static void send(ServerPlayer player,String error){var snapshot=snapshot(player);snapshot.addProperty("error",error);TradeNetwork.send(player,snapshot);}
    private static String message(Throwable failure){String m=failure.getMessage();return m!=null&&m.startsWith("error.realmeconomy.")?m:"error.realmeconomy.invalid_request";}
    public static void flush(MinecraftServer server){if(!CrownBridge.installed())return;var data=EconomyData.get(server);data.requireAvailable();int count=0;var outgoing=data.outbox();for(String key:outgoing.getAllKeys()){if(count++>=20)break;var tax=outgoing.getCompound(key);UUID id=UUID.fromString(key);try{if(tax.getBoolean("economic"))CrownBridge.economicPolicy(server,id,tax.getUUID("payer"),tax.getString("service"),tax.getString("old"),tax.getString("next"),tax.getLong("at"));else if(tax.getBoolean("policy"))CrownBridge.policy(server,id,tax.getUUID("payer"),tax.getInt("rate"),tax.getLong("at"));else if(tax.contains("service"))CrownBridge.creditService(server,id,tax.getUUID("payer"),tax.getLong("amount"),tax.getString("service"));else CrownBridge.credit(server,id,tax.getUUID("payer"),tax.getLong("amount"));data.delivered(id);}catch(RuntimeException|LinkageError failed){break;}}}
    public static void tick(MinecraftServer server){int tick=TICKS.merge(server,1,Integer::sum);if(tick%20!=0)return;try{flush(server);}catch(RuntimeException|LinkageError ignored){}for(var player:server.getPlayerList().getPlayers())if(SESSIONS.containsKey(player.getUUID())&&!valid(player))player.closeContainer();}
    public static void login(ServerPlayer player){try{TradeJournal.recover(player);}catch(RuntimeException|LinkageError failed){player.displayClientMessage(Component.translatable("error.realmeconomy.frozen"),false);}}
    public static void stop(MinecraftServer server){SESSIONS.entrySet().removeIf(e->e.getValue().merchant().getServer()==server);TICKS.remove(server);EconomyData.forget(server);}
}
