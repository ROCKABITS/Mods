package pt.realmeconomy.colony;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Optional workshop exception, cached for one second rather than scanning a colony every brewing tick. */
public final class ColonyBrewing {
    private record Check(int tick,boolean workshop){}
    private static final Map<ServerLevel,Map<BlockPos,Check>> CACHE=new WeakHashMap<>();
    public static boolean nativeStation(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!ColonyService.loaded())return false;
        int tick=server.getServer().getTickCount();var cache=CACHE.computeIfAbsent(server,k->new HashMap<>());var old=cache.get(pos);
        if(old!=null&&tick-old.tick()>=0&&tick-old.tick()<20)return old.workshop();
        boolean result=false;try{result=ColonyAccess.alchemistStation(server,pos);}catch(RuntimeException|LinkageError failure){com.mojang.logging.LogUtils.getLogger().debug("Native colony brewing station could not be confirmed",failure);}
        if(cache.size()>=4096)cache.clear();cache.put(pos.immutable(),new Check(tick,result));return result;
    }
    public static void clear(){CACHE.clear();}
}
