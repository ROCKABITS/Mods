package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class PokerTableBlock extends CasinoMachineBlock {
    public static final MapCodec<PokerTableBlock> CODEC=simpleCodec(PokerTableBlock::new);
    public PokerTableBlock(Properties properties){super(properties);}
    @Override public String game(){return "poker";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
