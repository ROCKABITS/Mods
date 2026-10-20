package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class MultiplayerBlackjackBlock extends CasinoMachineBlock {
    public static final MapCodec<MultiplayerBlackjackBlock> CODEC=simpleCodec(MultiplayerBlackjackBlock::new);
    public MultiplayerBlackjackBlock(Properties properties){super(properties);}
    @Override public String game(){return "blackjack_pvp";}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
}
