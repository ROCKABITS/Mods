package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class FiveSlotMachineBlock extends CasinoMachineBlock {
    public static final MapCodec<FiveSlotMachineBlock> CODEC=simpleCodec(FiveSlotMachineBlock::new);
    public FiveSlotMachineBlock(Properties properties){super(properties);}
    @Override public String game(){return "slots_five";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
