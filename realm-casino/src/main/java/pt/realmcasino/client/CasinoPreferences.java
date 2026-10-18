package pt.realmcasino.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/** Local comfort settings only. No balances, card hands or results are written here. */
public final class CasinoPreferences {
    private static CasinoPreferences instance;
    public boolean sound=true,motion=true;
    private CasinoPreferences() {}
    public static CasinoPreferences get() {
        if(instance==null){instance=new CasinoPreferences();try{
            Path path=FMLPaths.CONFIGDIR.get().resolve("realmcasino-client.properties");
            if(Files.isRegularFile(path)&&Files.size(path)<=1024){String text=Files.readString(path,StandardCharsets.UTF_8);for(String line:text.split("\\R")){if(line.strip().equals("sound=false"))instance.sound=false;if(line.strip().equals("motion=false"))instance.motion=false;}}
        }catch(Exception ignored){/* A local preference failure must never affect the server's game. */}}
        return instance;
    }
    public void save() {
        try {Path path=FMLPaths.CONFIGDIR.get().resolve("realmcasino-client.properties");Files.createDirectories(path.getParent());Files.writeString(path,"sound="+sound+"\nmotion="+motion+"\n",StandardCharsets.UTF_8);}catch(Exception ignored){}
    }
}
