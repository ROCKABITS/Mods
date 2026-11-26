package pt.realmfitness;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

final class ProteinItem extends Item {
    private final boolean drink;

    ProteinItem(boolean drink){
        super(new Properties().stacksTo(drink?16:64).food(food(drink)));
        this.drink=drink;
    }

    private static FoodProperties food(boolean drink){
        var food=new FoodProperties.Builder().nutrition(drink?4:3).saturationModifier(.45f).alwaysEdible();
        if(drink)food.usingConvertsTo(Items.GLASS_BOTTLE);
        return food.build();
    }

    @Override public UseAnim getUseAnimation(ItemStack stack){return drink?UseAnim.DRINK:UseAnim.EAT;}

    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        return ItemUtils.startUsingInstantly(level,player,hand);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        if(entity instanceof ServerPlayer player)FitnessService.consumeSupplement(player);
        return super.finishUsingItem(stack,level,entity);
    }

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(RecoveryView.status().copy().withStyle(ChatFormatting.GRAY));
        lines.add(RecoveryView.amount().copy().withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("fitness.recovery.limit").withStyle(ChatFormatting.DARK_GRAY));
    }
}
