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

/** Two actual vertical blocks; only the lower half drops an item. */
public final class GymBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public final Exercise exercise;
    private static final ThreadLocal<Boolean> CREATIVE=ThreadLocal.withInitial(()->false);
    public GymBlock(Exercise exercise,Properties properties){super(properties);this.exercise=exercise;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,DoubleBlockHalf.LOWER));}
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(p->new GymBlock(Exercise.BENCH,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,HALF);}
    public static BlockPos anchor(BlockPos pos,BlockState s){return s.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos;}
    public boolean intact(Level level,BlockPos p){
        if(!level.hasChunkAt(p)||!level.hasChunkAt(p.above()))return false;
        var a=level.getBlockState(p);var b=level.getBlockState(p.above());
        return a.is(this)&&b.is(this)&&a.getValue(HALF)==DoubleBlockHalf.LOWER&&b.getValue(HALF)==DoubleBlockHalf.UPPER&&a.getValue(FACING)==b.getValue(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){var p=c.getClickedPos();if(c.getLevel().isOutsideBuildHeight(p.above())||!c.getLevel().hasChunkAt(p.above())||!c.getLevel().getBlockState(p.above()).canBeReplaced(c)||!c.getLevel().getBlockState(p.below()).isFaceSturdy(c.getLevel(),p.below(),Direction.UP)||c.getPlayer()!=null&&(!c.getPlayer().mayUseItemAt(p.above(),Direction.UP,c.getItemInHand())||!c.getLevel().mayInteract(c.getPlayer(),p.above())))return null;return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity entity,ItemStack stack){super.setPlacedBy(l,p,s,entity,stack);if(!l.isClientSide){if(!l.getBlockState(p.above()).canBeReplaced()){l.destroyBlock(p,true);return;}l.setBlock(p.above(),s.setValue(HALF,DoubleBlockHalf.UPPER),3);}}
    @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos q){l.scheduleTick(p,this,1);return s;}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){BlockPos a=anchor(p,s);if(!intact(l,a)||!l.getBlockState(a.below()).isFaceSturdy(l,a.below(),Direction.UP))l.destroyBlock(p,true);}
    @Override public BlockState playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){if(!l.isClientSide&&player.isCreative()){CREATIVE.set(true);try{BlockPos q=s.getValue(HALF)==DoubleBlockHalf.UPPER?p.below():p.above();if(l.getBlockState(q).is(this))l.removeBlock(q,false);}finally{CREATIVE.remove();}}return super.playerWillDestroy(l,p,s,player);}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean piston){if(!l.isClientSide&&!next.is(this)){BlockPos q=s.getValue(HALF)==DoubleBlockHalf.UPPER?p.below():p.above();if(l.hasChunkAt(q)&&l.getBlockState(q).is(this))l.destroyBlock(q,!CREATIVE.get());}super.onRemove(s,l,p,next,piston);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){if(player instanceof ServerPlayer sp)FitnessService.open(sp,anchor(p,s),false);return InteractionResult.sidedSuccess(l.isClientSide);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return s.getValue(HALF)==DoubleBlockHalf.LOWER?Block.box(1,0,1,15,16,15):Block.box(1,0,4,15,15,12);}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
}
