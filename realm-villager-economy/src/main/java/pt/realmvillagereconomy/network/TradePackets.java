package pt.realmvillagereconomy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class TradePackets {
    private TradePackets() {}
    public record Action(String operation, String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("realmvillagereconomy", "action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buffer, packet) -> { buffer.writeUtf(packet.operation, 16); buffer.writeUtf(packet.json, 2048); },
                buffer -> new Action(buffer.readUtf(16), buffer.readUtf(2048)));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("realmvillagereconomy", "snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buffer, packet) -> buffer.writeUtf(packet.json, 262144), buffer -> new Snapshot(buffer.readUtf(262144)));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
}
