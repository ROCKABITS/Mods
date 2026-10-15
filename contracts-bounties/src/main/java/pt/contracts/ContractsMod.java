package pt.contracts;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import pt.contracts.network.ContractsNetwork;

@Mod(ContractsMod.ID)
public final class ContractsMod {
    public static final String ID = "contracts";
    public static final Logger LOG = LogUtils.getLogger();
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredBlock<ContractBoardBlock> BOARD = BLOCKS.register("contract_board",
            () -> new ContractBoardBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)));
    static { ITEMS.registerSimpleBlockItem(BOARD); }

    public ContractsMod(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(ContractsNetwork::register);
        NeoForge.EVENT_BUS.addListener(ContractsMod::commands);
        NeoForge.EVENT_BUS.addListener(ContractsMod::logout);
        NeoForge.EVENT_BUS.addListener(ContractsMod::stopped);
    }

    private static void commands(RegisterCommandsEvent event) {
        var command = Commands.literal("contracts").executes(context -> {
            ContractsService.open(context.getSource().getPlayerOrException(), null);
            return 1;
        }).then(Commands.literal("board").requires(source -> source.hasPermission(2)).executes(context -> {
            var player = context.getSource().getPlayerOrException();
            ItemStack stack = new ItemStack(BOARD.get());
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            return 1;
        }));
        event.getDispatcher().register(command);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ContractsService.logout(event.getEntity().getUUID());
    }
    private static void stopped(ServerStoppedEvent event) { ContractsService.clearSessions(); }
}
