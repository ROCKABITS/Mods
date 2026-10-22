package pt.cronicasrp.network;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** Bounded JSON components preserve translation keys and nested money arguments. */
final class MessageCodec {
    private static final int LIMIT = 8192;
    private MessageCodec() {}
    static void write(RegistryFriendlyByteBuf buffer, Component message) {
        buffer.writeUtf(ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, message).getOrThrow().toString(), LIMIT);
    }
    static Component read(RegistryFriendlyByteBuf buffer) {
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(buffer.readUtf(LIMIT))).getOrThrow();
    }
}
