package pt.realmcasino.visualtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import pt.realmcasino.compat.ChroniclesBridge;
import pt.realmcasino.service.CasinoService;

/** Server-side counterparty for the actual host GUI. This is NOT an authenticated second client. */
final class CasinoVisualPeer extends FakePlayer {
    static final long INITIAL = 100_000;
    private JsonObject snapshot;
    CasinoVisualPeer(ServerPlayer host) {
        super(host.serverLevel(), new GameProfile(UUID.randomUUID(), "CasinoPeerQA"));
        connection = new ServerGamePacketListenerImpl(host.getServer(), new Connection(PacketFlow.SERVERBOUND), this,
                CommonListenerCookie.createInitial(getGameProfile(), false)) {
            @Override public boolean isAcceptingMessages() { return true; }
            @Override public boolean hasChannel(ResourceLocation id) { return id.getNamespace().equals("realmcasino") || id.getNamespace().equals("realmnotifications"); }
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket wrapped && wrapped.payload().type().id().getNamespace().equals("realmcasino")) {
                    try { snapshot = JsonParser.parseString((String) wrapped.payload().getClass().getMethod("json").invoke(wrapped.payload())).getAsJsonObject(); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                }
            }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
        };
        try {
            Field players = PlayerList.class.getDeclaredField("players"), ids = PlayerList.class.getDeclaredField("playersByUUID"); players.setAccessible(true); ids.setAccessible(true);
            @SuppressWarnings("unchecked") List<ServerPlayer> online = (List<ServerPlayer>) players.get(host.getServer().getPlayerList());
            @SuppressWarnings("unchecked") Map<UUID,ServerPlayer> byId = (Map<UUID,ServerPlayer>) ids.get(host.getServer().getPlayerList());
            online.add(this); byId.put(getUUID(), this);
            var type = Class.forName("pt.cronicasrp.domain.CharacterProfile");
            Object profile = type.getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class)
                    .newInstance(true,"Ser Rowan",31,"origins:human","QA counterparty, fixture money only.","Ser Rowan","Comum",INITIAL,INITIAL,"");
            @SuppressWarnings("unchecked") AttachmentType<Object> attachment = (AttachmentType<Object>) ((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();
            setData(attachment,profile); host.getServer().getPlayerList().saveAll();
            if (ChroniclesBridge.balance(this) != INITIAL) throw new IllegalStateException("Missing actual counterparty wallet");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    void join(BlockPos pos,long buyIn) {
        setPos(pos.getX()+.5, pos.getY(),pos.getZ()-2); CasinoService.open(this,pos);
        JsonObject payload=base(); payload.addProperty("amount",buyIn); CasinoService.action(this,"table_join",payload.toString()); valid();
    }
    private JsonObject base() {
        valid(); JsonObject out=new JsonObject(); out.add("nonce",snapshot.get("nonce"));out.add("version",snapshot.get("version"));return out;
    }
    private void valid(){if(snapshot==null||snapshot.has("error")&&!snapshot.get("error").getAsString().isEmpty())throw new IllegalStateException("Counterparty snapshot failed: "+snapshot);}
    void advance() {
        valid(); if(!snapshot.has("table")||!snapshot.get("table").isJsonObject())return;
        var table=snapshot.getAsJsonObject("table");if(!table.get("phase").getAsString().equals("playing")||!table.get("yourTurn").getAsBoolean())return;
        var payload=base();payload.add("round",table.get("id"));payload.add("revision",table.get("revision"));
        String action=snapshot.get("game").getAsString().equals("blackjack_pvp")?"stand":table.get("call").getAsLong()==0?"check":"call";
        CasinoService.action(this,"table_"+action,payload.toString());valid();
    }
    void claim(){valid();if(snapshot.get("pending").getAsLong()>0){var payload=base();payload.addProperty("amount",0);CasinoService.action(this,"claim",payload.toString());valid();}}
}
