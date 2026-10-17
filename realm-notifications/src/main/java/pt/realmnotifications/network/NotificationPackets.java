package pt.realmnotifications.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import pt.realmnotifications.domain.NotificationRules;

public final class NotificationPackets {
    private static final Set<String> WIRE_KEYS = Set.of("text", "translate", "fallback", "with", "extra");
    private NotificationPackets() {}
    public record Show(String kind, Component title, Component body) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("realmnotifications", "show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.of(
                (buffer, packet) -> {
                    buffer.writeUtf(packet.kind, 16);
                    write(buffer, packet.title);
                    write(buffer, packet.body);
                }, buffer -> new Show(buffer.readUtf(16), read(buffer), read(buffer)));
        public Show {
            kind = NotificationRules.kind(kind);
            title = SafeComponents.sanitize(title, NotificationRules.TITLE_LIMIT);
            body = SafeComponents.sanitize(body, NotificationRules.BODY_LIMIT);
        }
        @Override public Type<Show> type() { return TYPE; }
        private static void write(RegistryFriendlyByteBuf buffer, Component component) {
            buffer.writeUtf(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow().toString(), NotificationRules.JSON_LIMIT);
        }
        private static Component read(RegistryFriendlyByteBuf buffer) {
            String json = buffer.readUtf(NotificationRules.JSON_LIMIT);
            NotificationRules.checkJsonEnvelope(json);
            JsonElement element = JsonParser.parseString(json);
            checkWireKeys(element);
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
        }
    }

    /** Reject dynamic/style content before the general component codec can decode hover items/NBT. */
    private static void checkWireKeys(JsonElement value) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (!WIRE_KEYS.contains(entry.getKey())) throw new IllegalArgumentException("Unsupported notification wire content");
                checkWireKeys(entry.getValue());
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) checkWireKeys(child);
        }
    }
}
