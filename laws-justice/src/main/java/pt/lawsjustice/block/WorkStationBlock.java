package pt.lawsjustice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import pt.lawsjustice.service.WorkService;

/** Player-built physical work furniture. Its position is checked against the registered zone. */
public final class WorkStationBlock extends HorizontalJusticeBlock {
    private final String kind;
    private final MapCodec<WorkStationBlock> codec;
    public WorkStationBlock(Properties properties,String kind){
        super(properties,shape(kind));this.kind=kind;this.codec=simpleCodec(p->new WorkStationBlock(p,kind));
    }
    public String kind(){return kind;}
    private static double[][] shape(String kind){return switch(kind){
        case "BOARD"->new double[][]{{1,0,5,15,16,11}};
        case "DELIVERY"->new double[][]{{1,0,1,15,12,15}};
        case "WORKSHOP"->new double[][]{{1,0,1,15,11,15}};
        default->new double[][]{{2,0,2,14,7,14},{4,7,5,12,16,11}};
    };}
    @Override protected MapCodec<WorkStationBlock> codec(){return codec;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide&&player instanceof ServerPlayer actor)WorkService.open(actor,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
