package pt.realmcasino.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;

/** Administrator-controlled server-wide bounds. Owners may only narrow these bounds. */
public record CasinoConfig(long minBet, long maxBet) {
    private static CasinoConfig current;
    public CasinoConfig {
        if(minBet<1||maxBet<minBet||maxBet>1_000_000_000L)throw new IllegalArgumentException("Invalid casino server limits.");
    }
    public static CasinoConfig get() {
        if(current!=null)return current;
        Path file=FMLPaths.CONFIGDIR.get().resolve("realmcasino-server.json");
        try {
            if(Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file,"{\n  \"minBet\": 1,\n  \"maxBet\": 100000\n}\n",StandardOpenOption.CREATE_NEW);
            }
            if(Files.size(file)>4096)throw new IOException("Casino config is too large.");
            JsonObject root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(!root.keySet().equals(Set.of("minBet","maxBet")))throw new IOException("Unknown casino configuration key.");
            current=new CasinoConfig(integer(root,"minBet"),integer(root,"maxBet"));return current;
        } catch(IOException|RuntimeException failure) {throw new IllegalStateException("error.realmcasino.config_unavailable",failure);}
    }
    private static long integer(JsonObject o,String key) {
        if(!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isNumber()||!o.get(key).getAsString().matches("[0-9]{1,19}"))throw new IllegalArgumentException();
        return o.get(key).getAsLong();
    }
}
