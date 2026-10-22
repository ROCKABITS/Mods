package pt.cronicasrp.i18n;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.*;
import pt.cronicasrp.domain.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest {
    private Language previous;
    private static Map<String, String> translations(String locale) throws Exception {
        try (var input = LocalizationTest.class.getResourceAsStream("/assets/cronicasrp/lang/" + locale + ".json")) {
            assertNotNull(input);
            var json = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> values = new HashMap<>();
            json.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
            return values;
        }
    }
    private static void language(String locale) throws Exception {
        var values = translations(locale);
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return values.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return FormattedCharSequence.forward(text.getString(), Style.EMPTY); }
        });
    }
    @BeforeEach void rememberLanguage() { previous = Language.getInstance(); }
    @AfterEach void restoreLanguage() { Language.inject(previous); }

    @Test void everyLocaleHasAllUsedKeysAndAllFifteenItems() throws Exception {
        var en = translations("en_us");
        assertEquals(en.keySet(), translations("pt_pt").keySet());
        assertEquals(en.keySet(), translations("pt_br").keySet());
        assertTrue(en.keySet().containsAll(TranslationCatalog.ERRORS.values()));
        assertTrue(en.keySet().containsAll(TranslationCatalog.LABELS.values()));
        for (var value : CurrencyDenomination.values()) assertTrue(en.containsKey("item.cronicasrp." + value.id()));
        var pattern = Pattern.compile("\"((?:gui|label|error|message|format)\\.cronicasrp\\.[a-zA-Z0-9_.]+)\"");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = pattern.matcher(Files.readString(file));
                while (matcher.find()) assertTrue(en.containsKey(matcher.group(1)), file + ": " + matcher.group(1));
            }
        }
    }
    @Test void serverComponentResolvesIndependentlyForEnglishAndPortuguese() throws Exception {
        Component notice = ModMessages.tr("message.cronicasrp.cash.withdrawn", ModMessages.money(123450));
        assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, notice.getContents());
        language("en_us"); assertEquals("You withdrew €1,234.50.", notice.getString());
        language("pt_pt"); assertEquals("Levantaste 1.234,50 €.", notice.getString());
        language("en_us"); assertEquals("You withdrew €1,234.50.", notice.getString());
    }
    @Test void canonicalSavedLabelsTranslateWithoutChangingCharacterData() throws Exception {
        var original = new CharacterProfile(true, "Comum", 128, "Elfo", "Humano — a minha história.", "Ari", "Herdeira", 20500, 123450,
                "", RpProgress.EMPTY.withOccupation("Estudioso").withReputation(80));
        language("en_us");
        assertEquals("Elf", ModMessages.label("race", original.race()).getString());
        assertEquals("Scholar", ModMessages.label("occupation", original.progress().occupation()).getString());
        assertEquals("Heir", ModMessages.label("origin", original.origin()).getString());
        assertEquals("Diamond", ModMessages.label("rank", original.progress().classification()).getString());
        var saved = ProfileStorage.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, ProfileStorage.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
        assertEquals("Comum", original.name());
        assertEquals("Humano — a minha história.", original.description());
        assertEquals("Ari", original.signature());
        language("pt_pt"); assertEquals("Elfo", ModMessages.label("race", original.race()).getString());
    }
    @Test void errorsAndCustomLabelsRemainUsefulInBothLanguages() throws Exception {
        var error = ModMessages.error("Não tens saldo digital suficiente.");
        language("en_us"); assertEquals("You do not have enough digital funds.", error.getString());
        language("pt_pt"); assertEquals("Não tens saldo digital suficiente.", error.getString());
        assertEquals("Moonborn", ModMessages.label("origin", "Moonborn").getString());
        assertEquals("", ModMessages.error("").getString());
    }
    @Test void currencyUsesExactIntegerFormattingAtBothEndsOfItsRange() throws Exception {
        language("en_us");
        assertEquals("€0.01", ModMessages.money(1).getString());
        assertEquals("€9,000,000,000,000,000.00", ModMessages.money(Money.MAX_CENTS).getString());
        assertThrows(IllegalArgumentException.class, () -> Money.formatEnglish(-1));
        language("pt_br"); assertEquals("0,01 €", ModMessages.money(1).getString());
    }
    @Test void publicNameValidationErrorResolvesInAllSupportedLanguages() throws Exception {
        var error = assertThrows(IllegalArgumentException.class, () -> RpName.checkedUpdate("Ari\nOther"));
        Component message = ModMessages.error(error.getMessage());
        language("en_us"); assertEquals("The public character name is invalid.", message.getString());
        language("pt_pt"); assertEquals("O nome público da personagem é inválido.", message.getString());
        language("pt_br"); assertEquals("O nome público do personagem é inválido.", message.getString());
    }
    @Test void everyDomainValidationMessageHasATranslation() throws Exception {
        var pattern = Pattern.compile("new Illegal(?:Argument|State)Exception\\(\"([^\"]+)\"");
        try (var files = Files.walk(Path.of("src/main/java/pt/cronicasrp/domain"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var matcher = pattern.matcher(Files.readString(file));
                while (matcher.find()) assertTrue(TranslationCatalog.ERRORS.containsKey(matcher.group(1)), matcher.group(1));
            }
        }
    }
}
