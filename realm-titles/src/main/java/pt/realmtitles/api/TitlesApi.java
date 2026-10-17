package pt.realmtitles.api;

import java.util.UUID;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import pt.realmtitles.data.TitleData;
import pt.realmtitles.domain.TitleLedger;

/** Read-only public API. UUID is the ecosystem player/account identifier, not a separate character ID. */
public final class TitlesApi {
    public static final int VERSION = 1;
    private TitlesApi() {}
    public static String activeTitlesJson(MinecraftServer server, UUID owner) {
        if (server == null || !server.isSameThread() || owner == null)
            throw new IllegalStateException("gui.realmtitles.invalid_request");
        TitleLedger ledger = TitleData.get(server).ledger();
        return activeTitles(ledger, owner, System.currentTimeMillis()).toString();
    }
    public static JsonArray activeTitles(TitleLedger ledger, UUID owner, long now) {
        JsonArray result = new JsonArray();
        for (var grant : ledger.activeGrants(owner, now)) {
            var definition = ledger.definitions().get(grant.titleId());
            JsonObject row = new JsonObject();
            row.addProperty("id", grant.id().toString()); row.addProperty("titleId", grant.titleId().toString());
            row.addProperty("name", definition.name()); row.addProperty("description", definition.description());
            row.addProperty("grantedAt", grant.grantedAt()); row.addProperty("expiresAt", grant.expiresAt());
            result.add(row);
        }
        return result;
    }
}
