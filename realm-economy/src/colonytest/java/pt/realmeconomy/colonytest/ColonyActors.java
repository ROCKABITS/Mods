package pt.realmeconomy.colonytest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import pt.realmeconomy.compat.ChroniclesBridge;

/** Real server players, real Chronicles attachments, and production-packet capture; test sources only. */
final class ColonyActors implements AutoCloseable {
    final GameTestHelper helper;
    final List<Actor> actors = new ArrayList<>();
    final List<ServerPlayer> online;
    final Map<UUID, ServerPlayer> byId;

    @SuppressWarnings("unchecked")
    ColonyActors(GameTestHelper helper) {
        this.helper = helper;
        try {
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field ids = PlayerList.class.getDeclaredField("playersByUUID"); ids.setAccessible(true);
            online = (List<ServerPlayer>) players.get(helper.getLevel().getServer().getPlayerList());
            byId = (Map<UUID, ServerPlayer>) ids.get(helper.getLevel().getServer().getPlayerList());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    Actor actor(String name, long balance) {
        Actor actor = new Actor(helper, name, true);
        actors.add(actor); online.add(actor); byId.put(actor.getUUID(), actor);
        if (ChroniclesBridge.loaded()) profile(actor, true, balance);
        return actor;
    }

    static final class Actor extends ServerPlayer {
        final List<Packet<?>> packets = new ArrayList<>();
        final List<Component> chat = new ArrayList<>();
        Runnable onNotice;int saves,failOnSave;Runnable savedHook;
        @Override public net.minecraft.nbt.CompoundTag saveWithoutId(net.minecraft.nbt.CompoundTag tag){saves++;if(saves==failOnSave)throw new IllegalStateException("Injected native player save failure");var out=super.saveWithoutId(tag);if(savedHook!=null)savedHook.run();return out;}
        Actor(GameTestHelper helper, String name, boolean notices) {
            super(helper.getLevel().getServer(),helper.getLevel(), new GameProfile(UUID.randomUUID(), name), net.minecraft.server.level.ClientInformation.createDefault());
            setGameMode(GameType.SURVIVAL);
            connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                    this, CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public boolean isAcceptingMessages() { return true; }
                @Override public boolean hasChannel(ResourceLocation id) {
                    return id.getNamespace().equals("realmeconomy") || notices && id.getNamespace().equals("realmnotifications");
                }
                @Override public void send(Packet<?> packet) {
                    packets.add(packet);
                    if (packet instanceof ClientboundCustomPayloadPacket payload
                            && payload.payload().type().id().getNamespace().equals("realmnotifications") && onNotice != null) onNotice.run();
                }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
            };
        }
        private int nextMenu=10;
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer){containerMenu=provider.createMenu(nextMenu++,getInventory(),this);return java.util.OptionalInt.of(containerMenu.containerId);}
        @Override public void closeContainer(){if(containerMenu!=inventoryMenu)containerMenu.removed(this);containerMenu=inventoryMenu;}
        @Override public void displayClientMessage(Component message, boolean overlay) { chat(message); }
        @Override public void sendSystemMessage(Component message) { chat(message); }
        @Override public void sendSystemMessage(Component message, boolean overlay) { chat(message); }
        private void chat(Component message) { if (chat != null) { chat.add(message); if (onNotice != null) onNotice.run(); } }
        void clear() { packets.clear(); chat.clear(); }
        long notificationPackets() {
            return packets.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                    .map(ClientboundCustomPayloadPacket.class::cast)
                    .filter(packet -> packet.payload().type().id().getNamespace().equals("realmnotifications")).count();
        }
        JsonObject snapshot() {
            for (Packet<?> packet : packets.reversed()) if (packet instanceof ClientboundCustomPayloadPacket wrapped
                    && wrapped.payload().type().id().getNamespace().equals("realmeconomy")) {
                try { return JsonParser.parseString((String) wrapped.payload().getClass().getMethod("json").invoke(wrapped.payload())).getAsJsonObject(); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
            throw new AssertionError("No production Realm Economy snapshot was sent");
        }
    }

    @SuppressWarnings("unchecked") static AttachmentType<Object> attachment() {
        try { return (AttachmentType<Object>) ((Supplier<?>) Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get(); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    static void profile(Actor player, boolean created, long balance) {
        try {
            Class<?> type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object value = type.getConstructor(boolean.class, String.class, int.class, String.class, String.class,
                    String.class, String.class, long.class, long.class, String.class)
                    .newInstance(created, "Public " + player.getGameProfile().getName(), 28, "origins:human",
                            "PRIVATE CASINO BIOGRAPHY", "PRIVATE SIGNATURE", "Private origin", 100_000L, balance, "");
            player.setData(attachment(), value);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Actual Chronicles profile is incompatible", failure); }
    }
    static Object profile(Actor actor) { return actor.getData(attachment()); }
    static void save(Actor actor) {
        try {
            var method = PlayerList.class.getDeclaredMethod("save", ServerPlayer.class); method.setAccessible(true);
            method.invoke(actor.server.getPlayerList(), actor);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not save the actual native player file", failure); }
    }
    static void stableProfile(GameTestHelper helper, Object before, Actor actor) {
        Object after = profile(actor);
        try {
            for (var field : before.getClass().getRecordComponents()) if (!field.getName().equals("balance"))
                helper.assertTrue(Objects.equals(field.getAccessor().invoke(before), field.getAccessor().invoke(after)),
                        "Casino wallet mutation changed Chronicles field " + field.getName());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    @Override public void close() {
        for (Actor actor : actors) {
            actor.onNotice = null; online.remove(actor); byId.remove(actor.getUUID(), actor);
            helper.getLevel().getServer().getPlayerList().getOps().remove(actor.getGameProfile());
            try {
                var root = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
                Files.deleteIfExists(root.resolve(actor.getUUID() + ".dat"));
                Files.deleteIfExists(root.resolve(actor.getUUID() + ".dat_old"));
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
    }
}
