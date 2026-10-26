package pt.crownledger.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import pt.crownledger.service.LoanService;

/** A craftable credit counter. Contracts belong to the world, never to a block entity. */
public final class LoanDeskBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LoanDeskBlock> CODEC=simpleCodec(LoanDeskBlock::new);
    private static final java.util.Map<Direction,VoxelShape> SHAPES=shapes();
    public LoanDeskBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES.get(s.getValue(FACING));}
    private static java.util.Map<Direction,VoxelShape> shapes(){
        var result=new java.util.EnumMap<Direction,VoxelShape>(Direction.class);
        VoxelShape shape=Shapes.or(Block.box(1,0,1,15,11,15),Block.box(0,11,0,16,12.5,16),Block.box(1.8,12.5,6.4,8.5,14.6,13.5),Block.box(11.7,12.5,10.4,14.4,14.2,13.1),Block.box(11.8,14.2,11.4,13.4,16,12.1));
        for(var facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){result.put(facing,shape.optimize());var boxes=new java.util.ArrayList<VoxelShape>();shape.forAllBoxes((a,b,c,d,e,f)->boxes.add(Shapes.box(1-f,b,a,1-c,e,d)));shape=Shapes.empty();for(var box:boxes)shape=Shapes.or(shape,box);}
        return java.util.Map.copyOf(result);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos pos,Player p,BlockHitResult hit){if(!l.isClientSide&&p instanceof ServerPlayer owner)LoanService.open(owner,pos);return InteractionResult.sidedSuccess(l.isClientSide);}
}
