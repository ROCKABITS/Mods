package pt.contracts;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
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
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import pt.contracts.network.ContractsNetwork;

@Mod(ContractsMod.ID)
public final class ContractsMod {
    public static final String ID = "contracts";
    public static final Logger LOG = LogUtils.getLogger();
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredBlock<ContractBoardBlock> BOARD = BLOCKS.register("contract_board",
            () -> new ContractBoardBlock(ContractBoardBlock.Variant.SMALL, boardProperties()));
    public static final DeferredBlock<ContractBoardBlock> TALL_BOARD = BLOCKS.register("tall_contract_board",
            () -> new ContractBoardBlock(ContractBoardBlock.Variant.TALL, boardProperties()));
    public static final DeferredBlock<ContractBoardBlock> WIDE_BOARD = BLOCKS.register("wide_contract_board",
            () -> new ContractBoardBlock(ContractBoardBlock.Variant.WIDE, boardProperties()));
    static {
        ITEMS.registerSimpleBlockItem(BOARD);
        ITEMS.registerSimpleBlockItem(TALL_BOARD);
        ITEMS.registerSimpleBlockItem(WIDE_BOARD);
    }
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("contracts", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.contracts"))
                    .icon(() -> new ItemStack(BOARD.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(BOARD.get());
                        output.accept(TALL_BOARD.get());
                        output.accept(WIDE_BOARD.get());
                    })
                    .build());

    private static BlockBehaviour.Properties boardProperties() {
        return BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion();
    }

    public ContractsMod(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
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
        })).then(Commands.literal("boards").requires(source -> source.hasPermission(2)).executes(context -> {
            var player = context.getSource().getPlayerOrException();
            for (var board : java.util.List.of(BOARD, TALL_BOARD, WIDE_BOARD)) {
                ItemStack stack = new ItemStack(board.get());
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
            return 1;
        }));
        event.getDispatcher().register(command);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ContractsService.logout(event.getEntity().getUUID());
    }
    private static void stopped(ServerStoppedEvent event) { ContractsService.clearSessions(); }
}
