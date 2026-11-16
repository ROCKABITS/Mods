package pt.realmfitness.domain;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Validated configuration updates preserve existing options and replace the file atomically. */
public final class FitnessConfig {
    private static final int MAX_BYTES=16384;
    private static final Gson PRETTY=new GsonBuilder().setPrettyPrinting().create();
    private FitnessConfig() {}

    private static JsonObject read(Path file) throws IOException {
        if(!Files.exists(file))return FitnessLedger.JSON.toJsonTree(new FitnessLedger.Settings()).getAsJsonObject();
        if(Files.size(file)>MAX_BYTES)throw new IllegalArgumentException("fitness.invalid_settings");
        var json=JsonParser.parseString(Files.readString(file));
        if(!json.isJsonObject())throw new IllegalArgumentException("fitness.invalid_settings");
        return json.getAsJsonObject();
    }
    private static FitnessLedger.Settings settings(JsonObject json) {
        var settings=FitnessLedger.JSON.fromJson(json,FitnessLedger.Settings.class);
        settings.validate();return settings;
    }
    private static void write(Path file,JsonObject json) throws IOException {
        String contents=PRETTY.toJson(json)+"\n";
        if(contents.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("fitness.invalid_settings");
        Path destination=file.toAbsolutePath();Files.createDirectories(destination.getParent());
        Path temp=Files.createTempFile(destination.getParent(),"realm-fitness-",".tmp");
        try{Files.writeString(temp,contents);Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
    public static FitnessLedger.Settings load(Path file) throws IOException {
        boolean existed=Files.exists(file);var json=read(file);var settings=settings(json);
        if(!existed||!json.has("conditioning")){
            json.add("conditioning",FitnessLedger.JSON.toJsonTree(settings.conditioning));write(file,json);
        }
        return settings;
    }
    public static FitnessLedger.Settings setConditioning(Path file,boolean enabled) throws IOException {
        var json=read(file);var settings=settings(json);
        settings.conditioning.enabled=enabled;
        if(!json.has("conditioning"))json.add("conditioning",FitnessLedger.JSON.toJsonTree(settings.conditioning));
        else json.getAsJsonObject("conditioning").addProperty("enabled",enabled);
        write(file,json);return settings;
    }
    public static FitnessLedger.Settings setConditioningDays(Path file,Conditioning.TimeMode mode,int days) throws IOException {
        var json=read(file);var settings=settings(json);
        settings.conditioning.timeMode=mode;settings.conditioning.graceDays=days;settings.validate();
        if(!json.has("conditioning"))json.add("conditioning",FitnessLedger.JSON.toJsonTree(settings.conditioning));
        else{var condition=json.getAsJsonObject("conditioning");condition.addProperty("timeMode",mode.id());condition.addProperty("graceDays",days);}
        write(file,json);return settings;
    }
}
