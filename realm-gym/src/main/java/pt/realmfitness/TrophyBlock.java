package pt.realmfitness;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TrophyBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape PLINTH = Block.box(2.25, 0, 4.25, 13.75, 3, 11.75);
    private static final VoxelShape FRONT = Shapes.or(PLINTH,
        Block.box(5.4, 3, 6.45, 10.7, 11.8, 10.15),
        Block.box(2.6, 10.8, 6.65, 13.4, 15.8, 9.7));
    private static final VoxelShape SIDE = Shapes.or(Block.box(4.25, 0, 2.25, 11.75, 3, 13.75),
        Block.box(5.85, 3, 5.4, 9.55, 11.8, 10.7),
        Block.box(6.3, 10.8, 2.6, 9.35, 15.8, 13.4));

    public TrophyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<TrophyBlock> codec() {
        return simpleCodec(TrophyBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? FRONT : SIDE;
    }
}
