package pt.realmcasino.block;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.realmcasino.blockentity.CasinoBlockEntities;

/** Three original pieces of furniture; no bank data is ever written to items. */
public final class BlockRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("realmcasino");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realmcasino");
    public static final DeferredBlock<SlotMachineBlock> SLOT_MACHINE = BLOCKS.register("slot_machine", () -> new SlotMachineBlock(properties()));
    public static final DeferredBlock<RouletteTableBlock> ROULETTE_TABLE = BLOCKS.register("roulette_table", () -> new RouletteTableBlock(properties()));
    public static final DeferredBlock<BlackjackTableBlock> BLACKJACK_TABLE = BLOCKS.register("blackjack_table", () -> new BlackjackTableBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> SLOT_MACHINE_ITEM = ITEMS.register("slot_machine", () -> new CasinoMachineBlock.MachineItem(SLOT_MACHINE.get(), new Item.Properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> ROULETTE_TABLE_ITEM = ITEMS.register("roulette_table", () -> new CasinoMachineBlock.MachineItem(ROULETTE_TABLE.get(), new Item.Properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> BLACKJACK_TABLE_ITEM = ITEMS.register("blackjack_table", () -> new CasinoMachineBlock.MachineItem(BLACKJACK_TABLE.get(), new Item.Properties()));

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().strength(2.5F, 3_600_000F).sound(SoundType.WOOD)
                .noOcclusion().pushReaction(PushReaction.BLOCK);
    }
    private BlockRegistry() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        CasinoBlockEntities.register(bus);
    }
}
