package pt.realmtitles;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.realmtitles.block.BlockRegistry;
import pt.realmtitles.compat.CouncilBridge;
import pt.realmtitles.network.TitleNetwork;
import pt.realmtitles.service.TitleService;

@Mod(RealmTitlesMod.ID)
public final class RealmTitlesMod {
    public static final String ID = "realmtitles";
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    static {
        TABS.register("realm_titles", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.realmtitles"))
                .icon(() -> new ItemStack(BlockRegistry.SEAL_DESK_ITEM.get()))
                .displayItems((parameters, output) -> output.accept(BlockRegistry.SEAL_DESK_ITEM.get())).build());
    }
    public RealmTitlesMod(IEventBus bus) {
        BlockRegistry.register(bus); TABS.register(bus); bus.addListener(TitleNetwork::register);
        NeoForge.EVENT_BUS.addListener(RealmTitlesMod::commands);
        NeoForge.EVENT_BUS.addListener(RealmTitlesMod::login);
        NeoForge.EVENT_BUS.addListener(RealmTitlesMod::logout);
        NeoForge.EVENT_BUS.addListener(RealmTitlesMod::stopped);
        NeoForge.EVENT_BUS.addListener(RealmTitlesMod::tick);
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("titles")
                .executes(context -> { context.getSource().sendSuccess(() -> Component.translatable("gui.realmtitles.command_help"), false); return 1; })
                .then(Commands.literal("desk").requires(source -> source.hasPermission(2)).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var item = new ItemStack(BlockRegistry.SEAL_DESK_ITEM.get());
                    if (!player.getInventory().add(item)) player.drop(item, false);
                    return 1;
                }))
                .then(Commands.literal("steward").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            if (CouncilBridge.loaded()) {
                                context.getSource().sendFailure(Component.translatable("gui.realmtitles.council_present")); return 0;
                            }
                            try {
                                var player = EntityArgument.getPlayer(context, "player");
                                TitleService.appointSteward(player);
                                context.getSource().sendSuccess(() -> Component.translatable("gui.realmtitles.steward_set", player.getDisplayName()), true);
                                return 1;
                            } catch (RuntimeException failure) {
                                context.getSource().sendFailure(Component.translatable("gui.realmtitles.registry_unavailable")); return 0;
                            }
                        }))));
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            try { TitleService.observe(player); }
            catch (RuntimeException ignored) { /* Corrupt storage remains intact and all mutations fail closed. */ }
        }
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { TitleService.logout(event.getEntity().getUUID()); }
    private static void stopped(ServerStoppedEvent event) { TitleService.stop(event.getServer()); }
    private static void tick(ServerTickEvent.Post event) { TitleService.tick(event.getServer()); }
}
