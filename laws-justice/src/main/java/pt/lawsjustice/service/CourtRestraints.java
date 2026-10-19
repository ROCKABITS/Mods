package pt.lawsjustice.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.compat.JusticeNotifications;
import pt.lawsjustice.data.JusticeData;
import pt.lawsjustice.domain.CourtLedger;
import pt.lawsjustice.domain.CourtLedger.Restraint;
import pt.lawsjustice.domain.CourtLedger.Warrant;
import pt.lawsjustice.domain.JusticeLedger.Point;

/** Temporary, server-authorized arrests. No player abilities or game modes are changed. */
public final class CourtRestraints {
    public static final ResourceLocation HANDCUFFED = ResourceLocation.fromNamespaceAndPath("lawsjustice", "handcuffed");
    private static final AttributeModifier SLOWNESS = new AttributeModifier(HANDCUFFED, -.6,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final WeakHashMap<MinecraftServer, RuntimeState> RUNTIMES = new WeakHashMap<>();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private static final class RuntimeState {
        final Set<UUID> offline = new HashSet<>();
        // A failed disk write must never reactivate an already cancelled arrest.
        final Set<UUID> cancelled = new HashSet<>();
        final Set<UUID> notifiedRelease = new HashSet<>();
        long lastStorageNotice;
    }

    private CourtRestraints() {}

    public static void apply(ServerPlayer actor, ServerPlayer target) {
        MinecraftServer server = nearby(actor, target);
        requireTool(actor);
        if (actor.isCreative() || target.isCreative()) throw error("restraint_target");
        cleanup(server, now());
        JusticeData data = JusticeData.get(server);
        if (data.ledger().active().containsKey(actor.getUUID()) || restricted(actor)) throw error("restraint_source");
        if (data.ledger().active().containsKey(target.getUUID())) throw error("already_imprisoned");
        if (data.court().restraints().containsKey(target.getUUID())) throw error("already_restrained");
        long at = now();
        CourtLedger court = data.court();
        Warrant warrant = physicalWarrant(actor, target, court, at);
        if (warrant == null) throw error("warrant_invalid");
        long limit = at > Long.MAX_VALUE - CourtLedger.MAX_RESTRAINT_MILLIS
                ? Long.MAX_VALUE : at + CourtLedger.MAX_RESTRAINT_MILLIS;
        Point anchor = new Point(target.blockPosition().getX(), target.blockPosition().getY(), target.blockPosition().getZ());
        Restraint arrest = new Restraint(target.getUUID(), actor.getUUID(), warrant.id(),
                target.level().dimension().location().toString(), anchor, at, Math.min(limit, warrant.expiresAt()));
        data.replaceCourt(court.restrain(arrest, at));
        effect(target, true);
        notify(target, "lawsjustice.notification.cuffed");
    }

    public static void release(ServerPlayer actor, ServerPlayer target) {
        MinecraftServer server = nearby(actor, target);
        requireTool(actor);
        JusticeData data = JusticeData.get(server);
        Restraint arrest = data.court().restraints().get(target.getUUID());
        if (arrest == null) throw error("not_restrained");
        if (!actor.hasPermissions(2) && !arrest.escort().equals(actor.getUUID())) throw error("restraint_release");
        // No effect is changed before the manual release is durable.
        data.replaceCourt(data.court().releaseRestraint(target.getUUID()));
        RuntimeState runtime = runtime(server);
        runtime.cancelled.remove(arrest.warrant());
        runtime.notifiedRelease.remove(arrest.warrant());
        effect(target, false);
        notify(target, "lawsjustice.notification.uncuffed");
    }

    /** Called once per server tick by CourtService; intentionally has no independent deduplication. */
    public static void tick(MinecraftServer server) {
        thread(server);
        long at = now();
        cleanup(server, at);
        JusticeData data = JusticeData.get(server);
        CourtLedger court = data.court();
        for (ServerPlayer target : List.copyOf(server.getPlayerList().getPlayers())) {
            Restraint arrest = court.restraints().get(target.getUUID());
            if (online(server, target.getUUID()) != target || !effective(server, data, court, arrest, at)) {
                effect(target, false);
                continue;
            }
            effect(target, target.isAlive());
            ServerPlayer escort = online(server, arrest.escort());
            if (target.isAlive() && escort != null) guide(target, escort);
        }
    }

    public static void login(ServerPlayer player) {
        MinecraftServer server = thread(player);
        runtime(server).offline.remove(player.getUUID());
        // Respawns can copy attributes; stale cuffs are removed even without a court record.
        effect(player, false);
        long at = now();
        cleanup(server, at);
        JusticeData data = JusticeData.get(server);
        Restraint arrest = data.court().restraints().get(player.getUUID());
        if (player.isAlive() && effective(server, data, data.court(), arrest, at)) effect(player, true);
    }

    public static void logout(ServerPlayer player) {
        MinecraftServer server = thread(player);
        runtime(server).offline.add(player.getUUID());
        effect(player, false);
        // Leaving as a target preserves the bounded record; leaving as an escort releases others.
        cleanup(server, now());
    }

    public static boolean restricted(ServerPlayer player) {
        MinecraftServer server = thread(player);
        JusticeData data = JusticeData.get(server);
        return effective(server, data, data.court(), data.court().restraints().get(player.getUUID()), now());
    }

    private static void cleanup(MinecraftServer server, long at) {
        thread(server);
        JusticeData data = JusticeData.get(server);
        RuntimeState runtime = runtime(server);
        CourtLedger before = data.court();
        CourtLedger after = before.expire(at);
        for (Restraint arrest : before.restraints().values()) {
            if (!effective(server, data, before, arrest, at)) {
                runtime.cancelled.add(arrest.warrant());
                after = after.releaseRestraint(arrest.player());
            }
        }
        if (after != before) {
            try {
                data.replaceCourt(after);
            } catch (RuntimeException failure) {
                // Expiry and an invalid escort must release the player even if storage is unavailable.
                // The consumed warrant stays consumed; retry removing its retained record next tick.
                if (runtime.lastStorageNotice == 0 || at - runtime.lastStorageNotice >= 30_000) {
                    runtime.lastStorageNotice = at;
                    LOG.error("Temporary arrest ended; retrying its durable checkpoint", failure);
                }
            }
        }
        CourtLedger current = data.court();
        for (ServerPlayer target : List.copyOf(server.getPlayerList().getPlayers())) {
            Restraint old = before.restraints().get(target.getUUID());
            Restraint active = current.restraints().get(target.getUUID());
            boolean retained = online(server, target.getUUID()) == target
                    && effective(server, data, current, active, at);
            if (!retained) effect(target, false);
            if (old != null && !retained && !runtime.offline.contains(target.getUUID())
                    && runtime.notifiedRelease.add(old.warrant())) {
                notify(target, "lawsjustice.notification.uncuffed");
            }
        }
        Set<UUID> retained = new HashSet<>();
        for (Restraint arrest : current.restraints().values()) retained.add(arrest.warrant());
        runtime.cancelled.retainAll(retained);
        runtime.notifiedRelease.retainAll(retained);
    }

    private static boolean effective(MinecraftServer server, JusticeData data, CourtLedger court,
                                     Restraint arrest, long at) {
        if (arrest == null || at < arrest.appliedAt() || at >= arrest.expiresAt()
                || runtime(server).cancelled.contains(arrest.warrant())
                || data.ledger().active().containsKey(arrest.player())) return false;
        ServerPlayer escort = online(server, arrest.escort());
        if (escort == null || !escort.isAlive() || escort.isSpectator()
                || data.ledger().active().containsKey(arrest.escort())
                || court.restraints().containsKey(arrest.escort())
                || !arrest.dimension().equals(escort.level().dimension().location().toString())) return false;
        ServerPlayer target = online(server, arrest.player());
        if (target != null && target.serverLevel() != escort.serverLevel()) return false;
        Warrant warrant = court.warrants().get(arrest.warrant());
        if (warrant == null || !CourtLedger.EXECUTED.equals(warrant.status())) return false;
        return warrant.caseId().isEmpty()
                || !Set.of(CourtLedger.ACQUITTED,CourtLedger.FINED).contains(court.cases().get(UUID.fromString(warrant.caseId())).status());
    }

    private static Warrant physicalWarrant(ServerPlayer actor, ServerPlayer target, CourtLedger court, long at) {
        for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) {
            ItemStack item = actor.getInventory().getItem(slot);
            if (!item.is(BlockRegistry.WARRANT.get())) continue;
            CustomData custom = item.get(DataComponents.CUSTOM_DATA);
            if (custom == null) continue;
            CompoundTag tag = custom.copyTag();
            if (!tag.contains("lawsjustice_warrant", Tag.TAG_STRING)) continue;
            String raw = tag.getString("lawsjustice_warrant");
            if (raw.length() != 36) continue;
            try {
                UUID id = UUID.fromString(raw);
                if (id.toString().equals(raw) && court.validWarrant(id, target.getUUID(), at)) {
                    return court.warrants().get(id);
                }
            } catch (IllegalArgumentException ignored) {
                // Renamed paper and malformed NBT never authorize an arrest.
            }
        }
        return null;
    }

