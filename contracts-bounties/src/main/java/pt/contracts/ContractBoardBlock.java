package pt.contracts;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A freestanding board whose larger variants occupy actual neighboring blocks. */
public final class ContractBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ContractBoardBlock> CODEC = simpleCodec(ContractBoardBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final int STRUCTURE_CHECK_INTERVAL = 200;
    private static final VoxelShape[][][] SHAPES = createShapes();
    private static final ThreadLocal<BlockPos> CREATIVE_BREAK_POS = new ThreadLocal<>();
    private final Variant variant;

    public enum Variant {
        SMALL(1, 1), TALL(1, 2), WIDE(2, 2);

        private final int width;
        private final int height;

        Variant(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width() { return width; }
        public int height() { return height; }
        public boolean contains(Part part) { return part.x() < width && part.y() < height; }
    }

    public enum Part implements StringRepresentable {
        LOWER_LEFT("lower_left", 0, 0), LOWER_RIGHT("lower_right", 1, 0),
        UPPER_LEFT("upper_left", 0, 1), UPPER_RIGHT("upper_right", 1, 1);

        private final String name;
        private final int x;
        private final int y;

        Part(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }

        @Override public String getSerializedName() { return name; }
        public int x() { return x; }
        public int y() { return y; }
    }

    public ContractBoardBlock(Properties properties) { this(Variant.SMALL, properties); }

    public ContractBoardBlock(Variant variant, Properties properties) {
        super(properties);
        this.variant = variant;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.LOWER_LEFT));
    }

    public Variant variant() { return variant; }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The lower-left block is the stable location used by the contracts menu. */
    public BlockPos anchor(BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        return pos.relative(state.getValue(FACING).getClockWise(), -part.x()).below(part.y());
    }

    private BlockPos position(BlockPos anchor, Direction facing, Part part) {
        return anchor.relative(facing.getClockWise(), part.x()).above(part.y());
    }

    private boolean matches(BlockState state, Direction facing, Part part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }

    /** Unloaded neighbors are unknown, never air; this method does not load chunks. */
    public boolean validStructure(LevelReader level, BlockPos anchor, BlockState state) {
        if (!variant.contains(state.getValue(PART))) return false;
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            if (!variant.contains(part)) continue;
            BlockPos partPos = position(anchor, facing, part);
            if (level.isOutsideBuildHeight(partPos)) return false;
            if (level.hasChunkAt(partPos) && !matches(level.getBlockState(partPos), facing, part)) return false;
        }
        return true;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos anchor = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        Player player = context.getPlayer();
        for (Part part : Part.values()) {
            if (!variant.contains(part)) continue;
            BlockPos partPos = position(anchor, facing, part);
            if (level.isOutsideBuildHeight(partPos) || !level.hasChunkAt(partPos)
                    || !level.getBlockState(partPos).canBeReplaced(context)) return null;
            if (player != null && (!level.mayInteract(player, partPos)
                    || !player.mayUseItemAt(partPos, context.getClickedFace(), context.getItemInHand()))) return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return state.canSurvive(level, anchor) ? state : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING);
        // Do not overwrite a block if another placement hook changed the footprint.
        for (Part part : Part.values()) {
            if (!variant.contains(part) || part == Part.LOWER_LEFT) continue;
            BlockPos partPos = position(pos, facing, part);
            if (!level.hasChunkAt(partPos) || !level.getBlockState(partPos).canBeReplaced()) {
                level.destroyBlock(pos, true);
                return;
            }
        }
        // Delay neighbor shape updates until every part exists.
        for (Part part : Part.values()) {
            if (!variant.contains(part) || part == Part.LOWER_LEFT) continue;
            level.setBlock(position(pos, facing, part), state.setValue(PART, part), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (Part part : Part.values()) {
            if (!variant.contains(part)) continue;
            BlockPos partPos = position(pos, facing, part);
            level.updateNeighborsAt(partPos, this);
            level.scheduleTick(partPos, this, STRUCTURE_CHECK_INTERVAL);
        }
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!variant.contains(state.getValue(PART))) return false;
        // Existing 0.1.x boards could be placed without a floor; keep them intact.
        if (variant == Variant.SMALL) return true;
        BlockPos anchor = anchor(pos, state);
        Direction right = state.getValue(FACING).getClockWise();
        for (int x = 0; x < variant.width(); x++) {
            BlockPos floor = anchor.relative(right, x).below();
            if (level.isOutsideBuildHeight(floor)) return false;
            if (level.hasChunkAt(floor) && !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        return true;
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                               LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // A deferred check also keeps initial multi-block placement atomic.
        level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && variant != Variant.SMALL) level.scheduleTick(pos, this, STRUCTURE_CHECK_INTERVAL);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canSurvive(state, level, pos) || !validStructure(level, anchor(pos, state), state)) {
            level.destroyBlock(pos, true);
        } else if (variant != Variant.SMALL) {
            // Saved per-part ticks let unloaded orphan parts clean up after reloading.
            level.scheduleTick(pos, this, STRUCTURE_CHECK_INTERVAL);
        }
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != Part.LOWER_LEFT) {
            BlockPos anchor = anchor(pos, state);
            if (level.hasChunkAt(anchor) && matches(level.getBlockState(anchor), state.getValue(FACING), Part.LOWER_LEFT)) {
                BlockPos previous = CREATIVE_BREAK_POS.get();
                CREATIVE_BREAK_POS.set(pos.immutable());
                try {
                    level.destroyBlock(anchor, false, player);
                } finally {
                    if (previous == null) CREATIVE_BREAK_POS.remove();
                    else CREATIVE_BREAK_POS.set(previous);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && (!newState.is(this) || newState.getValue(FACING) != state.getValue(FACING)
                || newState.getValue(PART) != state.getValue(PART))) {
            BlockPos anchor = anchor(pos, state);
            Direction facing = state.getValue(FACING);
            // Only the anchor has a loot entry. Destroy it first so recursive
            // sibling cleanup cannot produce multiple items.
            if (!pos.equals(anchor) && level.hasChunkAt(anchor)
                    && matches(level.getBlockState(anchor), facing, Part.LOWER_LEFT)) {
                level.destroyBlock(anchor, true);
            }
            for (Part part : Part.values()) {
                if (!variant.contains(part)) continue;
                BlockPos partPos = position(anchor, facing, part);
                if (!partPos.equals(pos) && !partPos.equals(CREATIVE_BREAK_POS.get()) && level.hasChunkAt(partPos)
                        && matches(level.getBlockState(partPos), facing, part)) {
                    level.removeBlock(partPos, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[variant.ordinal()][state.getValue(PART).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos anchor = anchor(pos, state);
        if (player instanceof ServerPlayer serverPlayer && validStructure(level, anchor, state)) ContractsService.open(serverPlayer, anchor);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static VoxelShape[][][] createShapes() {
        VoxelShape[][][] shapes = new VoxelShape[Variant.values().length][Part.values().length][4];
        for (Variant variant : Variant.values()) {
            double width = variant.width() * 16.0;
            double[][] boxes = variant == Variant.SMALL ? new double[][] {
                    {7, 0, 7, 9, 10, 9}, {1, 6, 7, 15, 16, 9}
            } : new double[][] {
                    {1, 0, 7, 3, 29.75, 9.15}, {width - 3, 0, 7, width - 1, 29.75, 9.15},
                    {2, 8, 7, width - 2, 26, 9}, {2, 3, 7, width - 2, 5, 9},
                    {0, 26, 0, width, 28, 3}, {0, 28, 3, width, 30, 6},
                    {0, 30, 6, width, 32, 10}, {0, 28, 10, width, 30, 13},
                    {0, 26, 13, width, 28, 16}
            };
            for (Part part : Part.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    VoxelShape shape = Shapes.empty();
                    if (variant.contains(part)) {
                        for (double[] box : boxes) {
                            double x1 = Math.max(box[0], part.x() * 16) - part.x() * 16;
                            double y1 = Math.max(box[1], part.y() * 16) - part.y() * 16;
                            double x2 = Math.min(box[3], (part.x() + 1) * 16) - part.x() * 16;
                            double y2 = Math.min(box[4], (part.y() + 1) * 16) - part.y() * 16;
                            if (x1 < x2 && y1 < y2) shape = Shapes.or(shape, rotatedBox(facing, x1, y1, box[2], x2, y2, box[5]));
                        }
                    }
                    shapes[variant.ordinal()][part.ordinal()][facing.get2DDataValue()] = shape.optimize();
                }
            }
        }
        return shapes;
    }

    private static VoxelShape rotatedBox(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case NORTH -> Block.box(x1, y1, z1, x2, y2, z2);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> throw new IllegalArgumentException("Boards require a horizontal facing");
        };
    }
}
