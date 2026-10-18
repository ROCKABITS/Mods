package pt.realmcasino.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pt.realmcasino.block.CasinoMachineBlock;
import pt.realmcasino.blockentity.CasinoBlockEntity;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.compat.NotificationsBridge;
import pt.realmcasino.data.CasinoData;
import pt.realmcasino.data.GameState;
import pt.realmcasino.domain.Blackjack;
import pt.realmcasino.domain.CasinoLedger;
import pt.realmcasino.domain.CasinoLedger.Kind;
import pt.realmcasino.domain.CasinoLedger.Machine;
import pt.realmcasino.domain.Money;
import pt.realmcasino.domain.Roulette;
import pt.realmcasino.domain.Slots;
import pt.realmcasino.network.CasinoNetwork;

/** Every result, identity, reservation and transaction is authoritative on the server thread. */
public final class CasinoService {
    private static Random RANDOM=new SecureRandom();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<MinecraftServer,Long> TICKS=new java.util.WeakHashMap<>();
    private static final String PREFIX="error.realmcasino.";
    private record Session(MinecraftServer server,ResourceKey<Level> dimension,BlockPos master,UUID machine,UUID nonce,long expires,long tick) {
        Session refreshNonce(){return new Session(server,dimension,master,machine,UUID.randomUUID(),expires,tick);}
    }
    private CasinoService() {}

    public static void open(ServerPlayer player,BlockPos master) {
        try {
            requirePlayer(player); WalletJournal.recover(player);physical(player,master);
            CasinoData data=CasinoData.get(player.getServer());
            Machine machine=data.ledger().at(dimension(player.level()),master.asLong()).orElseThrow(()->failure("machine_unavailable"));
            if(machine.kind()!=kind(blockGame(player.level(),master)))throw failure("machine_unavailable");
            Session previous=SESSIONS.get(player.getUUID());
            if(previous!=null&&!previous.machine().equals(machine.id()))autoStand(player.getServer(),player.getUUID());
            SESSIONS.put(player.getUUID(),new Session(player.getServer(),player.level().dimension(),master.immutable(),machine.id(),UUID.randomUUID(),now()+300_000,ticks(player.getServer())+6000));
            send(player,"","");
        }catch(RuntimeException|LinkageError problem){closeSnapshot(player,error(problem));}
    }
    public static Machine requireSession(ServerPlayer player) {
        requirePlayer(player);Session session=SESSIONS.get(player.getUUID());
        if(session==null||session.server()!=player.getServer()||!session.dimension().equals(player.level().dimension())
                ||now()>=session.expires()||ticks(player.getServer())>=session.tick())throw failure("session_expired");
        physical(player,session.master());
        Machine machine=CasinoData.get(player.getServer()).ledger().at(dimension(player.level()),session.master().asLong()).orElseThrow(()->failure("machine_unavailable"));
        if(!machine.id().equals(session.machine())||machine.kind()!=kind(blockGame(player.level(),session.master())))throw failure("session_expired");
        return machine;
    }
    private static void physical(ServerPlayer player,BlockPos master) {
        if(master==null||!player.level().hasChunkAt(master)||player.distanceToSqr(Vec3.atCenterOf(master))>64)throw failure("machine_range");
        var state=player.level().getBlockState(master);
        if(!(state.getBlock()instanceof CasinoMachineBlock block)||!CasinoMachineBlock.master(player.level(),master).equals(master)||!block.validStructure(player.level(),master))throw failure("machine_range");
    }
    public static void mayPlace(ServerPlayer player,BlockPos master,String game) {
        requirePlayer(player);Kind kind=kind(game);CasinoData data=CasinoData.get(player.getServer());data.requireAvailable();
        var existing=data.ledger().at(dimension(player.level()),master.asLong());
        if(existing.isPresent()) {
            Machine machine=existing.get();
            if(!machine.owner().equals(player.getUUID())||machine.kind()!=kind)throw failure("location_reserved");
        }else if(data.ledger().machines().size()>=CasinoLedger.MAX_MACHINES)throw failure("machine_limit");
    }
    public static void mayPlace(ServerPlayer player,BlockPos master){mayPlace(player,master,blockGame(player.level(),master));}
    public static void placed(ServerPlayer player,BlockPos master,String game) {
        mayPlace(player,master,game);physicalPlacement(player,master,game);
        CasinoData data=CasinoData.get(player.getServer());
        if(data.ledger().at(dimension(player.level()),master.asLong()).isPresent())return;
        CasinoConfig config=CasinoConfig.get();Kind kind=kind(game);
        long minimum=kind==Kind.BLACKJACK?Math.addExact(config.minBet(),config.minBet()%2):config.minBet();
        long maximum=kind==Kind.BLACKJACK?config.maxBet()-config.maxBet()%2:config.maxBet();
        if(minimum>maximum)throw failure("invalid_limits");
        data.replace(data.ledger().register(UUID.randomUUID(),player.getUUID(),kind,dimension(player.level()),master.asLong(),"Realm Casino",minimum,maximum),data.states());
    }
    public static void placed(ServerPlayer player,BlockPos master){placed(player,master,blockGame(player.level(),master));}
    private static void physicalPlacement(ServerPlayer player,BlockPos master,String game) {
        var state=player.level().getBlockState(master);
        if(!(state.getBlock()instanceof CasinoMachineBlock block)||!block.game().equals(game)||!block.validStructure(player.level(),master))throw failure("machine_range");
    }
    public static boolean canBreak(ServerPlayer player,BlockPos master) {
        try {
            requirePlayer(player);CasinoData data=CasinoData.get(player.getServer());
            if(data.frozen()||data.journal()!=null)return false;
            var machine=data.ledger().at(dimension(player.level()),master.asLong());
            return machine.isEmpty()||(machine.get().owner().equals(player.getUUID())&&machine.get().bank()==0&&machine.get().reserved()==0);
        }catch(RuntimeException|LinkageError problem){return false;}
    }
    public static boolean hasFunds(Level level,BlockPos master) {
        if(level.isClientSide||level.getServer()==null)return true;
        try {
            CasinoData data=CasinoData.get(level.getServer());
            if(data.frozen()||data.journal()!=null)return true;
            return data.ledger().at(dimension(level),master.asLong()).map(machine->machine.bank()!=0||machine.reserved()!=0).orElse(false);
        }catch(RuntimeException|LinkageError problem){return true;}
    }
    public static void removed(Level level,BlockPos master) {
        if(level.isClientSide||level.getServer()==null)return;
        try {
            CasinoData data=CasinoData.get(level.getServer());if(data.frozen()||data.journal()!=null)return;
            var record=data.ledger().at(dimension(level),master.asLong());
            if(record.isPresent()&&record.get().bank()==0&&record.get().reserved()==0)data.replace(data.ledger().remove(record.get().id(),record.get().owner()),data.states());
        }catch(RuntimeException|LinkageError ignored){/* Funded/unreadable records are retained for safe restoration at the same location. */}
    }

