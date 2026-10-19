package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class ChickenRoadTableBlock extends CasinoMachineBlock {
    public static final MapCodec<ChickenRoadTableBlock> CODEC=simpleCodec(ChickenRoadTableBlock::new);
    public ChickenRoadTableBlock(Properties properties){super(properties);}
    @Override public String game(){return "chicken";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
