package pt.cronicasrp.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import pt.cronicasrp.CronicasRP;

public final class CashPackets {
    public static final int WITHDRAW = 0, DEPOSIT = 1, WITHDRAW_PIECES = 2, DEPOSIT_PIECES = 3, DEPOSIT_ALL = 4;
    private CashPackets() {}
    public record Request(int action, long amount, String denomination, int quantity) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CronicasRP.MOD_ID, "cash_exchange"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.action()); buf.writeLong(p.amount()); buf.writeUtf(p.denomination(), 24); buf.writeVarInt(p.quantity());
        }, buf -> new Request(buf.readVarInt(), buf.readLong(), buf.readUtf(24), buf.readVarInt()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Result(boolean success, Component message) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CronicasRP.MOD_ID, "cash_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBoolean(p.success()); MessageCodec.write(buf, p.message());
        }, buf -> new Result(buf.readBoolean(), MessageCodec.read(buf)));
        @Override public Type<Result> type() { return TYPE; }
    }
}
