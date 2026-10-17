package pt.realmtitles.service;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import pt.realmtitles.api.TitlesApi;
import pt.realmtitles.block.BlockRegistry;
import pt.realmtitles.block.SealDeskBlock;
import pt.realmtitles.compat.ChroniclesBridge;
import pt.realmtitles.compat.CouncilBridge;
import pt.realmtitles.compat.NotificationsBridge;
import pt.realmtitles.data.TitleData;
import pt.realmtitles.domain.TitleLedger;
import pt.realmtitles.domain.TitleLedger.Grant;
import pt.realmtitles.network.TitleNetwork;

/** All client requests pass through a live, physical desk session and current authority. */
public final class TitleService {
    public static final long SESSION_MILLIS = 300_000;
    public static final long SESSION_TICKS = 6_000;
    public static final int PAGE_SIZE = 8;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<MinecraftServer, Long> LAST_PURGE = new HashMap<>();
    private static final Set<String> VIEW_KEYS = Set.of("selectedPlayer", "dSearch", "pSearch", "dPage", "pPage");
    private static final Set<String> OPERATIONS = Set.of("list", "create", "edit", "delete", "grant", "revoke", "close");
    private record View(UUID selected, String dSearch, String pSearch, int dPage, int pPage) {}
    private record Session(MinecraftServer server, ResourceLocation dimension, BlockPos pos,
                           long openedAt, long openedTick, View view) {
        Session withView(View replacement) { return new Session(server, dimension, pos, openedAt, openedTick, replacement); }
    }
    private record Manager(UUID id, String name) {}
    private TitleService() {}

    public static void openStation(ServerPlayer player, BlockPos pos) {
        requirePlayer(player);
        try {
            BlockPos master = SealDeskBlock.master(player.level(), pos);
            if (master == null) throw error("station_required");
            long now = System.currentTimeMillis();
            Session session = new Session(player.getServer(), player.level().dimension().location(), master.immutable(),
                    now, ticks(player.getServer()), new View(player.getUUID(), "", "", 0, 0));
            validate(player, session);
            SESSIONS.put(player.getUUID(), session);
            observe(player);
            refreshDirectory(player.getServer());
            send(player, "", "");
        } catch (RuntimeException failure) { reject(player, key(failure)); }
    }

    public static void requireStationSession(ServerPlayer player) { requireSession(player); }

    /** Main-thread public directory observation never reads or writes offline character files. */
    public static void observe(ServerPlayer player) {
        requirePlayer(player);
        TitleData data = TitleData.get(player.getServer());
        var profile = ChroniclesBridge.read(player);
        TitleLedger replacement = data.ledger().observeCitizen(player.getUUID(), profile.name(), profile.eligible());
        if (replacement != data.ledger()) data.replace(replacement);
    }

    public static void appointSteward(ServerPlayer player) {
        requirePlayer(player);
        if (CouncilBridge.loaded()) throw error("council_present");
        observe(player);
        TitleData data = TitleData.get(player.getServer());
        data.steward(player.getUUID());
        player.getServer().overworld().getDataStorage().save();
    }

    public static void action(ServerPlayer player, String operation, String json) {
        requirePlayer(player);
        try {
            Session session = requireSession(player);
            if (operation == null || operation.length() > 16 || !OPERATIONS.contains(operation)) throw error("invalid_request");
            JsonObject input = parse(json, operation);
            refreshDirectory(player.getServer());
            TitleData data = TitleData.get(player.getServer());
            View view = view(input, session.view(), data.ledger());
            SESSIONS.put(player.getUUID(), session.withView(view));
            if (operation.equals("close")) { logout(player.getUUID()); TitleNetwork.send(player, closed("")); return; }
            if (operation.equals("list")) { send(player, "", ""); return; }
            long now = System.currentTimeMillis();
            Manager manager = manager(player.getServer(), data, now);
            if (manager == null || !player.getUUID().equals(manager.id())) throw error("permission");
            // Expiry is committed independently, so later invalid mutations cannot hide expired rows or notifications.
            purge(player.getServer(), now);
            TitleLedger ledger = data.ledger();
            TitleLedger replacement;
            Grant notified = null;
            String notice = "";
            String message;
            switch (operation) {
                case "create" -> {
                    replacement = ledger.createDefinition(UUID.randomUUID(), text(input, "name", 48), text(input, "description", 256));
                    message = "saved";
                }
                case "edit" -> {
                    UUID id = definitionId(input);
                    replacement = ledger.editDefinition(id, text(input, "name", 48), text(input, "description", 256));
                    message = "saved";
                }
                case "delete" -> {
                    replacement = ledger.deleteDefinition(uuid(input, "id"), now);
                    message = "deleted";
                }
                case "grant" -> {
                    UUID owner = uuid(input, "selectedPlayer");
                    UUID title = uuid(input, "titleId");
                    UUID grantId = UUID.randomUUID();
                    replacement = ledger.grant(owner, title, integer(input, "days", 0, 365), now, grantId);
                    notified = replacement.grants().get(grantId); notice = "granted"; message = "granted";
                }
                case "revoke" -> {
                    UUID id = uuid(input, "id");
                    notified = ledger.grants().get(id);
                    replacement = ledger.revoke(id, now);
                    notice = "revoked"; message = "revoked";
                }
                default -> throw error("invalid_request");
            }
            // Validate authority and physical state again immediately before the only user mutation commit.
            requireSession(player);
            Manager current = manager(player.getServer(), data, System.currentTimeMillis());
            if (current == null || !player.getUUID().equals(current.id())) throw error("permission");
            data.replace(replacement);
            player.getServer().overworld().getDataStorage().save();
            if (notified != null && !notified.owner().equals(player.getUUID())) {
                var target = player.getServer().getPlayerList().getPlayer(notified.owner());
                var title = ledger.definitions().get(notified.titleId());
                if (target != null && title != null) NotificationsBridge.send(target, "notification.realmtitles." + notice, title.name());
            }
            send(player, "", "gui.realmtitles." + message);
        } catch (RuntimeException failure) { reject(player, key(failure)); }
    }

