package pt.lawsjustice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import pt.lawsjustice.service.FinanceService;

/** Public criminal records in a real bound book; no remote judicial authority. */
public final class RegisterBookBlock extends HorizontalJusticeBlock {
    public static final MapCodec<RegisterBookBlock> CODEC=simpleCodec(RegisterBookBlock::new);
    public RegisterBookBlock(Properties properties){super(properties,new double[][]{{1,0,1,15,2,15},{3,2,4,13,13,12},{1,12,2,15,16,14}});}
    @Override protected MapCodec<RegisterBookBlock> codec(){return CODEC;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!level.isClientSide&&player instanceof ServerPlayer actor)FinanceService.open(actor,pos);return InteractionResult.sidedSuccess(level.isClientSide);}
}
