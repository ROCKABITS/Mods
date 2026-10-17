package pt.realmnotifications;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import pt.realmnotifications.network.NotificationNetwork;

@Mod("realmnotifications")
public final class RealmNotificationsMod {
    public static final Logger LOGGER = LogUtils.getLogger();
    public RealmNotificationsMod(IEventBus bus) {
        bus.addListener(NotificationNetwork::register);
    }
}
