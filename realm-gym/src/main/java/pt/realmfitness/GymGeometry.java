package pt.realmfitness;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;
import pt.realmfitness.domain.Exercise;

/** Collision boxes are exported from the same authored geometry as the visual models. */
public final class GymGeometry {
    private static final VoxelShape[][][] SHAPES=load("collisions.json");
    private static final VoxelShape[][][] WORKING=load("collisions_working.json");
    private GymGeometry(){}
    public static int depth(Exercise e){return switch(e){case BENCH,RUN,ROW,LEG_PRESS->3;default->2;};}
    public static boolean contains(Exercise e,int part){return part>=0&&part<18&&(part/2)%3<depth(e);}
    public static int x(int part){return part%2;}
    public static int z(int part){return (part/2)%3;}
    public static int y(int part){return part/6;}
    public static VoxelShape shape(Exercise e,int part,Direction facing){return SHAPES[e.ordinal()][part][facing.get2DDataValue()];}
    public static VoxelShape shape(Exercise e,int part,Direction facing,boolean working){return (working?WORKING:SHAPES)[e.ordinal()][part][facing.get2DDataValue()];}
    private static VoxelShape[][][] load(String resource){
        VoxelShape[][][] result=new VoxelShape[Exercise.values().length][18][4];
        try(var input=GymGeometry.class.getResourceAsStream("/assets/realmfitness/geometry/"+resource)){
            if(input==null)throw new IOException("Missing gym collision geometry");
            JsonObject all=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var e:Exercise.values())for(int part=0;part<18;part++)for(var facing:Direction.Plane.HORIZONTAL){
                VoxelShape shape=Shapes.empty();
                for(var entry:all.getAsJsonObject(e.id()).getAsJsonArray(Integer.toString(part))){
                    var b=entry.getAsJsonArray();double x=b.get(0).getAsDouble(),y=b.get(1).getAsDouble(),z=b.get(2).getAsDouble(),X=b.get(3).getAsDouble(),Y=b.get(4).getAsDouble(),Z=b.get(5).getAsDouble();
                    VoxelShape box=switch(facing){case NORTH->Block.box(x,y,z,X,Y,Z);case EAST->Block.box(16-Z,y,x,16-z,Y,X);case SOUTH->Block.box(16-X,y,16-Z,16-x,Y,16-z);case WEST->Block.box(z,y,16-X,Z,Y,16-x);default->throw new IllegalArgumentException();};
                    shape=Shapes.or(shape,box);
                }
                result[e.ordinal()][part][facing.get2DDataValue()]=shape.optimize();
            }
            return result;
        }catch(IOException|RuntimeException error){throw new IllegalStateException("Invalid gym geometry",error);}
    }
}
