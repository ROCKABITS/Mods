package pt.realmidentity.compat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Optional public information, added only after the caller's document/session checks. */
public final class TitlesBridge {
    private TitlesBridge() {}

    public static void addTo(Map<String, Object> view, MinecraftServer server, UUID owner) {
        if (server == null || !server.isSameThread() || owner == null) throw new IllegalArgumentException();
        if (!ModList.get().isLoaded("realmtitles")) return;
        long now = System.currentTimeMillis();
        view.put("titlesAsOf", now);
        view.put("titles", List.of());
        try {
            view.put("titles", TitlesApiReader.read(server, owner, now, TitlesBridge.class.getClassLoader()));
            view.put("titlesStatus", "available");
        } catch (RuntimeException | LinkageError unavailable) {
            // A broken optional registry cannot invalidate an authenticated identity record.
            view.put("titlesStatus", "unavailable");
        }
    }
}
