package pt.cronicasrp;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import pt.cronicasrp.domain.EconomySettings;

public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final BandConfig DESTITUTE, HUMBLE, COMMON, COMFORTABLE, WEALTHY, NOBLE, HEIR;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Relative weights and starting funds in whole euros. Changes affect new characters only.").push("economia");
        DESTITUTE = new BandConfig(builder, "desamparada", 8, 20, 40);
        HUMBLE = new BandConfig(builder, "humilde", 24, 50, 80);
        COMMON = new BandConfig(builder, "comum", 36, 100, 140);
        COMFORTABLE = new BandConfig(builder, "confortavel", 18, 170, 220);
        WEALTHY = new BandConfig(builder, "abastada", 9, 260, 340);
        NOBLE = new BandConfig(builder, "nobre", 4, 450, 600);
        HEIR = new BandConfig(builder, "herdeira", 1, 800, 1000);
        builder.pop(); SPEC = builder.build();
    }
    private record BandConfig(ModConfigSpec.IntValue weight, ModConfigSpec.IntValue minimum, ModConfigSpec.IntValue maximum) {
        BandConfig(ModConfigSpec.Builder builder, String key, int weight, int minimum, int maximum) {
            this(builder.defineInRange(key + "Peso", weight, 0, 1000), builder.defineInRange(key + "Minimo", minimum, 0, 100_000_000), builder.defineInRange(key + "Maximo", maximum, 0, 100_000_000));
        }
        EconomySettings.Band band(String name) { return new EconomySettings.Band(name, weight.get(), Math.min(minimum.get(), maximum.get()), Math.max(minimum.get(), maximum.get())); }
    }
    private ServerConfig() {}
    public static EconomySettings settings() {
        try { return new EconomySettings(List.of(DESTITUTE.band("Desamparada"), HUMBLE.band("Humilde"),
                COMMON.band("Comum"), COMFORTABLE.band("Confortável"), WEALTHY.band("Abastada"),
                NOBLE.band("Nobre"), HEIR.band("Herdeira"))); }
        catch (IllegalArgumentException ex) { CronicasRP.LOGGER.warn("Invalid economy configuration: using the defaults."); return EconomySettings.defaults(); }
    }
}
