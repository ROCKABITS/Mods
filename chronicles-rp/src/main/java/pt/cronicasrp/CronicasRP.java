package pt.cronicasrp;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import pt.cronicasrp.network.ProfileNetwork;

@Mod(CronicasRP.MOD_ID)
public final class CronicasRP {
    public static final String MOD_ID = "cronicasrp";
    public static final Logger LOGGER = LogUtils.getLogger();
    public CronicasRP(IEventBus modBus, ModContainer container) {
        ModAttachments.register(modBus);
        CurrencyItems.register(modBus);
        modBus.addListener(ProfileNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(RpNames::onNameFormat);
        NeoForge.EVENT_BUS.addListener(RpNames::onTabListNameFormat);
        NeoForge.EVENT_BUS.addListener(RpNames::onLogin);
        NeoForge.EVENT_BUS.addListener(RpNames::onLogout);
        NeoForge.EVENT_BUS.addListener(RpNames::onRespawn);
        NeoForge.EVENT_BUS.addListener(RpNames::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(RpNames::onStartTracking);
        NeoForge.EVENT_BUS.addListener(RpNames::onServerTick);
        NeoForge.EVENT_BUS.addListener(RpNames::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(RpCommands::register);
    }
}
