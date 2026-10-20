package pt.realmmarriage.test;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.attachment.AttachmentType;
import pt.realmmarriage.block.*;
import pt.realmmarriage.compat.WalletBridge;
import pt.realmmarriage.data.MarriageData;
import pt.realmmarriage.domain.MarriageLedger.*;
import pt.realmmarriage.service.*;

final class MarriageActors implements AutoCloseable {
    final GameTestHelper h;final List<ServerPlayer> online;final Map<UUID,ServerPlayer> byId;final List<Actor> actors=new ArrayList<>();final BlockPos altar;
    @SuppressWarnings("unchecked") MarriageActors(GameTestHelper h){this.h=h;try{var type=net.minecraft.server.players.PlayerList.class;var f=type.getDeclaredField("players");f.setAccessible(true);online=(List<ServerPlayer>)f.get(h.getLevel().getServer().getPlayerList());f=type.getDeclaredField("playersByUUID");f.setAccessible(true);byId=(Map<UUID,ServerPlayer>)f.get(h.getLevel().getServer().getPlayerList());}catch(Exception e){throw new IllegalStateException(e);}altar=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(altar.below(),Blocks.STONE.defaultBlockState(),3);}
    Actor actor(long balance){Actor p=new Actor(h,altar);actors.add(p);online.add(p);byId.put(p.getUUID(),p);if(WalletBridge.loaded())profile(p,balance);MarriageService.data(p);return p;}
    void place(Actor p){p.setYRot(180);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BlockRegistry.ALTAR_ITEM.get()));var hit=new BlockHitResult(Vec3.atCenterOf(altar.below()).add(0,.5,0),Direction.UP,altar.below(),false);if(!p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)).consumesAction())throw new IllegalStateException("Actual altar placement failed");for(Actor a:actors)MarriageService.open(a,altar);}
    Union committing(Mode mode,long fee){Actor a=actors.get(0),b=actors.get(1),priest=actors.get(2);var d=MarriageData.get(h.getLevel().getServer());long now=System.currentTimeMillis();var l=d.ledger;Union u=l.propose(a.getUUID(),b.getUUID(),now);l.answer(u,b.getUUID(),true,now);l.configure(u,a.getUUID(),priest.getUUID(),List.of(),mode,5000,fee,now);l.agree(u,a.getUUID(),now);l.agree(u,b.getUUID(),now);l.invitation(u,priest.getUUID(),true,now);l.start(u,priest.getUUID(),h.getLevel().dimension().location().toString(),altar.getX(),altar.getY(),altar.getZ(),now);l.next(u,priest.getUUID(),now);l.next(u,priest.getUUID(),now);l.tick(u.objectionUntil);l.ask(u,priest.getUUID(),now);l.vow(u,a.getUUID(),now);l.ask(u,priest.getUUID(),now);l.vow(u,b.getUUID(),now);l.seal(u,priest.getUUID(),now);d.persist(h.getLevel().getServer());return u;}
    Union married(Mode mode,long fee){Union u=committing(mode,fee);MarriageService.settle(h.getLevel().getServer(),u);if(u.phase!=Phase.MARRIED)throw new IllegalStateException("Marriage did not settle: "+u.error);return u;}
    static final class Actor extends ServerPlayer {
        final List<Packet<?>> packets=new ArrayList<>();final List<Component> messages=new ArrayList<>();
        Actor(GameTestHelper h,BlockPos pos){super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"MarriageQA"),ClientInformation.createDefault());connection=new ServerGamePacketListenerImpl(getServer(),new Connection(PacketFlow.SERVERBOUND),this,CommonListenerCookie.createInitial(getGameProfile(),false)){
            @Override public boolean isAcceptingMessages(){return true;}
            @Override public boolean hasChannel(ResourceLocation id){return Set.of("realmmarriage","realmidentity","realmnotifications").contains(id.getNamespace());}
            @Override public void send(Packet<?> packet){packets.add(packet);}
            @Override public void send(Packet<?> packet,PacketSendListener listener){send(packet);}
        };setGameMode(GameType.SURVIVAL);setPos(pos.getX()+.5,pos.getY(),pos.getZ()+3.5);}
        @Override public void displayClientMessage(Component c,boolean overlay){if(messages!=null)messages.add(c);}
        @Override public void sendSystemMessage(Component c){if(messages!=null)messages.add(c);}
        @Override public void sendSystemMessage(Component c,boolean overlay){sendSystemMessage(c);}
    }
    @SuppressWarnings("unchecked") static void profile(ServerPlayer p,long balance){try{AttachmentType<Object> attachment=(AttachmentType<Object>)((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();Object profile=Class.forName("pt.cronicasrp.domain.CharacterProfile").getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,"RP "+p.getUUID().toString().substring(0,5),28,"origins:human","Private biography","Private signature","Comum",balance,balance,"");p.setData(attachment,profile);}catch(Exception e){throw new IllegalStateException(e);}}
    @SuppressWarnings({"rawtypes","unchecked"}) static long bank(net.minecraft.server.MinecraftServer s){try{Object data=Class.forName("pt.crownledger.data.TreasuryData").getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,s);Object treasury=data.getClass().getField("treasury").get(data);Class<Enum> currency=(Class<Enum>)Class.forName("pt.crownledger.domain.Treasury$Currency");return(long)treasury.getClass().getMethod("balance",currency).invoke(treasury,Enum.valueOf(currency,"CHRONICLES"));}catch(Exception e){throw new IllegalStateException(e);}}
    @Override public void close(){for(Actor p:actors){MarriageService.logout(p.getUUID());online.remove(p);byId.remove(p.getUUID());p.discard();}}
}
