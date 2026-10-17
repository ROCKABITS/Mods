package pt.realmrewards;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import pt.realmrewards.network.RewardNetwork;
import pt.realmrewards.service.RewardService;

@Mod("realmrewards")
public final class RealmRewardsMod {
    public static final Logger LOGGER=LogUtils.getLogger();
    public RealmRewardsMod(IEventBus bus) {
        bus.addListener(RewardNetwork::register);
        NeoForge.EVENT_BUS.addListener(RewardService::earned);
        NeoForge.EVENT_BUS.addListener(RewardService::login);
        NeoForge.EVENT_BUS.addListener(RewardService::tick);
        NeoForge.EVENT_BUS.addListener(RewardService::commands);
    }
}
