package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class HorseRaceTableBlock extends CasinoMachineBlock {
    public static final MapCodec<HorseRaceTableBlock> CODEC=simpleCodec(HorseRaceTableBlock::new);
    public HorseRaceTableBlock(Properties properties){super(properties);}
    @Override public String game(){return "horses";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
