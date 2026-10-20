package pt.realmvillagereconomy.test;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.LevelResource;
import pt.realmvillagereconomy.data.EconomyData;
import pt.realmvillagereconomy.service.TradingService;

final class TradeFixture implements AutoCloseable {
    final GameTestHelper h;final TradeActors actors;final TradeActors.Actor player,other;final Villager merchant;final EconomyData data,previous;final Map<MinecraftServer,EconomyData> loaded;final Path directory;
    @SuppressWarnings("unchecked") TradeFixture(GameTestHelper h){
        this.h=h;actors=new TradeActors(h);player=actors.actor("VillagerBuyerQA",100000);other=actors.actor("VillagerOtherQA",200000);var server=h.getLevel().getServer();previous=EconomyData.get(server);
        try{Field f=EconomyData.class.getDeclaredField("LOADED");f.setAccessible(true);loaded=(Map<MinecraftServer,EconomyData>)f.get(null);directory=server.getWorldPath(LevelResource.ROOT).resolve("villager-native/"+UUID.randomUUID());Files.createDirectories(directory);
            var fresh=EconomyData.class.getDeclaredMethod("fresh");fresh.setAccessible(true);var ctor=EconomyData.class.getDeclaredConstructor(Path.class,net.minecraft.nbt.CompoundTag.class);ctor.setAccessible(true);data=ctor.newInstance(directory.resolve("economy.dat"),fresh.invoke(null));loaded.put(server,data);
        }catch(Exception error){throw new IllegalStateException(error);}
        merchant=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));merchant.setNoAi(true);merchant.setVillagerData(merchant.getVillagerData().setProfession(VillagerProfession.FARMER));merchant.getOffers().clear();offer(new MerchantOffer(new ItemCost(Items.EMERALD,20),new ItemStack(Items.BREAD,3),12,4,.05F));
    }
    void offer(MerchantOffer offer){merchant.getOffers().add(offer);}
    void open(TradeActors.Actor actor){actor.setPos(merchant.getX()+1,merchant.getY(),merchant.getZ());TradingService.open(actor,merchant);}
    JsonObject view(TradeActors.Actor actor){return TradingService.snapshot(actor);}
    JsonObject payload(TradeActors.Actor actor){var view=view(actor);var p=new JsonObject();p.add("nonce",view.get("nonce"));p.add("quote",view.get("quote"));return p;}
    void trade(TradeActors.Actor actor,int index){var p=payload(actor);p.addProperty("offer",index);TradingService.action(actor,"trade",p.toString());}
    void policy(TradeActors.Actor actor,int rate){var p=payload(actor);p.addProperty("rate",rate);TradingService.action(actor,"policy",p.toString());}
    void op(TradeActors.Actor actor){actor.server.getPlayerList().getOps().add(new ServerOpListEntry(actor.getGameProfile(),2,false));}
    static void field(Object target,String name,Object value){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}catch(Exception error){throw new IllegalStateException(error);}}
    @Override public void close(){player.closeContainer();other.closeContainer();merchant.discard();loaded.put(h.getLevel().getServer(),previous);actors.close();}
}
