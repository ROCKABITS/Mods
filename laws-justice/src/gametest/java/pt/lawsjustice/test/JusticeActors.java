package pt.lawsjustice.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Actual native player attachments and production S2C packets, never runtime API substitutes. */
final class JusticeActors implements AutoCloseable {
    final GameTestHelper helper;
    final List<Actor> actors = new ArrayList<>();
    final List<ServerPlayer> online;
    final Map<UUID, ServerPlayer> byId;

    @SuppressWarnings("unchecked") JusticeActors(GameTestHelper helper) {
        this.helper = helper;
        try {
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field ids = PlayerList.class.getDeclaredField("playersByUUID"); ids.setAccessible(true);
            online = (List<ServerPlayer>) players.get(helper.getLevel().getServer().getPlayerList());
            byId = (Map<UUID, ServerPlayer>) ids.get(helper.getLevel().getServer().getPlayerList());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    Actor actor(String name, boolean admin) {
        Actor actor = new Actor(helper, name); actors.add(actor); reconnect(actor);
        if (admin) actor.server.getPlayerList().getOps().add(new ServerOpListEntry(actor.getGameProfile(), 2, false));
        if (ModList.get().isLoaded("cronicasrp")) profile(actor, "RP " + name);
        return actor;
    }
    Actor respawn(Actor previous) {
        disconnect(previous);
        Actor actor = new Actor(helper, previous.getGameProfile()); actors.add(actor); reconnect(actor); return actor;
    }
    void disconnect(Actor actor) { online.remove(actor); byId.remove(actor.getUUID()); }
    void reconnect(Actor actor) { if (!online.contains(actor)) online.add(actor); byId.put(actor.getUUID(), actor); }

    static final class Actor extends FakePlayer {
        final List<Packet<?>> packets = new ArrayList<>();
        final List<Component> chat = new ArrayList<>();
        Actor(GameTestHelper helper, String name) {
            this(helper, new GameProfile(UUID.randomUUID(), name));
        }
        Actor(GameTestHelper helper, GameProfile profile) {
            super(helper.getLevel(), profile); setGameMode(GameType.SURVIVAL);
            connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                    this, CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public boolean isAcceptingMessages() { return true; }
                @Override public boolean hasChannel(ResourceLocation id) {
                    return id.getNamespace().equals("lawsjustice") || id.getNamespace().equals("realmnotifications");
                }
                @Override public void send(Packet<?> packet) { packets.add(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
            };
        }
        @Override public void displayClientMessage(Component message, boolean overlay) { recordChat(message); }
        @Override public void sendSystemMessage(Component message) { recordChat(message); }
        @Override public void sendSystemMessage(Component message, boolean overlay) { recordChat(message); }
        private void recordChat(Component message) { if (chat != null) chat.add(message); }
        void clear() { packets.clear(); chat.clear(); }
        long notices() {
            return packets.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                    .map(ClientboundCustomPayloadPacket.class::cast)
                    .filter(packet -> packet.payload().type().id().getNamespace().equals("realmnotifications")).count();
        }
        JsonObject packetSnapshot() {
            for (Packet<?> packet : packets.reversed()) if (packet instanceof ClientboundCustomPayloadPacket wrapped
                    && wrapped.payload().type().id().getNamespace().equals("lawsjustice")) {
                try { return JsonParser.parseString((String) wrapped.payload().getClass().getMethod("json").invoke(wrapped.payload())).getAsJsonObject(); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
            throw new AssertionError("No production Laws & Justice snapshot was sent");
        }
    }

    @SuppressWarnings("unchecked") static void profile(Actor actor, String rpName) {
        try {
            var attachment = (AttachmentType<Object>) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();
            var type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object value = type.getConstructor(boolean.class, String.class, int.class, String.class, String.class,
                    String.class, String.class, long.class, long.class, String.class)
                    .newInstance(true, rpName, 28, "origins:human", "PRIVATE JUSTICE BIOGRAPHY", "PRIVATE JUSTICE SIGNATURE", "Private homeland", 100_000L, 50_000L, "");
            actor.setData(attachment, value);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("The real Chronicles profile API is incompatible", failure); }
    }

    @Override public void close() {
        for (Actor actor : actors) {
            disconnect(actor); actor.discard(); actor.server.getPlayerList().getOps().remove(actor.getGameProfile());
        }
    }
}
