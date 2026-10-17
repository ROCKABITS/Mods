package pt.realmrewards.service;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pt.realmrewards.RealmRewardsMod;
import pt.realmrewards.compat.ChroniclesBridge;
import pt.realmrewards.data.RewardData;
import pt.realmrewards.domain.RewardLedger;
import pt.realmrewards.network.RewardNetwork;

public final class RewardService {
    private RewardService() {}
    public static RewardLedger.Kind kind(AdvancementHolder holder) {
        if (holder.id().getPath().startsWith("recipes/") || holder.value().display().isEmpty()) return RewardLedger.Kind.BACKGROUND;
        return switch (holder.value().display().orElseThrow().getType()) {
            case CHALLENGE -> RewardLedger.Kind.CHALLENGE;
            case GOAL -> RewardLedger.Kind.GOAL;
            default -> RewardLedger.Kind.TASK;
        };
    }
    public static Component title(AdvancementHolder holder) {
        return holder.value().display().map(value->value.getTitle()).orElseGet(()->Component.literal(holder.id().toString()));
    }
    public static void earned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            requireThread(player);
            // Ignore fabricated/incomplete earn events: actual server progress is authoritative.
            AdvancementHolder actual=player.getServer().getAdvancements().get(event.getAdvancement().id());
            if (actual==null || !player.getAdvancements().getOrStartProgress(actual).isDone()) return;
            RewardData data=RewardData.get(player.getServer());
            RewardLedger next=data.ledger.copy();
            if (next.earn(player.getUUID(),actual.id().toString(),kind(actual))) {
                long amount=next.amount(actual.id().toString(),kind(actual));
                data.replace(next);
                long paid=payout(player);
                if (paid>0) player.sendSystemMessage(Component.translatable("message.realmrewards.earned",RewardLedger.euros(paid),title(actual)));
                long remaining=data.ledger.account(player.getUUID()).pending();
                if (amount>0 && remaining>0) player.sendSystemMessage(Component.translatable("message.realmrewards.queued",RewardLedger.euros(remaining)));
            }
        }
    }
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        requireThread(player);
        List<String> completed=new ArrayList<>();
        for (var holder:player.getServer().getAdvancements().getAllAdvancements())
            if (player.getAdvancements().getOrStartProgress(holder).isDone()) completed.add(holder.id().toString());
        RewardData data=RewardData.get(player.getServer());
        RewardLedger next=data.ledger.copy();
        if (next.baseline(player.getUUID(),completed)) data.replace(next);
        // Profile creation may still be pending. The periodic retry handles that case.
        long value=payout(player);
        if (value>0) player.sendSystemMessage(Component.translatable("message.realmrewards.pending_paid",RewardLedger.euros(value)));
    }
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount()%100!=0) return;
        RewardData data=RewardData.get(event.getServer());
        for (ServerPlayer player:event.getServer().getPlayerList().getPlayers()) {
            if (data.ledger.account(player.getUUID()).pending()>0) {
                long value=payout(player);
                if (value>0) player.sendSystemMessage(Component.translatable("message.realmrewards.pending_paid",RewardLedger.euros(value)));
            }
        }
    }
    /** Prepare the ledger before touching the online-only wallet; no offline profile writes. */
    public static long payout(ServerPlayer player) {
        requireThread(player);
        RewardData data=RewardData.get(player.getServer());
        long pending=data.ledger.account(player.getUUID()).pending();
        if (pending==0) return 0;
        try {
            if (!ChroniclesBridge.available(player)) return 0;
            long capacity=ChroniclesBridge.MAX_CENTS-ChroniclesBridge.balance(player);
            long value=Math.min(capacity,pending);
            if (value==0) return 0;
            RewardLedger next=data.ledger.copy(); next.paid(player.getUUID(),value);
            ChroniclesBridge.credit(player,value);
            data.replace(next); return value;
        } catch (RuntimeException | LinkageError failure) {
            // Keep the exact reserved amount, including with an unavailable/full wallet.
            return 0;
        }
    }
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rewards")
                .requires(source->source.hasPermission(2))
                .executes(context->{
                    ServerPlayer player=context.getSource().getPlayerOrException();
                    RewardNetwork.send(player,snapshot(player,new JsonObject(),"")); return 1;
                }));
    }
    public static void requireAdmin(ServerPlayer player) {
        requireThread(player);
        if (!player.hasPermissions(2)) throw new IllegalArgumentException("error.realmrewards.permission");
    }
    public static void configure(ServerPlayer player,String operation,JsonObject input) {
        requireAdmin(player);
        RewardData data=RewardData.get(player.getServer()); RewardLedger next=data.ledger.copy();
        switch (operation) {
            case "save", "reset" -> {
                String id=string(input,"id",512);
                if (player.getServer().getAdvancements().get(ResourceLocation.parse(id))==null) throw new IllegalArgumentException("error.realmrewards.unknown");
                if (operation.equals("reset")) next.reset(id); else next.configure(id,RewardLedger.parse(string(input,"value",20)));
            }
            case "defaults" -> next.defaults(RewardLedger.parse(string(input,"task",20)),RewardLedger.parse(string(input,"goal",20)),RewardLedger.parse(string(input,"challenge",20)));
            default -> throw new IllegalArgumentException("error.realmrewards.request");
        }
        data.replace(next);
    }
    public static void action(ServerPlayer player,String operation,String json) {
        // Fresh authorization for reads as well as writes; a revoked OP cannot retain the menu.
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("error.realmrewards.permission")); return;
        }
        JsonObject input=new JsonObject(); String error="";
        try {
            requireAdmin(player);
            input=JsonParser.parseString(json).getAsJsonObject();
            if (!operation.equals("list")) configure(player,operation,input);
        } catch (RuntimeException failure) { error="error.realmrewards.request"; }
        try { RewardNetwork.send(player,snapshot(player,input,error)); }
        catch (RuntimeException failure) { player.sendSystemMessage(Component.translatable("error.realmrewards.request")); }
    }
    public static JsonObject snapshot(ServerPlayer player,JsonObject input,String error) {
        requireAdmin(player);
        String search=input.has("search")?string(input,"search",128):"";
        int size=input.has("size")?integer(input,"size",1,12):8;
        int page=input.has("page")?integer(input,"page",0,100000):0;
        String needle=search.toLowerCase(Locale.ROOT);
        List<AdvancementHolder> holders=player.getServer().getAdvancements().getAllAdvancements().stream()
                .filter(holder->holder.id().toString().toLowerCase(Locale.ROOT).contains(needle)
                        || title(holder).getString().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(holder->holder.id().toString())).toList();
        RewardLedger ledger=RewardData.get(player.getServer()).ledger;
        JsonObject root=new JsonObject();
        page=Math.min(page,Math.max(0,(holders.size()-1)/size));
        root.addProperty("search",search); root.addProperty("page",page); root.addProperty("size",size); root.addProperty("total",holders.size()); root.addProperty("error",error);
        root.addProperty("chronicles",ChroniclesBridge.loaded());
        for (var kind:List.of(RewardLedger.Kind.TASK,RewardLedger.Kind.GOAL,RewardLedger.Kind.CHALLENGE)) root.addProperty(kind.name().toLowerCase(Locale.ROOT),RewardLedger.euros(ledger.defaults().get(kind)));
        JsonArray rows=new JsonArray();
        for (int i=page*size;i<Math.min(holders.size(),(page+1)*size);i++) {
            var holder=holders.get(i); JsonObject row=new JsonObject();
            row.addProperty("id",holder.id().toString()); row.addProperty("kind",kind(holder).name());
            JsonElement label=ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE,title(holder)).getOrThrow();
            row.add("title",label.toString().length()>4096?new JsonPrimitive(holder.id().toString()):label);
            row.addProperty("amount",RewardLedger.euros(ledger.amount(holder.id().toString(),kind(holder))));
            row.addProperty("override",ledger.overrides().containsKey(holder.id().toString())); rows.add(row);
        }
        root.add("rows",rows); return root;
    }
    private static String string(JsonObject input,String key,int max) {
        var field=input.get(key);
        if (field==null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString() || field.getAsString().length()>max) throw new IllegalArgumentException("error.realmrewards.request");
        return field.getAsString();
    }
    private static int integer(JsonObject input,String key,int min,int max) {
        var field=input.get(key);
        if (field==null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("error.realmrewards.request");
        int value=field.getAsBigDecimal().intValueExact();
        if (value<min || value>max) throw new IllegalArgumentException("error.realmrewards.request"); return value;
    }
    private static void requireThread(ServerPlayer player) {
        if (player.getServer()==null || !player.getServer().isSameThread()) throw new IllegalStateException("Rewards require the server thread");
    }
}
