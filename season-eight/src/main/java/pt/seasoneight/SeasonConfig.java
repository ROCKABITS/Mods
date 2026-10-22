package pt.seasoneight;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Defaults are usable before CLIENT configuration has been loaded during startup. */
public final class SeasonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue MENU, LOADING, TITLE, ICON, MOTION;
    public static final ModConfigSpec.ConfigValue<String> WINDOW_TITLE;
    static {
        var b = new ModConfigSpec.Builder();
        b.comment("Independent client presentation switches. Restart after changing these settings.",
                "Disable customMenu/customLoading when using another menu or loading-screen mod.");
        MENU = b.define("customMenu", true);
        LOADING = b.define("customLoading", true);
        TITLE = b.define("customWindowTitle", true);
        ICON = b.define("customWindowIcon", true);
        MOTION = b.define("animatedBackground", true);
        WINDOW_TITLE = b.define("windowTitle", "Season 8", v -> v instanceof String s &&
                !s.isBlank() && s.length() <= 64 && s.codePoints().noneMatch(Character::isISOControl));
        SPEC = b.build();
    }
    public static boolean enabled(ModConfigSpec.BooleanValue value) { return !SPEC.isLoaded() || value.get(); }
    public static String title() { return SPEC.isLoaded() ? WINDOW_TITLE.get() : "Season 8"; }
    private SeasonConfig() {}
}
