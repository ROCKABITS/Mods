package pt.realmidentity.service;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import pt.realmidentity.block.BlockRegistry;
import pt.realmidentity.compat.ChroniclesBridge;
import pt.realmidentity.compat.TitlesBridge;
import pt.realmidentity.data.IdentityData;
import pt.realmidentity.domain.Document;
import pt.realmidentity.domain.IdentityLedger;
import pt.realmidentity.domain.ProfileSnapshot;
import pt.realmidentity.network.IdentityNetwork;

/** Server-thread-only physical interactions. Client requests never provide identity fields. */
public final class IdentityService {
    private static final Gson JSON = new Gson();
    private static final long STATION_MILLIS = 300_000;
    private static final long OFFER_MILLIS = 30_000;
    private static final long OFFER_COOLDOWN_MILLIS = 3_000;
    private static final int MAX_OFFERS = 4096;
    private static final Map<UUID, StationSession> STATIONS = new HashMap<>();
    private static final Map<UUID, PendingOffer> OFFERS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_OFFER = new HashMap<>();
    private static final Set<String> SUCCESS = Set.of("gui.realmidentity.issued", "gui.realmidentity.reissued");

    private IdentityService() {}

    private record StationSession(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos,
                                  long expiresAt, long expiresTick) {}

    /** Immutable server-side consent request; no identity snapshot is placed in a chat link. */
    public record PendingOffer(UUID token, UUID presenter, UUID recipient, UUID documentId,
                               long createdAt, long expiresAt, long expiresTick) {}

    public static void openStation(ServerPlayer player, BlockPos pos) {
        try {
            requirePlayer(player);
            requirePhysicalStation(player, pos);
            long now = System.currentTimeMillis();
            STATIONS.put(player.getUUID(), new StationSession(player.getServer(), player.level().dimension(),
                    pos.immutable(), now + STATION_MILLIS, ticks(player.getServer()) + 6000));
            sendStation(player, "");
        } catch (RuntimeException | LinkageError failure) {
            close(player, error(failure));
        }
    }

    public static void requireStationSession(ServerPlayer player) {
        requirePlayer(player);
        StationSession session = STATIONS.get(player.getUUID());
        if (session == null || session.server() != player.getServer()
                || System.currentTimeMillis() >= session.expiresAt()
                || ticks(player.getServer()) >= session.expiresTick()
                || !session.dimension().equals(player.level().dimension())) {
            throw new IllegalStateException("gui.realmidentity.station_expired");
        }
        requirePhysicalStation(player, session.pos());
    }