    /** Bounded, paged public state; authority and physical conditions are never cached in the client. */
    public static JsonObject snapshot(ServerPlayer player) {
        Session session = requireSession(player);
        refreshDirectory(player.getServer());
        TitleData data = TitleData.get(player.getServer());
        TitleLedger ledger = data.ledger();
        long now = System.currentTimeMillis();
        View view = session.view();
        Manager manager = null;
        String unavailable = "";
        try { manager = manager(player.getServer(), data, now); }
        catch (RuntimeException failure) { unavailable = "gui.realmtitles.governor_unavailable"; }
        JsonObject result = new JsonObject();
        result.addProperty("closed", false); result.addProperty("serverNow", now);
        result.addProperty("canManage", manager != null && player.getUUID().equals(manager.id()));
        result.addProperty("managerName", manager == null ? "" : manager.name());
        result.addProperty("error", unavailable); result.addProperty("message", "");
        result.addProperty("selectedPlayer", view.selected().toString());
        var selected = ledger.citizens().get(view.selected());
        result.addProperty("playerName", selected == null ? "" : selected.name());
        result.addProperty("dSearch", view.dSearch()); result.addProperty("pSearch", view.pSearch());
        String dSearch = view.dSearch().toLowerCase(Locale.ROOT);
        String pSearch = view.pSearch().toLowerCase(Locale.ROOT);
        var definitions = ledger.definitions().values().stream().filter(row -> row.name().toLowerCase(Locale.ROOT).contains(dSearch))
                .sorted(Comparator.comparing(TitleLedger.Definition::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TitleLedger.Definition::id)).toList();
        var citizens = ledger.citizens().values().stream().filter(row -> row.name().toLowerCase(Locale.ROOT).contains(pSearch)
                        || row.id().toString().contains(pSearch))
                .sorted(Comparator.comparing(TitleLedger.Citizen::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TitleLedger.Citizen::id)).toList();
        int dPage = boundedPage(view.dPage(), definitions.size());
        int pPage = boundedPage(view.pPage(), citizens.size());
        result.addProperty("dPage", dPage); result.addProperty("pPage", pPage);
        result.addProperty("definitionTotal", definitions.size()); result.addProperty("citizenTotal", citizens.size());
        JsonArray definitionRows = new JsonArray();
        for (var definition : page(definitions, dPage)) {
            JsonObject row = new JsonObject(); row.addProperty("id", definition.id().toString());
            row.addProperty("name", definition.name()); row.addProperty("description", definition.description()); definitionRows.add(row);
        }
        Set<UUID> online = new HashSet<>();
        player.getServer().getPlayerList().getPlayers().forEach(current -> online.add(current.getUUID()));
        JsonArray citizenRows = new JsonArray();
        for (var citizen : page(citizens, pPage)) {
            JsonObject row = new JsonObject(); row.addProperty("id", citizen.id().toString()); row.addProperty("name", citizen.name());
            row.addProperty("eligible", citizen.eligible()); row.addProperty("online", online.contains(citizen.id())); citizenRows.add(row);
        }
        result.add("definitions", definitionRows); result.add("citizens", citizenRows);
        result.add("grants", TitlesApi.activeTitles(ledger, view.selected(), now));
        return result;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !server.isSameThread() || server.overworld() == null) return;
        long now = System.currentTimeMillis();
        long previous = LAST_PURGE.getOrDefault(server, 0L);
        if (now >= previous && now - previous < 60_000) return;
        LAST_PURGE.put(server, now);
        try { purge(server, now); } catch (RuntimeException ignored) { /* Reads and writes still fail closed on corrupt storage. */ }
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().server() == server
                && (now < entry.getValue().openedAt() || now - entry.getValue().openedAt() >= SESSION_MILLIS));
    }

    public static void logout(UUID player) { SESSIONS.remove(player); }
    public static void stop(MinecraftServer server) {
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().server() == server);
        LAST_PURGE.remove(server);
    }

    private static void refreshDirectory(MinecraftServer server) {
        TitleData data = TitleData.get(server);
        TitleLedger replacement = data.ledger();
        for (var player : server.getPlayerList().getPlayers()) {
            var profile = ChroniclesBridge.read(player);
            replacement = replacement.observeCitizen(player.getUUID(), profile.name(), profile.eligible());
        }
        if (replacement != data.ledger()) data.replace(replacement);
    }
    private static Manager manager(MinecraftServer server, TitleData data, long now) {
        if (CouncilBridge.loaded()) {
            var governor = CouncilBridge.governor(server, now);
            return governor == null ? null : new Manager(governor.id(), governor.name());
        }
        UUID id = data.steward();
        if (id == null) return null;
        var citizen = data.ledger().citizens().get(id);
        return new Manager(id, citizen == null ? id.toString() : citizen.name());
    }
    private static void purge(MinecraftServer server, long now) {
        TitleData data = TitleData.get(server);
        TitleLedger ledger = data.ledger();
        TitleLedger replacement = ledger.purgeExpired(now);
        if (replacement == ledger) return;
        List<Grant> expired = ledger.grants().values().stream().filter(row -> !row.activeAt(now)).toList();
        data.replace(replacement);
        server.overworld().getDataStorage().save();
        for (Grant grant : expired) {
            var target = server.getPlayerList().getPlayer(grant.owner());
            var definition = ledger.definitions().get(grant.titleId());
            if (target != null && definition != null) NotificationsBridge.send(target, "notification.realmtitles.expired", definition.name());
        }
    }
    private static Session requireSession(ServerPlayer player) {
        requirePlayer(player);
        Session session = SESSIONS.get(player.getUUID()); validate(player, session); return session;
    }
    private static void validate(ServerPlayer player, Session session) {
        long now = System.currentTimeMillis();
        long tick = ticks(player.getServer());
        if (session == null || session.server() != player.getServer() || !player.isAlive() || player.isRemoved()
                || now < session.openedAt() || now - session.openedAt() >= SESSION_MILLIS
                || tick < session.openedTick() || tick - session.openedTick() >= SESSION_TICKS
                || !player.level().dimension().location().equals(session.dimension())
                || player.distanceToSqr(session.pos().getX() + .5, session.pos().getY() + .5, session.pos().getZ() + .5) > 64
                || !player.level().hasChunkAt(session.pos()) || !player.level().hasChunkAt(session.pos().above())) throw error("station_required");
        var lower = player.level().getBlockState(session.pos());
        var upper = player.level().getBlockState(session.pos().above());
        if (!lower.is(BlockRegistry.SEAL_DESK.get()) || !upper.is(BlockRegistry.SEAL_DESK.get())
                || lower.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER
                || upper.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER
                || lower.getValue(BlockStateProperties.HORIZONTAL_FACING) != upper.getValue(BlockStateProperties.HORIZONTAL_FACING)
                || !BlockRegistry.SEAL_DESK.get().validStructure(player.level(), session.pos())) throw error("station_required");
    }
    private static void requirePlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) throw error("invalid_request");
    }
    private static long ticks(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static View view(JsonObject input, View old, TitleLedger ledger) {
        UUID selected = input.has("selectedPlayer") ? uuid(input, "selectedPlayer") : old.selected();
        if (!ledger.citizens().containsKey(selected)) throw error("unknown_player");
        String dSearch = input.has("dSearch") ? search(input, "dSearch") : old.dSearch();
        String pSearch = input.has("pSearch") ? search(input, "pSearch") : old.pSearch();
        int dPage = input.has("dPage") ? integer(input, "dPage", 0, 100_000) : old.dPage();
        int pPage = input.has("pPage") ? integer(input, "pPage", 0, 100_000) : old.pPage();
        return new View(selected, dSearch, pSearch, dPage, pPage);
    }
    private static String search(JsonObject input, String key) {
        String value = text(input, key, 64);
        if (value.codePoints().anyMatch(point -> Character.isISOControl(point) || Character.getType(point) == Character.FORMAT)) throw error("invalid_request");
        return value;
    }
    private static int boundedPage(int requested, int size) { return Math.min(requested, Math.max(0, (size - 1) / PAGE_SIZE)); }
    private static <T> List<T> page(List<T> rows, int page) { int first = page * PAGE_SIZE; return rows.subList(first, Math.min(rows.size(), first + PAGE_SIZE)); }
    private static UUID definitionId(JsonObject input) {
        UUID id = input.has("id") ? uuid(input, "id") : uuid(input, "titleUUID");
        if (input.has("titleUUID") && !id.equals(uuid(input, "titleUUID"))) throw error("invalid_request");
        return id;
    }
    private static String text(JsonObject input, String key, int limit) {
        if (!input.has(key) || !input.get(key).isJsonPrimitive() || !input.getAsJsonPrimitive(key).isString()) throw error("invalid_request");
        String value = input.get(key).getAsString();
        if (value.length() > limit) throw error("invalid_request"); return value;
    }
    private static UUID uuid(JsonObject input, String key) {
        String value = text(input, key, 36);
        try { UUID id = UUID.fromString(value); if (!id.toString().equals(value)) throw error("invalid_request"); return id; }
        catch (IllegalArgumentException failure) { throw error("invalid_request"); }
    }
    private static int integer(JsonObject input, String key, int min, int max) {
        if (!input.has(key) || !input.get(key).isJsonPrimitive() || !input.getAsJsonPrimitive(key).isNumber()) throw error("invalid_request");
        try { int value = input.get(key).getAsBigDecimal().intValueExact(); if (value < min || value > max) throw error("invalid_request"); return value; }
        catch (ArithmeticException | NumberFormatException failure) { throw error("invalid_request"); }
    }
    private static JsonObject parse(String json, String operation) {
        if (json == null || json.length() > 2048) throw error("invalid_request");
        Set<String> allowed = new HashSet<>(VIEW_KEYS);
        switch (operation) {
            case "create" -> allowed.addAll(Set.of("name", "description"));
            case "edit" -> allowed.addAll(Set.of("id", "titleUUID", "name", "description"));
            case "delete", "revoke" -> allowed.add("id");
            case "grant" -> allowed.addAll(Set.of("titleId", "days"));
            default -> { }
        }
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false); reader.beginObject(); JsonObject result = new JsonObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!allowed.contains(name) || result.has(name)) throw error("invalid_request");
                JsonToken token = reader.peek();
                if (token == JsonToken.STRING) result.addProperty(name, reader.nextString());
                else if (token == JsonToken.NUMBER) {
                    String number = reader.nextString();
                    if (!number.matches("-?(0|[1-9][0-9]{0,9})")) throw error("invalid_request");
                    result.addProperty(name, Long.parseLong(number));
                } else throw error("invalid_request");
            }
            reader.endObject(); if (reader.peek() != JsonToken.END_DOCUMENT) throw error("invalid_request"); return result;
        } catch (IOException | IllegalStateException | NumberFormatException failure) { throw error("invalid_request"); }
    }
    private static void send(ServerPlayer player, String error, String message) {
        JsonObject snapshot = snapshot(player);
        if (!error.isEmpty()) snapshot.addProperty("error", error);
        snapshot.addProperty("message", message); TitleNetwork.send(player, snapshot);
    }
    private static void reject(ServerPlayer player, String error) {
        try { send(player, error, ""); }
        catch (RuntimeException failure) { logout(player.getUUID()); TitleNetwork.send(player, closed(error)); }
    }
    private static JsonObject closed(String error) {
        JsonObject result = new JsonObject(); result.addProperty("closed", true); result.addProperty("canManage", false);
        result.addProperty("serverNow", System.currentTimeMillis()); result.addProperty("error", error); result.addProperty("message", "");
        result.addProperty("managerName", ""); result.addProperty("selectedPlayer", ""); result.addProperty("playerName", "");
        result.addProperty("dSearch", ""); result.addProperty("pSearch", ""); result.addProperty("dPage", 0); result.addProperty("pPage", 0);
        result.addProperty("definitionTotal", 0); result.addProperty("citizenTotal", 0);
        result.add("definitions", new JsonArray()); result.add("citizens", new JsonArray()); result.add("grants", new JsonArray()); return result;
    }
    private static String key(Throwable error) {
        String message = error.getMessage();
        return message != null && message.startsWith("gui.realmtitles.") ? message : "gui.realmtitles.registry_unavailable";
    }
    private static IllegalStateException error(String code) { return new IllegalStateException("gui.realmtitles." + code); }
}
