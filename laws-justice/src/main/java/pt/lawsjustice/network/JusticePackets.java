package pt.lawsjustice.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded requests; all authority, coordinates and sentence state remain on the server. */
public final class JusticePackets {
    private JusticePackets() {}
    public record Action(String operation, String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lawsjustice", "action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buffer, packet) -> { buffer.writeUtf(packet.operation, 24); buffer.writeUtf(packet.json, 8192); },
                buffer -> new Action(buffer.readUtf(24), buffer.readUtf(8192)));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lawsjustice", "snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeUtf(packet.json, 524288), buffer -> new Snapshot(buffer.readUtf(524288)));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
}
