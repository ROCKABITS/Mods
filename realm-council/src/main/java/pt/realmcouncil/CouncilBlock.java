package pt.realmcouncil;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.server.level.ServerPlayer;
public final class CouncilBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CouncilBlock> CODEC=simpleCodec(p->new CouncilBlock("board",p));
    public final String mode;
    public CouncilBlock(String mode,Properties p){super(p);this.mode=mode;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return simpleCodec(p->new CouncilBlock(mode,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter level,BlockPos pos,CollisionContext context){
        if(mode.equals("urn"))return Block.box(2,0,2,14,14,14);
        if(mode.equals("oath"))return Block.box(0,0,0,16,16,16);
        var dir=s.getValue(FACING);return dir.getAxis()==Direction.Axis.X?Block.box(6,0,0,10,16,16):Block.box(0,0,6,16,16,10);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&player instanceof ServerPlayer p)CouncilService.open(p,mode,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
