package pt.realmeconomy.service;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import pt.realmeconomy.data.EconomyData;
public final class BeaconService {
 private BeaconService(){}
 public static String key(Level level,BlockPos pos){return level.dimension().location()+"@"+pos.asLong();}
 public static int layers(Level level,BlockPos pos){int n=0;outer:for(int depth=1;depth<=4;depth++){for(int x=-depth;x<=depth;x++)for(int z=-depth;z<=depth;z++)if(!level.getBlockState(pos.offset(x,-depth,z)).is(BlockTags.BEACON_BASE_BLOCKS))break outer;n=depth;}return n;}
 public static boolean clearBeam(Level level,BlockPos pos){for(int y=pos.getY()+1;y<level.getMaxBuildHeight();y++){var p=new BlockPos(pos.getX(),y,pos.getZ());var state=level.getBlockState(p);if(state.getBeaconColorMultiplier(level,p,pos)==null&&state.getLightBlock(level,p)>=15&&!state.is(Blocks.BEDROCK))return false;}return true;}
 public static Holder<MobEffect> effect(int index){return switch(index){case 0->MobEffects.MOVEMENT_SPEED;case 1->MobEffects.DIG_SPEED;case 2->MobEffects.DAMAGE_RESISTANCE;case 3->MobEffects.JUMP;case 4->MobEffects.DAMAGE_BOOST;default->throw new IllegalArgumentException("Invalid beacon effect");};}
 public static boolean unlocked(int index,int level){return index>=0&&index<=4&&level>=(index<2?1:index<4?2:3);}
 public static void apply(Level level,BlockPos pos,int layers){
  if(!(level instanceof ServerLevel server)||layers<1)return;var entry=EconomyData.get(server.getServer()).entry("beacons",key(level,pos));long remaining=entry.getLong("until")-System.currentTimeMillis();int primary=entry.getInt("primary"),secondary=entry.getInt("secondary");if(remaining<=0||!entry.hasUUID("owner")||!unlocked(primary,layers))return;
  int duration=(int)Math.min((9+layers*2)*20,Math.max(1,(remaining+49)/50));double radius=layers*10+10;var area=new AABB(pos).inflate(radius).expandTowards(0,level.getHeight(),0);int amplifier=layers>=4&&secondary==1?1:0;
  for(var player:level.getEntitiesOfClass(Player.class,area)){player.addEffect(new MobEffectInstance(effect(primary),duration,amplifier,true,true));if(layers>=4&&secondary==2)player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,duration,0,true,true));}
 }
}
