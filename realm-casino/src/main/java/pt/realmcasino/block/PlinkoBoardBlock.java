package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class PlinkoBoardBlock extends CasinoMachineBlock {
    public static final MapCodec<PlinkoBoardBlock> CODEC=simpleCodec(PlinkoBoardBlock::new);
    public PlinkoBoardBlock(Properties properties){super(properties);}
    @Override public String game(){return "plinko";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
