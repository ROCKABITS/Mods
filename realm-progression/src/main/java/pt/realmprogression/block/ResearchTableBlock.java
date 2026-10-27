package pt.realmprogression.block;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.*;
public final class ResearchTableBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ResearchTableBlock> CODEC=simpleCodec(ResearchTableBlock::new);
    public ResearchTableBlock(BlockBehaviour.Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
    public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(0,0,0,16,16,16);}
}
