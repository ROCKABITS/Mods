package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class RouletteTableBlock extends CasinoMachineBlock {
    public static final MapCodec<RouletteTableBlock> CODEC = simpleCodec(RouletteTableBlock::new);
    public RouletteTableBlock(Properties properties) { super(properties); }
    @Override public String game() { return "roulette"; }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
}
