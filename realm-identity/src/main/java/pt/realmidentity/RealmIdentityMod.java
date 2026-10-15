package pt.realmidentity;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
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
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.realmidentity.block.BlockRegistry;
import pt.realmidentity.network.IdentityNetwork;
import pt.realmidentity.service.IdentityService;

@Mod(RealmIdentityMod.ID)
public final class RealmIdentityMod {
    public static final String ID = "realmidentity";
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    static {
        TABS.register("realm_identity", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.realmidentity"))
                .icon(() -> new ItemStack(BlockRegistry.IDENTITY_DOCUMENT.get()))
                .displayItems((parameters, output) -> {
                    output.accept(BlockRegistry.WRITING_DESK_ITEM.get());
                    output.accept(BlockRegistry.IDENTITY_DOCUMENT.get());
                }).build());
    }
    public RealmIdentityMod(IEventBus bus) {
        BlockRegistry.register(bus);
        TABS.register(bus);
        bus.addListener(IdentityNetwork::register);
        NeoForge.EVENT_BUS.addListener(RealmIdentityMod::commands);
        NeoForge.EVENT_BUS.addListener(RealmIdentityMod::logout);
        NeoForge.EVENT_BUS.addListener(RealmIdentityMod::stopped);
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("identity")
                .then(Commands.literal("inspect").then(Commands.argument("token", UuidArgument.uuid())
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            try {
                                IdentityService.acceptOffer(player, UuidArgument.getUuid(context, "token"));
                                return 1;
                            } catch (RuntimeException error) {
                                String key = error.getMessage();
                                if (key == null || !key.startsWith("gui.realmidentity.")) key = "gui.realmidentity.invalid_request";
                                player.displayClientMessage(Component.translatable(key), false);
                                return 0;
                            }
                        })))
                .then(Commands.literal("desk").requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            var stack = new ItemStack(BlockRegistry.WRITING_DESK_ITEM.get());
                            if (!player.getInventory().add(stack)) player.drop(stack, false);
                            return 1;
                        })));
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { IdentityService.logout(event.getEntity().getUUID()); }
    private static void stopped(ServerStoppedEvent event) { IdentityService.stop(event.getServer()); }
}
