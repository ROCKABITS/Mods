package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class FortuneWheelBlock extends CasinoMachineBlock {
    public static final MapCodec<FortuneWheelBlock> CODEC=simpleCodec(FortuneWheelBlock::new);
    public FortuneWheelBlock(Properties properties){super(properties);}
    @Override public String game(){return "wheel";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
