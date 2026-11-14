package pt.realmfitness;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RecordBoardEntity extends BlockEntity {
    public String exercise="BENCH",period="all",scope="server",gym="",display="[]";
    public int load=20,repetitions=8;
    public RecordBoardEntity(BlockPos p,BlockState s){super(FitnessMod.BOARD_ENTITY.get(),p,s);}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);t.putString("exercise",exercise);t.putString("period",period);t.putString("scope",scope);t.putString("gym",gym);t.putInt("load",load);t.putInt("repetitions",repetitions);t.putString("display",display);}
    @Override public void load(CompoundTag t){super.load(t);exercise=t.getString("exercise");period=t.getString("period");scope=t.getString("scope");gym=t.getString("gym");load=t.getInt("load");repetitions=t.getInt("repetitions");display=t.getString("display");if(display.length()>8192)display="[]";}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)FitnessService.watch(this);}
    public void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
}
