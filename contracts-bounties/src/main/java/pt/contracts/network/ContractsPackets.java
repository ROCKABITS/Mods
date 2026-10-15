package pt.contracts.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class ContractsPackets {
    private ContractsPackets() {}
    public record Action(String operation, String contractId, String title, String item, int quantity,
                         long reward, String currency) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("contracts", "action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.operation, 20); b.writeUtf(p.contractId, 36); b.writeUtf(p.title, 64); b.writeUtf(p.item, 128);
            b.writeVarInt(p.quantity); b.writeVarLong(p.reward); b.writeUtf(p.currency, 16);
        }, b -> new Action(b.readUtf(20), b.readUtf(36), b.readUtf(64), b.readUtf(128), b.readVarInt(), b.readVarLong(), b.readUtf(16)));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("contracts", "snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (b, p) -> b.writeUtf(p.json, 196608), b -> new Snapshot(b.readUtf(196608)));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
}
