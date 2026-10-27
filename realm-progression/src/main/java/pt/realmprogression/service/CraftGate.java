package pt.realmprogression.service;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.*;
import pt.realmprogression.data.ProgressStore;
import pt.realmprogression.domain.ProgressLedger;

public final class CraftGate {
    public static boolean blocked(Level level,ItemStack result){
        if(result.isEmpty())return false;
        String namespace=BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace();
        if(ProgressLedger.PROTECTED.contains(namespace))return false;
        if(level.isClientSide)return pt.realmprogression.client.ClientProgress.locked().contains(namespace);
        var server=level.getServer();ProgressService.observe(server);
        try{return ProgressStore.get(server).state().blocked(namespace);}
        catch(RuntimeException failure){return true;}
    }
    public static void refresh(MinecraftServer server){
        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            p.inventoryMenu.slotsChanged(p.inventoryMenu.getSlot(1).container);
            if(p.containerMenu instanceof CraftingMenu menu)menu.slotsChanged(menu.getSlot(1).container);
            if(p.containerMenu instanceof CrafterMenu menu)((pt.realmprogression.mixin.CrafterMenuAccess)menu).realmProgression$refresh();
            p.containerMenu.broadcastChanges();
        }
    }
    private CraftGate(){}
}
