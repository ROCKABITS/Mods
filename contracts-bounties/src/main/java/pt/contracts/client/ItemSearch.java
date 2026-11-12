package pt.contracts.client;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Search an immutable catalogue without touching Minecraft registries on each keystroke. */
public final class ItemSearch {
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private ItemSearch() {}

    public static final class Entry {
        private final String id, name, searchable;
        private final List<String> tags;
        private final int owned;
        public Entry(String id, String name, List<String> tags, int owned) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.tags = tags.stream().map(ItemSearch::normalize).toList();
            if (owned < 0) throw new IllegalArgumentException("Negative inventory count");
            this.owned = owned;
            searchable = normalize(name + " " + id);
        }
        public String id() { return id; }
        public String name() { return name; }
        public int owned() { return owned; }
        private boolean matches(String[] words) {
            for (String word : words) {
                if (word.startsWith("#")) {
                    String tag = word.substring(1);
                    if (tags.stream().noneMatch(value -> value.contains(tag))) return false;
                } else if (!searchable.contains(word)) return false;
            }
            return true;
        }
    }

    public static String normalize(String value) {
        return ACCENTS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static List<Entry> filter(List<Entry> catalogue, String query, boolean inventoryOnly) {
        String normalized = normalize(query).strip();
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        return catalogue.stream().filter(e -> !inventoryOnly || e.owned() > 0).filter(e -> e.matches(words)).toList();
    }
}
