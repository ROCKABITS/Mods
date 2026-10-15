package pt.crownledger.network;

import com.google.gson.JsonObject;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Net {
    private Net() {}
    public static void send(String action, JsonObject payload) {
        PacketDistributor.sendToServer(new CrownPackets.Action(action, payload.toString()));
    }
}
