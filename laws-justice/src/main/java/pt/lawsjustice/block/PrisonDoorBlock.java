package pt.lawsjustice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;
import pt.lawsjustice.service.JusticeService;

/** Iron cell door: native paired halves and redstone, server-authorized hand use. */
public final class PrisonDoorBlock extends DoorBlock {
    public static final MapCodec<PrisonDoorBlock> CODEC = simpleCodec(PrisonDoorBlock::new);

    public PrisonDoorBlock(Properties properties) {
        super(BlockSetType.IRON, properties);
    }

    @Override public MapCodec<PrisonDoorBlock> codec() { return CODEC; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        // Clients must never predict an opening before the sentence check.
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer actor) || !JusticeService.canUseDoor(actor, pos)) {
            return InteractionResult.FAIL;
        }
        setOpen(actor, level, state, pos, !state.getValue(OPEN));
        return InteractionResult.CONSUME;
    }
}
