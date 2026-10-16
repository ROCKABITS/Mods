package pt.cronicasrp.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.domain.CharacterProfile;
import pt.cronicasrp.domain.PlayerStatistics;
import pt.cronicasrp.network.ProfilePackets;
import pt.cronicasrp.network.CashPackets;

@EventBusSubscriber(modid = CronicasRP.MOD_ID, value = Dist.CLIENT)
public final class ClientState {
    private static CharacterProfile profile = CharacterProfile.empty();
    private static PlayerStatistics statistics = PlayerStatistics.EMPTY;
    private static int refreshTicks;
    private static int pending = ProfilePackets.SYNC;
    private static boolean wasInWorld;
    private ClientState() {}
    public static CharacterProfile profile() { return profile; }
    public static PlayerStatistics statistics() { return statistics; }
    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
    private static void reset() {
        profile = CharacterProfile.empty(); statistics = PlayerStatistics.EMPTY;
        refreshTicks = 0; pending = ProfilePackets.SYNC; wasInWorld = false;
        OriginsCreationFlow.reset();
    }
    public static void accept(ProfilePackets.State packet) {
        profile = packet.profile();
        statistics = packet.statistics();
        Minecraft minecraft = Minecraft.getInstance();
        if (!packet.error().getString().isEmpty() && minecraft.screen instanceof CharacterCreationScreen screen
                && (!profile.created() || screen.isEditing())) {
            screen.serverError(packet.error()); return;
        }
        if (packet.mode() == ProfilePackets.VIEW && profile.created()) {
            minecraft.setScreen(new CharacterProfileScreen()); pending = ProfilePackets.SYNC;
        } else if (packet.mode() == ProfilePackets.CREATE && !profile.created()) {
            pending = ProfilePackets.CREATE;
        } else if (packet.mode() == ProfilePackets.EDIT && profile.created()) {
            minecraft.setScreen(new CharacterCreationScreen(true));
        } else if (packet.mode() == ProfilePackets.REVEAL && profile.created()) {
            pending = ProfilePackets.SYNC;
            minecraft.setScreen(new DestinyRevealScreen(profile));
        } else if (packet.mode() == ProfilePackets.CASH && profile.created()) {
            minecraft.setScreen(new CurrencyExchangeScreen()); pending = ProfilePackets.SYNC;
        }
    }
    public static void cashResult(CashPackets.Result result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CurrencyExchangeScreen screen) screen.serverResult(result);
    }
    // Before vanilla handles P and releases key clicks while opening its Social screen.
    @SubscribeEvent public static void profileKey(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        while (ClientModEvents.PROFILE_KEY.consumeClick()) {
            if (minecraft.screen == null) {
                if (ClientModEvents.PROFILE_KEY.same(minecraft.options.keySocialInteractions))
                    while (minecraft.options.keySocialInteractions.consumeClick()) {}
                PacketDistributor.sendToServer(new ProfilePackets.Request());
            }
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            if (wasInWorld) reset();
            refreshTicks = 0;
            return;
        }
        wasInWorld = true;
        OriginsCreationFlow.tick();
        if (pending == ProfilePackets.CREATE && minecraft.screen == null) {
            pending = ProfilePackets.SYNC; minecraft.setScreen(new CharacterCreationScreen(false));
        }
        if ((minecraft.screen instanceof CharacterProfileScreen || minecraft.screen instanceof CurrencyExchangeScreen) && !minecraft.isPaused()) {
            if (++refreshTicks >= 40) { refreshTicks = 0; PacketDistributor.sendToServer(new ProfilePackets.Request(false)); }
        } else refreshTicks = 0;
    }
}
