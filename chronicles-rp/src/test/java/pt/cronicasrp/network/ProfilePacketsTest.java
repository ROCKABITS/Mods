package pt.cronicasrp.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.chat.Component;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import pt.cronicasrp.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfilePacketsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrapMinecraft() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    private static String ink() {
        byte[] bytes = new byte[SignatureDrawing.BYTES]; SignatureDrawing.pixel(bytes, 200, 80, true);
        return SignatureDrawing.encode(bytes);
    }
    @Test void longDatapackRaceIdSurvivesCreationAndServerState() {
        String id = "fantasy_pack:" + "royal_".repeat(20) + "elf";
        var draft = new CharacterDraft("Ari", 24, id, "História.", "Ari", ink(), "Mago");
        var profile = new CharacterProfile(true, "Ari", 24, id, "História.", "Ari", "Comum", 11000, 1700, ink());
        var create = new ProfilePackets.Create(draft);
        var state = new ProfilePackets.State(profile, PlayerStatistics.EMPTY, ProfilePackets.SYNC, Component.empty());
        var buf = buffer();
        try {
            ProfilePackets.Create.CODEC.encode(buf, create);
            assertEquals(create, ProfilePackets.Create.CODEC.decode(buf));
            ProfilePackets.State.CODEC.encode(buf, state);
            assertEquals(state, ProfilePackets.State.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
    @Test void fullServerStateRoundtripIncludesProgressAndRealStatisticsWithoutTrailingBytes() {
        var progress = RpProgress.EMPTY.withOccupation("Estudioso").withReputation(64).completeMissions(3)
                .award(RpProgress.Medal.GOLD).award(RpProgress.Medal.BRONZE);
        var profile = new CharacterProfile(true, "Ari da Lua", 128, "Elfo", "Um novo capítulo.", "Ari", "Comum", 110, 17, ink(), progress);
        var packet = new ProfilePackets.State(profile, new PlayerStatistics(92_600, 63, 2), ProfilePackets.SYNC, Component.empty());
        var buf = buffer();
        try { ProfilePackets.State.CODEC.encode(buf, packet); assertEquals(packet, ProfilePackets.State.CODEC.decode(buf)); assertEquals(0, buf.readableBytes()); }
        finally { buf.release(); }
    }
    @Test void CreationEditAndRefreshPacketsKeepTheirEditableFieldsAndIntent() {
        var draft = new CharacterDraft("Ari", 24, "Elfo", "História.", "Ari", ink(), "Mago");
        var create = new ProfilePackets.Create(draft);
        var edit = new ProfilePackets.Edit("Outra história.", "Ari", ink(), "Bardo");
        var buf = buffer();
        try {
            ProfilePackets.Create.CODEC.encode(buf, create); assertEquals(create, ProfilePackets.Create.CODEC.decode(buf));
            ProfilePackets.Edit.CODEC.encode(buf, edit); assertEquals(edit, ProfilePackets.Edit.CODEC.decode(buf));
            ProfilePackets.Request.CODEC.encode(buf, new ProfilePackets.Request(false));
            assertFalse(ProfilePackets.Request.CODEC.decode(buf).open()); assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
}
