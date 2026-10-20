package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class DiceTableBlock extends CasinoMachineBlock {
    public static final MapCodec<DiceTableBlock> CODEC=simpleCodec(DiceTableBlock::new);
    public DiceTableBlock(Properties properties){super(properties);}
    @Override public String game(){return "dice";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
