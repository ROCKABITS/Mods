package pt.realmtitles.compat;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Reads the live, sworn mandate on every authorization. Installed failures never use a steward. */
public final class CouncilBridge {
    public record Governor(UUID id, String name) {}
    private CouncilBridge() {}
    public static boolean loaded() { return ModList.get().isLoaded("realmcouncil"); }
    public static Governor governor(MinecraftServer server, long now) {
        if (!loaded()) return null;
        if (server == null || !server.isSameThread() || now < 0) throw unavailable(null);
        try {
            Class<?> dataType = Class.forName("pt.realmcouncil.CouncilData");
            Class<?> stateType = Class.forName("pt.realmcouncil.domain.CouncilState");
            Class<?> mandateType = Class.forName("pt.realmcouncil.domain.CouncilState$Mandate");
            Object data = dataType.getMethod("get", MinecraftServer.class).invoke(null, server);
            if (!dataType.isInstance(data)) throw unavailable(null);
            Object state = dataType.getField("state").get(data);
            if (state == null || state.getClass() != stateType) throw unavailable(null);
            Object mandate = stateType.getField("mandate").get(state);
            if (mandate == null) return null;
            if (mandate.getClass() != mandateType) throw unavailable(null);
            UUID owner = (UUID) mandateType.getField("playerId").get(mandate);
            long begins = mandateType.getField("beginsAt").getLong(mandate);
            long ends = mandateType.getField("endsAt").getLong(mandate);
            long sworn = mandateType.getField("claimedAt").getLong(mandate);
            if (owner == null || begins < 0 || sworn <= 0 || sworn > now || begins > now || now >= ends) return null;
            Object raw = mandateType.getField("name").get(mandate);
            String name = raw instanceof String text && safe(text) ? text : owner.toString();
            return new Governor(owner, name);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError failure) {
            throw unavailable(failure);
        }
    }
    private static boolean safe(String value) {
        return !value.isBlank() && value.length() <= 64 && value.codePoints().noneMatch(Character::isISOControl);
    }
    private static IllegalStateException unavailable(Throwable cause) {
        return new IllegalStateException("gui.realmtitles.governor_unavailable", cause);
    }
}
