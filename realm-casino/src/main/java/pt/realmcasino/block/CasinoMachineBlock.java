package pt.realmcasino.block;

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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pt.realmcasino.blockentity.CasinoBlockEntity;
import pt.realmcasino.service.CasinoService;

/** Physical furniture addresses world-owned machines; items never carry money. */
public abstract class CasinoMachineBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final ThreadLocal<Set<Removal>> REMOVING = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<BlockPos> CREATIVE_BREAK = new ThreadLocal<>();
    private static final VoxelShape[][] SLOT_SHAPES = slotShapes();
    private static final VoxelShape[] TABLE_SHAPES = tableShapes();
    private record Removal(Level level, BlockPos master) {}

    protected CasinoMachineBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        if (isTall()) state = state.setValue(HALF, DoubleBlockHalf.LOWER);
        registerDefaultState(state);
    }

    public abstract String game();
    public final boolean isTall() { return this instanceof SlotMachineBlock || this instanceof FiveSlotMachineBlock || this instanceof PlinkoBoardBlock || this instanceof FortuneWheelBlock; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        if (isTall()) builder.add(HALF);
    }

    /** Resolve either slot half without loading a chunk. */
    public static BlockPos master(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return pos.immutable();
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CasinoMachineBlock block ? block.master(pos, state) : pos.immutable();
    }

    public final BlockPos master(BlockPos pos, BlockState state) {
        return isTall() && state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.immutable();
    }

    private boolean matches(BlockState state, Direction facing, DoubleBlockHalf half) {
        return state.is(this) && state.getValue(FACING) == facing && (!isTall() || state.getValue(HALF) == half);
    }

    public final boolean validStructure(LevelReader level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
        BlockState lower = level.getBlockState(pos);
        if (!lower.is(this) || isTall() && lower.getValue(HALF) != DoubleBlockHalf.LOWER) return false;
        return !isTall() || !level.isOutsideBuildHeight(pos.above()) && level.hasChunkAt(pos.above())
                && matches(level.getBlockState(pos.above()), lower.getValue(FACING), DoubleBlockHalf.UPPER);
    }

    private static boolean supported(LevelReader level, BlockPos pos) {
        BlockPos floor = pos.below();
        return !level.isOutsideBuildHeight(floor) && level.hasChunkAt(floor)
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos base = context.getClickedPos();
        Player player = context.getPlayer();
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (player == null || !supported(level, base)) return null;
        for (int part = 0; part < (isTall() ? 2 : 1); part++) {
            BlockPos target = base.above(part);
            BlockState state = defaultBlockState().setValue(FACING, facing);
            if (isTall()) state = state.setValue(HALF, part == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
            if (level.isOutsideBuildHeight(target) || !level.hasChunkAt(target)
                    || !level.getWorldBorder().isWithinBounds(target)
                    || !level.getBlockState(target).canBeReplaced(context)
                    || !level.mayInteract(player, target)
                    || !player.mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())
                    || !level.isUnobstructed(state, target, CollisionContext.of(player))) return null;
        }
        if (!level.isClientSide) {
            if (!(player instanceof ServerPlayer serverPlayer)) return null;
            try { CasinoService.mayPlace(serverPlayer, base, game()); }
            catch (RuntimeException | LinkageError failure) {
                player.displayClientMessage(Component.translatable("error.realmcasino.placement_denied"), true);
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        if (!(placer instanceof ServerPlayer player)) throw new PlacementRejected();
        try {
            CasinoService.mayPlace(player, pos, game());
            if (isTall()) {
                BlockPos upper = pos.above();
                if (level.isOutsideBuildHeight(upper) || !level.hasChunkAt(upper)
                        || !level.getBlockState(upper).canBeReplaced()
                        || !level.setBlock(upper, state.setValue(HALF, DoubleBlockHalf.UPPER),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) throw new PlacementRejected();
            }
            CasinoService.placed(player, pos, game());
        } catch (RuntimeException | LinkageError failure) {
            throw failure instanceof PlacementRejected rejected ? rejected : new PlacementRejected(failure);
        }
        level.updateNeighborsAt(pos, this);
        if (isTall()) level.updateNeighborsAt(pos.above(), this);
    }

    // Furniture needs a floor during placement, but later excavation cannot
    // erase a funded bank or an unsettled game. No gravity/neighbor destruction.
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos base = master(pos, state);
            if (validStructure(level, base)) CasinoService.open(serverPlayer, base);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && isTall() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos base = pos.below();
            if (matches(level.getBlockState(base), state.getValue(FACING), DoubleBlockHalf.LOWER)) {
                BlockPos previous = CREATIVE_BREAK.get();
                CREATIVE_BREAK.set(pos.immutable());
                try { level.destroyBlock(base, false, player); }
                finally {
                    if (previous == null) CREATIVE_BREAK.remove(); else CREATIVE_BREAK.set(previous);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean piston) {
        if (!level.isClientSide && (!next.is(this) || next.getValue(FACING) != state.getValue(FACING)
                || isTall() && next.getValue(HALF) != state.getValue(HALF))) {
            BlockPos base = master(pos, state);
            Removal removal = new Removal(level, base);
            Set<Removal> removing = REMOVING.get();
            if (removing.add(removal)) {
                try {
                    if (isTall()) {
                        Direction facing = state.getValue(FACING);
                        if (!pos.equals(base) && level.hasChunkAt(base)
                                && matches(level.getBlockState(base), facing, DoubleBlockHalf.LOWER)) {
                            // A forced command can remove furniture, but a funded
                            // record stays in SavedData and is never itemized.
                            boolean drop = false;
                            try { drop = !CasinoService.hasFunds(level, base); }
                            catch (RuntimeException | LinkageError failure) { /* Fail closed. */ }
                            level.destroyBlock(base, drop);
                        }
                        BlockPos sibling = pos.equals(base) ? base.above() : base;
                        DoubleBlockHalf half = pos.equals(base) ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER;
                        if (!sibling.equals(CREATIVE_BREAK.get()) && level.hasChunkAt(sibling)
                                && matches(level.getBlockState(sibling), facing, half)) level.removeBlock(sibling, false);
                    }
                    CasinoService.removed(level, base);
                } finally {
                    removing.remove(removal);
                    if (removing.isEmpty()) REMOVING.remove();
                }
            }
        }
        super.onRemove(state, level, pos, next, piston);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isTall() && state.getValue(HALF) == DoubleBlockHalf.UPPER ? null : new CasinoBlockEntity(pos, state);
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int facing = state.getValue(FACING).get2DDataValue();
        return isTall() ? SLOT_SHAPES[state.getValue(HALF).ordinal()][facing] : TABLE_SHAPES[facing];
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    private static VoxelShape[] tableShapes() {
        double[][] boxes = {{.2,10.6,.2,15.8,13.3,15.8},
                {1,0,1,3.3,10.6,3.3},{12.7,0,1,15,10.6,3.3},
                {1,0,12.7,3.3,10.6,15},{12.7,0,12.7,15,10.6,15},
                {1.5,3,7.4,14.5,3.8,8.6},{8.5,13.3,2.8,14.8,14.9,10}};
        VoxelShape[] result = new VoxelShape[4];
        for (Direction facing : Direction.Plane.HORIZONTAL) result[facing.get2DDataValue()] = shape(boxes, 0, facing);
        return result;
    }

    private static VoxelShape[][] slotShapes() {
        double[][] boxes = {{1,0,2,13.7,4.1,14.4},{1.8,3.8,2.4,13,25.6,13.5},
                {1.35,13.5,1.55,13.4,15.3,5},{3.25,5.75,.95,11.5,7.3,3.7},
                {1.35,24.5,2,13.35,29.5,13.9},{4.4,29.5,3.4,10.3,31.85,5.4},
                {12.7,17.6,7.2,15.4,24.55,9.6}};
        VoxelShape[][] result = new VoxelShape[2][4];
        for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                result[half.ordinal()][facing.get2DDataValue()] = shape(boxes, half == DoubleBlockHalf.LOWER ? 0 : 16, facing);
            }
        }
        return result;
    }

    private static VoxelShape shape(double[][] boxes, double offset, Direction facing) {
        VoxelShape result = Shapes.empty();
        for (double[] box : boxes) {
            double y0 = Math.max(box[1], offset) - offset, y1 = Math.min(box[4], offset + 16) - offset;
            if (y0 >= y1) continue;
            VoxelShape piece = switch (facing) {
                case NORTH -> Block.box(box[0], y0, box[2], box[3], y1, box[5]);
                case EAST -> Block.box(16-box[5], y0, box[0], 16-box[2], y1, box[3]);
                case SOUTH -> Block.box(16-box[3], y0, 16-box[5], 16-box[0], y1, 16-box[2]);
                case WEST -> Block.box(box[2], y0, 16-box[3], box[5], y1, 16-box[0]);
                default -> throw new IllegalArgumentException("Horizontal facing required");
            };
            result = Shapes.or(result, piece);
        }
        return result.optimize();
    }

    private static final class PlacementRejected extends RuntimeException {
        PlacementRejected() {}
        PlacementRejected(Throwable cause) { super(cause); }
    }

    /** Roll back every occupied cell if a placement hook blocks the structure. */
    public static final class MachineItem extends BlockItem {
        public MachineItem(CasinoMachineBlock block, Item.Properties properties) { super(block, properties); }

        @Override public InteractionResult place(BlockPlaceContext context) {
            if (context.getLevel().isClientSide) return super.place(context);
            CasinoMachineBlock block = (CasinoMachineBlock) getBlock();
            Level level = context.getLevel();
            BlockPos base = context.getClickedPos();
            int count = block.isTall() ? 2 : 1;
            BlockState[] before = new BlockState[count];
            for (int i = 0; i < count; i++) {
                if (!level.hasChunkAt(base.above(i)) || level.isOutsideBuildHeight(base.above(i))) return InteractionResult.FAIL;
                before[i] = level.getBlockState(base.above(i));
            }
            Direction facing = context.getHorizontalDirection().getOpposite();
            try { return super.place(context); }
            catch (PlacementRejected failure) {
                Removal removal = new Removal(level, base);
                Set<Removal> removing = REMOVING.get();
                boolean added = removing.add(removal);
                try {
                    for (int i = 0; i < count; i++) {
                        BlockPos target = base.above(i);
                        BlockState state = level.getBlockState(target);
                        if (state.is(block) && state.getValue(FACING) == facing) level.setBlock(target, before[i], Block.UPDATE_ALL);
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
