package pt.lawsjustice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import pt.lawsjustice.service.CourtService;

/** Physical court desk; every judicial action rechecks the current presidency. */
public final class JusticeDeskBlock extends HorizontalJusticeBlock {
    public static final MapCodec<JusticeDeskBlock> CODEC = simpleCodec(JusticeDeskBlock::new);

    public JusticeDeskBlock(Properties properties) {
        super(properties, new double[][] {
                {.5, 0, .5, 15.5, 10, 15.5},
                {3, 10, 8, 13, 16, 11}
        });
    }

    @Override protected MapCodec<JusticeDeskBlock> codec() { return CODEC; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer actor) CourtService.open(actor, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
