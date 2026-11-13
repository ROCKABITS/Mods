package pt.realmfitness;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import pt.realmfitness.domain.Exercise;

/** Rotatable equipment with real occupied cells and a single item drop. */
public final class GymBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty EXPANDED=BooleanProperty.create("expanded");
    public static final BooleanProperty WORKING=BooleanProperty.create("working");
    public static final IntegerProperty PART=IntegerProperty.create("part",0,17);
    public final Exercise exercise;
    private static final ThreadLocal<Boolean> REMOVING=ThreadLocal.withInitial(()->false);

    public GymBlock(Exercise exercise,Properties properties){
        super(properties);this.exercise=exercise;
        // Missing properties in 0.1 saves resolve to the old two-cell layout.
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,DoubleBlockHalf.LOWER).setValue(EXPANDED,false).setValue(WORKING,false).setValue(PART,0));
    }
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(p->new GymBlock(Exercise.BENCH,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,HALF,EXPANDED,PART,WORKING);}
    public static BlockPos cell(BlockPos anchor,Direction facing,int part){return anchor.relative(facing.getClockWise(),GymGeometry.x(part)).relative(facing.getOpposite(),GymGeometry.z(part)).above(GymGeometry.y(part));}
    public static BlockPos anchor(BlockPos pos,BlockState state){
        if(!state.getValue(EXPANDED))return state.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos;
        int part=state.getValue(PART);Direction facing=state.getValue(FACING);
        return pos.relative(facing.getCounterClockWise(),GymGeometry.x(part)).relative(facing,GymGeometry.z(part)).below(GymGeometry.y(part));
    }
    public BlockState partState(Direction facing,int part){return defaultBlockState().setValue(FACING,facing).setValue(EXPANDED,true).setValue(PART,part).setValue(HALF,GymGeometry.y(part)==0?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER);}
    private boolean matches(BlockState state,BlockState root,int part){return state.is(this)&&state.getValue(FACING)==root.getValue(FACING)&&state.getValue(EXPANDED)==root.getValue(EXPANDED)&&(root.getValue(EXPANDED)?state.getValue(PART)==part&&state.getValue(HALF)==(GymGeometry.y(part)==0?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER):state.getValue(HALF)==(part==0?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER));}
    private BlockPos position(BlockPos anchor,BlockState root,int part){return root.getValue(EXPANDED)?cell(anchor,root.getValue(FACING),part):anchor.above(part);}
    private boolean includes(BlockState root,int part){return root.getValue(EXPANDED)?GymGeometry.contains(exercise,part):part<2;}
    private boolean loaded(Level level,BlockPos anchor,BlockState root){for(int i=0;i<18;i++)if(includes(root,i)&&!level.hasChunkAt(position(anchor,root,i)))return false;return true;}
    public boolean intact(Level level,BlockPos anchor){
        if(!level.hasChunkAt(anchor))return false;BlockState root=level.getBlockState(anchor);
        if(!root.is(this)||root.getValue(HALF)!=DoubleBlockHalf.LOWER||root.getValue(PART)!=0||!loaded(level,anchor,root))return false;
        for(int i=0;i<18;i++)if(includes(root,i)&&!matches(level.getBlockState(position(anchor,root,i)),root,i))return false;
        return true;
    }
    public void working(Level level,BlockPos anchor,boolean active){
        if(!level.hasChunkAt(anchor))return;BlockState root=level.getBlockState(anchor);if(!root.is(this))return;
        for(int i=0;i<18;i++)if(includes(root,i)){BlockPos p=position(anchor,root,i);if(level.hasChunkAt(p)&&matches(level.getBlockState(p),root,i))level.setBlock(p,level.getBlockState(p).setValue(WORKING,active),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
    }
    private boolean supported(Level level,BlockPos anchor,BlockState root){
        int cells=root.getValue(EXPANDED)?GymGeometry.depth(exercise)*2:1;
        for(int i=0;i<cells;i++){BlockPos floor=position(anchor,root,i).below();if(!level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP))return false;}
        return true;
    }
    private boolean room(BlockPlaceContext context,BlockState root){
        Level level=context.getLevel();BlockPos anchor=context.getClickedPos();Player player=context.getPlayer();
        for(int i=0;i<18;i++)if(includes(root,i)){
            BlockPos pos=position(anchor,root,i);
            if(level.isOutsideBuildHeight(pos)||!level.hasChunkAt(pos))return false;
            if(!level.getBlockState(pos).canBeReplaced(context)||!level.isUnobstructed(partState(root.getValue(FACING),i),pos,CollisionContext.empty()))return false;
            if(player!=null&&(!player.mayUseItemAt(pos,Direction.UP,context.getItemInHand())||!level.mayInteract(player,pos)))return false;
        }
        return supported(level,anchor,root);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){BlockState root=partState(context.getHorizontalDirection().getOpposite(),0);return room(context,root)?root:null;}
    @Override public void setPlacedBy(Level level,BlockPos anchor,BlockState root,LivingEntity entity,ItemStack stack){
        super.setPlacedBy(level,anchor,root,entity,stack);if(level.isClientSide)return;
        for(int i=1;i<18;i++)if(includes(root,i)){
            BlockPos pos=position(anchor,root,i);
            if(!level.getBlockState(pos).canBeReplaced()){level.destroyBlock(anchor,true);return;}
        }
        for(int i=1;i<18;i++)if(includes(root,i))level.setBlock(position(anchor,root,i),partState(root.getValue(FACING),i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        for(int i=0;i<18;i++)if(includes(root,i))level.updateNeighborsAt(position(anchor,root,i),this);
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos next){level.scheduleTick(pos,this,1);return state;}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){BlockPos anchor=anchor(pos,state);if(loaded(level,anchor,state)&&(!intact(level,anchor)||!supported(level,anchor,state)))level.destroyBlock(pos,true);}
    private void removeOthers(Level level,BlockPos pos,BlockState state,boolean drops){
        if(REMOVING.get())return;REMOVING.set(true);
        try{
            BlockPos anchor=anchor(pos,state);
            if(!pos.equals(anchor)&&level.hasChunkAt(anchor)&&matches(level.getBlockState(anchor),state,0))level.destroyBlock(anchor,drops);
            for(int i=0;i<18;i++)if(includes(state,i)){
                BlockPos next=position(anchor,state,i);
                if(!next.equals(pos)&&level.hasChunkAt(next)&&matches(level.getBlockState(next),state,i))level.removeBlock(next,false);
            }
        }finally{REMOVING.remove();}
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){if(!level.isClientSide&&player.isCreative())removeOthers(level,pos,state,false);return super.playerWillDestroy(level,pos,state,player);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean piston){if(!level.isClientSide&&!next.is(this))removeOthers(level,pos,state,true);super.onRemove(state,level,pos,next,piston);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player instanceof ServerPlayer serverPlayer)FitnessService.interact(serverPlayer,anchor(pos,state),player.isShiftKeyDown());return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(EXPANDED)?GymGeometry.shape(exercise,state.getValue(PART),state.getValue(FACING),state.getValue(WORKING)):(state.getValue(HALF)==DoubleBlockHalf.LOWER?Block.box(1,0,1,15,16,15):Block.box(1,0,4,15,15,12));}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
