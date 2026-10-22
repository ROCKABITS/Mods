package pt.cronicasrp.i18n;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import pt.cronicasrp.domain.CurrencyDenomination;
import pt.cronicasrp.domain.Money;

/** Server-safe components: each receiving client resolves its own language. */
public final class ModMessages {
    private ModMessages() {}
    public static MutableComponent tr(String key, Object... arguments) { return Component.translatable(key, arguments); }
    public static Component error(String legacyMessage) {
        if (legacyMessage == null) return tr("error.cronicasrp.action_failed");
        if (legacyMessage.isEmpty()) return Component.empty();
        return tr(TranslationCatalog.ERRORS.getOrDefault(legacyMessage, "error.cronicasrp.action_failed"));
    }
    public static Component label(String category, String canonical) {
        String key = TranslationCatalog.LABELS.get(category + "/" + canonical);
        return key == null ? Component.literal(canonical) : tr(key);
    }
    public static Component money(long cents) {
        // Both exact integer-based representations travel to the client. Its
        // translation selects the correct decimal separator and currency order.
        return tr("format.cronicasrp.money", Money.formatEnglish(cents), Money.format(cents));
    }
    public static Component number(long value) {
        String grouped = String.format(java.util.Locale.ROOT, "%,d", value);
        return tr("format.cronicasrp.number", grouped, grouped.replace(',', '.'));
    }
    public static Component denomination(CurrencyDenomination denomination) {
        return tr("item.cronicasrp." + denomination.id());
    }
}
