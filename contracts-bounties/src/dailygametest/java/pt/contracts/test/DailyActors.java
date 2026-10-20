package pt.contracts.test;

import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.players.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.attachment.AttachmentType;
import pt.contracts.*;
import pt.contracts.daily.*;
import pt.contracts.daily.DailyRules.*;
import pt.contracts.compat.ChroniclesBridge;

/** Real ServerPlayer entities and normal saves; only the network connection is captured. */
final class DailyActors implements AutoCloseable {
    final GameTestHelper h;
    final List<Actor> actors=new ArrayList<>();
    final List<ServerPlayer> online;
    final Map<UUID,ServerPlayer> ids;
    @SuppressWarnings("unchecked") DailyActors(GameTestHelper h){this.h=h;try{
        var type=PlayerList.class;var field=type.getDeclaredField("players");field.setAccessible(true);online=(List<ServerPlayer>)field.get(h.getLevel().getServer().getPlayerList());field=type.getDeclaredField("playersByUUID");field.setAccessible(true);ids=(Map<UUID,ServerPlayer>)field.get(h.getLevel().getServer().getPlayerList());
    }catch(Exception e){throw new IllegalStateException(e);}}
    Actor actor(){Actor p=new Actor(h);actors.add(p);online.add(p);ids.put(p.getUUID(),p);if(ChroniclesBridge.loaded())profile(p,100000);var data=DailyData.get(p.getServer());data.ledger.initialized=true;if(!ChroniclesBridge.loaded())data.ledger.settings.emeraldFallback=true;baselineRewards(p);return p;}
    static final class Actor extends ServerPlayer {
        final List<Packet<?>> packets=new ArrayList<>();
        final List<Component> messages=new ArrayList<>();
        Actor(GameTestHelper h){super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"DailyQA"),ClientInformation.createDefault());
            connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),this,CommonListenerCookie.createInitial(getGameProfile(),false)){
                @Override public boolean isAcceptingMessages(){return true;}
                @Override public boolean hasChannel(ResourceLocation id){return id.getNamespace().equals("contracts")||id.getNamespace().equals("realmnotifications");}
                @Override public void send(Packet<?> p){packets.add(p);}
                @Override public void send(Packet<?> p,PacketSendListener listener){send(p);}
            };setGameMode(GameType.SURVIVAL);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        }
        @Override public void sendSystemMessage(Component message){if(messages!=null)messages.add(message);}
        @Override public void sendSystemMessage(Component message,boolean overlay){sendSystemMessage(message);}
        @Override public void displayClientMessage(Component message,boolean overlay){sendSystemMessage(message);}
        long finales(){return packets.stream().filter(p->p instanceof ClientboundCustomPayloadPacket c&&c.payload()instanceof DailyPackets.Finale).count();}
    }
    static void profile(ServerPlayer p,long balance){try{
        @SuppressWarnings("unchecked") AttachmentType<Object> attachment=(AttachmentType<Object>)((Supplier<?>)Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null)).get();
        Object value=Class.forName("pt.cronicasrp.domain.CharacterProfile").getConstructor(boolean.class,String.class,int.class,String.class,String.class,String.class,String.class,long.class,long.class,String.class).newInstance(true,"Aetherion",28,"origins:human","Private history","Private signature","Comum",100000L,balance,"");p.setData(attachment,value);
    }catch(Exception e){throw new IllegalStateException(e);}}
    static void baselineRewards(ServerPlayer p){try{Class<?> type=Class.forName("pt.realmrewards.data.RewardData");Object data=type.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,p.getServer());Object ledger=type.getField("ledger").get(data);List<String> all=p.getServer().getAdvancements().getAllAdvancements().stream().map(a->a.id().toString()).toList();ledger.getClass().getMethod("baseline",UUID.class,Collection.class).invoke(ledger,p.getUUID(),all);}catch(ClassNotFoundException absent){}catch(Exception e){throw new IllegalStateException(e);}}
    static void op(ServerPlayer p){p.getServer().getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(),2,false));}
    static List<Template> templates(){return List.of(
        template("stone",Category.MINING,Difficulty.EASY,"minecraft:stone",1),template("wheat",Category.FARMING,Difficulty.EASY,"minecraft:wheat",1),
        template("zombie",Category.COMBAT,Difficulty.MEDIUM,"minecraft:zombie",1),template("fish",Category.FISHING,Difficulty.MEDIUM,"*fish",1),template("delivery",Category.DELIVERY,Difficulty.HARD,"minecraft:iron_ingot",2));}
    static Template template(String id,Category category,Difficulty difficulty,String target,int quantity){return new Template("daily_fixture:"+id,"Native "+id,category,difficulty,target,quantity,-1,10,Set.of(),Set.of(),Set.of(),true,null);}
    static DailyLedger.Round round(ServerPlayer p){var data=DailyData.get(p.getServer());var account=data.ledger.account(p.getUUID());account.dimensions.add(p.level().dimension().location().toString());account.biomes.add(p.level().getBiome(p.blockPosition()).unwrapKey().orElseThrow().location().toString());return data.ledger.generate(p.getUUID(),System.currentTimeMillis(),templates(),ChroniclesBridge.loaded()?"CHRONICLES":"EMERALD");}
    static DailyLedger.Quest quest(ServerPlayer p,Category category){return round(p).quests.stream().filter(q->q.template.category()==category).findFirst().orElseThrow();}
    static void board(ServerPlayer p,GameTestHelper h){BlockPos pos=h.absolutePos(new BlockPos(2,2,4));h.getLevel().setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(pos,ContractsMod.BOARD.get().defaultBlockState(),3);p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+1.5);ContractsService.open(p,pos);ContractsService.requireSession(p);}
    @Override public void close(){for(var p:actors){online.remove(p);ids.remove(p.getUUID());p.getServer().getPlayerList().deop(p.getGameProfile());ContractsService.logout(p.getUUID());DailyService.logout(p.getUUID());}}
}
