package pt.realmidentity.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pt.realmidentity.service.IdentityService;

/** A single oak writing desk. It holds neither documents nor any identity data. */
public final class WritingDeskBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WritingDeskBlock> CODEC = simpleCodec(WritingDeskBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    public WritingDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            IdentityService.openStation(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Map<Direction, VoxelShape> shapes() {
        var result = new EnumMap<Direction, VoxelShape>(Direction.class);
        VoxelShape north = Shapes.or(
                Block.box(1, 0, 2, 4, 10.5, 5), Block.box(12, 0, 2, 15, 10.5, 5),
                Block.box(1, 0, 11, 4, 10.5, 14), Block.box(12, 0, 11, 15, 10.5, 14),
                Block.box(.5, 10, 1, 15.5, 12, 15),
                Block.box(2, 8, 2, 14, 10.5, 14),
                Block.box(2, 3, 7, 14, 4, 9),
                Block.box(2, 12, 2, 10.6, 12.6, 12.5),
                Block.box(11.2, 12, 10.2, 13.8, 14, 12.8),
                Block.box(11, 13, 10.5, 13.5, 15.8, 12.5),
                Block.box(11.5, 12, 3.3, 13.5, 13.6, 5.3)).optimize();
        result.put(Direction.NORTH, north);
        result.put(Direction.EAST, turn(north));
        result.put(Direction.SOUTH, turn(result.get(Direction.EAST)));
        result.put(Direction.WEST, turn(result.get(Direction.SOUTH)));
        return Map.copyOf(result);
    }

    private static VoxelShape turn(VoxelShape source) {
        var rotated = new java.util.ArrayList<VoxelShape>();
        source.forAllBoxes((x0, y0, z0, x1, y1, z1) ->
                rotated.add(Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
        VoxelShape result = Shapes.empty();
        for (var box : rotated) result = Shapes.or(result, box);
        return result.optimize();
    }
}
