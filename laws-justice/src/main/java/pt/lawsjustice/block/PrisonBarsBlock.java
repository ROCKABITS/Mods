package pt.lawsjustice.block;

import com.mojang.serialization.MapCodec;

/** Self-contained wrought-iron panel; repeat it to build player-designed cells. */
public final class PrisonBarsBlock extends HorizontalJusticeBlock {
    public static final MapCodec<PrisonBarsBlock> CODEC = simpleCodec(PrisonBarsBlock::new);

    public PrisonBarsBlock(Properties properties) {
        super(properties, new double[][] {{0, 0, 6, 16, 16, 10}});
    }

    @Override protected MapCodec<PrisonBarsBlock> codec() { return CODEC; }
}
