package pt.contracts.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Only presentation preferences and opaque animation IDs are saved, never quest/payment data. */
public final class DailyVisualPreferences {
    public boolean enabled=true,reducedParticles=false,simplified=false;
    public float volume=.55f;
    public int duration=5;
    public List<String> seen=new ArrayList<>();
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/contracts-daily-visuals.json");}
    public static DailyVisualPreferences load(){try{if(Files.isRegularFile(file())&&Files.size(file())<32768){var p=new Gson().fromJson(Files.readString(file()),DailyVisualPreferences.class);p.volume=Float.isFinite(p.volume)?Math.clamp(p.volume,0,1):.55f;p.duration=Math.clamp(p.duration,3,5);if(p.seen==null)p.seen=new ArrayList<>();p.seen.removeIf(s->{try{UUID.fromString(s);return false;}catch(RuntimeException e){return true;}});while(p.seen.size()>512)p.seen.removeFirst();return p;}}catch(Exception ignored){}return new DailyVisualPreferences();}
    public boolean save(){try{Files.createDirectories(file().getParent());Path temp=file().resolveSibling("contracts-daily-visuals.json.tmp");Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(this));Files.move(temp,file(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);return true;}catch(Exception ignored){return false;}}
}
