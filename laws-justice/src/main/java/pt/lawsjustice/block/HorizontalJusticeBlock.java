package pt.lawsjustice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Four north-authored model rotations, with matching physical selection shapes. */
public abstract class HorizontalJusticeBlock extends HorizontalDirectionalBlock {
    private final VoxelShape[] shapes;

    protected HorizontalJusticeBlock(Properties properties, double[][] boxes) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        shapes = rotateShapes(boxes);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                            CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                     CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    private static VoxelShape[] rotateShapes(double[][] boxes) {
        VoxelShape[] result = new VoxelShape[4];
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                VoxelShape piece = switch (facing) {
                    case NORTH -> Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
                    case EAST -> Block.box(16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]);
                    case SOUTH -> Block.box(16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]);
                    case WEST -> Block.box(b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]);
                    default -> throw new IllegalArgumentException("Horizontal prison block facing required");
                };
                shape = Shapes.or(shape, piece);
            }
            result[facing.get2DDataValue()] = shape.optimize();
        }
        return result;
    }
}
