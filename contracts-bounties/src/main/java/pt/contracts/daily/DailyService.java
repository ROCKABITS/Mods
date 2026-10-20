package pt.contracts.daily;

import com.google.gson.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import pt.contracts.*;
import pt.contracts.api.ContractsApi;
import pt.contracts.compat.ChroniclesBridge;
import pt.contracts.compat.NotificationsBridge;
import pt.contracts.daily.DailyRules.*;
import pt.contracts.daily.DailyLedger.*;

/** Only physical board sessions may accept, deliver or govern. Event progress has no client endpoint. */
public final class DailyService {
    private static final Map<UUID,Long> LAST=new HashMap<>(),PROGRESS_NOTICE=new HashMap<>();
    private static final Map<UUID,JsonObject> VIEWS=new HashMap<>();
    private DailyService(){}
    public static void thread(ServerPlayer p){if(p==null||p.getServer()==null||!p.getServer().isSameThread())throw new IllegalStateException("daily.contracts.invalid_request");}
    public static boolean survivor(ServerPlayer p){return p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL||p.gameMode.getGameModeForPlayer()==GameType.ADVENTURE;}
    public static void requireSurvivor(ServerPlayer p){if(!survivor(p)||!p.isAlive())throw new IllegalStateException("daily.contracts.survival_only");}
    public static DailyData ensure(ServerPlayer p){
        thread(p);DailyData data=DailyData.get(p.getServer());data.available();
        if(!data.ledger.initialized){data.ledger.settings=DailyCatalog.initialSettings();data.ledger.initialized=true;data.persist(p.getServer());}
        if(data.ledger.needsRound(p.getUUID(),System.currentTimeMillis())){
            data.ledger.generate(p.getUUID(),System.currentTimeMillis(),DailyCatalog.available(p,data.ledger),!ChroniclesBridge.loaded()&&data.ledger.settings.emeraldFallback?"EMERALD":"CHRONICLES");
            data.persist(p.getServer());
            if(p.connection!=null&&p.connection.isAcceptingMessages())NotificationsBridge.notify(p,"contract",Component.translatable("daily.contracts.new_day"));
        }return data;
    }
    public static void open(ServerPlayer p){ContractsService.requireSession(p);ensure(p);DailyNetwork.snapshot(p,snapshot(p,""));}
    public static void logout(UUID id){LAST.remove(id);PROGRESS_NOTICE.remove(id);VIEWS.remove(id);}
    public static void clear(){LAST.clear();PROGRESS_NOTICE.clear();VIEWS.clear();}
    public static void request(ServerPlayer p,String operation,String text){
        thread(p);
        try{
            if(operation.equals("ack")){DailyRules.jsonEnvelope(text,256);JsonObject input=JsonParser.parseString(text).getAsJsonObject();acknowledge(p,UUID.fromString(string(input,"id",36)));return;}
            ContractsService.requireSession(p);long now=System.nanoTime();Long previous=LAST.put(p.getUUID(),now);if(previous!=null&&now-previous<50_000_000)return;
            DailyRules.jsonEnvelope(text,16384);JsonObject input=JsonParser.parseString(text).getAsJsonObject();
            DailyData data=ensure(p);String message="";
            switch(operation){
                case "refresh" -> {remember(p,input);retry(p);}
                case "accept" -> {requireSurvivor(p);data.ledger.accept(p.getUUID(),UUID.fromString(string(input,"id",36)),System.currentTimeMillis());data.persist(p.getServer());}
                case "deliver" -> deliver(p,UUID.fromString(string(input,"id",36)));
                case "retry" -> {retry(p);message="daily.contracts.retry_done";}
                case "policy" -> {govern(p,input);message="daily.contracts.policy_saved";}
                case "settings" -> {adminSettings(p,input);message="daily.contracts.settings_saved";}
                case "template" -> {adminTemplate(p,input);message="daily.contracts.template_saved";}
                case "reload" -> {DailyPermissions.requireAdmin(p);DailyCatalog.reloadFiles();data.ledger.audit(p.getUUID(),"catalog_reload","Server file templates reloaded",System.currentTimeMillis());data.persist(p.getServer());message="daily.contracts.catalog_reloaded";}
                case "inspect" -> {DailyPermissions.requireAdmin(p);remember(p,input);}
                case "retry_player" -> {DailyPermissions.requireAdmin(p);UUID target=UUID.fromString(string(input,"player",36));ServerPlayer online=p.getServer().getPlayerList().getPlayer(target);if(online!=null)retry(online);data.ledger.audit(p.getUUID(),"retry_player",target.toString(),System.currentTimeMillis());data.persist(p.getServer());}
                case "repair_progress" -> {repair(p,input);message="daily.contracts.repair_saved";}
                default -> throw new IllegalStateException("daily.contracts.invalid_request");
            }
            DailyNetwork.snapshot(p,snapshot(p,message));
        }catch(RuntimeException|LinkageError error){
            String message=safeError(error);
            try{ContractsService.requireSession(p);DailyNetwork.snapshot(p,snapshot(p,message));}
            catch(RuntimeException|LinkageError missing){try{DailyNetwork.snapshot(p,"{\"close\":true}");p.displayClientMessage(Component.translatable(message),false);}catch(RuntimeException ignored){}}
        }
    }
    private static void remember(ServerPlayer p,JsonObject input){JsonObject view=new JsonObject();if(input.has("search"))view.addProperty("search",string(input,"search",128));if(input.has("page"))view.addProperty("page",integer(input,"page",0,100000));VIEWS.put(p.getUUID(),view);}
    public static void activity(ServerPlayer p,Category category,Validation validation,Predicate<Template> target,int amount){
        thread(p);if(!survivor(p)||!p.isAlive()||amount<1)return;DailyData data=ensure(p);Round round=data.ledger.account(p.getUUID()).round;
        if(round==null)return;long now=System.currentTimeMillis();boolean changed=false;
        for(var q:List.copyOf(round.quests))if(q.state==State.ACTIVE&&q.template.category()==category&&q.template.validation()==validation&&requirements(p,q.template)&&target.test(q.template)){
            boolean complete=data.ledger.progress(p.getUUID(),q.id,amount,now);changed=true;
            if(complete){complete(p,q.id,false);retry(p);}
            else if(data.ledger.settings.progressNotices){long last=PROGRESS_NOTICE.getOrDefault(p.getUUID(),0L);if(now-last>=10000){PROGRESS_NOTICE.put(p.getUUID(),now);NotificationsBridge.notify(p,"contract",Component.translatable("daily.contracts.progress_notice",label(q.template.title()),q.progress,q.template.quantity()));}}
        }if(changed)data.setDirty();
    }
    public static boolean requirements(ServerPlayer p,Template t){String dimension=p.level().dimension().location().toString();String biome=p.level().getBiome(p.blockPosition()).unwrapKey().map(k->k.location().toString()).orElse("");return(t.dimensions().isEmpty()||t.dimensions().contains(dimension))&&(t.biomes().isEmpty()||t.biomes().contains(biome));}
    public static boolean item(Template t,ItemStack stack){if(stack.isEmpty())return false;String target=t.target();return target.startsWith("#")?stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse(target.substring(1)))):stack.getItem().builtInRegistryHolder().key().location().toString().equals(target);}
    public static void deliver(ServerPlayer p,UUID id){
        ContractsService.requireSession(p);requireSurvivor(p);DailyData data=ensure(p);if(data.journal(p.getUUID())!=null)throw new IllegalStateException("daily.contracts.payment_pending");
        Quest quest=data.ledger.quest(p.getUUID(),id);if(quest.state!=State.ACTIVE||quest.template.category()!=Category.DELIVERY||!requirements(p,quest.template))throw new IllegalStateException("daily.contracts.invalid_request");
        List<ItemStack> after=DailyRewardJournal.items(p);int left=quest.template.quantity();
        for(ItemStack stack:after)if(item(quest.template,stack)&&ItemStack.isSameItemSameComponents(stack,new ItemStack(stack.getItem()))){int take=Math.min(left,stack.getCount());stack.shrink(take);left-=take;if(left==0)break;}
        if(left!=0)throw new IllegalStateException("gui.contracts.insufficient_items");
        DailyLedger before=data.ledger.copy();
        try{
            if(!data.ledger.progress(p.getUUID(),id,quest.template.quantity(),System.currentTimeMillis()))throw new IllegalStateException("daily.contracts.expired");
            Reward reward=complete(p,id,true);DailyRewardJournal.begin(p,reward,after);
        }catch(RuntimeException|LinkageError failed){if(data.journal(p.getUUID())==null&&data.ledger.rewards.get(id)!=null&&!data.ledger.rewards.get(id).deliveryConfirmed){data.ledger=before;data.setDirty();}throw failed;}
        retry(p);
    }
    public static Reward complete(ServerPlayer p,UUID id,boolean delivery){
        DailyData data=DailyData.get(p.getServer());long now=System.currentTimeMillis(),day=Math.floorDiv(Math.max(now,data.ledger.highWater),DailyRules.DAY);
        int publicRep=0,limit=data.ledger.settings.reputationLimit;
        var previous=ContractsData.get(p.getServer()).ledger.dailySnapshot().get(p.getUUID());if(previous!=null&&previous.day()>=day)publicRep=previous.reputation();
        try{limit=Math.min(limit,ContractsApi.policy(p.getServer()).dailyReputationLimit());}catch(RuntimeException|LinkageError broken){limit=0;}
        Reward reward=data.ledger.complete(p.getUUID(),id,now,DailyTreasury.installed()?data.ledger.settings.taxBps:0,publicRep,limit);
        reward.deliveryConfirmed=!delivery;if(!delivery)data.persist(p.getServer());return reward;
    }
    public static void retry(ServerPlayer p){
        thread(p);if(!survivor(p)||!p.isAlive())return;DailyData data=DailyData.get(p.getServer());data.available();
        try{DailyRewardJournal.recover(p);}catch(RuntimeException|LinkageError failed){error(data,p,failed);sendFinales(p);return;}
        int paid=0;for(Reward reward:List.copyOf(data.ledger.rewards.values()))if(reward.player.equals(p.getUUID())&&!reward.paid&&reward.deliveryConfirmed){
            try{DailyRewardJournal.begin(p,reward,null);paid++;}catch(RuntimeException|LinkageError failed){error(data,p,failed);break;}if(paid>=6)break;
        }
        var account=data.ledger.account(p.getUUID());List<Round> rounds=new ArrayList<>(account.history);if(account.round!=null)rounds.add(account.round);
        for(Round round:rounds){Reward special=data.ledger.special(p.getUUID(),round,System.currentTimeMillis(),DailyTreasury.installed()?data.ledger.settings.taxBps:0);if(special!=null){data.persist(p.getServer());try{DailyRewardJournal.begin(p,special,null);}catch(RuntimeException|LinkageError failed){error(data,p,failed);}}}
        sendFinales(p);
    }
    private static void error(DailyData data,ServerPlayer p,Throwable failed){String error=safeError(failed);for(Reward r:data.ledger.rewards.values())if(r.player.equals(p.getUUID())&&!r.paid&&!r.error.equals(error)){r.error=error;data.setDirty();}ContractsMod.LOG.debug("Daily reward remains reserved for {}: {}",p.getUUID(),error);}
    public static void sendFinales(ServerPlayer p){
        DailyData data=DailyData.get(p.getServer());if(p.connection==null||!p.connection.isAcceptingMessages()||!p.connection.hasChannel(DailyPackets.Finale.TYPE.id()))return;
        int sent=0;for(Reward reward:data.ledger.rewards.values())if(reward.player.equals(p.getUUID())&&reward.paid&&!reward.notified){
            Map<String,Object> payload=new LinkedHashMap<>();payload.put("id",reward.id.toString());payload.put("title",reward.title);payload.put("difficulty",reward.difficulty.name());payload.put("money",reward.net);payload.put("tax",reward.tax);payload.put("currency",reward.currency);payload.put("reputation",reward.reputation);payload.put("day",reward.day);
            long total=data.ledger.rewards.values().stream().filter(r->r.player.equals(p.getUUID())&&r.day==reward.day&&r.paid).mapToLong(r->r.net).sum();payload.put("total",total);payload.put("completed",reward.difficulty==Difficulty.ALL?5:1);
            DailyNetwork.finale(p,DailyLedger.JSON.toJson(payload));if(++sent>=8)break;
        }
    }
    public static void acknowledge(ServerPlayer p,UUID id){DailyData data=DailyData.get(p.getServer());Reward r=data.ledger.rewards.get(id);if(r==null||!r.player.equals(p.getUUID())||!r.paid)throw new IllegalStateException("daily.contracts.invalid_request");if(!r.notified){r.notified=true;data.persist(p.getServer());}}
    private static void revision(DailyData data,JsonObject input){if(!input.has("revision")||input.get("revision").getAsLong()!=data.ledger.revision)throw new IllegalStateException("daily.contracts.stale_policy");}
    public static void govern(ServerPlayer p,JsonObject input){
        DailyPermissions.requireGovernor(p);DailyData data=DailyData.get(p.getServer());revision(data,input);Settings next=DailyLedger.JSON.fromJson(DailyLedger.JSON.toJson(data.ledger.settings),Settings.class);
        next.taxBps=integer(input,"taxBps",0,next.taxCapBps);if(next.taxBps>0&&!DailyTreasury.available())throw new IllegalStateException("daily.contracts.treasury_error");
        JsonObject bonuses=input.getAsJsonObject("bonuses");for(Category c:Category.values())next.bonuses.put(c,integer(bonuses,c.name(),0,next.bonusCapBps));next.validate();
        data.ledger.audit(p.getUUID(),"presidential_policy",DailyLedger.JSON.toJson(next),System.currentTimeMillis());data.ledger.settings=next;data.persist(p.getServer());
    }
    public static void adminSettings(ServerPlayer p,JsonObject input){
        DailyPermissions.requireAdmin(p);DailyData data=DailyData.get(p.getServer());revision(data,input);Settings settings=DailyLedger.JSON.fromJson(input.get("settings"),Settings.class);settings.validate();
        if(settings.taxBps>0&&!DailyTreasury.available())throw new IllegalStateException("daily.contracts.treasury_error");
        data.ledger.audit(p.getUUID(),"admin_settings",DailyLedger.JSON.toJson(settings),System.currentTimeMillis());data.ledger.settings=settings;data.persist(p.getServer());
    }
    public static void adminTemplate(ServerPlayer p,JsonObject input){
        DailyPermissions.requireAdmin(p);DailyData data=DailyData.get(p.getServer());revision(data,input);Template template=DailyLedger.JSON.fromJson(input.get("template"),Template.class);
        if(!data.ledger.overrides.containsKey(template.id())&&data.ledger.overrides.size()>=1024)throw new IllegalStateException("daily.contracts.catalog_error");
        data.ledger.overrides.put(template.id(),template);data.ledger.audit(p.getUUID(),"template",DailyLedger.JSON.toJson(template),System.currentTimeMillis());data.persist(p.getServer());
    }
    public static void repair(ServerPlayer actor,JsonObject input){
        DailyPermissions.requireAdmin(actor);DailyData data=DailyData.get(actor.getServer());UUID player=UUID.fromString(string(input,"player",36)),quest=UUID.fromString(string(input,"quest",36));
        String reason=string(input,"reason",256).strip();if(reason.length()<8)throw new IllegalStateException("daily.contracts.invalid_request");
        ServerPlayer target=actor.getServer().getPlayerList().getPlayer(player);if(target==null)throw new IllegalStateException("daily.contracts.player_offline");
        Quest q=data.ledger.quest(player,quest);if(q.state!=State.ACTIVE||data.journal(player)!=null||System.currentTimeMillis()>=data.ledger.account(player).round.resetAt||q.template.category()==Category.DELIVERY)throw new IllegalStateException("daily.contracts.invalid_request");
        int progress=integer(input,"progress",0,q.template.quantity());int previous=q.progress;q.progress=progress;
        data.ledger.audit(actor.getUUID(),"repair_progress",player+"/"+quest+":"+previous+">"+progress+":"+reason,System.currentTimeMillis());
        if(progress==q.template.quantity()){q.state=State.COMPLETED;complete(target,q.id,false);}else data.persist(actor.getServer());retry(target);
    }
    public static String snapshot(ServerPlayer p,String message){
        ContractsService.requireSession(p);DailyData data=DailyData.get(p.getServer());var account=data.ledger.account(p.getUUID());Map<String,Object> map=new LinkedHashMap<>();
        map.put("name",DailyWallet.name(p));map.put("message",message);map.put("now",Math.max(data.ledger.highWater,System.currentTimeMillis()));map.put("clockBackwards",System.currentTimeMillis()<data.ledger.highWater-2000);map.put("revision",data.ledger.revision);map.put("round",roundState(account.round,true));map.put("history",account.history.stream().limit(7).map(r->roundState(r,false)).toList());map.put("settings",data.ledger.settings);map.put("admin",DailyPermissions.administrator(p));map.put("governor",DailyPermissions.governor(p));map.put("treasury",DailyTreasury.available());map.put("trackingFull",data.ledger.trackingFull);
        List<Reward> mine=data.ledger.rewards.values().stream().filter(r->r.player.equals(p.getUUID())).toList();List<Reward> recent=new ArrayList<>(mine);Collections.reverse(recent);map.put("rewards",recent.stream().limit(24).toList());map.put("pending",mine.stream().filter(r->!r.paid).mapToLong(r->r.net).sum());
        if(account.round!=null)map.put("netToday",mine.stream().filter(r->r.day==account.round.day&&r.paid).mapToLong(r->r.net).sum());
        if(DailyPermissions.administrator(p)){
            JsonObject view=VIEWS.getOrDefault(p.getUUID(),new JsonObject());String search=view.has("search")?view.get("search").getAsString().toLowerCase(Locale.ROOT):"";int page=view.has("page")?view.get("page").getAsInt():0;
            List<Template> templates=DailyCatalog.all(data.ledger).stream().filter(t->t.id().toLowerCase(Locale.ROOT).contains(search)||t.title().toLowerCase(Locale.ROOT).contains(search)).toList();int largest=templates.stream().mapToInt(t->DailyLedger.JSON.toJson(t).length()).max().orElse(1),size=Math.clamp(18000/Math.max(1,largest),1,8);page=Math.min(page,Math.max(0,(templates.size()-1)/size));map.put("catalog",templates.subList(page*size,Math.min(templates.size(),page*size+size)));map.put("catalogPageSize",size);map.put("catalogPage",page);map.put("catalogTotal",templates.size());map.put("audit",data.ledger.audit.stream().limit(12).toList());map.put("errors",data.ledger.rewards.values().stream().filter(r->!r.paid&&!r.error.isBlank()).limit(12).toList());
        }
        else if(DailyPermissions.governor(p))map.put("audit",data.ledger.audit.stream().filter(a->a.operation().equals("presidential_policy")).limit(12).toList());
        String json=DailyLedger.JSON.toJson(map);if(json.length()>65536)throw new IllegalStateException("daily.contracts.snapshot_limit");return json;
    }
    private static JsonObject roundState(Round round,boolean current){
        if(round==null)return null;JsonObject json=DailyLedger.JSON.toJsonTree(round).getAsJsonObject();
        for(var row:json.getAsJsonArray("quests")){JsonObject template=row.getAsJsonObject().getAsJsonObject("template");template.remove("mods");for(String key:List.of("biomes","dimensions")){JsonArray old=template.getAsJsonArray(key),trimmed=new JsonArray();if(current)for(int i=0;i<Math.min(2,old.size());i++)trimmed.add(old.get(i));template.add(key,trimmed);if(current&&old.size()>2)template.addProperty(key+"More",old.size()-2);}}
        return json;
    }
    public static Component label(String key){return Component.translatableWithFallback(key,key);}
    public static String safeError(Throwable error){String message=Objects.toString(error.getMessage(),"");return message.startsWith("daily.contracts.")||message.startsWith("gui.contracts.")?message:"daily.contracts.invalid_request";}
    public static String string(JsonObject object,String key,int limit){var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().length()>limit)throw new IllegalStateException("daily.contracts.invalid_request");return value.getAsString();}
    public static int integer(JsonObject object,String key,int min,int max){var value=object.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalStateException("daily.contracts.invalid_request");int n=value.getAsBigDecimal().intValueExact();if(n<min||n>max)throw new IllegalStateException("daily.contracts.invalid_request");return n;}
}
