package pt.realmfitness;

import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Player;

/** Optional Dynamic Nutrition storage bridge. No nutrition-mod classes are linked at startup. */
final class GymNutrition {
    private static final String ROOT="com.chillpavz.dynamicnutrition.";
    private GymNutrition() {}

    private record Access(Object storage, Method getPlayer, Method getNutrient, Object protein) {
        static Access find(){
            try{
                Class<?> service=Class.forName(ROOT+"platform.Services");
                Class<?> storage=Class.forName(ROOT+"platform.services.INutritionStorage");
                Class<?> playerData=Class.forName(ROOT+"player.PlayerNutrition");
                Class<?> nutrient=Class.forName(ROOT+"nutrition.Nutrient");
                Object protein;
                try{
                    protein=Class.forName(ROOT+"nutrition.Nutrients").getField("PROTEIN").get(null);
                }catch(ClassNotFoundException|NoSuchFieldException olderVersion){
                    protein=nutrient.getField("PROTEIN").get(null);
                }
                return new Access(service.getField("STORAGE").get(null),storage.getMethod("get",Player.class),
                    playerData.getMethod("get",nutrient),protein);
            }catch(ClassNotFoundException missing){
                return null;
            }catch(ReflectiveOperationException|LinkageError incompatible){
                FitnessMod.LOG.warn("Dynamic Nutrition recovery integration unavailable",incompatible);
                return null;
            }
        }
    }

    private static final class Bridge {
        static final Access ACCESS=Access.find();
        static boolean readFailed;
    }

    static double protein(Player player){
        Access bridge=Bridge.ACCESS;
        if(bridge==null||Bridge.readFailed)return 0;
        try{
            Object data=bridge.getPlayer.invoke(bridge.storage,player);
            return data==null?0:((Number)bridge.getNutrient.invoke(data,bridge.protein)).doubleValue()/100;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException error){
            Bridge.readFailed=true;
            FitnessMod.LOG.warn("Could not read Dynamic Nutrition protein; using base supplement recovery",error);
            return 0;
        }
    }
}
