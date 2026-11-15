package pt.realmfitness.domain;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FitnessConfigTest {
    @TempDir Path directory;
    private Path file(){return directory.resolve("config/realm-fitness.json");}
    private JsonObject document() throws IOException {return JsonParser.parseString(Files.readString(file())).getAsJsonObject();}
    private void write(JsonObject json) throws IOException {Files.createDirectories(file().getParent());Files.writeString(file(),json.toString());}

    @Test void firstLoadCreatesDisabledDefaults(){
        var settings=assertDoesNotThrow(()->FitnessConfig.load(file()));assertFalse(settings.conditioning.enabled);assertTrue(Files.isRegularFile(file()));
        assertFalse(assertDoesNotThrow(this::document).getAsJsonObject("conditioning").get("enabled").getAsBoolean());
    }
    @Test void commandsPersistBothStatesForFutureLoads() throws IOException {
        assertTrue(FitnessConfig.setConditioning(file(),true).conditioning.enabled);assertTrue(FitnessConfig.load(file()).conditioning.enabled);
        assertFalse(FitnessConfig.setConditioning(file(),false).conditioning.enabled);assertFalse(FitnessConfig.load(file()).conditioning.enabled);
    }
    @Test void changingEnabledPreservesEveryOtherOptionAndUnknownField() throws IOException {
        var json=FitnessLedger.JSON.toJsonTree(new FitnessLedger.Settings()).getAsJsonObject();json.addProperty("dailyXp",133);json.addProperty("custom_server_note","preserve this");
        var config=json.getAsJsonObject("conditioning");config.addProperty("graceDays",12);config.addProperty("lossPerDay",2.5);config.addProperty("minimumPercent",40);config.addProperty("countOfflineTime",false);config.addProperty("custom_extension",73);
        write(json);FitnessConfig.setConditioning(file(),true);var saved=document();saved.getAsJsonObject("conditioning").addProperty("enabled",false);
        assertEquals(json,saved);var settings=FitnessConfig.load(file());assertEquals(133,settings.dailyXp);assertEquals(12,settings.conditioning.graceDays);assertEquals(2.5,settings.conditioning.lossPerDay);assertFalse(settings.conditioning.countOfflineTime);
    }
    @Test void enablingLegacyConfigurationAddsTheBlockWithoutResettingExistingSettings() throws IOException {
        var json=new JsonObject();json.addProperty("dailyXp",125);json.addProperty("unknown","saved");write(json);
        var settings=FitnessConfig.setConditioning(file(),true);assertTrue(settings.conditioning.enabled);assertEquals(125,settings.dailyXp);assertEquals("saved",document().get("unknown").getAsString());assertEquals(7,document().getAsJsonObject("conditioning").get("graceDays").getAsInt());
    }
    @Test void loadingLegacyConfigurationKeepsTheFeatureDisabled() throws IOException {
        var json=new JsonObject();json.addProperty("dailyXp",120);write(json);assertFalse(FitnessConfig.load(file()).conditioning.enabled);
        assertEquals(120,document().get("dailyXp").getAsInt());assertTrue(document().has("conditioning"));
    }
    @Test void invalidSettingsAreRejectedBeforeReplacingTheFile() throws IOException {
        FitnessConfig.load(file());var json=document();json.getAsJsonObject("conditioning").addProperty("graceDays",-1);write(json);var before=Files.readString(file());
        assertThrows(IllegalArgumentException.class,()->FitnessConfig.setConditioning(file(),true));assertEquals(before,Files.readString(file()));
    }
    @Test void malformedJsonIsRejectedWithoutReplacingIt() throws IOException {
        Files.createDirectories(file().getParent());Files.writeString(file(),"{broken");
        assertThrows(RuntimeException.class,()->FitnessConfig.setConditioning(file(),true));assertEquals("{broken",Files.readString(file()));
    }
    @Test void oversizedConfigurationIsRejectedWithoutReplacingIt() throws IOException {
        Files.createDirectories(file().getParent());String text=" ".repeat(16385);Files.writeString(file(),text);
        assertThrows(IllegalArgumentException.class,()->FitnessConfig.setConditioning(file(),true));assertEquals(text,Files.readString(file()));
    }
    @Test void filesystemFailuresDoNotDeleteExistingFiles() throws IOException {
        Path occupied=directory.resolve("occupied");Files.writeString(occupied,"keep");
        assertThrows(IOException.class,()->FitnessConfig.setConditioning(occupied.resolve("realm-fitness.json"),true));assertEquals("keep",Files.readString(occupied));
    }
    @Test void repeatedOnLeavesTheSameSavedConfigurationAndNoTemporaryFiles() throws IOException {
        FitnessConfig.setConditioning(file(),true);String before=Files.readString(file());FitnessConfig.setConditioning(file(),true);assertEquals(before,Files.readString(file()));
        try(var files=Files.list(file().getParent())){assertEquals(1,files.count());}
    }
}
