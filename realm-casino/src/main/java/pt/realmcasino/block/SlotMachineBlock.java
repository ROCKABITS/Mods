package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class SlotMachineBlock extends CasinoMachineBlock {
    public static final MapCodec<SlotMachineBlock> CODEC = simpleCodec(SlotMachineBlock::new);
    public SlotMachineBlock(Properties properties) { super(properties); }
    @Override public String game() { return "slots"; }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
}
