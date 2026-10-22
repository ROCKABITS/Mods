package pt.cronicasrp.client;

import net.minecraft.network.chat.Component;
import pt.cronicasrp.domain.CurrencyDenomination;
import pt.cronicasrp.domain.RpProgress;
import pt.cronicasrp.i18n.ModMessages;

/** String resolution belongs only to the client; user-written text stays literal. */
final class UiText {
    private UiText() {}
    static Component c(String key, Object... args) { return ModMessages.tr(key, args); }
    static String s(String key, Object... args) { return c(key, args).getString(); }
    static String error(String message) { return ModMessages.error(message).getString(); }
    static String race(String value) {
        if (value.equals(pt.cronicasrp.compat.OriginsBridge.NO_ORIGIN)) return s("gui.cronicasrp.origins.none");
        var id = net.minecraft.resources.ResourceLocation.tryParse(value);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (value.contains(":") && id != null && player != null) {
            var registry = player.registryAccess().registry(com.iafenvoy.origins.data.origin.OriginRegistries.ORIGIN_KEY);
            var origin = registry.flatMap(r -> r.getHolder(net.minecraft.resources.ResourceKey.create(
                    com.iafenvoy.origins.data.origin.OriginRegistries.ORIGIN_KEY, id)));
            if (origin.isPresent()) return com.iafenvoy.origins.data.origin.Origin.getName(origin.get()).getString();
        }
        return ModMessages.label("race", value).getString();
    }
    static String selectedRace() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return pt.cronicasrp.compat.OriginsBridge.race(player).map(c -> c.name().getString())
                .orElseGet(() -> s(pt.cronicasrp.compat.OriginsBridge.ready(player)
                        ? "gui.cronicasrp.origins.none" : "gui.cronicasrp.origins.pending"));
    }
    static String playerClass() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return pt.cronicasrp.compat.OriginsBridge.playerClass(player).map(c -> c.name().getString())
                .orElseGet(() -> s(net.neoforged.fml.ModList.get().isLoaded("origins_classes")
                        ? (pt.cronicasrp.compat.OriginsBridge.ready(player) ? "gui.cronicasrp.origins.none" : "gui.cronicasrp.origins.pending")
                        : "gui.cronicasrp.origins.classes_absent"));
    }
    static String origin(String value) { return ModMessages.label("origin", value).getString(); }
    static String occupation(String value) { return ModMessages.label("occupation", value).getString(); }
    static String rank(String value) { return ModMessages.label("rank", value).getString(); }
    static String medal(RpProgress.Medal value) { return ModMessages.label("medal", value.id()).getString(); }
    static Component denomination(CurrencyDenomination value) { return ModMessages.denomination(value); }
}
