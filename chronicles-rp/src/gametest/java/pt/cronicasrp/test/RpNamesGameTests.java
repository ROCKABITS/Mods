package pt.cronicasrp.test;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.ModAttachments;
import pt.cronicasrp.RpNames;
import pt.cronicasrp.domain.CharacterDraft;
import pt.cronicasrp.domain.CharacterProfile;
import pt.cronicasrp.domain.RpProgress;
import pt.cronicasrp.domain.SignatureDrawing;
import pt.cronicasrp.network.ProfileNetwork;
import pt.cronicasrp.network.ProfilePackets;
import pt.cronicasrp.network.RpNamePackets;

/** Native name hooks and lifecycle; outgoing payloads use real server PlayerList routing. */
@GameTestHolder(CronicasRP.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RpNamesGameTests {
    private static final String ACCOUNT = "AccountQA";
    private static final String NAME = "Éowyn da Lua";

    private static CharacterProfile profile(String name) {
        return new CharacterProfile(true, name, 29, "origins:human", "PRIVATE_BIOGRAPHY_QA",
                "PRIVATE_SIGNATURE_QA", "Comum", 90_000, 12_345, "",
                RpProgress.EMPTY.withOccupation("Ferreiro").withReputation(63));
    }

    private static final class RecordedPlayer extends FakePlayer {
        final List<Packet<?>> sent = new ArrayList<>();

        RecordedPlayer(GameTestHelper helper, UUID id, String username) {
            super(helper.getLevel(), new GameProfile(id, username));
            connection = new RecordingConnection(helper.getLevel().getServer(), this, sent);
        }

        RecordedPlayer(GameTestHelper helper, String username) {
            this(helper, UUID.randomUUID(), username);
        }

        List<CustomPacketPayload> payloads() {
            return sent.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                    .map(ClientboundCustomPayloadPacket.class::cast)
                    .map(ClientboundCustomPayloadPacket::payload).toList();
        }

        List<RpNamePackets.Update> names() {
            return payloads().stream().filter(RpNamePackets.Update.class::isInstance)
                    .map(RpNamePackets.Update.class::cast).toList();
        }
    }

    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> sent;

        RecordingConnection(MinecraftServer server, ServerPlayer player, List<Packet<?>> sent) {
            super(server, new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.sent = sent;
        }

        @Override public void send(Packet<?> packet) { sent.add(packet); }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { sent.add(packet); }
    }

    /** getPlayers() is immutable in NeoForge; register in the genuine backing list/UUID index. */
    private static final class OnlinePlayers implements AutoCloseable {
        private final List<ServerPlayer> online;
        private final Map<UUID, ServerPlayer> byId;
        private final List<RecordedPlayer> fixtures = new ArrayList<>();

        @SuppressWarnings("unchecked")
        OnlinePlayers(GameTestHelper helper, RecordedPlayer... players) {
            var list = helper.getLevel().getServer().getPlayerList();
            try {
                Field onlineField = PlayerList.class.getDeclaredField("players");
                onlineField.setAccessible(true);
                online = (List<ServerPlayer>) onlineField.get(list);
                Field indexField = PlayerList.class.getDeclaredField("playersByUUID");
                indexField.setAccessible(true);
                byId = (Map<UUID, ServerPlayer>) indexField.get(list);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot register native RP name fixtures", exception);
            }
            for (var player : players) add(player);
        }

        void add(RecordedPlayer player) {
            if (byId.putIfAbsent(player.getUUID(), player) != null)
                throw new IllegalStateException("Duplicate UUID in native fixture");
            online.add(player);
            fixtures.add(player);
        }

        void remove(RecordedPlayer player) {
            online.remove(player);
            byId.remove(player.getUUID(), player);
            fixtures.remove(player);
        }

        @Override public void close() {
            for (var player : List.copyOf(fixtures)) {
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                remove(player);
            }
        }
    }

    private static RecordedPlayer character(GameTestHelper helper, String account, String name) {
        var player = new RecordedPlayer(helper, account);
        player.setData(ModAttachments.PROFILE, profile(name));
        return player;
    }

    private static void unchangedIdentity(GameTestHelper helper, RecordedPlayer player, UUID id, String account) {
        helper.assertTrue(player.getUUID().equals(id) && player.getGameProfile().getId().equals(id),
                "Visual RP names changed the real player UUID.");
        helper.assertTrue(player.getName().getString().equals(account)
                && player.getGameProfile().getName().equals(account),
                "Visual RP names changed the authenticated account name.");
    }

    private static void displayed(GameTestHelper helper, RecordedPlayer player, String name) {
        helper.assertTrue(player.getDisplayName().getString().equals(name),
                "The native Player display-name hook did not use the RP name: " + player.getDisplayName().getString());
        var tab = player.getTabListDisplayName();
        helper.assertTrue(tab != null && tab.getString().equals(name),
                "The actual server TAB display name did not use the RP name.");
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void actualRuntimeHasTheExpectedOriginsAndCompanionCombination(GameTestHelper helper) {
        var mods = net.neoforged.fml.ModList.get();
        helper.assertTrue(mods.isLoaded("origins") && mods.isLoaded("jupiter"),
                "Native tests did not load the actual required Origins and Jupiter mods.");
        boolean expectedClasses = Boolean.getBoolean("cronicasrp.qaExpectedClasses");
        boolean expectedCompanions = Boolean.getBoolean("cronicasrp.qaExpectedCompanions");
        helper.assertTrue(mods.isLoaded("origins_classes") == expectedClasses,
                "Origins: Classes runtime did not match the requested test configuration.");
        for (String id : List.of("contracts", "realmcouncil", "kingdomtreasury", "realmidentity", "realmmarkets"))
            helper.assertTrue(mods.isLoaded(id) == expectedCompanions,
                    "Companion runtime did not match the requested test configuration: " + id);
        CronicasRP.LOGGER.info("CRP_NAMES_RUNTIME_DEPENDENCIES_PASSED classes={} companions={}",
                expectedClasses, expectedCompanions);
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void uncreatedCharactersRetainTheVanillaAccountName(GameTestHelper helper) {
        var player = new RecordedPlayer(helper, ACCOUNT);
        var uuid = player.getUUID();
        helper.assertTrue(!player.getData(ModAttachments.PROFILE).created(), "Fixture unexpectedly has a character.");
        helper.assertTrue(player.getDisplayName().getString().equals(ACCOUNT), "No-character fallback lost the username.");
        helper.assertTrue(player.getTabListDisplayName() == null, "No-character fallback replaced vanilla TAB rendering.");
        unchangedIdentity(helper, player, uuid, ACCOUNT);
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void actualNameAndTabHooksDecorateChatWithoutChangingIdentity(GameTestHelper helper) {
        var player = character(helper, ACCOUNT, NAME);
        var uuid = player.getUUID();
        displayed(helper, player, NAME);
        var chat = ChatType.bind(ChatType.CHAT, player).decorate(Component.literal("Olá reino"));
        helper.assertTrue(chat.getString().contains(NAME) && chat.getString().contains("Olá reino")
                && !chat.getString().contains(ACCOUNT), "Vanilla ChatType decoration exposed the account username.");
        unchangedIdentity(helper, player, uuid, ACCOUNT);
        CronicasRP.LOGGER.info("CRP_NAMES_NATIVE_TESTS_PASSED");
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void scoreboardTeamsAndNeoForgeAffixesKeepTheirFormatting(GameTestHelper helper) {
        var player = character(helper, "TeamFormatQA", NAME);
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("rp_" + UUID.randomUUID().toString().substring(0, 8));
        team.setPlayerPrefix(Component.literal("[Guild] "));
        team.setPlayerSuffix(Component.literal(" [Knight]"));
        scoreboard.addPlayerToTeam("TeamFormatQA", team);
        player.getPrefixes().add(Component.literal("<"));
        player.getSuffixes().add(Component.literal(">"));
        try {
            helper.assertTrue(player.getDisplayName().getString().equals("<[Guild] " + NAME + " [Knight]>"),
                    "RP names discarded team formatting or NeoForge display-name affixes.");
            helper.assertTrue(player.getTabListDisplayName().getString().equals("[Guild] " + NAME + " [Knight]"),
                    "TAB names discarded the actual scoreboard team prefix/suffix.");
            unchangedIdentity(helper, player, player.getUUID(), "TeamFormatQA");
        } finally { scoreboard.removePlayerTeam(team); }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void visibleNameHoverAndShiftInsertionUseTheRpName(GameTestHelper helper) {
        final String account = "TeamHoverQA";
        var player = character(helper, account, NAME);
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("rp_" + UUID.randomUUID().toString().substring(0, 8));
        team.setPlayerPrefix(Component.literal("[Guild] "));
        team.setPlayerSuffix(Component.literal(" [Knight]"));
        scoreboard.addPlayerToTeam(account, team);
        player.getPrefixes().add(Component.literal("<"));
        player.getSuffixes().add(Component.literal(">"));
        try {
            var display = player.getDisplayName();
            int[] visits = {0};
            display.visit((style, text) -> {
                if (!text.isEmpty()) {
                    visits[0]++;
                    helper.assertTrue(NAME.equals(style.getInsertion()),
                            "Shift-insertion exposed the raw username on visible RP/team text: " + text);
                    helper.assertTrue(style.getHoverEvent() != null, "RP/team display-name hover disappeared.");
                    // Inspect only the effective hover; the /tell click target must keep the real account name.
                    JsonElement encoded = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE,
                            Component.literal(text).withStyle(net.minecraft.network.chat.Style.EMPTY
                                    .withHoverEvent(style.getHoverEvent()))).getOrThrow();
                    helper.assertTrue(encoded.toString().contains(NAME) && !encoded.toString().contains(account),
                            "The effective hover of visible RP/team text exposed the raw account name: " + text);
                    helper.assertTrue(style.getClickEvent() != null
                            && style.getClickEvent().getAction() == net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND
                            && style.getClickEvent().getValue().contains("/tell " + account),
                            "RP decoration broke the actual account-name /tell suggestion.");
                }
                return Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            helper.assertTrue(visits[0] >= 5, "The hover test did not visit RP text and every team/NeoForge affix.");
            unchangedIdentity(helper, player, player.getUUID(), account);
        } finally { scoreboard.removePlayerTeam(team); }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void characterCreationPacketPublishesTheNewNameImmediately(GameTestHelper helper) {
        var player = new RecordedPlayer(helper, ACCOUNT);
        var observer = new RecordedPlayer(helper, "ObserverQA");
        helper.assertTrue(player.getDisplayName().getString().equals(ACCOUNT), "Pre-creation display fixture was not cached.");
        helper.assertTrue(player.getTabListDisplayName() == null, "Pre-creation TAB fixture was not cached.");
        selectOrigins(player);
        byte[] ink = new byte[SignatureDrawing.BYTES];
        SignatureDrawing.pixel(ink, 40, 20, true);
        String drawing = SignatureDrawing.encode(ink);
        try (var online = new OnlinePlayers(helper, player, observer)) {
            invokeCreate(player, new CharacterDraft(NAME, 29, "forged:race", "Lua", "Éowyn", drawing, "Ferreiro"));
            helper.assertTrue(player.getData(ModAttachments.PROFILE).created(), "The real creation packet handler rejected the valid character.");
            displayed(helper, player, NAME);
            helper.assertTrue(observer.names().stream().anyMatch(packet -> packet.playerId().equals(player.getUUID())
                    && packet.name().equals(NAME)), "Successful creation did not broadcast the new public name.");
            helper.assertTrue(observer.payloads().stream().noneMatch(ProfilePackets.State.class::isInstance),
                    "Creating a character broadcast the private profile to another player.");
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void ordinaryProfileSyncUpdatesRenamesButDoesNotResendUnchangedNames(GameTestHelper helper) {
        var player = character(helper, ACCOUNT, NAME);
        var observer = new RecordedPlayer(helper, "ObserverQA");
        try (var online = new OnlinePlayers(helper, player, observer)) {
            ProfileNetwork.send(player, ProfilePackets.SYNC, "");
            observer.sent.clear();
            var renamed = profile("Aetherion");
            player.setData(ModAttachments.PROFILE, renamed);
            ProfileNetwork.send(player, ProfilePackets.SYNC, "");
            displayed(helper, player, "Aetherion");
            helper.assertTrue(observer.names().size() == 1 && observer.names().getFirst().name().equals("Aetherion"),
                    "A trusted profile name change did not produce exactly one public name update.");
            helper.assertTrue(player.getData(ModAttachments.PROFILE).equals(renamed), "Name synchronization altered private character fields.");
            observer.sent.clear();
            player.setData(ModAttachments.PROFILE, renamed.withBalance(renamed.balance() + 1));
            ProfileNetwork.send(player, ProfilePackets.SYNC, "");
            helper.assertTrue(observer.names().isEmpty(), "A balance-only update redundantly broadcast the public name.");
            unchangedIdentity(helper, player, player.getUUID(), ACCOUNT);
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void removingACharacterClearsTheCachedNameAndRestoresFallback(GameTestHelper helper) {
        var player = character(helper, ACCOUNT, NAME);
        var observer = new RecordedPlayer(helper, "ObserverQA");
        try (var online = new OnlinePlayers(helper, player, observer)) {
            ProfileNetwork.send(player, ProfilePackets.SYNC, "");
            displayed(helper, player, NAME);
            observer.sent.clear();
            player.setData(ModAttachments.PROFILE, CharacterProfile.empty());
            ProfileNetwork.send(player, ProfilePackets.SYNC, "");
            helper.assertTrue(player.getDisplayName().getString().equals(ACCOUNT)
                    && player.getTabListDisplayName() == null, "Resetting a character left its old RP display name cached.");
            helper.assertTrue(observer.names().size() == 1 && observer.names().getFirst().name().isEmpty(),
                    "Removing a character did not clear its public UUID/name mapping.");
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void secondJoinReceivesAResetAndTheExistingPublicRosterOnly(GameTestHelper helper) {
        var existing = character(helper, "ExistingQA", "Aetherion");
        var joining = character(helper, "JoiningQA", NAME);
        var uncreated = new RecordedPlayer(helper, "UncreatedQA");
        try (var online = new OnlinePlayers(helper, existing, joining, uncreated)) {
            RpNames.synchronize(existing);
            existing.sent.clear(); joining.sent.clear(); uncreated.sent.clear();
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(joining));
            var namePayloads = joining.payloads().stream().filter(payload -> payload instanceof RpNamePackets.Reset
                    || payload instanceof RpNamePackets.Update).toList();
            helper.assertTrue(!namePayloads.isEmpty() && namePayloads.getFirst() instanceof RpNamePackets.Reset,
                    "A joining client did not clear stale public names before receiving its roster.");
            helper.assertTrue(joining.names().stream().anyMatch(packet -> packet.playerId().equals(existing.getUUID())
                    && packet.name().equals("Aetherion")), "A new client did not receive an already-online character name.");
            helper.assertTrue(joining.names().stream().anyMatch(packet -> packet.playerId().equals(joining.getUUID())
                    && packet.name().equals(NAME)), "A new client did not receive its own public name.");
            helper.assertTrue(joining.names().stream().noneMatch(packet -> packet.playerId().equals(uncreated.getUUID())
                    && !packet.name().isEmpty()), "An uncreated player received a fabricated RP name.");
            helper.assertTrue(existing.payloads().stream().noneMatch(ProfilePackets.State.class::isInstance),
                    "A joining character leaked its private profile to another online player.");
            helper.assertTrue(joining.names().stream().noneMatch(packet -> packet.toString().contains("PRIVATE_")),
                    "The public roster contains private profile data.");
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void logoutClearsObserversAndRejoiningTheSameUuidPublishesAgain(GameTestHelper helper) {
        var departing = character(helper, ACCOUNT, NAME);
        var observer = new RecordedPlayer(helper, "ObserverQA");
        try (var online = new OnlinePlayers(helper, departing, observer)) {
            RpNames.synchronize(departing);
            observer.sent.clear();
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(departing));
            helper.assertTrue(observer.names().stream().anyMatch(packet -> packet.playerId().equals(departing.getUUID())
                    && packet.name().isEmpty()), "Logout did not remove the public name from other clients.");
            online.remove(departing);
            observer.sent.clear();
            var rejoining = new RecordedPlayer(helper, departing.getUUID(), ACCOUNT);
            rejoining.setData(ModAttachments.PROFILE, profile(NAME));
            online.add(rejoining);
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(rejoining));
            helper.assertTrue(observer.names().stream().anyMatch(packet -> packet.playerId().equals(rejoining.getUUID())
                    && packet.name().equals(NAME)), "A reconnect with the same UUID was suppressed by stale server cache.");
            displayed(helper, rejoining, NAME);
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void actualDeathCloneKeepsTheCharacterAndRespawnDisplayName(GameTestHelper helper) {
        var original = character(helper, ACCOUNT, NAME);
        var clone = new RecordedPlayer(helper, original.getUUID(), ACCOUNT);
        var previous = original.getData(ModAttachments.PROFILE);
        helper.assertTrue(clone.getDisplayName().getString().equals(ACCOUNT), "Clone fallback was not initially cached.");
        clone.restoreFrom(original, false);
        helper.assertTrue(clone.getData(ModAttachments.PROFILE).equals(previous),
                "The real death-clone attachment copy did not preserve the complete RP profile.");
        try (var online = new OnlinePlayers(helper, clone)) {
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(clone, false));
            displayed(helper, clone, NAME);
            unchangedIdentity(helper, clone, original.getUUID(), ACCOUNT);
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void dimensionChangeRefreshesAnOldDisplayNameAndKeepsPrivateData(GameTestHelper helper) {
        var player = new RecordedPlayer(helper, ACCOUNT);
        helper.assertTrue(player.getDisplayName().getString().equals(ACCOUNT), "Fallback fixture did not cache the account name.");
        player.setData(ModAttachments.PROFILE, profile(NAME));
        var before = player.getData(ModAttachments.PROFILE);
        try (var online = new OnlinePlayers(helper, player)) {
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
            displayed(helper, player, NAME);
            helper.assertTrue(player.getData(ModAttachments.PROFILE).equals(before), "Dimension-name synchronization altered the character.");
            unchangedIdentity(helper, player, player.getUUID(), ACCOUNT);
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void startingToTrackAPlayerSendsItsNameWithoutItsPrivateProfile(GameTestHelper helper) {
        var target = character(helper, ACCOUNT, NAME);
        var observer = new RecordedPlayer(helper, "ObserverQA");
        try (var online = new OnlinePlayers(helper, target, observer)) {
            NeoForge.EVENT_BUS.post(new PlayerEvent.StartTracking(observer, target));
            helper.assertTrue(observer.names().stream().anyMatch(packet -> packet.playerId().equals(target.getUUID())
                    && packet.name().equals(NAME)), "Tracking a player did not provide its RP name.");
            helper.assertTrue(observer.payloads().stream().noneMatch(ProfilePackets.State.class::isInstance),
                    "Tracking a player exposed its private character profile.");
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void publicPacketCodecContainsOnlyTheUuidAndBoundedUnicodeName(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var input = new RpNamePackets.Update(id, NAME);
            RpNamePackets.Update.CODEC.encode(buffer, input);
            helper.assertTrue(buffer.readUUID().equals(id) && buffer.readUtf(40).equals(NAME) && !buffer.isReadable(),
                    "Public name payload contained data beyond its UUID and bounded name.");
            buffer.readerIndex(0);
            helper.assertTrue(RpNamePackets.Update.CODEC.decode(buffer).equals(input), "Unicode RP name did not round-trip.");
            buffer.clear();
            RpNamePackets.Reset.CODEC.encode(buffer, new RpNamePackets.Reset());
            helper.assertTrue(!buffer.isReadable(), "Reset payload unexpectedly carried private data.");
            RpNamePackets.Reset.CODEC.decode(buffer);
            helper.assertTrue(RpNamePackets.Update.class.getRecordComponents().length == 2,
                    "The public name message grew private character fields.");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void malformedPublicNamePacketsCannotInstallUnsafeText(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (String invalid : List.of("x".repeat(41), "§aForged", "Bad\nName")) {
                buffer.clear();
                buffer.writeUUID(UUID.randomUUID()); buffer.writeUtf(invalid);
                boolean rejected = false;
                try { RpNamePackets.Update.CODEC.decode(buffer); }
                catch (RuntimeException expected) { rejected = true; }
                helper.assertTrue(rejected, "Public name codec accepted unsafe or overlong text.");
            }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void equalRpNamesRemainSeparateAuthenticatedPlayers(GameTestHelper helper) {
        var first = character(helper, "FirstAccountQA", NAME);
        var second = character(helper, "SecondAccountQA", NAME);
        var observer = new RecordedPlayer(helper, "ObserverQA");
        try (var online = new OnlinePlayers(helper, first, second, observer)) {
            RpNames.sendRoster(observer);
            helper.assertTrue(observer.names().stream().anyMatch(packet -> packet.playerId().equals(first.getUUID())
                    && packet.name().equals(NAME)) && observer.names().stream().anyMatch(packet ->
                    packet.playerId().equals(second.getUUID()) && packet.name().equals(NAME)),
                    "Duplicate RP text collapsed two distinct authenticated UUIDs.");
            helper.assertTrue(!first.getUUID().equals(second.getUUID()), "Fixture UUIDs were not distinct.");
            unchangedIdentity(helper, first, first.getUUID(), "FirstAccountQA");
            unchangedIdentity(helper, second, second.getUUID(), "SecondAccountQA");
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void realAccountNamesStillAddressCommandTargets(GameTestHelper helper) {
        var target = character(helper, "AccountTargetQA", NAME);
        var operator = new RecordedPlayer(helper, "OperatorQA");
        try (var online = new OnlinePlayers(helper, target, operator)) {
            long before = target.getData(ModAttachments.PROFILE).balance();
            helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    operator.createCommandSourceStack().withPermission(2), "rp admin give AccountTargetQA 1.23");
            helper.assertTrue(target.getData(ModAttachments.PROFILE).balance() == before + 123,
                    "Replacing the visual name broke vanilla account-name command targeting.");
            displayed(helper, target, NAME);
            unchangedIdentity(helper, target, target.getUUID(), "AccountTargetQA");
        }
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void periodicServerTickRefreshesChangedTeamFormattingWithoutARename(GameTestHelper helper) {
        var player = character(helper, "PeriodicTeamQA", NAME);
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("rp_" + UUID.randomUUID().toString().substring(0, 8));
        scoreboard.addPlayerToTeam("PeriodicTeamQA", team);
        team.setPlayerPrefix(Component.literal("[Before] "));
        var online = new OnlinePlayers(helper, player);
        RpNames.synchronize(player);
        helper.assertTrue(player.getTabListDisplayName().getString().equals("[Before] " + NAME),
                "Initial TAB team formatting was not cached.");
        var before = player.getData(ModAttachments.PROFILE);
        team.setPlayerPrefix(Component.literal("[After] "));
        team.setPlayerSuffix(Component.literal(" [Duke]"));
        helper.runAfterDelay(22, () -> {
            try {
                helper.assertTrue(player.getTabListDisplayName().getString().equals("[After] " + NAME + " [Duke]"),
                        "Actual periodic server ticks left stale team affixes in TAB when the RP name was unchanged.");
                helper.assertTrue(player.getData(ModAttachments.PROFILE).equals(before), "Team refresh changed the private character.");
                helper.succeed();
            } finally {
                online.close();
                scoreboard.removePlayerTeam(team);
            }
        });
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void periodicServerTickDetectsTrustedAttachmentNameChanges(GameTestHelper helper) {
        var player = character(helper, ACCOUNT, NAME);
        var observer = new RecordedPlayer(helper, "ObserverQA");
        var online = new OnlinePlayers(helper, player, observer);
        RpNames.synchronize(player);
        displayed(helper, player, NAME);
        observer.sent.clear();
        player.setData(ModAttachments.PROFILE, profile("Íris da Serra"));
        helper.runAfterDelay(22, () -> {
            try {
                displayed(helper, player, "Íris da Serra");
                helper.assertTrue(observer.names().stream().anyMatch(packet -> packet.playerId().equals(player.getUUID())
                        && packet.name().equals("Íris da Serra")), "Actual periodic ticks did not publish an attachment-only name change.");
                unchangedIdentity(helper, player, player.getUUID(), ACCOUNT);
                helper.succeed();
            } finally { online.close(); }
        });
    }

    private static void invokeCreate(ServerPlayer player, CharacterDraft draft) {
        try {
            IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                    new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> {
                        if (method.getName().equals("player")) return player;
                        throw new UnsupportedOperationException("Unused native payload-context operation: " + method.getName());
                    });
            var method = ProfileNetwork.class.getDeclaredMethod("create", ProfilePackets.Create.class, IPayloadContext.class);
            method.setAccessible(true);
            method.invoke(null, new ProfilePackets.Create(draft), context);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot invoke the real profile-creation payload handler", exception);
        }
    }

    private static void selectOrigins(RecordedPlayer player) {
        var holder = com.iafenvoy.origins.attachment.OriginDataHolder.get(player);
        var layers = player.registryAccess().registryOrThrow(com.iafenvoy.origins.data.layer.LayerRegistries.LAYER_KEY);
        var origins = player.registryAccess().registryOrThrow(com.iafenvoy.origins.data.origin.OriginRegistries.ORIGIN_KEY);
        var raceLayer = layers.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                com.iafenvoy.origins.data.layer.LayerRegistries.LAYER_KEY, pt.cronicasrp.compat.OriginsBridge.RACE_LAYER));
        var human = origins.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                com.iafenvoy.origins.data.origin.OriginRegistries.ORIGIN_KEY,
                net.minecraft.resources.ResourceLocation.parse("origins:human")));
        holder.setOrigin(raceLayer, human);
        if (layers.containsKey(pt.cronicasrp.compat.OriginsBridge.CLASS_LAYER)) {
            holder.setOrigin(layers.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                    com.iafenvoy.origins.data.layer.LayerRegistries.LAYER_KEY, pt.cronicasrp.compat.OriginsBridge.CLASS_LAYER)),
                    origins.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                            com.iafenvoy.origins.data.origin.OriginRegistries.ORIGIN_KEY,
                            net.minecraft.resources.ResourceLocation.parse("origins_classes:warrior"))));
        }
    }
}
