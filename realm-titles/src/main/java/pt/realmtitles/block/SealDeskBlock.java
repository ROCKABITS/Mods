package pt.realmtitles.block;

import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pt.realmtitles.service.TitleService;

/** A supported two-block desk: one physical item, and one lower station address. */
public final class SealDeskBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SealDeskBlock> CODEC = simpleCodec(SealDeskBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final int STRUCTURE_CHECK_INTERVAL = 200;
    private static final VoxelShape[][] SHAPES = shapes();
    private static final ThreadLocal<Set<Removal>> REMOVING = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<BlockPos> CREATIVE_BREAK = new ThreadLocal<>();

    private record Removal(Level level, BlockPos master) {}

    public SealDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** Resolves either physical half without loading another chunk. */
    public static BlockPos master(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return pos.immutable();
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SealDeskBlock && state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? pos.below() : pos.immutable();
    }

    private static BlockPos master(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    private boolean matches(BlockState state, Direction facing, DoubleBlockHalf half) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(HALF) == half;
    }

    /** Missing, unsupported or unknown pieces never authorize a station session. */
    public boolean validStructure(LevelReader level, BlockPos master) {
        if (level.isOutsideBuildHeight(master) || level.isOutsideBuildHeight(master.above())
                || !level.hasChunkAt(master) || !level.hasChunkAt(master.above())) return false;
        BlockState lower = level.getBlockState(master);
        return lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                && matches(level.getBlockState(master.above()), lower.getValue(FACING), DoubleBlockHalf.UPPER)
                && supported(level, master);
    }

    private static boolean supported(LevelReader level, BlockPos master) {
        BlockPos floor = master.below();
        return !level.isOutsideBuildHeight(floor) && level.hasChunkAt(floor)
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos base = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        Player player = context.getPlayer();
        if (player == null || !supported(level, base)) return null;
        for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
            BlockPos target = half == DoubleBlockHalf.LOWER ? base : base.above();
            BlockState state = defaultBlockState().setValue(FACING, facing).setValue(HALF, half);
            if (level.isOutsideBuildHeight(target) || !level.hasChunkAt(target)
                    || !level.getWorldBorder().isWithinBounds(target)
                    || !level.getBlockState(target).canBeReplaced(context)
                    || !level.mayInteract(player, target)
                    || !player.mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())
                    || !level.isUnobstructed(state, target, CollisionContext.of(player))) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        // Recheck after placement hooks. A failed pair is rolled back by DeskItem
        // before vanilla consumes the item, and never generates free desk loot.
        BlockPos upper = pos.above();
        if (!matches(level.getBlockState(pos), state.getValue(FACING), DoubleBlockHalf.LOWER)
                || !supported(level, pos) || level.isOutsideBuildHeight(upper) || !level.hasChunkAt(upper)
                || !level.getBlockState(upper).canBeReplaced()
                || !level.setBlock(upper, state.setValue(HALF, DoubleBlockHalf.UPPER),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) throw new PlacementRejected();
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(upper, this);
        level.scheduleTick(pos, this, 1);
        level.scheduleTick(upper, this, 1);
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos base = master(pos, state);
        if (!supported(level, base)) return false;
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                || matches(level.getBlockState(base), state.getValue(FACING), DoubleBlockHalf.LOWER);
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                                LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        // Like a vanilla door, losing the other half or the supporting floor
        // updates immediately. Returning air lets vanilla's updateOrDestroy
        // retain the proper drop flags. Initial placement suppresses these
        // shape updates until both halves exist.
        if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)
                && !matches(neighborState, state.getValue(FACING),
                        half == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !supported(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean piston) {
        super.onPlace(state, level, pos, oldState, piston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!validStructure(level, master(pos, state))) level.destroyBlock(pos, true);
        else level.scheduleTick(pos, this, STRUCTURE_CHECK_INTERVAL);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos base = pos.below();
            if (matches(level.getBlockState(base), state.getValue(FACING), DoubleBlockHalf.LOWER)) {
                BlockPos previous = CREATIVE_BREAK.get();
                CREATIVE_BREAK.set(pos.immutable());
                try { level.destroyBlock(base, false, player); }
                finally {
                    if (previous == null) CREATIVE_BREAK.remove();
                    else CREATIVE_BREAK.set(previous);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean piston) {
        if (!level.isClientSide && (!next.is(this) || next.getValue(HALF) != state.getValue(HALF)
                || next.getValue(FACING) != state.getValue(FACING))) {
            BlockPos base = master(pos, state);
            Removal removal = new Removal(level, base);
            Set<Removal> removing = REMOVING.get();
            if (removing.add(removal)) {
                try {
                    Direction facing = state.getValue(FACING);
                    // Only the lower half has loot. Upper-half destruction
                    // transfers that one drop to the lower half exactly once.
                    if (!pos.equals(base) && level.hasChunkAt(base)
                            && matches(level.getBlockState(base), facing, DoubleBlockHalf.LOWER)) {
                        level.destroyBlock(base, true);
                    }
                    for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                        BlockPos sibling = half == DoubleBlockHalf.LOWER ? base : base.above();
                        if (!sibling.equals(pos) && !sibling.equals(CREATIVE_BREAK.get()) && level.hasChunkAt(sibling)
                                && matches(level.getBlockState(sibling), facing, half)) level.removeBlock(sibling, false);
                    }
                } finally {
                    removing.remove(removal);
                    if (removing.isEmpty()) REMOVING.remove();
                }
            }
        }
        super.onRemove(state, level, pos, next, piston);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                         BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos base = master(pos, state);
            if (validStructure(level, base)) TitleService.openStation(serverPlayer, base);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(HALF).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /** Major model volumes, clipped at the exact lower/upper block boundary. */
    private static VoxelShape[][] shapes() {
        double[][] boxes = {
                {.7, 0, 1.1, 3.3, 11.4, 3.9}, {12.7, 0, 1.1, 15.3, 11.4, 3.9},
                {.7, 0, 12, 3.3, 11.4, 14.8}, {12.7, 0, 12, 15.3, 11.4, 14.8},
                {1.19, 12.7, 12.49, 2.81, 29.8, 14.16}, {13.19, 12.7, 12.49, 14.81, 29.8, 14.16},
                {1.4, 29.8, 12.7, 2.6, 31.5, 13.9}, {13.4, 29.8, 12.7, 14.6, 31.5, 13.9},
                {2, 3.1, 7.4, 14, 4.1, 8.8}, {2, 8.5, 1.6, 14, 11.3, 14.5},
                {.2, 11.1, .6, 15.8, 12.7, 15.4}, {2, 12.7, 2.7, 10.6, 15.2, 10.4},
                {11.1, 12.7, 3, 14.8, 15.9, 6.1}, {11.7, 12.7, 9.5, 15.3, 22.3, 11.6},
                {2.3, 18.3, 12.6, 13.7, 29.4, 14.1}, {3.7, 16.2, 12.6, 12.3, 18.3, 14.1},
                {1, 28.4, 12.1, 15, 30, 14.7}, {5.4, 30, 12.5, 10.6, 31.9, 14.3}
        };
        VoxelShape[][] result = new VoxelShape[2][4];
        for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
            double dy = half == DoubleBlockHalf.LOWER ? 0 : 16;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = Shapes.empty();
                for (double[] box : boxes) {
                    double y0 = Math.max(box[1], dy) - dy, y1 = Math.min(box[4], dy + 16) - dy;
                    if (y0 >= y1) continue;
                    VoxelShape piece = switch (facing) {
                        case NORTH -> Block.box(box[0], y0, box[2], box[3], y1, box[5]);
                        case EAST -> Block.box(16 - box[5], y0, box[0], 16 - box[2], y1, box[3]);
                        case SOUTH -> Block.box(16 - box[3], y0, 16 - box[5], 16 - box[0], y1, 16 - box[2]);
                        case WEST -> Block.box(box[2], y0, 16 - box[3], box[5], y1, 16 - box[0]);
                        default -> throw new IllegalArgumentException("Horizontal desk facing required");
                    };
                    shape = Shapes.or(shape, piece);
                }
                result[half.ordinal()][facing.get2DDataValue()] = shape.optimize();
            }
        }
        return result;
    }

    private static final class PlacementRejected extends RuntimeException {}

    /** Restores both replaceable cells if another placement hook blocks the pair. */
    public static final class DeskItem extends BlockItem {
        public DeskItem(SealDeskBlock block, Item.Properties properties) { super(block, properties); }

        @Override public InteractionResult place(BlockPlaceContext context) {
            if (context.getLevel().isClientSide) return super.place(context);
            Level level = context.getLevel();
            BlockPos base = context.getClickedPos();
            if (!level.hasChunkAt(base) || level.isOutsideBuildHeight(base)
                    || level.isOutsideBuildHeight(base.above())) return InteractionResult.FAIL;
            BlockState[] before = {level.getBlockState(base), level.getBlockState(base.above())};
            Direction facing = context.getHorizontalDirection().getOpposite();
            try { return super.place(context); }
            catch (PlacementRejected failure) {
                Removal removal = new Removal(level, base);
                Set<Removal> removing = REMOVING.get();
                boolean added = removing.add(removal);
                try {
                    for (int index = 0; index < 2; index++) {
                        BlockPos target = base.above(index);
                        BlockState current = level.getBlockState(target);
                        DoubleBlockHalf half = index == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER;
                        if (current.is(getBlock()) && current.getValue(FACING) == facing && current.getValue(HALF) == half) {
                            level.setBlock(target, before[index], Block.UPDATE_ALL);
                        }
                    }
                } finally {
                    if (added) removing.remove(removal);
                    if (removing.isEmpty()) REMOVING.remove();
                }
                return InteractionResult.FAIL;
            }
        }
    }
}