    private static void guide(ServerPlayer target, ServerPlayer escort) {
        Vec3 separation = escort.position().subtract(target.position());
        double horizontal = Math.sqrt(separation.x * separation.x + separation.z * separation.z);
        if (horizontal <= 4) return;
        Vec3 current = target.getDeltaMovement();
        double x = current.x * .5 + separation.x / horizontal * .08;
        double z = current.z * .5 + separation.z / horizontal * .08;
        double magnitude = Math.sqrt(x * x + z * z);
        if (magnitude > .16) { x = x / magnitude * .16; z = z / magnitude * .16; }
        // Vanilla motion and collision resolve walls; there is no escort teleport or vertical lift.
        target.setDeltaMovement(x, current.y, z);
        target.hasImpulse = true;
        target.hurtMarked = true;
    }

    private static void effect(ServerPlayer target, boolean active) {
        AttributeInstance movement = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        AttributeModifier present = movement.getModifier(HANDCUFFED);
        if (!active) {
            if (present != null) movement.removeModifier(HANDCUFFED);
        } else if (!SLOWNESS.equals(present)) {
            if (present != null) movement.removeModifier(HANDCUFFED);
            movement.addTransientModifier(SLOWNESS);
        }
    }

    private static MinecraftServer nearby(ServerPlayer actor, ServerPlayer target) {
        MinecraftServer server = thread(actor);
        if (target == null || target.getServer() != server || actor.getUUID().equals(target.getUUID())
                || !actor.isAlive() || !target.isAlive() || actor.isSpectator() || target.isSpectator()
                || actor.serverLevel() != target.serverLevel() || actor.distanceToSqr(target) > 9
                || online(server, actor.getUUID()) != actor || online(server, target.getUUID()) != target) {
            throw error("restraint_target");
        }
        return server;
    }

    private static void requireTool(ServerPlayer actor) {
        if (!actor.getMainHandItem().is(BlockRegistry.HANDCUFFS.get())) throw error("handcuffs_required");
    }

    private static ServerPlayer online(MinecraftServer server, UUID player) {
        return runtime(server).offline.contains(player) ? null : server.getPlayerList().getPlayer(player);
    }
    private static RuntimeState runtime(MinecraftServer server) { return RUNTIMES.computeIfAbsent(server, key -> new RuntimeState()); }
    private static MinecraftServer thread(ServerPlayer player) {
        if (player == null || player.getServer() == null) throw error("thread");
        thread(player.getServer());
        return player.getServer();
    }
    private static void thread(MinecraftServer server) { if (server == null || !server.isSameThread()) throw error("thread"); }
    private static long now() { return Math.max(0, System.currentTimeMillis()); }
    private static IllegalStateException error(String key) { return new IllegalStateException("lawsjustice.error." + key); }
    private static void notify(ServerPlayer player, String key) {
        try { JusticeNotifications.send(player, key); }
        catch (RuntimeException | LinkageError ignored) { /* Optional presentation cannot undo a committed arrest. */ }
    }
}
