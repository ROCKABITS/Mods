package pt.cronicasrp.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.domain.CharacterDraft;
import pt.cronicasrp.domain.CharacterProfile;
import pt.cronicasrp.domain.SignatureDrawing;
import pt.cronicasrp.domain.PlayerStatistics;
import pt.cronicasrp.domain.RpProgress;

public final class ProfilePackets {
    public static final int SYNC = 0, CREATE = 1, VIEW = 2, EDIT = 3, REVEAL = 4, CASH = 5;
    private ProfilePackets() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CronicasRP.MOD_ID, name));
    }
    public record Request(boolean open) implements CustomPacketPayload {
        public Request() { this(true); }
        public static final Type<Request> TYPE = ProfilePackets.type("request_profile");
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, packet) -> buf.writeBoolean(packet.open()), buf -> new Request(buf.readBoolean()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Create(CharacterDraft draft) implements CustomPacketPayload {
        public static final Type<Create> TYPE = ProfilePackets.type("create_profile");
        public static final StreamCodec<RegistryFriendlyByteBuf, Create> CODEC = StreamCodec.of((buf, packet) -> {
            CharacterDraft d = packet.draft();
            buf.writeUtf(d.name(), 40); buf.writeVarInt(d.age()); buf.writeUtf(d.race(), pt.cronicasrp.domain.CharacterRules.RACE_LIMIT);
            buf.writeUtf(d.description(), 600); buf.writeUtf(d.signature(), 60);
            buf.writeUtf(d.signatureDrawing(), SignatureDrawing.MAX_LENGTH);
            buf.writeUtf(d.occupation(), 24);
        }, buf -> new Create(new CharacterDraft(buf.readUtf(40), buf.readVarInt(), buf.readUtf(pt.cronicasrp.domain.CharacterRules.RACE_LIMIT), buf.readUtf(600), buf.readUtf(60), buf.readUtf(SignatureDrawing.MAX_LENGTH), buf.readUtf(24))));
        @Override public Type<Create> type() { return TYPE; }
    }
    public record Edit(String description, String signature, String signatureDrawing, String occupation) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = ProfilePackets.type("edit_story");
        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = StreamCodec.of((buf, packet) -> {
            buf.writeUtf(packet.description(), 600); buf.writeUtf(packet.signature(), 60);
            buf.writeUtf(packet.signatureDrawing(), SignatureDrawing.MAX_LENGTH);
            buf.writeUtf(packet.occupation(), 24);
        }, buf -> new Edit(buf.readUtf(600), buf.readUtf(60), buf.readUtf(SignatureDrawing.MAX_LENGTH), buf.readUtf(24)));
        @Override public Type<Edit> type() { return TYPE; }
    }
    public record State(CharacterProfile profile, PlayerStatistics statistics, int mode, Component error) implements CustomPacketPayload {
        public static final Type<State> TYPE = ProfilePackets.type("profile_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buf, packet) -> {
            CharacterProfile p = packet.profile();
            buf.writeBoolean(p.created()); buf.writeUtf(p.name(), 40); buf.writeVarInt(p.age());
            buf.writeUtf(p.race(), pt.cronicasrp.domain.CharacterRules.RACE_LIMIT); buf.writeUtf(p.description(), 600); buf.writeUtf(p.signature(), 60);
            buf.writeUtf(p.origin(), 64); buf.writeLong(p.initialBalance()); buf.writeLong(p.balance());
            buf.writeUtf(p.signatureDrawing(), SignatureDrawing.MAX_LENGTH);
            RpProgress rp = p.progress();
            buf.writeUtf(rp.occupation(), 24); buf.writeVarInt(rp.reputation()); buf.writeVarInt(rp.missions());
            buf.writeVarInt(rp.medals()); buf.writeUtf(rp.bestMedal().id(), 16);
            PlayerStatistics stats = packet.statistics();
            buf.writeVarLong(stats.playTicks()); buf.writeVarInt(stats.creaturesDefeated()); buf.writeVarInt(stats.bossesDefeated());
            buf.writeVarInt(packet.mode()); MessageCodec.write(buf, packet.error());
        }, buf -> new State(new CharacterProfile(buf.readBoolean(), buf.readUtf(40), buf.readVarInt(),
                buf.readUtf(pt.cronicasrp.domain.CharacterRules.RACE_LIMIT), buf.readUtf(600), buf.readUtf(60), buf.readUtf(64), buf.readLong(), buf.readLong(), buf.readUtf(SignatureDrawing.MAX_LENGTH),
                new RpProgress(buf.readUtf(24), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), RpProgress.Medal.fromId(buf.readUtf(16)))),
                new PlayerStatistics(buf.readVarLong(), buf.readVarInt(), buf.readVarInt()),
                buf.readVarInt(), MessageCodec.read(buf)));
        @Override public Type<State> type() { return TYPE; }
    }
}
