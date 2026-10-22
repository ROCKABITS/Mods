package pt.cronicasrp.test;

import com.iafenvoy.origins.attachment.OriginDataHolder;
import com.iafenvoy.origins.data.layer.LayerRegistries;
import com.iafenvoy.origins.data.origin.OriginRegistries;
import com.iafenvoy.origins.data.power.PowerRegistries;
import com.iafenvoy.origins.data.power.builtin.regular.StackingEffectPower;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pt.cronicasrp.CronicasRP;
import pt.cronicasrp.compat.OriginsBridge;

/** Exercises the actual Origins registry and native power implementation. */
@GameTestHolder(CronicasRP.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OriginsPowerGameTests {
    private static final ResourceLocation CLAUSTROPHOBIA = ResourceLocation.parse("origins:claustrophobia");

    private static StackingEffectPower power(GameTestHelper helper) {
        var loaded = helper.getLevel().registryAccess().registryOrThrow(PowerRegistries.POWER_KEY)
                .getHolderOrThrow(ResourceKey.create(PowerRegistries.POWER_KEY, CLAUSTROPHOBIA)).value();
        helper.assertTrue(loaded instanceof StackingEffectPower,
                "Claustrophobia failed decoding and became an empty power: " + loaded.getClass().getSimpleName());
        return (StackingEffectPower) loaded;
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void claustrophobiaLoadsBothEffectsFromTheRealRegistry(GameTestHelper helper) {
        var power = power(helper);
        helper.assertTrue(power.getEffects().size() == 2, "Claustrophobia lost an effect.");
        helper.assertTrue(power.getEffects().stream().anyMatch(e -> e.effect().equals(MobEffects.WEAKNESS)),
                "Weakness was not decoded.");
        helper.assertTrue(power.getEffects().stream().anyMatch(e -> e.effect().equals(MobEffects.MOVEMENT_SLOWDOWN)),
                "Slowness was not decoded.");
        helper.assertTrue(power.getEffects().stream().allMatch(e ->
                e.amplifier() == 0 && !e.showParticles() && e.showIcon()), "Effect settings changed.");
        helper.assertTrue(power.getMinStacks() == -20 && power.getMaxStacks() == 361
                && power.getDurationPerStack() == 10, "The compatibility fix changed the original tuning.");
        helper.succeed();
    }

    @GameTest(template = "cash_empty", timeoutTicks = 100)
    public static void elytrianGetsWeaknessAndSlownessOnlyUnderALowCeiling(GameTestHelper helper) {
        var power = power(helper);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ElytrianQA"));
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int y = 0; y <= 3; y++) helper.getLevel().setBlockAndUpdate(pos.above(y), Blocks.AIR.defaultBlockState());
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        var holder = OriginDataHolder.get(player);
        var layer = player.registryAccess().registryOrThrow(LayerRegistries.LAYER_KEY)
                .getHolderOrThrow(ResourceKey.create(LayerRegistries.LAYER_KEY, OriginsBridge.RACE_LAYER));
        var elytrian = player.registryAccess().registryOrThrow(OriginRegistries.ORIGIN_KEY)
                .getHolderOrThrow(ResourceKey.create(OriginRegistries.ORIGIN_KEY, ResourceLocation.parse("origins:elytrian")));
        holder.setOrigin(layer, elytrian);
        helper.assertTrue(holder.streamPowers(StackingEffectPower.class).anyMatch(p -> p == power),
                "Elytrian does not receive the registered claustrophobia power.");

        power.tick(holder);
        helper.assertTrue(!player.hasEffect(MobEffects.WEAKNESS) && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "Claustrophobia applied in an open space.");

        helper.getLevel().setBlockAndUpdate(pos.above(2), Blocks.STONE.defaultBlockState());
        power.tick(holder);
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS) && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "A low ceiling did not apply both effects.");
        helper.assertTrue(!player.getEffect(MobEffects.WEAKNESS).isVisible()
                && player.getEffect(MobEffects.WEAKNESS).showIcon(), "Weakness display flags were lost.");

        helper.getLevel().setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        power.tick(holder);
        helper.assertTrue(!power.isActive(holder) && !player.hasEffect(MobEffects.WEAKNESS)
                && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Effects were reapplied after leaving the low ceiling.");
        helper.succeed();
    }
}
