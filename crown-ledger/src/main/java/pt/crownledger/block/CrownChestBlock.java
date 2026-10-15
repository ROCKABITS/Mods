package pt.crownledger.block;

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
import pt.crownledger.service.CrownService;

/** A single decorative chest and ledger. Treasury contents live entirely in saved server data. */
public final class CrownChestBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CrownChestBlock> CODEC = simpleCodec(CrownChestBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    public CrownChestBlock(Properties properties) {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) CrownService.openBoard(serverPlayer, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Map<Direction, VoxelShape> shapes() {
        var result = new EnumMap<Direction, VoxelShape>(Direction.class);
        VoxelShape north = Shapes.or(Block.box(1, 0, 1.5, 15, 13.4, 14.5),
                Block.box(3, 13.4, 5.3, 13, 16, 13.4),
                Block.box(6.6, 4.6, 0.55, 9.4, 8.6, 2.25),
                Block.box(5.8, 8.1, 1.1, 10.2, 11.2, 2.25),
                Block.box(0.5, 6, 5.7, 1.5, 8.3, 10.2),
                Block.box(14.5, 6, 5.7, 15.5, 8.3, 10.2)).optimize();
        result.put(Direction.NORTH, north);
        result.put(Direction.EAST, turn(north));
        result.put(Direction.SOUTH, turn(result.get(Direction.EAST)));
        result.put(Direction.WEST, turn(result.get(Direction.SOUTH)));
        return Map.copyOf(result);
    }

    private static VoxelShape turn(VoxelShape source) {
        var boxes = new java.util.ArrayList<VoxelShape>();
        source.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                boxes.add(Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
        VoxelShape rotated = Shapes.empty();
        for (var box : boxes) rotated = Shapes.or(rotated, box);
        return rotated.optimize();
    }
}
