package pt.crownledger;

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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.crownledger.block.BlockRegistry;
import pt.crownledger.network.CrownNetwork;
import pt.crownledger.service.CrownService;

@Mod(CrownLedgerMod.ID)
public final class CrownLedgerMod {
    public static final String ID = "kingdomtreasury";
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    static {
        TABS.register("crown_ledger", () -> CreativeModeTab.builder()
                .title(Component.literal("Crown Ledger"))
                .icon(() -> new ItemStack(BlockRegistry.CROWN_CHEST_ITEM.get()))
                .displayItems((parameters, output) -> output.accept(BlockRegistry.CROWN_CHEST_ITEM.get()))
                .build());
    }
    public CrownLedgerMod(IEventBus bus) {
        BlockRegistry.register(bus);
        TABS.register(bus);
        bus.addListener(CrownNetwork::register);
        NeoForge.EVENT_BUS.addListener(CrownLedgerMod::commands);
        NeoForge.EVENT_BUS.addListener(CrownLedgerMod::started);
        NeoForge.EVENT_BUS.addListener(CrownLedgerMod::stopped);
        NeoForge.EVENT_BUS.addListener(CrownLedgerMod::tick);
        NeoForge.EVENT_BUS.addListener(CrownLedgerMod::logout);
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("crown").requires(source -> source.hasPermission(2))
                .then(Commands.literal("chest").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var stack = new ItemStack(BlockRegistry.CROWN_CHEST_ITEM.get());
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                    return 1;
                }))
                .then(Commands.literal("steward").then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            var target = EntityArgument.getPlayer(context, "player");
                            CrownService.appoint(context.getSource(), target);
                            context.getSource().sendSuccess(() -> Component.literal("Crown Ledger: " + target.getGameProfile().getName()), true);
                            return 1;
                        }))));
    }
    private static void started(ServerStartedEvent event) { CrownService.start(event.getServer()); }
    private static void stopped(ServerStoppedEvent event) { CrownService.stop(event.getServer()); }
    private static void tick(ServerTickEvent.Post event) { CrownService.tick(event.getServer()); }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { CrownService.logout(event.getEntity().getUUID()); }
}
