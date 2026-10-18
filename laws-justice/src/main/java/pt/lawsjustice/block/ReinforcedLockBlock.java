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

/** Auxiliary physical custody terminal; it never grants a role or a master key. */
public final class ReinforcedLockBlock extends HorizontalJusticeBlock {
    public static final MapCodec<ReinforcedLockBlock> CODEC = simpleCodec(ReinforcedLockBlock::new);

    public ReinforcedLockBlock(Properties properties) {
        super(properties, new double[][] {{4, 4, 12, 12, 12, 16}});
    }

    @Override protected MapCodec<ReinforcedLockBlock> codec() { return CODEC; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer actor) JusticeService.open(actor, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
