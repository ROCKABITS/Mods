package pt.realmfitness;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pt.realmfitness.domain.Exercise;

/** A temporary, tracked vehicle: the player actually rides the physical equipment. */
public final class GymMount extends Entity {
    private static final EntityDataAccessor<BlockPos> ANCHOR=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> FACING=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.INT),EXERCISE=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.INT),TICK=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.INT),KEYS=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.INT),ACTION=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.INT),ACTION_TICK=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> COMPACT=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> EFFORT=SynchedEntityData.defineId(GymMount.class,EntityDataSerializers.FLOAT);
    private Vec3 entrance;
    private long received=System.nanoTime(),effortReceived=System.nanoTime();
    private double visualCorrection,visualServer,fromEffort=.5,toEffort=.5;
    public GymMount(EntityType<? extends GymMount> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(ANCHOR,BlockPos.ZERO);b.define(FACING,Direction.NORTH.get3DDataValue());b.define(EXERCISE,0);b.define(TICK,0);b.define(KEYS,0);b.define(ACTION,0);b.define(ACTION_TICK,-100);b.define(COMPACT,false);b.define(EFFORT,.5f);}
    public void configure(BlockPos anchor,Direction facing,Exercise exercise,boolean compact,Vec3 entrance){entityData.set(ANCHOR,anchor.immutable());entityData.set(FACING,facing.get3DDataValue());entityData.set(EXERCISE,exercise.ordinal());entityData.set(COMPACT,compact);this.entrance=entrance;setPos(actorPosition());}
    public BlockPos anchor(){return entityData.get(ANCHOR);}
    public Direction facing(){return Direction.from3DDataValue(entityData.get(FACING));}
    public Exercise exercise(){return Exercise.values()[Math.clamp(entityData.get(EXERCISE),0,5)];}
    public boolean compact(){return entityData.get(COMPACT);}
    public double effort(){double t=Math.clamp((System.nanoTime()-effortReceived)/75_000_000.0,0,1);return fromEffort+(toEffort-fromEffort)*t;}
    public int keys(){return entityData.get(KEYS);}
    public int action(){return entityData.get(ACTION);}
    public float actionAge(){return visualTick()-entityData.get(ACTION_TICK);}
    public float visualTick(){if(entityData.get(TICK)==0)return 0;double elapsed=Math.clamp((System.nanoTime()-received)/1_000_000_000.0,0,.15);return (float)(visualServer+elapsed*20+visualCorrection*Math.exp(-elapsed/.12));}
    public float actorYaw(){return (exercise()==Exercise.BENCH?facing():facing().getOpposite()).toYRot();}
    public void progress(int tick,double effort,int keys){int edges=keys&~keys();if(edges!=0){entityData.set(ACTION,edges);entityData.set(ACTION_TICK,tick);}entityData.set(TICK,tick);entityData.set(EFFORT,(float)effort);entityData.set(KEYS,keys);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key==TICK){double previous=visualServer==0?0:visualTick();double next=entityData.get(TICK);boolean newRound=Math.abs(next-visualServer)>4;visualServer=next;visualCorrection=newRound?0:previous-visualServer;received=System.nanoTime();}else if(key==EFFORT){fromEffort=effort();toEffort=entityData.get(EFFORT);effortReceived=System.nanoTime();}}
    public Vec3 local(double x,double y,double z){double scale=compact()?.48:1;Vec3 a=Vec3.atLowerCornerOf(anchor());Direction right=facing().getClockWise(),back=facing().getOpposite();return a.add(.5+.5*right.getStepX()+.5*back.getStepX(),0,.5+.5*right.getStepZ()+.5*back.getStepZ()).add((x-1)*right.getStepX()*scale,y*scale,(x-1)*right.getStepZ()*scale).add((z-1)*back.getStepX()*scale,0,(z-1)*back.getStepZ()*scale);}
    private Vec3 actorPosition(){return switch(exercise()){
        case BENCH->local(1,0,1.0);case RUN->local(1,.394,1.25);case BOX->local(.94,0,.03);case PULL->local(1,.5,.47);case ROW->local(1,0,.65);case SQUAT->local(1,.15,1.1875);
    };}
    @Override protected void positionRider(Entity rider,Entity.MoveFunction move){Vec3 p=actorPosition();move.accept(rider,p.x,p.y,p.z);rider.setYBodyRot(actorYaw());rider.fallDistance=0;}
    @Override protected boolean canAddPassenger(Entity passenger){return getPassengers().isEmpty();}
    @Override public boolean shouldRiderSit(){return false;}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public void tick(){super.tick();setDeltaMovement(Vec3.ZERO);if(!level().isClientSide&&tickCount>5&&(getPassengers().isEmpty()||!(level().getBlockState(anchor()).getBlock() instanceof GymBlock block)||!block.intact(level(),anchor())))discard();}
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity rider){return safeExit(rider);}
    public Vec3 safeExit(LivingEntity rider){
        if(entrance!=null&&safe(rider,entrance))return entrance;
        for(int distance=1;distance<=4;distance++)for(Direction d:Direction.Plane.HORIZONTAL){Vec3 p=Vec3.atBottomCenterOf(anchor().relative(d,distance));if(safe(rider,p))return p;}
        return entrance==null?Vec3.atBottomCenterOf(anchor().relative(facing(),2)):entrance;
    }
    private boolean safe(LivingEntity rider,Vec3 p){BlockPos floor=BlockPos.containing(p.add(0,-.01,0));return level().hasChunkAt(floor)&&level().getWorldBorder().isWithinBounds(floor)&&level().getBlockState(floor).isFaceSturdy(level(),floor,Direction.UP)&&level().noCollision(rider,rider.getDimensions(Pose.STANDING).makeBoundingBox(p));}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
}