    public static void action(ServerPlayer player,String operation,String json) {
        try {
            requirePlayer(player);JsonObject payload=parse(json);
            if(operation.equals("close")) { fields(payload,Set.of());logout(player);closeSnapshot(player,"");return; }
            Machine machine=requireSession(player);CasinoData data=CasinoData.get(player.getServer());
            if(operation.equals("refresh")) {fields(payload,Set.of());SESSIONS.put(player.getUUID(),SESSIONS.get(player.getUUID()).refreshNonce());send(player,"","");return;}
            Set<String> allowed=switch(operation) {
                case "bet"->Set.of("nonce","version","amount","selection","number");
                case "hit","stand"->Set.of("nonce","version","round","revision");
                case "deposit","withdraw","claim"->Set.of("nonce","version","amount");
                case "configure"->Set.of("nonce","version","name","minBet","maxBet");
                default->throw failure("invalid_request");
            };
            fields(payload,allowed);Session session=SESSIONS.get(player.getUUID());
            if(!uuid(payload,"nonce").equals(session.nonce())||integer(payload,"version")!=machine.version())throw failure("stale_view");
            // Consume before any write. A replay can never debit a wallet twice, including error/recovery paths.
            SESSIONS.put(player.getUUID(),session.refreshNonce());data.requireAvailable();
            String message=switch(operation) {
                case "bet"->{bet(player,machine,payload);yield "gui.realmcasino.bet_accepted";}
                case "hit","stand"->{playCard(player,machine,payload,operation.equals("hit"));yield "";}
                case "deposit"->{requireEconomy(player);long amount=positive(payload,"amount");WalletJournal.transfer(player,-amount,"deposit",data.ledger().deposit(machine.id(),player.getUUID(),amount),data.states());yield "gui.realmcasino.deposited";}
                case "withdraw"->{requireEconomy(player);long amount=positive(payload,"amount");data.replace(data.ledger().withdraw(machine.id(),player.getUUID(),amount),data.states());yield "gui.realmcasino.withdrawn_pending";}
                case "claim"->{claim(player,payload);yield "gui.realmcasino.claimed";}
                case "configure"->{configure(player,machine,payload);yield "gui.realmcasino.configured";}
                default->throw failure("invalid_request");
            };
            send(player,message,"");
        }catch(RuntimeException|LinkageError problem) {
            try {requireSession(player);send(player,"",error(problem));}
            catch(RuntimeException|LinkageError expired){closeSnapshot(player,error(problem));}
        }
    }
    private static void bet(ServerPlayer player,Machine machine,JsonObject payload) {
        requireEconomy(player);CasinoConfig config=CasinoConfig.get();long stake=positive(payload,"amount");
        if(stake<config.minBet()||stake>config.maxBet())throw failure("invalid_stake");
        CasinoData data=CasinoData.get(player.getServer());
        GameState previous=data.states().get(player.getUUID());if(previous!=null&&!previous.finished())throw failure("hand_active");
        if(ChroniclesBridge.balance(player)<stake)throw failure("insufficient_funds");
        UUID round=UUID.randomUUID();long now=now();
        CasinoLedger next=data.ledger().startBet(machine.id(),player.getUUID(),stake,round,machine.version(),now);
        String selection=optionalString(payload,"selection",16,"");
        GameState game=switch(machine.kind()) {
            case SLOTS->{if(!selection.isEmpty()||payload.has("number"))throw failure("invalid_request");yield GameState.slots(round,machine.id(),player.getUUID(),stake,now,Slots.draw(RANDOM::nextInt));}
            case ROULETTE->{
                Roulette.Type type;
                try{type=Roulette.Type.valueOf(selection.toUpperCase(Locale.ROOT));}catch(RuntimeException e){throw failure("invalid_selection");}
                if(type!=Roulette.Type.NUMBER&&payload.has("number"))throw failure("invalid_request");
                int number=type==Roulette.Type.NUMBER?Math.toIntExact(integer(payload,"number")):-1;
                Roulette.Bet bet=new Roulette.Bet(type,number);yield GameState.roulette(round,machine.id(),player.getUUID(),stake,now,bet,Roulette.draw(RANDOM::nextInt));
            }
            case BLACKJACK->{if(!selection.isEmpty()||payload.has("number"))throw failure("invalid_request");yield GameState.blackjack(round,machine.id(),player.getUUID(),stake,now,Blackjack.deal(RANDOM::nextInt));}
        };
        Map<UUID,GameState> games=new LinkedHashMap<>(data.states());games.put(player.getUUID(),game);
        if(game.finished())next=next.settle(round,game.payout(),now);
        WalletJournal.transfer(player,-stake,"bet",next,games);
        publish(player.getServer(),game);
        if(game.finished())NotificationsBridge.send(player,"notification.realmcasino.result",game.payout());
    }
    private static void playCard(ServerPlayer player,Machine machine,JsonObject payload,boolean hit) {
        CasinoData data=CasinoData.get(player.getServer());GameState game=data.states().get(player.getUUID());
        if(game==null||game.finished()||!game.machine().equals(machine.id())||!game.id().equals(uuid(payload,"round"))||game.revision()!=integer(payload,"revision"))throw failure("stale_round");
        if(now()>=game.deadline()){settleHand(player.getServer(),game,false);throw failure("hand_expired");}
        settleHand(player.getServer(),game,hit);
    }
    private static void settleHand(MinecraftServer server,GameState before,boolean hit) {
        CasinoData data=CasinoData.get(server);data.requireAvailable();
        GameState game=before.update(hit?before.blackjack().hit():before.blackjack().stand());
        Map<UUID,GameState> games=new LinkedHashMap<>(data.states());games.put(game.player(),game);
        CasinoLedger ledger=data.ledger();if(game.finished())ledger=ledger.settle(game.id(),game.payout(),Math.max(now(),game.startedAt()));
        data.replace(ledger,games);publish(server,game);
        ServerPlayer player=server.getPlayerList().getPlayer(game.player());
        if(game.finished()&&player!=null)NotificationsBridge.send(player,"notification.realmcasino.result",game.payout());
    }
    private static void claim(ServerPlayer player,JsonObject payload) {
        requireEconomy(player);CasinoData data=CasinoData.get(player.getServer());long requested=integer(payload,"amount");
        if(requested<0)throw failure("invalid_amount");long pending=data.ledger().pending(player.getUUID());
        long room=ChroniclesBridge.MAX_CENTS-ChroniclesBridge.balance(player);
        long amount=requested==0?Math.min(pending,room):requested;
        if(amount<=0)throw failure(pending==0?"nothing_pending":"wallet_full");
        if(amount>room)throw failure("wallet_full");
        WalletJournal.transfer(player,amount,"claim",data.ledger().claim(player.getUUID(),amount),data.states());
        NotificationsBridge.send(player,"notification.realmcasino.claimed",amount);
    }
    private static void configure(ServerPlayer player,Machine machine,JsonObject payload) {
        CasinoConfig config=CasinoConfig.get();long min=positive(payload,"minBet"),max=positive(payload,"maxBet");
        if(min<config.minBet()||max>config.maxBet()||min>max)throw failure("invalid_limits");
        CasinoData data=CasinoData.get(player.getServer());data.replace(data.ledger().configure(machine.id(),player.getUUID(),string(payload,"name",48),min,max),data.states());
    }
    private static void requireEconomy(ServerPlayer player) {
        if(!ChroniclesBridge.loaded()||!ChroniclesBridge.available(player))throw failure("chronicles_unavailable");
    }
    public static JsonObject snapshot(ServerPlayer player) {
        Machine machine=requireSession(player);CasinoData data=CasinoData.get(player.getServer());Session session=SESSIONS.get(player.getUUID());CasinoConfig config=CasinoConfig.get();
        JsonObject out=new JsonObject();out.addProperty("closed",false);out.addProperty("serverNow",now());out.addProperty("nonce",session.nonce().toString());out.addProperty("version",machine.version());
        out.addProperty("game",game(machine.kind()));out.addProperty("machineId",machine.id().toString());out.addProperty("owner",machine.owner().equals(player.getUUID()));out.addProperty("ownerName",publicName(player.getServer(),machine.owner()));out.addProperty("name",machine.name());
        boolean economy=false;long balance=0;
        try{economy=ChroniclesBridge.loaded()&&ChroniclesBridge.available(player);if(economy)balance=ChroniclesBridge.balance(player);}catch(RuntimeException|LinkageError ignored){}
        out.addProperty("economyAvailable",economy&&!data.frozen()&&data.journal()==null);out.addProperty("balance",balance);
        out.addProperty("bank",machine.bank());out.addProperty("reserved",machine.reserved());out.addProperty("available",machine.available());out.addProperty("pending",data.ledger().pending(player.getUUID()));
        out.addProperty("minBet",machine.minBet());out.addProperty("maxBet",machine.maxBet());out.addProperty("serverMin",config.minBet());out.addProperty("serverMax",config.maxBet());
        out.addProperty("message","");out.addProperty("error",data.frozen()||data.journal()!=null?PREFIX+"ledger_frozen":"");
        boolean busy=data.ledger().rounds().values().stream().anyMatch(round->round.machineId().equals(machine.id())&&!round.player().equals(player.getUUID()));out.addProperty("busy",busy);
        GameState state=data.states().get(player.getUUID());
        if(state!=null&&state.machine().equals(machine.id()))out.add("round",round(state));else out.add("round",JsonNull.INSTANCE);
        JsonArray history=new JsonArray();List<CasinoLedger.History> all=data.ledger().history();
        for(int i=all.size()-1;i>=0&&history.size()<20;i--){var row=all.get(i);if(!row.machineId().equals(machine.id())||(!machine.owner().equals(player.getUUID())&&!row.player().equals(player.getUUID())))continue;JsonObject entry=new JsonObject();entry.addProperty("at",row.settledAt());entry.addProperty("kind",row.payout()>row.stake()?"win":row.payout()==row.stake()?"push":"lose");entry.addProperty("amount",row.payout()-row.stake());entry.addProperty("playerName",publicName(player.getServer(),row.player()));history.add(entry);}
        out.add("history",history);return out;
    }
    private static JsonObject round(GameState game) {
        JsonObject out=new JsonObject();out.addProperty("id",game.id().toString());out.addProperty("game",game(game.kind()));out.addProperty("phase",game.finished()?"settled":"active");out.addProperty("stake",game.stake());out.addProperty("payout",game.payout());out.addProperty("maxPayout",CasinoLedger.maximumPayout(game.kind(),game.stake()));out.addProperty("startedAt",game.startedAt());out.addProperty("deadline",game.deadline());out.addProperty("revision",game.revision());
        out.addProperty("outcome",!game.finished()?"":game.kind()==Kind.BLACKJACK?(game.blackjack().outcome()==Blackjack.Outcome.LOSS?"lose":game.blackjack().outcome().name().toLowerCase(Locale.ROOT)):game.payout()>game.stake()?"win":game.payout()==game.stake()?"push":"lose");
        JsonArray result=new JsonArray(),pc=new JsonArray(),dc=new JsonArray();game.reels().forEach(result::add);if(game.kind()==Kind.ROULETTE)result.add(game.rouletteNumber());
        if(game.blackjack()!=null){game.blackjack().player().forEach(pc::add);dc.add(game.blackjack().dealer().getFirst());if(game.finished())for(int i=1;i<game.blackjack().dealer().size();i++)dc.add(game.blackjack().dealer().get(i));else dc.add(-1);}
        out.add("results",result);out.add("playerCards",pc);out.add("dealerCards",dc);out.addProperty("canHit",!game.finished());out.addProperty("canStand",!game.finished());return out;
    }
    public static void login(ServerPlayer player){try{WalletJournal.recover(player);}catch(RuntimeException|LinkageError ignored){/* A frozen intent remains inspectable; no hidden retry or reset. */}}
    public static void logout(ServerPlayer player) {
        if(player==null||player.getServer()==null)return;
        try{autoStand(player.getServer(),player.getUUID());}catch(RuntimeException|LinkageError ignored){}
        SESSIONS.remove(player.getUUID());
    }
    private static void autoStand(MinecraftServer server,UUID player) {
        GameState game=CasinoData.get(server).states().get(player);if(game!=null&&!game.finished())settleHand(server,game,false);
    }
    public static void tick(MinecraftServer server) {
        TICKS.merge(server,1L,Long::sum);if(ticks(server)%20!=0)return;
        try {
            CasinoData data=CasinoData.get(server);if(data.frozen()||data.journal()!=null)return;
            for(GameState game:new ArrayList<>(data.states().values())) {
                if(game.finished())continue;ServerPlayer player=server.getPlayerList().getPlayer(game.player());boolean end=player==null||now()>=game.deadline();
                if(!end)try{Machine machine=requireSession(player);end=!machine.id().equals(game.machine());}catch(RuntimeException|LinkageError failure){end=true;}
                if(end){settleHand(server,game,false);if(player!=null&&SESSIONS.containsKey(player.getUUID()))try{send(player,"gui.realmcasino.auto_stand","");}catch(RuntimeException|LinkageError ignored){}}
            }
        }catch(RuntimeException|LinkageError ignored){/* IO or invalid registry freezes money instead of resetting it. */}
    }
    public static void stop(MinecraftServer server){SESSIONS.entrySet().removeIf(e->e.getValue().server()==server);TICKS.remove(server);CasinoData.forget(server);}
    private static void publish(MinecraftServer server,GameState game) {
        Machine machine=CasinoData.get(server).ledger().machines().get(game.machine());if(machine==null)return;
        ResourceKey<Level> key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(machine.dimension()));var level=server.getLevel(key);if(level==null)return;
        BlockPos position=BlockPos.of(machine.position());if(!level.hasChunkAt(position))return;
        if(level.getBlockEntity(position)instanceof CasinoBlockEntity entity) {
            int[] result=switch(game.kind()) {
                case SLOTS->game.reels().stream().mapToInt(Integer::intValue).toArray();
                case ROULETTE->new int[]{game.rouletteNumber()};
                case BLACKJACK->game.finished()?game.blackjack().dealer().stream().mapToInt(Integer::intValue).toArray():new int[]{game.blackjack().dealer().getFirst(),-1};
            };
            entity.show(game.id(),game(game.kind()),game.startedAt(),result,game.payout(),!game.finished());
        }
    }
    private static void send(ServerPlayer player,String message,String error){JsonObject out=snapshot(player);out.addProperty("message",message);if(!error.isEmpty())out.addProperty("error",error);CasinoNetwork.send(player,out);}
    private static void closeSnapshot(ServerPlayer player,String error){SESSIONS.remove(player.getUUID());JsonObject out=new JsonObject();out.addProperty("closed",true);out.addProperty("error",error);CasinoNetwork.send(player,out);}
    private static String publicName(MinecraftServer server,UUID id){ServerPlayer player=server.getPlayerList().getPlayer(id);if(player!=null)try{return ChroniclesBridge.publicName(player);}catch(RuntimeException|LinkageError ignored){return player.getGameProfile().getName();}return server.getProfileCache()!=null?server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString().substring(0,8)):id.toString().substring(0,8);}
    private static String blockGame(Level level,BlockPos master){if(level.getBlockState(master).getBlock()instanceof CasinoMachineBlock block)return block.game();throw failure("machine_unavailable");}
    private static String dimension(Level level){return level.dimension().location().toString();}
    private static Kind kind(String game){try{return Kind.valueOf(game.toUpperCase(Locale.ROOT));}catch(RuntimeException failure){throw failure("invalid_request");}}
    private static String game(Kind kind){return kind.name().toLowerCase(Locale.ROOT);}
    private static long now(){return System.currentTimeMillis();}
    private static long ticks(MinecraftServer server){return TICKS.getOrDefault(server,0L);}
    private static void requirePlayer(ServerPlayer player){if(player==null||player.getServer()==null||!player.getServer().isSameThread()||!player.isAlive()||player.isSpectator())throw failure("invalid_request");}
    private static JsonObject parse(String json) {
        if(json==null||json.length()>2048)throw failure("invalid_request");
        try(JsonReader reader=new JsonReader(new StringReader(json))){JsonObject out=new JsonObject();reader.beginObject();while(reader.hasNext()){String name=reader.nextName();if(out.has(name)||name.length()>24||out.size()>=10)throw failure("invalid_request");JsonToken token=reader.peek();if(token==JsonToken.STRING){String value=reader.nextString();if(value.length()>256)throw failure("invalid_request");out.addProperty(name,value);}else if(token==JsonToken.NUMBER){String value=reader.nextString();if(!value.matches("-?(0|[1-9][0-9]{0,18})"))throw failure("invalid_request");out.addProperty(name,Long.parseLong(value));}else throw failure("invalid_request");}reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw failure("invalid_request");return out;}catch(IOException|RuntimeException problem){throw failure("invalid_request");}
    }
    private static void fields(JsonObject payload,Set<String> allowed){if(!allowed.containsAll(payload.keySet()))throw failure("invalid_request");}
    private static long integer(JsonObject payload,String key){if(!payload.has(key)||!payload.get(key).isJsonPrimitive()||!payload.getAsJsonPrimitive(key).isNumber())throw failure("invalid_request");return payload.get(key).getAsLong();}
    private static long positive(JsonObject payload,String key){long value=integer(payload,key);if(value<=0||value>Money.MAX_CENTS)throw failure("invalid_amount");return value;}
    private static String string(JsonObject payload,String key,int max){if(!payload.has(key)||!payload.get(key).isJsonPrimitive()||!payload.getAsJsonPrimitive(key).isString())throw failure("invalid_request");String value=payload.get(key).getAsString();if(value.length()>max||value.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x00a7))throw failure("invalid_request");return value;}
    private static String optionalString(JsonObject payload,String key,int max,String fallback){return payload.has(key)?string(payload,key,max):fallback;}
    private static UUID uuid(JsonObject payload,String key){try{String text=string(payload,key,36);UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw failure("invalid_request");return id;}catch(RuntimeException invalid){throw failure("invalid_request");}}
    private static IllegalStateException failure(String key){return new IllegalStateException(PREFIX+key);}
    private static String error(Throwable failure){String message=failure.getMessage();return message!=null&&message.matches("error\\.realmcasino\\.[a-z_]{1,64}")?message:PREFIX+"invalid_request";}
}
