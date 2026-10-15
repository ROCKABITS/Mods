package pt.crownledger.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class CrownPackets {
    private CrownPackets() {}
    public record Action(String operation, String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kingdomtreasury", "action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (b,p) -> { b.writeUtf(p.operation, 24); b.writeUtf(p.json, 8192); },
                b -> new Action(b.readUtf(24), b.readUtf(8192)));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kingdomtreasury", "snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (b,p) -> b.writeUtf(p.json, 196608), b -> new Snapshot(b.readUtf(196608)));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
}
