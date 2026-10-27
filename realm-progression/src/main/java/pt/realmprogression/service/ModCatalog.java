package pt.realmprogression.service;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.ModList;
import pt.realmprogression.domain.ProgressLedger;

/** The server's loaded mods and output namespaces; recipe IDs need not belong to the output mod. */
public final class ModCatalog {
    public record Mod(String id,String name,String version,boolean installed){}
    private static Map<String,Mod> loaded;
    public static synchronized Map<String,Mod> installed(){
        if(loaded!=null)return loaded;
        var result=new TreeMap<String,Mod>();
        for(var mod:ModList.get().getMods())result.put(mod.getModId(),new Mod(mod.getModId(),clip(mod.getDisplayName(),100),clip(mod.getVersion().toString(),64),true));
        loaded=Collections.unmodifiableMap(result);return loaded;
    }
    public static Set<String> namespaces(){var out=new TreeSet<String>();BuiltInRegistries.ITEM.keySet().forEach(id->out.add(id.getNamespace()));return out;}
    public static Map<String,Integer> coverage(MinecraftServer server){
        var result=new HashMap<String,Integer>();
        for(var recipe:server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)){
            try{var stack=recipe.value().getResultItem(server.registryAccess());if(!stack.isEmpty())result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(),1,Integer::sum);}
            catch(RuntimeException ignored){/* Dynamic/custom recipes remain subject to the actual-result guard. */}
        }
        return result;
    }
    public static int count(Map<String,Integer> coverage,Set<String> namespaces){return namespaces.stream().mapToInt(ns->coverage.getOrDefault(ns,0)).sum();}
    public static String clip(String s,int max){return s.length()<=max?s:s.substring(0,max);}
    private ModCatalog(){}
}
