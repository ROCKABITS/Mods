package pt.realmprogression.test;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.storage.LevelResource;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Treasury;
import pt.realmprogression.data.ProgressStore;
import pt.realmprogression.service.ProgressService;
import pt.realmprogression.domain.ProgressLedger;
import pt.realmprogression.block.ProgressBlocks;
import pt.realmprogression.network.ProgressPackets;
import com.google.gson.*;

final class ProgressFixture implements AutoCloseable {
    final GameTestHelper h;final Actor admin,player;final BlockPos pos;final TreasuryData previousTreasury;
    final ProgressStore store,previous;final Map<MinecraftServer,ProgressStore> stores;
    final List<ServerPlayer> online;final Map<UUID,ServerPlayer> ids;
    @SuppressWarnings("unchecked") ProgressFixture(GameTestHelper h)throws Exception{
        this.h=h;var s=h.getLevel().getServer();previousTreasury=TreasuryData.get(s);s.overworld().getDataStorage().set("crown_ledger",new TreasuryData());
        var field=ProgressStore.class.getDeclaredField("LOADED");field.setAccessible(true);stores=(Map<MinecraftServer,ProgressStore>)field.get(null);previous=stores.get(s);store=new ProgressStore(s.getWorldPath(LevelResource.ROOT).resolve("progression-native/"+UUID.randomUUID()+"/progress.json"));stores.put(s,store);
        var pf=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");pf.setAccessible(true);online=(List<ServerPlayer>)pf.get(s.getPlayerList());var idf=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");idf.setAccessible(true);ids=(Map<UUID,ServerPlayer>)idf.get(s.getPlayerList());
        admin=new Actor(h,"ResearchAdmin");player=new Actor(h,"ResearchPlayer");for(var p:List.of(admin,player)){online.add(p);ids.put(p.getUUID(),p);}
        s.getPlayerList().getOps().add(new ServerOpListEntry(admin.getGameProfile(),2,false));
        pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,ProgressBlocks.TABLE.get().defaultBlockState(),3);for(var p:List.of(admin,player))p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1);
    }
    void lock(long cents){store.commit(ProgressLedger.configure(store.state(),"progressionfixture",Set.of("progressionfixture"),cents,true,admin.getUUID(),"Admin",10));}
    void funds(long cents){var d=TreasuryData.get(h.getLevel().getServer());var fresh=new Treasury();if(cents>0)fresh.addFunds(Treasury.Currency.CHRONICLES,cents,Treasury.Kind.DEPOSIT,admin.getUUID(),"Native QA funds",System.currentTimeMillis());d.treasury=fresh;d.setDirty();}
    void open(Actor p){ProgressService.open(p,pos);}
    JsonObject body(Actor p,long cents,boolean enabled){var snap=ProgressService.snapshot(p);var b=new JsonObject();b.add("nonce",snap.get("nonce"));b.add("revision",snap.get("revision"));b.addProperty("mod","progressionfixture");b.addProperty("goal",cents);b.addProperty("enabled",enabled);b.addProperty("namespaces","progressionfixture");return b;}
    void releaseLimit(Actor p)throws Exception{var sf=ProgressService.class.getDeclaredField("SESSIONS");sf.setAccessible(true);Object session=((Map<?,?>)sf.get(null)).get(p.getUUID());for(String name:List.of("lastAction","lastQuery")){var f=session.getClass().getDeclaredField(name);f.setAccessible(true);f.setLong(session,0);}}
    static final class Actor extends ServerPlayer {
        final List<Packet<?>> packets=new ArrayList<>();
        Actor(GameTestHelper h,String name){super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),name),net.minecraft.server.level.ClientInformation.createDefault());
            connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),this,CommonListenerCookie.createInitial(getGameProfile(),false)){
                public boolean isAcceptingMessages(){return true;}
                public boolean hasChannel(net.minecraft.resources.ResourceLocation id){return id.getNamespace().equals("realmprogression");}
                public void send(Packet<?> p){packets.add(p);}
                public void send(Packet<?> p,PacketSendListener l){send(p);}
            };
        }
        private int next=10;
        public OptionalInt openMenu(MenuProvider p,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer){containerMenu=p.createMenu(next++,getInventory(),this);return OptionalInt.of(containerMenu.containerId);}
        public void closeContainer(){if(containerMenu!=inventoryMenu)containerMenu.removed(this);containerMenu=inventoryMenu;}
        public void displayClientMessage(net.minecraft.network.chat.Component c,boolean overlay){}
        long announcements(){return packets.stream().filter(ClientboundCustomPayloadPacket.class::isInstance).map(ClientboundCustomPayloadPacket.class::cast).filter(p->p.payload() instanceof ProgressPackets.Unlock).count();}
    }
    public void close(){
        var s=h.getLevel().getServer();for(var p:List.of(admin,player)){p.closeContainer();online.remove(p);ids.remove(p.getUUID());s.getPlayerList().getOps().remove(p.getGameProfile());}
        s.overworld().getDataStorage().set("crown_ledger",previousTreasury);if(previous==null)stores.remove(s);else stores.put(s,previous);
    }
}
