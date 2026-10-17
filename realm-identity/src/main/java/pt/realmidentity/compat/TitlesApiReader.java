package pt.realmidentity.compat;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import pt.realmidentity.domain.PublicTitle;

/** The sole optional Titles entry point: no ledger, profile, or mutation API is resolved. */
public final class TitlesApiReader {
    private static final Set<String> FIELDS = Set.of("id", "titleId", "name", "description", "grantedAt", "expiresAt");
    private TitlesApiReader() {}

    public static List<PublicTitle> read(Object server, UUID owner, long now, ClassLoader loader) {
        try {
            if (server == null || owner == null || now < 0) throw new IllegalArgumentException();
            Class<?> serverType = Class.forName("net.minecraft.server.MinecraftServer", false, loader);
            Class<?> api = Class.forName("pt.realmtitles.api.TitlesApi", true, loader);
            var version = api.getField("VERSION");
            var method = api.getMethod("activeTitlesJson", serverType, UUID.class);
            if (version.getType() != int.class || !Modifier.isStatic(version.getModifiers())
                    || version.getInt(null) != 1 || method.getReturnType() != String.class
                    || !Modifier.isStatic(method.getModifiers()) || !serverType.isInstance(server)) {
                throw new IllegalArgumentException();
            }
            // Invoke on every consultation; never retain a previous success after revoke or expiry.
            return parse((String) method.invoke(null, server, owner), now);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IllegalStateException("gui.realmidentity.titles_unavailable", failure);
        }
    }

    static List<PublicTitle> parse(String json, long now) {
        if (json == null || json.length() > 32768 || now < 0) throw new IllegalArgumentException();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            reader.beginArray();
            List<PublicTitle> result = new ArrayList<>();
            Set<UUID> ids = new HashSet<>(), titleIds = new HashSet<>();
            int count = 0;
            while (reader.hasNext()) {
                if (++count > 16) throw new IllegalArgumentException();
                reader.beginObject();
                Set<String> fields = new HashSet<>();
                String id = null, titleId = null, name = null, description = null;
                long grantedAt = -1, expiresAt = -1;
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    if (!FIELDS.contains(field) || !fields.add(field)) throw new IllegalArgumentException();
                    switch (field) {
                        case "id" -> id = string(reader);
                        case "titleId" -> titleId = string(reader);
                        case "name" -> name = string(reader);
                        case "description" -> description = string(reader);
                        case "grantedAt" -> grantedAt = integer(reader);
                        case "expiresAt" -> expiresAt = integer(reader);
                        default -> throw new IllegalArgumentException();
                    }
                }
                reader.endObject();
                if (!fields.equals(FIELDS)) throw new IllegalArgumentException();
                PublicTitle title = new PublicTitle(uuid(id), uuid(titleId), name, description, grantedAt, expiresAt);
                if (!ids.add(title.id()) || !titleIds.add(title.titleId()) || title.grantedAt() > now) {
                    throw new IllegalArgumentException();
                }
                if (title.active(now)) result.add(title);
            }
            reader.endArray();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException();
            return List.copyOf(result);
        } catch (IOException | IllegalStateException failure) {
            throw new IllegalArgumentException("Malformed public titles", failure);
        }
    }

    private static String string(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw new IllegalArgumentException();
        return reader.nextString();
    }
    private static long integer(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw new IllegalArgumentException();
        String token = reader.nextString();
        if (!token.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException();
        return Long.parseLong(token);
    }
    private static UUID uuid(String value) {
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equals(value)) throw new IllegalArgumentException();
        return uuid;
    }
}