    private static void requirePhysicalStation(ServerPlayer player, BlockPos pos) {
        if (pos == null || !player.level().hasChunkAt(pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64
                || !player.level().getBlockState(pos).is(BlockRegistry.WRITING_DESK.get())) {
            throw new IllegalStateException("gui.realmidentity.station_range");
        }
    }

    public static void action(ServerPlayer player, String operation, String payload) {
        try {
            requirePlayer(player);
            if ("close".equals(operation)) {
                STATIONS.remove(player.getUUID());
                return;
            }
            // A physical station is checked before parsing a request or reading registry/profile data.
            requireStationSession(player);
            if (payload == null || payload.length() > 256) throw new IllegalArgumentException();
            var request = JsonParser.parseString(payload);
            if (!request.isJsonObject() || !request.getAsJsonObject().entrySet().isEmpty()) {
                throw new IllegalArgumentException();
            }
            String message = switch (operation == null ? "" : operation) {
                case "snapshot", "refresh" -> "";
                case "issue" -> {
                    if (IdentityData.get(player.getServer()).ledger.current(player.getUUID()).isPresent()) {
                        throw new IllegalStateException("gui.realmidentity.already_issued");
                    }
                    trustedIssue(player);
                    yield "gui.realmidentity.issued";
                }
                case "reissue" -> {
                    if (IdentityData.get(player.getServer()).ledger.current(player.getUUID()).isEmpty()) {
                        throw new IllegalStateException("gui.realmidentity.not_issued");
                    }
                    trustedIssue(player);
                    yield "gui.realmidentity.reissued";
                }
                default -> throw new IllegalArgumentException();
            };
            sendStation(player, message);
        } catch (RuntimeException | LinkageError failure) {
            try {
                requireStationSession(player);
                sendStation(player, error(failure));
            } catch (RuntimeException | LinkageError invalid) {
                close(player, error(invalid));
            }
        }
    }

    /** A trusted caller still needs a nearby desk, a current profile, materials, and output space. */
    public static ItemStack trustedIssue(ServerPlayer player) {
        requireStationSession(player);
        ProfileSnapshot profile = ChroniclesBridge.snapshot(player);
        IdentityData data = IdentityData.get(player.getServer());
        IdentityLedger next = data.ledger.copy();
        Document issued = next.issue(player.getUUID(), profile, System.currentTimeMillis());
        ItemStack output = documentStack(issued, next.seal());
        List<ItemStack> inventory = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) inventory.add(player.getInventory().getItem(slot).copy());
        consume(inventory, Items.PAPER);
        consume(inventory, Items.INK_SAC);
        int destination = -1;
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.get(slot).isEmpty()) { destination = slot; break; }
        }
        if (destination < 0) throw new IllegalStateException("gui.realmidentity.inventory_full");
        inventory.set(destination, output.copy());

        // All fallible profile, registry, material, and capacity work has finished on copies.
        // No callbacks or player code run between these simple in-memory assignments.
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, inventory.get(slot));
        data.replace(next);
        player.getInventory().setChanged();
        try { player.containerMenu.broadcastChanges(); }
        catch (RuntimeException ignored) { /* The committed inventory remains authoritative. */ }
        return output.copy();
    }

    private static void consume(List<ItemStack> inventory, Item required) {
        for (ItemStack stack : inventory) {
            if (stack.is(required) && !stack.isEmpty()) { stack.shrink(1); return; }
        }
        throw new IllegalStateException("gui.realmidentity.materials_required");
    }

    /** Physical stacks contain only opaque registry references, never a copy of a profile. */
    public static ItemStack documentStack(Document document, UUID seal) {
        ItemStack stack = new ItemStack(BlockRegistry.IDENTITY_DOCUMENT.get());
        CompoundTag reference = new CompoundTag();
        reference.putUUID("documentId", document.id());
        reference.putUUID("seal", seal);
        CompoundTag data = new CompoundTag();
        data.put("realmidentity", reference);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    public static void inspectHeld(ServerPlayer player, InteractionHand hand) {
        try {
            requirePlayer(player);
            if (hand == null || !player.getItemInHand(hand).is(BlockRegistry.IDENTITY_DOCUMENT.get())) {
                throw new IllegalStateException("gui.realmidentity.document_required");
            }
            Map<String, Object> view = inspection(player, player.getItemInHand(hand));
            STATIONS.remove(player.getUUID());
            IdentityNetwork.send(player, JSON.toJson(view));
        } catch (RuntimeException | LinkageError failure) {
            tell(player, error(failure));
        }
    }

    /** This server-side view helper never trusts names, lore, or profile fields on an ItemStack. */
    public static Map<String, Object> inspection(ServerPlayer holder, ItemStack physical) {
        requirePlayer(holder);
        Map<String, Object> view = emptyView("ITEM");
        putHolder(view, holder);
        Optional<Document> document = authenticated(holder.getServer(), physical);
        if (document.isPresent()) putDocument(view, holder.getServer(), document.get(), holder);
        return view;
    }

    public static void offer(ServerPlayer presenter, ServerPlayer recipient, InteractionHand hand) {
        try {
            requireNearby(presenter, recipient);
            if (hand == null) throw new IllegalArgumentException();
            Document document = authenticated(presenter.getServer(), presenter.getItemInHand(hand))
                    .orElseThrow(() -> new IllegalStateException("gui.realmidentity.document_invalid"));
            if (!document.owner().equals(presenter.getUUID())) {
                throw new IllegalStateException("gui.realmidentity.owner_required");
            }
            long now = System.currentTimeMillis();
            cleanup(presenter.getServer(), now);
            Long last = LAST_OFFER.get(presenter.getUUID());
            if (last != null && now - last < OFFER_COOLDOWN_MILLIS) {
                throw new IllegalStateException("gui.realmidentity.offer_cooldown");
            }
            if (OFFERS.size() >= MAX_OFFERS) throw new IllegalStateException("gui.realmidentity.offer_busy");
            UUID token;
            do { token = UUID.randomUUID(); } while (OFFERS.containsKey(token));
            PendingOffer offer = new PendingOffer(token, presenter.getUUID(), recipient.getUUID(), document.id(),
                    now, now + OFFER_MILLIS, ticks(presenter.getServer()) + 600);
            OFFERS.put(token, offer);
            LAST_OFFER.put(presenter.getUUID(), now);
            Component link = Component.translatable("gui.realmidentity.offer_inspect")
                    .withStyle(style -> style.withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/identity inspect " + offer.token())));
            recipient.sendSystemMessage(Component.translatable("gui.realmidentity.offer_received",
                    presenter.getDisplayName(), link));
            tell(presenter, "gui.realmidentity.offer_sent");
        } catch (RuntimeException | LinkageError failure) {
            tell(presenter, error(failure));
        }
    }

    public static void acceptOffer(ServerPlayer viewer, UUID token) {
        try {
            requirePlayer(viewer);
            cleanup(viewer.getServer(), System.currentTimeMillis());
            PendingOffer offer = OFFERS.get(token);
            if (offer == null || !offer.recipient().equals(viewer.getUUID())) {
                throw new IllegalStateException("gui.realmidentity.offer_invalid");
            }
            ServerPlayer presenter = viewer.getServer().getPlayerList().getPlayer(offer.presenter());
            requireNearby(presenter, viewer);
            Document document = heldDocument(presenter, offer.documentId())
                    .orElseThrow(() -> new IllegalStateException("gui.realmidentity.offer_invalid"));
            if (!document.owner().equals(presenter.getUUID())) {
                throw new IllegalStateException("gui.realmidentity.owner_required");
            }
            Map<String, Object> view = emptyView("PRESENTED");
            putHolder(view, presenter);
            putDocument(view, viewer.getServer(), document, presenter);
            view.put("presenterName", presenter.getGameProfile().getName());
            view.put("presenterUuid", presenter.getUUID().toString());
            IdentityNetwork.send(viewer, JSON.toJson(view));
            STATIONS.remove(viewer.getUUID());
            OFFERS.remove(token); // Consume consent only after the validated view was sent.
        } catch (RuntimeException | LinkageError failure) {
            tell(viewer, error(failure));
        }
    }

    private static Optional<Document> heldDocument(ServerPlayer owner, UUID id) {
        for (InteractionHand hand : InteractionHand.values()) {
            Optional<Document> document = authenticated(owner.getServer(), owner.getItemInHand(hand));
            if (document.isPresent() && document.get().id().equals(id)) return document;
        }
        return Optional.empty();
    }

    private static Optional<Document> authenticated(MinecraftServer server, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(BlockRegistry.IDENTITY_DOCUMENT.get())) return Optional.empty();
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return Optional.empty();
        CompoundTag data = custom.copyTag();
        if (!data.contains("realmidentity", Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag reference = data.getCompound("realmidentity");
        if (!reference.hasUUID("documentId") || !reference.hasUUID("seal")) return Optional.empty();
        IdentityLedger ledger = IdentityData.get(server).ledger;
        if (!ledger.seal().equals(reference.getUUID("seal"))) return Optional.empty();
        return ledger.lookup(reference.getUUID("documentId"));
    }

    private static void requireNearby(ServerPlayer presenter, ServerPlayer recipient) {
        requirePlayer(presenter);
        requirePlayer(recipient);
        if (presenter == recipient || presenter.getServer() != recipient.getServer()
                || !presenter.level().dimension().equals(recipient.level().dimension())
                || presenter.distanceToSqr(recipient) > 36
                || !presenter.hasLineOfSight(recipient) || !recipient.hasLineOfSight(presenter)) {
            throw new IllegalStateException("gui.realmidentity.offer_range");
        }
    }

    private static Map<String, Object> stationView(ServerPlayer player, String message) {
        requireStationSession(player);
        IdentityLedger ledger = IdentityData.get(player.getServer()).ledger;
        Map<String, Object> view = emptyView("STATION");
        putHolder(view, player);
        view.put("ownerUuid", player.getUUID().toString());
        view.put("ownerName", player.getGameProfile().getName());
        int paper = count(player, Items.PAPER), ink = count(player, Items.INK_SAC);
        view.put("paperCount", paper);
        view.put("inkCount", ink);
        Optional<Document> current = ledger.current(player.getUUID());
        current.ifPresent(document -> {
            view.put("documentId", document.id().toString());
            view.put("serial", document.serial());
            view.put("issuedAt", document.issuedAt());
        });
        try {
            ProfileSnapshot profile = ChroniclesBridge.snapshot(player);
            view.put("profile", profile);
            view.put("source", profile.source().name());
            view.put("status", "CURRENT");
            view.put("canIssue", current.isEmpty() && paper > 0 && ink > 0);
            view.put("canReissue", current.isPresent() && paper > 0 && ink > 0);
            TitlesBridge.addTo(view, player.getServer(), player.getUUID());
        } catch (RuntimeException | LinkageError failure) {
            view.put("status", "PROFILE_UNAVAILABLE");
            if (message.isBlank()) message = error(failure);
        }
        view.put("message", message);
        view.put("success", SUCCESS.contains(message));
        return view;
    }

    private static Map<String, Object> emptyView(String mode) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mode", mode);
        view.put("stationMode", mode.equals("STATION"));
        view.put("readOnly", !mode.equals("STATION"));
        view.put("message", "");
        view.put("success", false);
        view.put("profile", null);
        view.put("status", "INVALID");
        view.put("authentic", false);
        view.put("documentId", "");
        view.put("serial", 0L);
        view.put("issuedAt", 0L);
        for (String key : List.of("holderName", "holderUuid", "ownerName", "ownerUuid", "presenterName", "presenterUuid", "source")) {
            view.put(key, "");
        }
        view.put("holderIsOwner", false);
        view.put("paperCount", 0);
        view.put("inkCount", 0);
        view.put("canIssue", false);
        view.put("canReissue", false);
        view.put("titlesStatus", "absent");
        return view;
    }

    private static void putHolder(Map<String, Object> view, ServerPlayer holder) {
        view.put("holderName", holder.getGameProfile().getName());
        view.put("holderUuid", holder.getUUID().toString());
    }

    private static void putDocument(Map<String, Object> view, MinecraftServer server, Document document, ServerPlayer holder) {
        view.put("authentic", true);
        view.put("profile", document.profile());
        view.put("source", document.profile().source().name());
        view.put("documentId", document.id().toString());
        view.put("serial", document.serial());
        view.put("issuedAt", document.issuedAt());
        view.put("ownerUuid", document.owner().toString());
        view.put("holderIsOwner", document.owner().equals(holder.getUUID()));
        TitlesBridge.addTo(view, server, document.owner());
        ServerPlayer online = server.getPlayerList().getPlayer(document.owner());
        view.put("ownerName", online == null ? "" : online.getGameProfile().getName());
        if (online == null) {
            view.put("status", "OFFLINE");
            return;
        }
        try {
            view.put("status", document.profile().equals(ChroniclesBridge.snapshot(online)) ? "CURRENT" : "OUTDATED");
        } catch (RuntimeException | LinkageError failure) {
            view.put("status", "PROFILE_UNAVAILABLE");
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    public static Optional<UUID> pendingOfferToken(UUID presenter, UUID recipient) {
        return OFFERS.values().stream().filter(offer -> offer.presenter().equals(presenter)
                && offer.recipient().equals(recipient)).map(PendingOffer::token).reduce((first, last) -> last);
    }

    public static Optional<PendingOffer> pendingOffer(UUID token) { return Optional.ofNullable(OFFERS.get(token)); }

    private static void cleanup(MinecraftServer server, long now) {
        long tick = ticks(server);
        OFFERS.values().removeIf(offer -> now >= offer.expiresAt() || tick >= offer.expiresTick());
        LAST_OFFER.entrySet().removeIf(entry -> now - entry.getValue() >= OFFER_MILLIS);
        STATIONS.values().removeIf(session -> now >= session.expiresAt()
                || (session.server() == server && tick >= session.expiresTick()));
    }

    public static void logout(UUID player) {
        STATIONS.remove(player);
        LAST_OFFER.remove(player);
        OFFERS.values().removeIf(offer -> offer.presenter().equals(player) || offer.recipient().equals(player));
    }

    public static void stop(MinecraftServer server) {
        STATIONS.clear();
        OFFERS.clear();
        LAST_OFFER.clear();
    }

    private static long ticks(MinecraftServer server) { return server.overworld().getGameTime(); }

    private static void sendStation(ServerPlayer player, String message) {
        IdentityNetwork.send(player, JSON.toJson(stationView(player, message)));
    }

    private static void close(ServerPlayer player, String message) {
        if (player == null) return;
        STATIONS.remove(player.getUUID());
        try { IdentityNetwork.send(player, JSON.toJson(Map.of("close", true, "message", message))); }
        catch (RuntimeException ignored) { }
        tell(player, message);
    }

    private static void tell(ServerPlayer player, String message) {
        if (player != null && message != null && !message.isBlank()) {
            player.displayClientMessage(Component.translatable(message), false);
        }
    }

    private static String error(Throwable failure) {
        String message = failure.getMessage();
        return message != null && message.startsWith("gui.realmidentity.") ? message : "gui.realmidentity.invalid_request";
    }

    private static void requirePlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()
                || player.isSpectator() || !player.isAlive()) {
            throw new IllegalStateException("gui.realmidentity.invalid_request");
        }
    }
}
