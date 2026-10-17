package pt.realmrewards.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class RewardPackets {
    private RewardPackets() {}
    public record Action(String operation,String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("realmrewards","action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of(
                (b,p)->{ b.writeUtf(p.operation,16); b.writeUtf(p.json,2048); },b->new Action(b.readUtf(16),b.readUtf(2048)));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("realmrewards","snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
                (b,p)->b.writeUtf(p.json,65536),b->new Snapshot(b.readUtf(65536)));
        @Override public Type<Snapshot> type() { return TYPE; }
    }
}
