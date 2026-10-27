package pt.realmeconomy.service;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import pt.realmeconomy.domain.ServiceKind;
import pt.realmeconomy.data.EconomyData;
public final class ServiceGates {
 private ServiceGates(){}
 public static boolean enabled(Level level,ServiceKind kind,net.minecraft.core.BlockPos pos){return enabled(level,kind)&&!(kind==ServiceKind.BREWING&&pt.realmeconomy.colony.ColonyBrewing.nativeStation(level,pos));}
 public static boolean enabled(Level level,ServiceKind kind){
  if(!(level instanceof ServerLevel server))return false;
  try{return EconomyData.get(server.getServer()).rule(kind==ServiceKind.ANVIL_COMBINE||kind==ServiceKind.ANVIL_RENAME?ServiceKind.ANVIL_REPAIR:kind).enabled();}
  catch(RuntimeException|LinkageError invalid){return true;}
 }
}
