package pt.realmmarkets.block;

import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pt.realmmarkets.service.MarketService;

/** Four real blocks, one server-owned shop, and exactly one physical item. */
public final class MarketStallBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MarketStallBlock> CODEC = simpleCodec(MarketStallBlock::new);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    private static final VoxelShape[][] SHAPES = shapes();
    private static final ThreadLocal<Set<Removal>> REMOVING = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<BlockPos> CREATIVE_BREAK = new ThreadLocal<>();

    private record Removal(Level level, BlockPos anchor) {}

    public MarketStallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, 0));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** Model front is south; its lower-right part lies to the east. */
    public static BlockPos position(BlockPos master, Direction facing, int part) {
        if (part < 0 || part > 3 || facing.getAxis().isVertical()) throw new IllegalArgumentException("Invalid stall part.");
        return master.relative(facing.getCounterClockWise(), part & 1).above(part >> 1);
    }

    public BlockPos anchor(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.relative(state.getValue(FACING).getCounterClockWise(), -(part & 1)).below(part >> 1);
    }

    private boolean matches(BlockState state, Direction facing, int part) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(PART) == part;
    }

    /** Unknown or unloaded pieces never authorize a trade, and never load a chunk. */
    public boolean validStructure(LevelReader level, BlockPos master, BlockState state) {
        if (!state.is(this)) return false;
        Direction facing = state.getValue(FACING);
        for (int part = 0; part < 4; part++) {
            BlockPos pos = position(master, facing, part);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)
                    || !matches(level.getBlockState(pos), facing, part)) return false;
        }
        return true;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        Player player = context.getPlayer();
        if (player == null) return null;
        for (int part = 0; part < 4; part++) {
            BlockPos pos = position(master, facing, part);
            BlockState state = defaultBlockState().setValue(FACING, facing).setValue(PART, part);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)
                    || !level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())
                    || !level.isUnobstructed(state, pos, CollisionContext.of(player))) return null;
            if (part < 2) {
                BlockPos floor = pos.below();
                if (level.isOutsideBuildHeight(floor) || !level.hasChunkAt(floor)
                        || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return null;
            }
        }
        if (!level.isClientSide) {
            if (!(player instanceof ServerPlayer serverPlayer)) return null;
            try { MarketService.mayPlace(serverPlayer, master); }
            catch (RuntimeException | LinkageError failure) {
                player.displayClientMessage(Component.translatable("gui.realmmarkets.stall_unavailable"), true);
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, 0);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        if (!(placer instanceof ServerPlayer player)) throw new PlacementRejected();
        Direction facing = state.getValue(FACING);
        try {
            MarketService.mayPlace(player, pos);
            // A placement hook may have changed a neighboring cell after preflight.
            for (int part = 1; part < 4; part++) {
                BlockPos target = position(pos, facing, part);
                if (!level.hasChunkAt(target) || !level.getBlockState(target).canBeReplaced()) throw new PlacementRejected();
            }
            for (int part = 1; part < 4; part++) {
                if (!level.setBlock(position(pos, facing, part), state.setValue(PART, part),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) throw new PlacementRejected();
            }
            MarketService.placed(player, pos);
        } catch (RuntimeException | LinkageError failure) {
            throw failure instanceof PlacementRejected rejected ? rejected : new PlacementRejected(failure);
        }
        for (int part = 0; part < 4; part++) level.updateNeighborsAt(position(pos, facing, part), this);
    }

    // Ground is required when placing the shop. Removing its floor afterwards must
    // never erase the finite stock or money entrusted to an existing shop.
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MarketService.open(serverPlayer, anchor(pos, state));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != 0) {
            BlockPos master = anchor(pos, state);
            if (level.hasChunkAt(master) && matches(level.getBlockState(master), state.getValue(FACING), 0)) {
                BlockPos previous = CREATIVE_BREAK.get();
                CREATIVE_BREAK.set(pos.immutable());
                try { level.destroyBlock(master, false, player); }
                finally {
                    if (previous == null) CREATIVE_BREAK.remove();
                    else CREATIVE_BREAK.set(previous);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean piston) {
        if (!level.isClientSide && (!next.is(this) || next.getValue(FACING) != state.getValue(FACING)
                || next.getValue(PART) != state.getValue(PART))) {
            BlockPos master = anchor(pos, state);
            Removal removal = new Removal(level, master);
            Set<Removal> removing = REMOVING.get();
            if (removing.add(removal)) {
                try {
                    Direction facing = state.getValue(FACING);
                    // Only the master has loot. Destroy it once when a survival
                    // player removes a child; siblings are removed without loot.
                    if (!pos.equals(master) && level.hasChunkAt(master)
                            && matches(level.getBlockState(master), facing, 0)) level.destroyBlock(master, true);
                    for (int part = 0; part < 4; part++) {
                        BlockPos sibling = position(master, facing, part);
                        if (!sibling.equals(pos) && !sibling.equals(CREATIVE_BREAK.get()) && level.hasChunkAt(sibling)
                                && matches(level.getBlockState(sibling), facing, part)) level.removeBlock(sibling, false);
                    }
                    // Only an empty record is removed. Administrative commands
                    // cannot silently delete protected stock or unpaid proceeds.
                    MarketService.removed(level, master);
                } finally {
                    removing.remove(removal);
                    if (removing.isEmpty()) REMOVING.remove();
                }
            }
        }
        super.onRemove(state, level, pos, next, piston);
    }

    @Override public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 0; }
    @Override public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) { return 0; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(PART)][state.getValue(FACING).get2DDataValue()];
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Posts, counter, stocked trays and stepped cloth canopy, clipped to each part. */
    private static VoxelShape[][] shapes() {
        double[][] boxes = {
                {0.75, 0, .9, 3.55, 31.3, 3.6}, {28.65, 0, .9, 31.45, 31.3, 3.6},
                {.75, 0, 12.75, 3.55, 25.8, 15.45}, {28.65, 0, 12.75, 31.45, 25.8, 15.45},
                {.35, 10.9, 1, 31.65, 13.1, 15.7}, {1.4, 3, 2.2, 30.6, 10.9, 14.6},
                {3, 13.1, 4, 29, 16.4, 14.8},
                {0, 29.1, 1, 32, 30.8, 5}, {0, 27.7, 5, 32, 29.5, 9},
                {0, 26.3, 9, 32, 28.1, 13}, {0, 23.4, 13, 32, 26.7, 15.8}
        };
        VoxelShape[][] result = new VoxelShape[4][4];
        for (int part = 0; part < 4; part++) {
            int dx = (part & 1) * 16, dy = (part >> 1) * 16;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape shape = Shapes.empty();
                for (double[] box : boxes) {
                    double x0 = Math.max(box[0], dx) - dx, x1 = Math.min(box[3], dx + 16) - dx;
                    double y0 = Math.max(box[1], dy) - dy, y1 = Math.min(box[4], dy + 16) - dy;
                    if (x0 >= x1 || y0 >= y1) continue;
                    VoxelShape piece = switch (facing) {
                        case SOUTH -> Block.box(x0, y0, box[2], x1, y1, box[5]);
                        case WEST -> Block.box(16 - box[5], y0, x0, 16 - box[2], y1, x1);
                        case NORTH -> Block.box(16 - x1, y0, 16 - box[5], 16 - x0, y1, 16 - box[2]);
                        case EAST -> Block.box(box[2], y0, 16 - x1, box[5], y1, 16 - x0);
                        default -> throw new IllegalArgumentException();
                    };
                    shape = Shapes.or(shape, piece);
                }
                result[part][facing.get2DDataValue()] = shape.optimize();
            }
        }
        return result;
    }

    private static final class PlacementRejected extends RuntimeException {
        PlacementRejected() {}
        PlacementRejected(Throwable cause) { super(cause); }
    }

    /** Roll back all four cells before vanilla consumes an item on failed placement. */
    public static final class StallItem extends BlockItem {
        public StallItem(MarketStallBlock block, Item.Properties properties) { super(block, properties); }

        @Override public InteractionResult place(BlockPlaceContext context) {
            if (context.getLevel().isClientSide) return super.place(context);
            Level level = context.getLevel();
            BlockPos master = context.getClickedPos();
            Direction facing = context.getHorizontalDirection().getOpposite();
            BlockState[] before = new BlockState[4];
            for (int part = 0; part < 4; part++) {
                BlockPos pos = position(master, facing, part);
                // Preflight in the block will reject these cells without access.
                if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return InteractionResult.FAIL;
                before[part] = level.getBlockState(pos);
            }
            try { return super.place(context); }
            catch (PlacementRejected failure) {
                // onRemove cleans up the uncommitted structure. Restore replaced
                // vegetation/fluid states afterwards, without dropping a free shop.
                boolean[] restore = new boolean[4];
                for (int part = 0; part < 4; part++) {
                    BlockState current = level.getBlockState(position(master, facing, part));
                    restore[part] = current.is(getBlock()) && current.getValue(FACING) == facing
                            && current.getValue(PART) == part;
                }
                Removal removal = new Removal(level, master);
                Set<Removal> removing = REMOVING.get();
                boolean added = removing.add(removal);
                try {
                    // A failed placement must never run the survival-child path
                    // which destroys its master with loot enabled.
                    for (int part = 0; part < 4; part++) {
                        BlockPos pos = position(master, facing, part);
                        if (restore[part] && level.getBlockState(pos).is(getBlock())) level.removeBlock(pos, false);
                    }
                    for (int part = 0; part < 4; part++) {
                        // Leave an unrelated placement-hook change intact.
                        if (restore[part]) level.setBlock(position(master, facing, part), before[part], Block.UPDATE_ALL);
                    }
                } finally {
                    if (added) removing.remove(removal);
                    if (removing.isEmpty()) REMOVING.remove();
                }
                MarketService.removed(level, master);
                if (context.getPlayer() != null) context.getPlayer().displayClientMessage(
                        Component.translatable("gui.realmmarkets.stall_unavailable"), true);
                return InteractionResult.FAIL;
            }
        }
    }
}
