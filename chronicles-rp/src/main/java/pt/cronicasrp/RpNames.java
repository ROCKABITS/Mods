package pt.cronicasrp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pt.cronicasrp.domain.RpName;
import pt.cronicasrp.network.RpNamePackets;

/** Keeps visual character names separate from the immutable Minecraft account identity. */
public final class RpNames {
    private static final Map<MinecraftServer, Map<UUID, String>> SENT = new WeakHashMap<>();
    // Common types only: the player display-name mixin must also load on a dedicated server.
    // Only RpNamesClient's checked main-thread handlers write this session-scoped client cache.
    private static final Map<UUID, String> CLIENT_NAMES = new HashMap<>();
    private RpNames() {}

    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String name = publicName(player);
            if (!name.isEmpty()) event.setDisplayname(nameComponent(player, name));
        }
    }

    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String name = publicName(player);
            if (!name.isEmpty()) event.setDisplayName(PlayerTeam.formatNameForTeam(player.getTeam(), nameComponent(player, name)));
        }
    }

    /** Explicit metadata on the RP text prevents vanilla's parent hover from showing the username. */
    public static MutableComponent nameComponent(Player player, String name) {
        return withPublicMetadata(player, Component.literal(name), name);
    }

    public static MutableComponent withPublicMetadata(Player player, MutableComponent component, String name) {
        return component.withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,
                        new HoverEvent.EntityTooltipInfo(player.getType(), player.getUUID(), Component.literal(name))))
                .withInsertion(name));
    }

    public static String clientName(UUID playerId) { return CLIENT_NAMES.getOrDefault(playerId, ""); }
    public static void updateClientName(UUID playerId, String name) {
        RpName.checkedUpdate(name);
        if (name.isEmpty()) CLIENT_NAMES.remove(playerId);
        else CLIENT_NAMES.put(playerId, name);
    }
    public static void clearClientNames() { CLIENT_NAMES.clear(); }

    public static String publicName(ServerPlayer player) {
        return RpName.publicName(player.getData(ModAttachments.PROFILE));
    }

    /** Also detects profile changes made by compatible mods; normal wallet updates publish nothing. */
    public static void synchronize(ServerPlayer player) {
        MinecraftServer server = serverThread(player);
        String name = publicName(player);
        Map<UUID, String> sent = SENT.computeIfAbsent(server, ignored -> new HashMap<>());
        String previous = sent.put(player.getUUID(), name);
        if (!name.equals(previous)) {
            refresh(player);
            PacketDistributor.sendToAllPlayers(new RpNamePackets.Update(player.getUUID(), name));
        }
    }

    public static void refresh(ServerPlayer player) {
        serverThread(player);
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    /** Single-entry packets keep even a large player roster below custom-payload size limits. */
    public static void sendRoster(ServerPlayer recipient) {
        MinecraftServer server = serverThread(recipient);
        PacketDistributor.sendToPlayer(recipient, new RpNamePackets.Reset());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = publicName(player);
            if (!name.isEmpty()) PacketDistributor.sendToPlayer(recipient, new RpNamePackets.Update(player.getUUID(), name));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendRoster(player);
            synchronize(player);
            refresh(player);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = serverThread(player);
            Map<UUID, String> sent = SENT.get(server);
            if (sent != null) sent.remove(player.getUUID());
            PacketDistributor.sendToAllPlayers(new RpNamePackets.Update(player.getUUID(), ""));
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { resynchronize(event); }
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) { resynchronize(event); }
    private static void resynchronize(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refresh(player);
            synchronize(player);
            sendRoster(player);
        }
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer recipient && event.getTarget() instanceof ServerPlayer target) {
            refresh(target);
            PacketDistributor.sendToPlayer(recipient, new RpNamePackets.Update(target.getUUID(), publicName(target)));
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                synchronize(player);
                // Explicit TAB names must also follow scoreboard color/prefix/suffix changes.
                refresh(player);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) { SENT.remove(event.getServer()); }

    private static MinecraftServer serverThread(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread())
            throw new IllegalStateException("Public character names require the server thread.");
        return server;
    }
}
