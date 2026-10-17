package pt.realmnotifications.test;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.realmnotifications.RealmNotificationsMod;
import pt.realmnotifications.api.NotificationsApi;
import pt.realmnotifications.network.NotificationPackets.Show;

/** Native server routing and real payload codec, with explicitly simulated channel support. */
@GameTestHolder("realmnotifications")
@PrefixGameTestTemplate(false)
public final class NotificationsGameTests {
    public static final class Actor extends FakePlayer {
        private final List<Packet<?>> sent = new ArrayList<>();
        private final List<Component> chat = new ArrayList<>();
        private final PlayerAdvancements actual;
        public boolean supports;
        public boolean accepting = true;
        public boolean failSend;
        public boolean failChannel;

        public Actor(GameTestHelper helper, boolean supports) {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "NoticeQA"));
            this.supports = supports;
            var server = helper.getLevel().getServer();
            actual = new PlayerAdvancements(server.getFixerUpper(), server.getPlayerList(), server.getAdvancements(),
                    server.getWorldPath(LevelResource.ROOT).resolve("notification-qa/" + getUUID() + ".json"), this);
            connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), this,
                    CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public boolean isAcceptingMessages() { return accepting; }
                @Override public boolean hasChannel(ResourceLocation id) {
                    if (failChannel) throw new IllegalStateException("Intentional QA unavailable channel");
                    return Actor.this.supports && Show.TYPE.id().equals(id);
                }
                @Override public void send(Packet<?> packet) {
                    if (failSend) throw new IllegalStateException("Intentional QA disconnected send");
                    sent.add(packet);
                }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
            };
        }
        @Override public PlayerAdvancements getAdvancements() { return actual == null ? super.getAdvancements() : actual; }
        @Override public void sendSystemMessage(Component message) { if (chat != null) chat.add(message); }
        @Override public void sendSystemMessage(Component message, boolean overlay) { sendSystemMessage(message); }
        public List<Show> shows() {
            return sent.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                    .map(ClientboundCustomPayloadPacket.class::cast).map(ClientboundCustomPayloadPacket::payload)
                    .filter(Show.class::isInstance).map(Show.class::cast).toList();
        }
        public List<Component> chats() { return List.copyOf(chat); }
        public void reset() { sent.clear(); chat.clear(); }
    }
    private static void rejected(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected, "Malformed notification was accepted");
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void actualCompanionsMatchTheRequestedRuntime(GameTestHelper helper) {
        boolean integration = Boolean.getBoolean("notifications.integration");
        helper.assertTrue(ModList.get().isLoaded("cronicasrp") == integration, "Unexpected Chronicles runtime mode");
        if (integration) for (String id : List.of("contracts", "realmcouncil", "kingdomtreasury", "realmmarkets", "realmrewards", "realmidentity", "origins", "jupiter"))
            helper.assertTrue(ModList.get().isLoaded(id), "Real integration dependency missing: " + id);
        RealmNotificationsMod.LOGGER.info("NOTIFICATIONS_RUNTIME_MODE_PASSED integration={}", integration);
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void supportedChannelSendsOneRealPayloadAndNoChat(GameTestHelper helper) {
        Actor player = new Actor(helper, true);
        helper.assertTrue(NotificationsApi.send(player, "money", Component.literal("+50 € por concluir uma conquista")), "Negotiated channel was refused");
        helper.assertTrue(player.shows().size() == 1 && player.chats().isEmpty(), "Notification duplicated into chat or sent twice");
        Show payload = player.shows().getFirst();
        helper.assertTrue(payload.kind().equals("money") && payload.body().getString().contains("50 €"), "Real payload did not preserve its message");
        RealmNotificationsMod.LOGGER.info("NOTIFICATIONS_NATIVE_PASSED");
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void absentOrDisconnectedClientCannotSuppressChatFallback(GameTestHelper helper) {
        Actor player = new Actor(helper, false);
        helper.assertTrue(!NotificationsApi.send(player, "money", Component.literal("+1 €")), "Client without optional channel was accepted");
        player.supports = true; player.accepting = false;
        helper.assertTrue(!NotificationsApi.send(player, "money", Component.literal("+1 €")), "Disconnected client was accepted");
        helper.assertTrue(player.shows().isEmpty(), "Unsupported client received a custom payload");
        helper.assertTrue(!NotificationsApi.send(null, "info", Component.literal("ignored")), "Null target was accepted");
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void optionalPresentationFailuresNeverEscapeIntoTransactions(GameTestHelper helper) {
        Actor player = new Actor(helper, true);
        player.failSend = true;
        helper.assertTrue(!NotificationsApi.send(player, "money", Component.literal("+1 €")), "Failed send incorrectly reported success");
        player.failSend = false; player.failChannel = true;
        helper.assertTrue(!NotificationsApi.send(player, "money", Component.literal("+1 €")), "Failed channel lookup incorrectly reported success");
        player.failChannel = false;
        helper.assertTrue(!NotificationsApi.send(player, "money", Component.literal("a".repeat(513))), "Oversized body was accepted");
        helper.assertTrue(!NotificationsApi.send(player, "money", null), "Missing body was accepted");
        helper.assertTrue(player.shows().isEmpty(), "Invalid notification created an outgoing payload");
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void offThreadCallsAreRejectedWithoutSendingOrScheduling(GameTestHelper helper) throws InterruptedException {
        Actor player = new Actor(helper, true); AtomicBoolean accepted = new AtomicBoolean(true);
        Thread worker = new Thread(() -> accepted.set(NotificationsApi.send(player, "info", Component.literal("unsafe thread"))), "notification-off-thread-qa");
        worker.start(); worker.join(1000);
        helper.assertTrue(!worker.isAlive() && !accepted.get() && player.shows().isEmpty(), "Off-thread caller sent or stalled a payload");
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void componentStylesAndPrivateHoverDataAreStrippedWithoutMutatingInput(GameTestHelper helper) {
        Actor player = new Actor(helper, true);
        var original = Component.translatableWithFallback("qa.notice", "Pagamento: %s", Component.literal("+5 €").withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/op attacker"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("PRIVATE_PROFILE")))
                .withInsertion("PRIVATE_SIGNATURE").withBold(true)));
        helper.assertTrue(NotificationsApi.send(player, "unrecognized", original), "Safe translation with styled argument was rejected");
        Show payload = player.shows().getFirst();
        helper.assertTrue(payload.kind().equals("info"), "Unknown kind did not use info");
        String serialized = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, payload.body()).getOrThrow().toString();
        helper.assertTrue(!serialized.contains("PRIVATE_") && !serialized.contains("clickEvent") && !serialized.contains("bold") && !serialized.contains("/op"), "Unsafe styles or private hover data survived");
        helper.assertTrue(original.getString().contains("5 €"), "Input component was modified");
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void actualWireCodecRoundTripsTranslationAndUnicode(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            Show input = new Show("reward", Component.literal("Conquista 🎉"), Component.translatableWithFallback("qa.reward", "+%s € — %s", "50,00", Component.literal("Aetherion")));
            Show.CODEC.encode(buffer, input);
            Show output = Show.CODEC.decode(buffer);
            // Minecraft canonically decodes a literal translation argument as a String.
            // Compare the wire meaning rather than its pre-encoding Java representation.
            var codec = net.minecraft.network.chat.ComponentSerialization.CODEC;
            var ops = com.mojang.serialization.JsonOps.INSTANCE;
            helper.assertTrue(output.kind().equals("reward") && output.title().getString().equals("Conquista 🎉")
                    && output.body().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents
                    && output.body().getString().equals("+50,00 € — Aetherion")
                    && codec.encodeStart(ops,input.title()).getOrThrow().equals(codec.encodeStart(ops,output.title()).getOrThrow())
                    && codec.encodeStart(ops,input.body()).getOrThrow().equals(codec.encodeStart(ops,output.body()).getOrThrow())
                    && !buffer.isReadable(), "Notification payload lost its translation, arguments, Unicode or framing");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void malformedAndDeepWireTreesAreRefusedBeforeRecursiveDecoding(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (String invalid : List.of("[".repeat(1000) + "0" + "]".repeat(1000), "{\"text\":\"" + "x".repeat(513) + "\"}", "{not json}",
                    "{\"text\":\"Hello\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":{\"id\":\"minecraft:diamond\"}}}",
                    "{\"selector\":\"@a\"}")) {
                buffer.clear(); buffer.writeUtf("info", 16); buffer.writeUtf("\"Title\""); buffer.writeUtf(invalid);
                rejected(helper, () -> Show.CODEC.decode(buffer));
            }
            Component deep = Component.literal("end");
            for (int i = 0; i < 20; i++) deep = Component.empty().append(deep);
            Component tooDeep = deep;
            rejected(helper, () -> new Show("info", Component.literal("title"), tooDeep));
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    @SuppressWarnings("unchecked")
    public static void broadcastCountsOnlyClientsWhichAcceptedThePayload(GameTestHelper helper) throws Exception {
        Actor supported = new Actor(helper, true), absent = new Actor(helper, false);
        var server = helper.getLevel().getServer();
        var field = PlayerList.class.getDeclaredField("players"); field.setAccessible(true);
        var online = (List<ServerPlayer>) field.get(server.getPlayerList());
        online.add(supported); online.add(absent);
        try {
            helper.assertTrue(NotificationsApi.broadcast(server, "election", Component.literal("Votação aberta")) == 1, "Broadcast counted an unsupported client");
            helper.assertTrue(supported.shows().size() == 1 && absent.shows().isEmpty(), "Broadcast routing did not respect optional support");
        } finally { online.remove(supported); online.remove(absent); }
        helper.succeed();
    }

    @GameTest(template="notifications_empty", timeoutTicks=100)
    public static void allFreshCompanionBridgesUseTheActualApiAndPreserveFallback(GameTestHelper helper) throws Exception {
        if (!Boolean.getBoolean("notifications.integration")) { helper.succeed(); return; }
        Actor supported = new Actor(helper, true), absent = new Actor(helper, false);
        for (String name : List.of("pt.cronicasrp", "pt.contracts", "pt.realmcouncil", "pt.crownledger", "pt.realmmarkets", "pt.realmrewards")) {
            var bridge = Class.forName(name + ".compat.NotificationsBridge").getMethod("send", ServerPlayer.class, String.class, Component.class);
            helper.assertTrue(Boolean.TRUE.equals(bridge.invoke(null, supported, "money", Component.literal(name))), "Fresh companion bridge failed: " + name);
            helper.assertTrue(Boolean.FALSE.equals(bridge.invoke(null, absent, "money", Component.literal(name))), "Fresh companion suppressed fallback: " + name);
        }
        helper.assertTrue(supported.shows().size() == 6 && absent.shows().isEmpty(), "Companion bridges duplicated or misrouted messages");
        RealmNotificationsMod.LOGGER.info("NOTIFICATIONS_FRESH_COMPANION_BRIDGES_PASSED");
        helper.succeed();
    }
}
