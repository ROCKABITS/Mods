package pt.cronicasrp.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.domain.CharacterRules;
import pt.cronicasrp.domain.RpName;

/** Server-to-client public identity only; never contains biography, wallet or signature data. */
public final class RpNamePackets {
    private RpNamePackets() {}

    public record Update(UUID playerId, String name) implements CustomPacketPayload {
        public Update {
            Objects.requireNonNull(playerId, "playerId");
            name = RpName.checkedUpdate(name);
        }
        public static final Type<Update> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CronicasRP.MOD_ID, "rp_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeUUID(packet.playerId());
            buffer.writeUtf(packet.name(), CharacterRules.NAME_LIMIT);
        }, buffer -> new Update(buffer.readUUID(), buffer.readUtf(CharacterRules.NAME_LIMIT)));
        @Override public Type<Update> type() { return TYPE; }
    }

    /** Clears the previous session/roster before the server sends the currently connected names. */
    public record Reset() implements CustomPacketPayload {
        public static final Type<Reset> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CronicasRP.MOD_ID, "reset_rp_names"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reset> CODEC = StreamCodec.unit(new Reset());
        @Override public Type<Reset> type() { return TYPE; }
    }
}
