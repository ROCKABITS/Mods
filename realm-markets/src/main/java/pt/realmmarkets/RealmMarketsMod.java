package pt.realmmarkets;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.realmmarkets.block.BlockRegistry;
import pt.realmmarkets.block.MarketStallBlock;
import pt.realmmarkets.network.MarketNetwork;
import pt.realmmarkets.service.MarketService;

@Mod(RealmMarketsMod.ID)
public final class RealmMarketsMod {
    public static final String ID = "realmmarkets";
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    static {
        TABS.register("realm_markets", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.realmmarkets"))
                .icon(() -> new ItemStack(BlockRegistry.CRIMSON_STALL_ITEM.get()))
                .displayItems((parameters, output) -> {
                    output.accept(BlockRegistry.CRIMSON_STALL_ITEM.get());
                    output.accept(BlockRegistry.SAGE_STALL_ITEM.get());
                }).build());
    }

    public RealmMarketsMod(IEventBus bus) {
        BlockRegistry.register(bus);
        TABS.register(bus);
        bus.addListener(MarketNetwork::register);
        NeoForge.EVENT_BUS.addListener(RealmMarketsMod::commands);
        NeoForge.EVENT_BUS.addListener(RealmMarketsMod::breakBlock);
        NeoForge.EVENT_BUS.addListener(RealmMarketsMod::explosion);
        NeoForge.EVENT_BUS.addListener(RealmMarketsMod::logout);
        NeoForge.EVENT_BUS.addListener(RealmMarketsMod::stopped);
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("markets")
                .then(Commands.literal("stall").requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            for (var item : new net.minecraft.world.item.Item[] {
                                    BlockRegistry.CRIMSON_STALL_ITEM.get(), BlockRegistry.SAGE_STALL_ITEM.get()}) {
                                var stack = new ItemStack(item);
                                if (!player.getInventory().add(stack)) player.drop(stack, false);
                            }
                            return 1;
                        })));
    }

    private static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof MarketStallBlock block)) return;
        boolean permitted = false;
        if (event.getPlayer() instanceof ServerPlayer player) {
            try { permitted = MarketService.canBreak(player, block.anchor(event.getPos(), event.getState())); }
            catch (RuntimeException | LinkageError failure) { /* Keep stored property protected. */ }
            if (!permitted) player.displayClientMessage(Component.translatable("gui.realmmarkets.break_denied"), true);
        }
        if (!permitted) event.setCanceled(true);
    }

    private static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().getBlockState(pos).getBlock() instanceof MarketStallBlock);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) { MarketService.logout(event.getEntity().getUUID()); }
    private static void stopped(ServerStoppedEvent event) { MarketService.stop(event.getServer()); }
}
