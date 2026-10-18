package pt.realmcasino.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class BlackjackTableBlock extends CasinoMachineBlock {
    public static final MapCodec<BlackjackTableBlock> CODEC = simpleCodec(BlackjackTableBlock::new);
    public BlackjackTableBlock(Properties properties) { super(properties); }
    @Override public String game() { return "blackjack"; }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
}
