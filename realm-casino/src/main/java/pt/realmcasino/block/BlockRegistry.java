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

/** Original pieces of furniture; no bank data is ever written to items. */
public final class BlockRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("realmcasino");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("realmcasino");
    public static final DeferredBlock<SlotMachineBlock> SLOT_MACHINE = BLOCKS.register("slot_machine", () -> new SlotMachineBlock(properties()));
    public static final DeferredBlock<RouletteTableBlock> ROULETTE_TABLE = BLOCKS.register("roulette_table", () -> new RouletteTableBlock(properties()));
    public static final DeferredBlock<BlackjackTableBlock> BLACKJACK_TABLE = BLOCKS.register("blackjack_table", () -> new BlackjackTableBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> SLOT_MACHINE_ITEM = ITEMS.register("slot_machine", () -> new CasinoMachineBlock.MachineItem(SLOT_MACHINE.get(), new Item.Properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> ROULETTE_TABLE_ITEM = ITEMS.register("roulette_table", () -> new CasinoMachineBlock.MachineItem(ROULETTE_TABLE.get(), new Item.Properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> BLACKJACK_TABLE_ITEM = ITEMS.register("blackjack_table", () -> new CasinoMachineBlock.MachineItem(BLACKJACK_TABLE.get(), new Item.Properties()));

    public static final DeferredBlock<PlinkoBoardBlock> PLINKO_BOARD=BLOCKS.register("plinko_board",()->new PlinkoBoardBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> PLINKO_BOARD_ITEM=ITEMS.register("plinko_board",()->new CasinoMachineBlock.MachineItem(PLINKO_BOARD.get(),new Item.Properties()));
    public static final DeferredBlock<FortuneWheelBlock> FORTUNE_WHEEL=BLOCKS.register("fortune_wheel",()->new FortuneWheelBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> FORTUNE_WHEEL_ITEM=ITEMS.register("fortune_wheel",()->new CasinoMachineBlock.MachineItem(FORTUNE_WHEEL.get(),new Item.Properties()));
    public static final DeferredBlock<ChickenRoadTableBlock> CHICKEN_ROAD_TABLE=BLOCKS.register("chicken_road_table",()->new ChickenRoadTableBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> CHICKEN_ROAD_TABLE_ITEM=ITEMS.register("chicken_road_table",()->new CasinoMachineBlock.MachineItem(CHICKEN_ROAD_TABLE.get(),new Item.Properties()));

    public static final DeferredBlock<FiveSlotMachineBlock> FIVE_SLOT_MACHINE=BLOCKS.register("five_slot_machine",()->new FiveSlotMachineBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> FIVE_SLOT_MACHINE_ITEM=ITEMS.register("five_slot_machine",()->new CasinoMachineBlock.MachineItem(FIVE_SLOT_MACHINE.get(),new Item.Properties()));
    public static final DeferredBlock<HorseRaceTableBlock> HORSE_RACE_TABLE=BLOCKS.register("horse_race_table",()->new HorseRaceTableBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> HORSE_RACE_TABLE_ITEM=ITEMS.register("horse_race_table",()->new CasinoMachineBlock.MachineItem(HORSE_RACE_TABLE.get(),new Item.Properties()));
    public static final DeferredBlock<PokerTableBlock> POKER_TABLE=BLOCKS.register("poker_table",()->new PokerTableBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> POKER_TABLE_ITEM=ITEMS.register("poker_table",()->new CasinoMachineBlock.MachineItem(POKER_TABLE.get(),new Item.Properties()));
    public static final DeferredBlock<MultiplayerBlackjackBlock> MULTIPLAYER_BLACKJACK=BLOCKS.register("multiplayer_blackjack_table",()->new MultiplayerBlackjackBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> MULTIPLAYER_BLACKJACK_ITEM=ITEMS.register("multiplayer_blackjack_table",()->new CasinoMachineBlock.MachineItem(MULTIPLAYER_BLACKJACK.get(),new Item.Properties()));
    public static final DeferredBlock<DiceTableBlock> DICE_TABLE=BLOCKS.register("dice_table",()->new DiceTableBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> DICE_TABLE_ITEM=ITEMS.register("dice_table",()->new CasinoMachineBlock.MachineItem(DICE_TABLE.get(),new Item.Properties()));
    public static final DeferredBlock<CoinTableBlock> COIN_TABLE=BLOCKS.register("coin_table",()->new CoinTableBlock(properties()));
    public static final DeferredItem<CasinoMachineBlock.MachineItem> COIN_TABLE_ITEM=ITEMS.register("coin_table",()->new CasinoMachineBlock.MachineItem(COIN_TABLE.get(),new Item.Properties()));

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
