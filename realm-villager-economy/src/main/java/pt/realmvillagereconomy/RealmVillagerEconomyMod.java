package pt.realmvillagereconomy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import pt.realmvillagereconomy.menu.EconomyMenu;
import pt.realmvillagereconomy.network.TradeNetwork;
import pt.realmvillagereconomy.service.TradingService;

@Mod("realmvillagereconomy")
public final class RealmVillagerEconomyMod {
    public RealmVillagerEconomyMod(IEventBus bus){
        EconomyMenu.register(bus);bus.addListener(TradeNetwork::register);
        NeoForge.EVENT_BUS.addListener(TradingService::interact);
        NeoForge.EVENT_BUS.addListener(TradingService::commands);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->TradingService.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)TradingService.login(player);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)TradingService.close(player);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e)->{var receipt=e.getOriginal().getPersistentData().get(pt.realmvillagereconomy.service.TradeJournal.RECEIPT_KEY);if(receipt!=null)e.getEntity().getPersistentData().put(pt.realmvillagereconomy.service.TradeJournal.RECEIPT_KEY,receipt.copy());});
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e)->{if(!e.getLevel().isClientSide&&e.getEntity() instanceof net.minecraft.world.entity.npc.AbstractVillager merchant)TradingService.restore(merchant);});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->TradingService.stop(e.getServer()));
    }
}
