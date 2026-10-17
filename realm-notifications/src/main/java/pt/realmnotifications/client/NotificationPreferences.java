package pt.realmnotifications.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.client.Minecraft;

/** Presentation preferences only. History and received messages never go to disk. */
public final class NotificationPreferences {
    public enum Position { TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public boolean enabled=true, sound=true, animations=true, history=true;
    public int seconds=6;
    public Position position=Position.TOP_LEFT;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Path file() {return Minecraft.getInstance().gameDirectory.toPath().resolve("config/realm-notifications-client.json");}
    public static NotificationPreferences load() {
        NotificationPreferences p=new NotificationPreferences();
        try {
            if(!Files.isRegularFile(file()) || Files.size(file())>4096)return p;
            JsonObject o=JsonParser.parseString(Files.readString(file(),StandardCharsets.UTF_8)).getAsJsonObject();
            if(o.has("enabled"))p.enabled=o.get("enabled").getAsBoolean();
            if(o.has("sound"))p.sound=o.get("sound").getAsBoolean();
            if(o.has("animations"))p.animations=o.get("animations").getAsBoolean();
            if(o.has("history"))p.history=o.get("history").getAsBoolean();
            if(o.has("seconds"))p.seconds=Math.clamp(o.get("seconds").getAsInt(),3,15);
            if(o.has("position"))p.position=Position.valueOf(o.get("position").getAsString());
        } catch(Exception ignored) { /* A damaged client preference file cannot prevent joining. */ }
        return p;
    }
    public boolean save() {
        try {
            Files.createDirectories(file().getParent());
            Path temporary=file().resolveSibling("realm-notifications-client.json.tmp");
            Files.writeString(temporary,JSON.toJson(this),StandardCharsets.UTF_8);
            try {Files.move(temporary,file(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temporary,file(),StandardCopyOption.REPLACE_EXISTING);}
            return true;
        } catch(Exception e) {return false;}
    }
}
