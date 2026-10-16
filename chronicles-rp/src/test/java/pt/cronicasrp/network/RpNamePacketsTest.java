package pt.cronicasrp.network;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RpNamePacketsTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
    @Test void publicUpdateContainsExactlyUuidAndBoundedName() {
        UUID id = UUID.fromString("513f6423-104c-414a-81c7-a55ae8719157");
        var update = new RpNamePackets.Update(id, "Aetherion da Lua 🌙");
        var buffer = buffer();
        try {
            RpNamePackets.Update.CODEC.encode(buffer, update);
            // Read the complete wire format independently: no profile, balance, signature or biography.
            assertEquals(id, buffer.readUUID());
            assertEquals(update.name(), buffer.readUtf(40));
            assertEquals(0, buffer.readableBytes());
            buffer.clear();
            RpNamePackets.Update.CODEC.encode(buffer, update);
            assertEquals(update, RpNamePackets.Update.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void removalAndSessionResetRoundTripWithoutExtraData() {
        var removal = new RpNamePackets.Update(UUID.randomUUID(), "");
        var buffer = buffer();
        try {
            RpNamePackets.Update.CODEC.encode(buffer, removal);
            assertEquals(removal, RpNamePackets.Update.CODEC.decode(buffer));
            RpNamePackets.Reset.CODEC.encode(buffer, new RpNamePackets.Reset());
            assertEquals(0, buffer.readableBytes());
            assertEquals(new RpNamePackets.Reset(), RpNamePackets.Reset.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void decoderRejectsOversizedAndLineInjectionPayloads() {
        var buffer = buffer();
        try {
            buffer.writeUUID(UUID.randomUUID()); buffer.writeUtf("A".repeat(41));
            assertThrows(RuntimeException.class, () -> RpNamePackets.Update.CODEC.decode(buffer));
            buffer.clear(); buffer.writeUUID(UUID.randomUUID()); buffer.writeUtf("Ari\n<Admin>");
            assertThrows(IllegalArgumentException.class, () -> RpNamePackets.Update.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void nullIdentityAndMalformedNamesCannotBeConstructedForBroadcast() {
        assertThrows(NullPointerException.class, () -> new RpNamePackets.Update(null, "Ari"));
        assertThrows(IllegalArgumentException.class, () -> new RpNamePackets.Update(UUID.randomUUID(), "§cAri"));
    }
}
