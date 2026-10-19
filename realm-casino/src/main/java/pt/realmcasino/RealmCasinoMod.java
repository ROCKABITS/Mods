package pt.realmcasino;

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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.realmcasino.block.BlockRegistry;
import pt.realmcasino.block.CasinoMachineBlock;
import pt.realmcasino.network.CasinoNetwork;
import pt.realmcasino.service.CasinoService;
import pt.realmcasino.service.WalletJournal;

@Mod(RealmCasinoMod.ID)
public final class RealmCasinoMod {
    public static final String ID="realmcasino";
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    static {
        TABS.register("realm_casino",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.realmcasino"))
                .icon(()->new ItemStack(BlockRegistry.SLOT_MACHINE_ITEM.get()))
                .displayItems((parameters,output)->{output.accept(BlockRegistry.SLOT_MACHINE_ITEM.get());output.accept(BlockRegistry.ROULETTE_TABLE_ITEM.get());output.accept(BlockRegistry.BLACKJACK_TABLE_ITEM.get());output.accept(BlockRegistry.PLINKO_BOARD_ITEM.get());output.accept(BlockRegistry.FORTUNE_WHEEL_ITEM.get());output.accept(BlockRegistry.CHICKEN_ROAD_TABLE_ITEM.get());}).build());
    }
    public RealmCasinoMod(IEventBus bus) {
        BlockRegistry.register(bus);TABS.register(bus);bus.addListener(CasinoNetwork::register);
        NeoForge.EVENT_BUS.addListener(RealmCasinoMod::commands);
        NeoForge.EVENT_BUS.addListener(RealmCasinoMod::login);
        NeoForge.EVENT_BUS.addListener(RealmCasinoMod::logout);
        NeoForge.EVENT_BUS.addListener(RealmCasinoMod::clonePlayer);
        NeoForge.EVENT_BUS.addListener(RealmCasinoMod::stopped);
        NeoForge.EVENT_BUS.addListener(RealmCasinoMod::tick);
        NeoForge.EVENT_BUS.addListener(RealmCasinoMod::breakBlock);
        NeoForge.EVENT_BUS.addListener(RealmCasinoMod::explosion);
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("casino").executes(context->{context.getSource().sendSuccess(()->Component.translatable("gui.realmcasino.command_help"),false);return 1;})
                .then(Commands.literal("blocks").requires(source->source.hasPermission(2)).executes(context->{ServerPlayer player=context.getSource().getPlayerOrException();for(var item:java.util.List.of(BlockRegistry.SLOT_MACHINE_ITEM.get(),BlockRegistry.ROULETTE_TABLE_ITEM.get(),BlockRegistry.BLACKJACK_TABLE_ITEM.get(),BlockRegistry.PLINKO_BOARD_ITEM.get(),BlockRegistry.FORTUNE_WHEEL_ITEM.get(),BlockRegistry.CHICKEN_ROAD_TABLE_ITEM.get())){ItemStack stack=new ItemStack(item);if(!player.getInventory().add(stack))player.drop(stack,false);}return 1;})));
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity()instanceof ServerPlayer player)CasinoService.login(player);}
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity()instanceof ServerPlayer player)CasinoService.logout(player);}
    private static void clonePlayer(PlayerEvent.Clone event){var receipt=event.getOriginal().getPersistentData().get(WalletJournal.RECEIPT_KEY);if(receipt!=null)event.getEntity().getPersistentData().put(WalletJournal.RECEIPT_KEY,receipt.copy());}
    private static void stopped(ServerStoppedEvent event){CasinoService.stop(event.getServer());}
    private static void tick(ServerTickEvent.Post event){CasinoService.tick(event.getServer());}
    private static void breakBlock(BlockEvent.BreakEvent event){if(event.getPlayer()instanceof ServerPlayer player&&event.getState().getBlock()instanceof CasinoMachineBlock&&!CasinoService.canBreak(player,CasinoMachineBlock.master(player.level(),event.getPos())))event.setCanceled(true);}
    private static void explosion(ExplosionEvent.Detonate event){event.getAffectedBlocks().removeIf(pos->event.getLevel().getBlockState(pos).getBlock()instanceof CasinoMachineBlock);}
}
