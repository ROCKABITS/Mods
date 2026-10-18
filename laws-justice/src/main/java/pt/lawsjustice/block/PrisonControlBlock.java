package pt.lawsjustice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import pt.lawsjustice.service.JusticeService;

/** The physical ledger desk for prison administration and personal sentence views. */
public final class PrisonControlBlock extends HorizontalJusticeBlock {
    public static final MapCodec<PrisonControlBlock> CODEC = simpleCodec(PrisonControlBlock::new);

    public PrisonControlBlock(Properties properties) {
        super(properties, new double[][] {
                {.5, 0, .5, 15.5, 3, 15.5},
                {3, 3, 3, 13, 12, 13},
                {1, 12, 1, 15, 14, 15},
                {2, 14, 3, 11, 16, 13},
                {12, 14, 11, 14, 16, 13}
        });
    }

    @Override protected MapCodec<PrisonControlBlock> codec() { return CODEC; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer actor) JusticeService.open(actor, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
