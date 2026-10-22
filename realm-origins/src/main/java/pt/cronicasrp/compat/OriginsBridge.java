package pt.cronicasrp.compat;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import com.iafenvoy.origins.attachment.OriginDataHolder;
import com.iafenvoy.origins.data.layer.Layer;
import com.iafenvoy.origins.data.layer.LayerRegistries;
import com.iafenvoy.origins.data.origin.Origin;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import pt.cronicasrp.ModAttachments;
import pt.cronicasrp.domain.CharacterDraft;
import pt.cronicasrp.domain.CharacterProfile;

/** Origins owns selection, powers, eligibility and persistence. RP only mirrors identity. */
public final class OriginsBridge {
    public static final ResourceLocation RACE_LAYER = ResourceLocation.fromNamespaceAndPath("origins", "origin");
    public static final ResourceLocation CLASS_LAYER = ResourceLocation.fromNamespaceAndPath("origins_classes", "class");
    public static final String NO_ORIGIN = "origins:none";
    private OriginsBridge() {}

    public record Choice(ResourceLocation layerId, Holder<Layer> layer, ResourceLocation id, Holder<Origin> origin) {
        public Component name() { return Origin.getName(origin); }
        public Component description() { return Origin.getDescription(origin); }
    }

    public static List<Choice> choices(Player player) {
        if (player == null || player.registryAccess().registry(LayerRegistries.LAYER_KEY).isEmpty()) return List.of();
        var holder = OriginDataHolder.get(player);
        if (holder == null) return List.of();
        return holder.getOrigins().entrySet().stream()
                .filter(e -> e.getKey().value().enabled() && !e.getKey().value().hidden() && e.getValue().value() != Origin.EMPTY)
                .filter(e -> e.getKey().unwrapKey().isPresent() && e.getValue().unwrapKey().isPresent())
                .map(e -> new Choice(e.getKey().unwrapKey().orElseThrow().location(), e.getKey(),
                        e.getValue().unwrapKey().orElseThrow().location(), e.getValue()))
                .sorted(Comparator.<Choice, Layer>comparing(c -> c.layer().value()).thenComparing(c -> c.layerId().toString()))
                .toList();
    }

    public static Optional<Choice> race(Player player) {
        var choices = choices(player);
        return choices.stream().filter(c -> c.layerId().equals(RACE_LAYER)).findFirst()
                .or(() -> choices.stream().filter(c -> !c.layerId().equals(CLASS_LAYER)).findFirst());
    }

    public static Optional<Choice> playerClass(Player player) {
        return choices(player).stream().filter(c -> c.layerId().equals(CLASS_LAYER)).findFirst();
    }

    public static boolean ready(Player player) {
        if (player == null || player.registryAccess().registry(LayerRegistries.LAYER_KEY).isEmpty()) return false;
        var holder = OriginDataHolder.get(player);
        return holder != null && holder.hasAllOrigins();
    }

    /** Only unresolved, eligible layers; lineage first, class next, add-on layers afterwards. */
    public static List<Holder<Layer>> pendingLayers(Player player) {
        if (player == null || player.registryAccess().registry(LayerRegistries.LAYER_KEY).isEmpty()) return List.of();
        var data = OriginDataHolder.get(player);
        return LayerRegistries.streamAvailableLayers(player.registryAccess())
                .filter(h -> !data.hasOriginInLayer(h)
                        && h.value().getOriginOptionCount(player) > 0)
                .sorted(Comparator.<Holder<Layer>>comparingInt(h -> h.is(RACE_LAYER) ? 0 : h.is(CLASS_LAYER) ? 1 : 2)
                        .thenComparing(Holder::value).thenComparing(h -> h.unwrapKey().orElseThrow().location().toString()))
                .toList();
    }

    /** Ignore the client's race field: the server's selected origin is authoritative. */
    public static CharacterDraft verifiedDraft(ServerPlayer player, CharacterDraft draft) {
        if (!ready(player)) throw new IllegalArgumentException("Escolhe primeiro a tua origem e classe no Origins.");
        String raceId = race(player).map(c -> c.id().toString()).orElse(NO_ORIGIN);
        return new CharacterDraft(draft.name(), draft.age(), raceId, draft.description(), draft.signature(),
                draft.signatureDrawing(), draft.occupation());
    }

    public static CharacterProfile synchronize(ServerPlayer player) {
        var profile = player.getData(ModAttachments.PROFILE);
        if (profile.created()) {
            var selected = race(player);
            if (selected.isPresent() && !selected.get().id().toString().equals(profile.race())) {
                profile = profile.withRace(selected.get().id().toString());
                player.setData(ModAttachments.PROFILE, profile);
            }
        }
        return profile;
    }
}

