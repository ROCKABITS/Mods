package pt.cronicasrp.test;

import com.iafenvoy.origins.attachment.OriginDataHolder;
import com.iafenvoy.origins.data.layer.Layer;
import com.iafenvoy.origins.data.layer.LayerRegistries;
import com.iafenvoy.origins.data.origin.Origin;
import com.iafenvoy.origins.data.origin.OriginRegistries;
import com.mojang.authlib.GameProfile;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.ModAttachments;
import pt.cronicasrp.compat.OriginsBridge;
import pt.cronicasrp.domain.*;

@GameTestHolder(CronicasRP.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OriginsGameTests {
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static FakePlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OriginsQA"));
    }
    private static Holder.Reference<Origin> origin(FakePlayer player, String value) {
        return player.registryAccess().registryOrThrow(OriginRegistries.ORIGIN_KEY)
                .getHolderOrThrow(ResourceKey.create(OriginRegistries.ORIGIN_KEY, id(value)));
    }
    private static Holder.Reference<Layer> layer(FakePlayer player, ResourceLocation value) {
        return player.registryAccess().registryOrThrow(LayerRegistries.LAYER_KEY)
                .getHolderOrThrow(ResourceKey.create(LayerRegistries.LAYER_KEY, value));
    }
    private static void select(FakePlayer player, String race) {
        var holder = OriginDataHolder.get(player);
        holder.setOrigin(layer(player, OriginsBridge.RACE_LAYER), origin(player, race));
        if (player.registryAccess().registryOrThrow(LayerRegistries.LAYER_KEY).containsKey(OriginsBridge.CLASS_LAYER))
            holder.setOrigin(layer(player, OriginsBridge.CLASS_LAYER), origin(player, "origins_classes:warrior"));
    }
    private static CharacterProfile oldProfile() {
        byte[] ink = new byte[SignatureDrawing.BYTES]; SignatureDrawing.pixel(ink, 40, 20, true);
        return new CharacterProfile(true, "Ari da Lua", 128, "Elfo", "Floresta", "Ari", "Nobre", 50000, 12345,
                SignatureDrawing.encode(ink), RpProgress.EMPTY.withOccupation("Ferreiro").withReputation(88)
                        .completeMissions(3).award(RpProgress.Medal.GOLD));
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void pendingChoicesAreRaceThenClassAndSkipCompletedLayers(GameTestHelper helper) {
        var player = player(helper);
        var layers = OriginsBridge.pendingLayers(player);
        helper.assertTrue(!layers.isEmpty() && layers.getFirst().is(OriginsBridge.RACE_LAYER), "Race was not the first choice.");
        boolean classes = player.registryAccess().registryOrThrow(LayerRegistries.LAYER_KEY).containsKey(OriginsBridge.CLASS_LAYER);
        helper.assertTrue(layers.size() == (classes ? 2 : 1), "Missing or unexpected pending layer.");
        if (classes) helper.assertTrue(layers.get(1).is(OriginsBridge.CLASS_LAYER), "Class did not follow race.");
        var data = OriginDataHolder.get(player);
        data.setOrigin(layer(player, OriginsBridge.RACE_LAYER), origin(player, "origins:human"));
        var remaining = OriginsBridge.pendingLayers(player);
        helper.assertTrue(remaining.size() == (classes ? 1 : 0), "A selected race was offered again.");
        if (classes) {
            helper.assertTrue(remaining.getFirst().is(OriginsBridge.CLASS_LAYER), "The pending class was skipped.");
            data.setOrigin(layer(player, OriginsBridge.CLASS_LAYER), origin(player, "origins_classes:warrior"));
        }
        helper.assertTrue(OriginsBridge.pendingLayers(player).isEmpty() && OriginsBridge.ready(player), "Completed selections stayed pending.");
        helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void unfinishedOriginsCannotCreateAnRpCharacter(GameTestHelper helper) {
        var player = player(helper);
        helper.assertTrue(!OriginsBridge.ready(player), "First join should await Origins.");
        boolean rejected = false;
        try { OriginsBridge.verifiedDraft(player, new CharacterDraft("Ari", 24, "Elfo", "", "Ari")); }
        catch (IllegalArgumentException ex) { rejected = true; }
        helper.assertTrue(rejected && !player.getData(ModAttachments.PROFILE).created(), "Incomplete selection created a profile.");
        helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void originsClassesMustFinishBeforeRpWhenInstalled(GameTestHelper helper) {
        var player = player(helper);
        var holder = OriginDataHolder.get(player);
        holder.setOrigin(layer(player, OriginsBridge.RACE_LAYER), origin(player, "origins:human"));
        boolean classes = player.registryAccess().registryOrThrow(LayerRegistries.LAYER_KEY).containsKey(OriginsBridge.CLASS_LAYER);
        helper.assertTrue(OriginsBridge.ready(player) == !classes, "RP gate ignored the optional class layer.");
        if (classes) {
            holder.setOrigin(layer(player, OriginsBridge.CLASS_LAYER), origin(player, "origins_classes:warrior"));
            helper.assertTrue(OriginsBridge.ready(player), "Complete Origins selection never unlocked RP.");
        }
        helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void serverSelectionOverridesForgedOrStaleRace(GameTestHelper helper) {
        var player = player(helper); select(player, "origins:avian");
        var original = oldProfile();
        var incoming = new CharacterDraft("Ari", 24, "forged:overpowered", "Lua", "Ari", original.signatureDrawing(), "Ferreiro");
        var verified = OriginsBridge.verifiedDraft(player, incoming);
        var profile = CharacterRules.create(CharacterProfile.empty(), verified, EconomySettings.defaults(), new Random(7), Set.of(verified.race()));
        helper.assertTrue(profile.race().equals("origins:avian"), "Untrusted race reached the RP profile.");
        helper.assertTrue(profile.progress().occupation().equals("Ferreiro"), "Origins class overwrote RP occupation.");
        helper.assertTrue(profile.initialBalance() == profile.balance(), "Starting balance was credited incorrectly.");
        helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void existingCharacterAndChangingOriginsKeepAllRpData(GameTestHelper helper) {
        var player = player(helper); select(player, "origins:human");
        var old = oldProfile(); player.setData(ModAttachments.PROFILE, old);
        var migrated = OriginsBridge.synchronize(player);
        helper.assertTrue(migrated.race().equals("origins:human") && migrated.withRace("Elfo").equals(old), "Migration changed RP or money.");
        OriginDataHolder.get(player).setOrigin(layer(player, OriginsBridge.RACE_LAYER), origin(player, "origins:shulk"));
        var changed = OriginsBridge.synchronize(player);
        helper.assertTrue(changed.race().equals("origins:shulk") && changed.withRace("Elfo").equals(old), "Origin change altered the character.");
        helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void classIsIndependentOfProfessionAndWealth(GameTestHelper helper) {
        var player = player(helper); select(player, "origins:human");
        var old = oldProfile().withRace("origins:human"); player.setData(ModAttachments.PROFILE, old);
        if (player.registryAccess().registryOrThrow(LayerRegistries.LAYER_KEY).containsKey(OriginsBridge.CLASS_LAYER)) {
            OriginDataHolder.get(player).setOrigin(layer(player, OriginsBridge.CLASS_LAYER), origin(player, "origins_classes:merchant"));
            helper.assertTrue(OriginsBridge.playerClass(player).orElseThrow().id().equals(id("origins_classes:merchant")), "Class was not read from Origins.");
        } else helper.assertTrue(OriginsBridge.playerClass(player).isEmpty(), "Missing Classes fabricated a class.");
        helper.assertTrue(OriginsBridge.synchronize(player).equals(old), "Class choice changed economy or profession.");
        helper.succeed();
    }
    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void datapackOriginsAreReadFromTheirActualRegistry(GameTestHelper helper) {
        var player = player(helper); select(player, "rp_fixture:moonborn");
        var selected = OriginsBridge.race(player).orElseThrow();
        helper.assertTrue(selected.id().equals(id("rp_fixture:moonborn")), "Custom origin was replaced by a fixed race list.");
        helper.assertTrue(selected.name().getString().equals("Moonborn QA"), "Custom display name was lost.");
        helper.assertTrue(selected.description().getString().equals("A custom datapack origin."), "Datapack description was lost.");
        player.setData(ModAttachments.PROFILE, oldProfile());
        helper.assertTrue(OriginsBridge.synchronize(player).race().equals("rp_fixture:moonborn"), "Custom origin did not persist in RP.");
        helper.succeed();
    }
}
