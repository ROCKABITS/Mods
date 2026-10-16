package pt.cronicasrp.network;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import pt.cronicasrp.ModAttachments;
import pt.cronicasrp.i18n.ModMessages;
import net.minecraft.network.chat.Component;
import pt.cronicasrp.ServerConfig;
import pt.cronicasrp.CashService;
import pt.cronicasrp.RpNames;
import pt.cronicasrp.client.RpNamesClient;
import pt.cronicasrp.client.ClientState;
import pt.cronicasrp.domain.CharacterRules;
import pt.cronicasrp.domain.PlayerStatistics;
import pt.cronicasrp.compat.OriginsBridge;

public final class ProfileNetwork {
    private ProfileNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("7");
        registrar.playToClient(RpNamePackets.Update.TYPE, RpNamePackets.Update.CODEC,
                (packet, context) -> context.enqueueWork(() -> RpNamesClient.accept(packet)));
        registrar.playToClient(RpNamePackets.Reset.TYPE, RpNamePackets.Reset.CODEC,
                (packet, context) -> context.enqueueWork(RpNamesClient::reset));
        registrar.playToServer(ProfilePackets.Request.TYPE, ProfilePackets.Request.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) send(player,
                    packet.open() ? (player.getData(ModAttachments.PROFILE).created() ? ProfilePackets.VIEW : ProfilePackets.CREATE) : ProfilePackets.SYNC, "");
        });
        registrar.playToServer(ProfilePackets.Create.TYPE, ProfilePackets.Create.CODEC, ProfileNetwork::create);
        registrar.playToServer(ProfilePackets.Edit.TYPE, ProfilePackets.Edit.CODEC, ProfileNetwork::edit);
        registrar.playToClient(ProfilePackets.State.TYPE, ProfilePackets.State.CODEC,
                (packet, context) -> context.enqueueWork(() -> ClientState.accept(packet)));
        registrar.playToServer(CashPackets.Request.TYPE, CashPackets.Request.CODEC, (packet, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                Component message = CashService.perform(player, packet);
                PacketDistributor.sendToPlayer(player, new CashPackets.Result(true, message));
            } catch (IllegalArgumentException ex) {
                PacketDistributor.sendToPlayer(player, new CashPackets.Result(false, ModMessages.error(ex.getMessage())));
            }
        });
        registrar.playToClient(CashPackets.Result.TYPE, CashPackets.Result.CODEC,
                (packet, context) -> context.enqueueWork(() -> ClientState.cashResult(packet)));
    }
    private static void create(ProfilePackets.Create packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        try {
            var draft = OriginsBridge.verifiedDraft(player, packet.draft());
            var profile = CharacterRules.create(player.getData(ModAttachments.PROFILE), draft, ServerConfig.settings(),
                    ThreadLocalRandom.current(), java.util.Set.of(draft.race()));
            player.setData(ModAttachments.PROFILE, profile);
            send(player, ProfilePackets.REVEAL, "");
        } catch (IllegalArgumentException ex) {
            send(player, player.getData(ModAttachments.PROFILE).created() ? ProfilePackets.VIEW : ProfilePackets.CREATE, ex.getMessage());
        }
    }
    private static void edit(ProfilePackets.Edit packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        try {
            player.setData(ModAttachments.PROFILE, CharacterRules.editStory(player.getData(ModAttachments.PROFILE), packet.description(), packet.signature(), packet.signatureDrawing(), packet.occupation()));
            send(player, ProfilePackets.VIEW, "");
        } catch (IllegalArgumentException ex) { send(player, ProfilePackets.EDIT, ex.getMessage()); }
    }
    public static void send(ServerPlayer player, int mode, String error) {
        var counter = player.getStats();
        long bosses = (long) counter.getValue(Stats.ENTITY_KILLED.get(EntityType.WITHER))
                + counter.getValue(Stats.ENTITY_KILLED.get(EntityType.ENDER_DRAGON));
        var statistics = new PlayerStatistics(Math.max(0, counter.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME))),
                Math.max(0, counter.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS))), (int) Math.clamp(bosses, 0, Integer.MAX_VALUE));
        var profile = OriginsBridge.synchronize(player);
        RpNames.synchronize(player);
        PacketDistributor.sendToPlayer(player, new ProfilePackets.State(profile, statistics, mode, ModMessages.error(error)));
    }
}
