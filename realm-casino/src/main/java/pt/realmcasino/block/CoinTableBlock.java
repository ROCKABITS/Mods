package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class CoinTableBlock extends CasinoMachineBlock {
    public static final MapCodec<CoinTableBlock> CODEC=simpleCodec(CoinTableBlock::new);
    public CoinTableBlock(Properties properties){super(properties);}
    @Override public String game(){return "coin";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
