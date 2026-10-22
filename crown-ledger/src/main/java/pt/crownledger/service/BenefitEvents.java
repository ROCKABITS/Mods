package pt.crownledger.service;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import pt.crownledger.domain.Governance.Benefit;

@EventBusSubscriber(modid="kingdomtreasury")
public final class BenefitEvents {
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void mining(PlayerEvent.BreakSpeed event){
        if(event.getEntity() instanceof ServerPlayer player){
            int strength=GovernanceService.strength(player,Benefit.WORKS);
            if(strength>0&&event.getNewSpeed()>0)event.setNewSpeed(event.getNewSpeed()*(1+strength/100F));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void experience(PlayerXpEvent.XpChange event){
        if(event.getAmount()>0&&event.getEntity() instanceof ServerPlayer player){
            int percent=GovernanceService.strength(player,Benefit.KNOWLEDGE);
            if(percent>0){long scaled=event.getAmount()*(long)percent;long bonus=scaled/100+(player.getRandom().nextInt(100)<scaled%100?1:0);
                event.setAmount((int)Math.min(Integer.MAX_VALUE,event.getAmount()+bonus));}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void crops(CropGrowEvent.Pre event){
        if(event.getLevel() instanceof ServerLevel level && event.getResult()==CropGrowEvent.Pre.Result.DEFAULT &&
                event.getState().getBlock() instanceof CropBlock crop && !crop.isMaxAge(event.getState())){
            int percent=pt.crownledger.data.TreasuryData.get(level.getServer()).governance.strength(Benefit.HARVEST,GovernanceService.now(level.getServer()));
            if(percent>0&&level.random.nextInt(100)<percent)event.setResult(CropGrowEvent.Pre.Result.GROW);
        }
    }
    public static float exhaustion(ServerPlayer player,float amount){
        int percent=GovernanceService.strength(player,Benefit.FEAST);
        return amount>0&&percent>0?amount*(1-percent/100F):amount;
    }
    private BenefitEvents(){}
}
