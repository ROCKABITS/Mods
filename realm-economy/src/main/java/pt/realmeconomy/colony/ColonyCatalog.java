package pt.realmeconomy.colony;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

/** Daily bounded demand. Administrators may extend real items without optional mod dependencies. */
public final class ColonyCatalog {
    public record Product(String item,int quantity,long cents){public Product{if(item==null||ResourceLocation.tryParse(item)==null||quantity<1||quantity>4096||cents<1||cents>1_000_000)throw new IllegalArgumentException("Invalid export product");}}
    public record Configuration(ColonyLedger.Policy initialPolicy,List<Product> products){public Configuration{Objects.requireNonNull(initialPolicy);products=List.copyOf(products);if(products.size()<5||products.size()>256||products.stream().map(Product::item).distinct().count()!=products.size())throw new IllegalArgumentException("Invalid export catalogue");}}
    private static Configuration config;
    public static Configuration config(){if(config==null){var path=FMLPaths.CONFIGDIR.get().resolve("realm-economy-minecolonies.json");try{if(!Files.exists(path)){Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(defaults())+"\n");}if(Files.size(path)>128000)throw new IllegalStateException("Export catalogue too large");config=ColonyLedger.JSON.fromJson(Files.readString(path),Configuration.class);}catch(java.io.IOException e){throw new IllegalStateException("error.realmeconomy.storage",e);}}return config;}
    public static List<ColonyLedger.Order> generate(String key,long day,UUID salt,int count){
        var list=new ArrayList<>(config().products.stream().filter(p->BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(p.item))&&!BuiltInRegistries.ITEM.get(ResourceLocation.parse(p.item)).equals(Items.AIR)).toList());
        if(list.size()<count)throw new IllegalStateException("error.realmeconomy.colony_catalog");
        Collections.shuffle(list,new Random(salt.getMostSignificantBits()^salt.getLeastSignificantBits()^key.hashCode()^day));
        var result=new ArrayList<ColonyLedger.Order>();for(var p:list.subList(0,count)){UUID id=UUID.nameUUIDFromBytes((salt+":"+key+":"+day+":"+p.item).getBytes(java.nio.charset.StandardCharsets.UTF_8));result.add(new ColonyLedger.Order(id.toString(),p.item,p.quantity,p.cents,false));}return result;
    }
    private static Configuration defaults(){return new Configuration(ColonyLedger.Policy.defaults(),List.of(
        new Product("minecraft:bread",128,4000),new Product("minecraft:baked_potato",128,3500),new Product("minecraft:carrot",192,3000),new Product("minecraft:wheat",192,3000),new Product("minecraft:pumpkin",96,3500),new Product("minecraft:apple",64,2500),
        new Product("minecraft:oak_log",192,4000),new Product("minecraft:spruce_log",192,4000),new Product("minecraft:dark_oak_log",192,4000),new Product("minecraft:stone",256,2500),new Product("minecraft:stone_bricks",192,3500),new Product("minecraft:glass",128,3500),
        new Product("minecraft:paper",192,3000),new Product("minecraft:book",32,4500),new Product("minecraft:leather",64,4500),new Product("minecraft:white_wool",128,4000),new Product("minecraft:cooked_cod",64,4500),new Product("minecraft:cooked_beef",64,5000),new Product("minecraft:iron_ingot",64,5000),new Product("minecraft:charcoal",128,3500)));
    }
    public static void clear(){config=null;}
}
