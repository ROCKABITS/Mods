package pt.realmvillagereconomy.compat;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Reads a live, sworn mandate; operator permissions never override Realm Council authority. */
public final class RealmBridge {
    public record Governor(UUID id, String name) {}
    private RealmBridge() {}

    public static boolean loaded() { return ModList.get().isLoaded("realmcouncil"); }

    public static Governor governor(MinecraftServer server, long now) {
        if (server == null || !server.isSameThread()) throw new IllegalStateException("error.realmvillagereconomy.invalid_request");
        if (!loaded()) return null;
        try {
            Class<?> dataType = Class.forName("pt.realmcouncil.CouncilData", true, RealmBridge.class.getClassLoader());
            Object data = dataType.getMethod("get", MinecraftServer.class).invoke(null, server);
            Object state = dataType.getField("state").get(data);
            if (state == null) throw new IllegalStateException("error.realmvillagereconomy.realm_unavailable");
            Object mandate = state.getClass().getField("mandate").get(state);
            if (mandate == null) return null;
            Class<?> type = mandate.getClass();
            Object rawId = type.getField("playerId").get(mandate);
            long claimedAt = type.getField("claimedAt").getLong(mandate);
            long beginsAt = type.getField("beginsAt").getLong(mandate);
            long endsAt = type.getField("endsAt").getLong(mandate);
            if (!(rawId instanceof UUID id)) throw new IllegalStateException("error.realmvillagereconomy.realm_unavailable");
            if (claimedAt <= 0 || claimedAt > now || beginsAt > now || now >= endsAt || beginsAt >= endsAt) return null;
            Object name = type.getField("name").get(mandate);
            return new Governor(id, name instanceof String text ? text : id.toString());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            Throwable cause = ex instanceof InvocationTargetException invocation ? invocation.getCause() : ex;
            throw new IllegalStateException("error.realmvillagereconomy.realm_unavailable", cause);
        }
    }
}
