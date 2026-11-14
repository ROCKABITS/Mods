package pt.realmfitness;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import pt.realmfitness.domain.Exercise;

/** Shared authored contact points, in metres. Source geometry uses model pixels. */
public final class GymLayout {
    private static final JsonObject ALL=load();
    private GymLayout(){}
    public record Point(double x,double y,double z){}
    public static double scalar(Exercise e,String name){return ALL.getAsJsonObject(e.id()).get(name).getAsDouble();}
    public static double number(Exercise e,String name){return scalar(e,name)/16;}
    public static Point point(Exercise e,String name){var a=ALL.getAsJsonObject(e.id()).getAsJsonArray(name);return new Point(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16);}
    private static JsonObject load(){
        try(var stream=GymLayout.class.getResourceAsStream("/assets/realmfitness/geometry/layout.json")){
            if(stream==null)throw new IOException("Missing equipment contact points");
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException|RuntimeException error){throw new IllegalStateException("Invalid gym layout",error);}
    }
}
