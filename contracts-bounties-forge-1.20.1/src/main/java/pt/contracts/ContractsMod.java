package pt.contracts;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import org.slf4j.Logger;
import pt.contracts.network.ContractsNetwork;

@Mod(ContractsMod.ID)
public final class ContractsMod {
    public static final String ID = "contracts";
    public static final Logger LOG = LogUtils.getLogger();
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final RegistryObject<ContractBoardBlock> BOARD = BLOCKS.register("contract_board",
            () -> new ContractBoardBlock(ContractBoardBlock.Variant.SMALL, boardProperties()));
    public static final RegistryObject<ContractBoardBlock> TALL_BOARD = BLOCKS.register("tall_contract_board",
            () -> new ContractBoardBlock(ContractBoardBlock.Variant.TALL, boardProperties()));
    public static final RegistryObject<ContractBoardBlock> WIDE_BOARD = BLOCKS.register("wide_contract_board",
            () -> new ContractBoardBlock(ContractBoardBlock.Variant.WIDE, boardProperties()));
    static {
        ITEMS.register("contract_board", () -> new BlockItem(BOARD.get(), new Item.Properties()));
        ITEMS.register("tall_contract_board", () -> new BlockItem(TALL_BOARD.get(), new Item.Properties()));
        ITEMS.register("wide_contract_board", () -> new BlockItem(WIDE_BOARD.get(), new Item.Properties()));
    }
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("contracts", () ->
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
        return BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    public ContractsMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener((FMLCommonSetupEvent event) -> { ContractsNetwork.register(); pt.contracts.daily.DailyNetwork.register(); });
        pt.contracts.daily.DailySounds.register(bus);
        MinecraftForge.EVENT_BUS.addListener(pt.contracts.daily.DailyCatalog::register);
        MinecraftForge.EVENT_BUS.addListener(ContractsMod::commands);
        MinecraftForge.EVENT_BUS.addListener(ContractsMod::logout);
        MinecraftForge.EVENT_BUS.addListener(ContractsMod::stopped);
    }

    private static void commands(RegisterCommandsEvent event) {
        var command = Commands.literal("contracts").requires(source -> source.hasPermission(2))
                .then(Commands.literal("board").executes(context -> {
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
        pt.contracts.daily.DailyService.logout(event.getEntity().getUUID());
    }
    private static void stopped(ServerStoppedEvent event) { ContractsService.clearSessions();pt.contracts.daily.DailyService.clear();pt.contracts.daily.DailyEvents.clear(); }
}
