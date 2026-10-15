package pt.realmcouncil;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import pt.realmcouncil.network.CouncilNetwork;

@Mod(RealmCouncil.ID)
public final class RealmCouncil {
    public static final String ID="realmcouncil";
    public static final Logger LOG=LogUtils.getLogger();
    public RealmCouncil(IEventBus bus,ModContainer container){
        CouncilBlocks.register(bus);bus.addListener(CouncilNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER,CouncilConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(CouncilEvents::login);
        NeoForge.EVENT_BUS.addListener(CouncilEvents::logout);
        NeoForge.EVENT_BUS.addListener(CouncilEvents::tick);
        NeoForge.EVENT_BUS.addListener(CouncilEvents::stopped);
        NeoForge.EVENT_BUS.addListener(CouncilCommands::register);
    }
}
