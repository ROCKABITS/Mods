package pt.realmeconomy.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import pt.realmeconomy.domain.TradePrice;

/** Administrator-owned bounds. Reloaded only by an operator command or a server restart. */
public record EconomyConfig(long centsPerEmerald,int maximumTaxBasisPoints){
    private static EconomyConfig current;
    public EconomyConfig{if(centsPerEmerald<1||centsPerEmerald>TradePrice.MAX/256||maximumTaxBasisPoints<0||maximumTaxBasisPoints>10000)throw new IllegalArgumentException("Invalid villager conversion/tax bounds");}
    public static synchronized EconomyConfig get(){if(current==null)current=read();return current;}
    public static synchronized void reload(){EconomyConfig next=read();current=next;}
    private static EconomyConfig read(){
        Path file=FMLPaths.CONFIGDIR.get().resolve("realmeconomy-server.json");if(!Files.exists(file)&&Files.exists(file.resolveSibling("realmvillagereconomy-server.json")))file=file.resolveSibling("realmvillagereconomy-server.json");
        try{
            if(!Files.exists(file)){Files.createDirectories(file.getParent());var defaults=new EconomyConfig(500,2500);Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(defaults)+"\n");return defaults;}
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(!json.keySet().equals(java.util.Set.of("centsPerEmerald","maximumTaxBasisPoints")))throw new IllegalArgumentException("Unknown/missing administrator setting");
            return new EconomyConfig(json.get("centsPerEmerald").getAsBigDecimal().longValueExact(),json.get("maximumTaxBasisPoints").getAsBigDecimal().intValueExact());
        }catch(Exception error){throw new IllegalStateException("Invalid realmeconomy-server.json; preserve the file and correct its values",error);}
    }
}
