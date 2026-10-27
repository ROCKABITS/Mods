package pt.realmeconomy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import pt.realmeconomy.menu.EconomyMenu;
import pt.realmeconomy.network.TradeNetwork;
import pt.realmeconomy.service.TradingService;

@Mod("realmeconomy")
public final class RealmEconomyMod {
    private static void tick(net.minecraft.server.MinecraftServer server){TradingService.tick(server);pt.realmeconomy.service.Services.tick(server);}
    private static void stop(net.minecraft.server.MinecraftServer server){pt.realmeconomy.service.Services.stop(server);TradingService.stop(server);pt.realmeconomy.colony.ColonyService.stop();}
    public RealmEconomyMod(IEventBus bus){
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(pt.realmeconomy.colony.ColonyService::install));
        NeoForge.EVENT_BUS.addListener(pt.realmeconomy.colony.ColonyService::interact);
        pt.realmeconomy.block.EconomyBlocks.register(bus);EconomyMenu.register(bus);bus.addListener(TradeNetwork::register);
        NeoForge.EVENT_BUS.addListener(TradingService::interact);NeoForge.EVENT_BUS.addListener(pt.realmeconomy.service.Services::interact);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)TradingService.login(player);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer player){TradingService.close(player);pt.realmeconomy.service.Services.close(player);pt.realmeconomy.colony.ColonyService.close(player);}});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e)->{var receipt=e.getOriginal().getPersistentData().get(pt.realmeconomy.service.TradeJournal.RECEIPT_KEY);if(receipt!=null)e.getEntity().getPersistentData().put(pt.realmeconomy.service.TradeJournal.RECEIPT_KEY,receipt.copy());});
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{if(!e.getLevel().isClientSide&&e.getEntity() instanceof net.minecraft.world.entity.npc.AbstractVillager merchant)TradingService.restore(merchant);});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->stop(e.getServer()));
    }
}
