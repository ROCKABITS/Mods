package pt.realmprogression.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.network.chat.Component;
@EventBusSubscriber(modid="realmprogression",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent e){e.register(pt.realmprogression.menu.ProgressMenu.TYPE.get(),ProgressScreen::new);}
    @EventBusSubscriber(modid="realmprogression",value=Dist.CLIENT)
    public static final class Game {
        @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut e){ClientProgress.clear();}
        @SubscribeEvent public static void overlay(RenderGuiEvent.Post e){ClientProgress.render(e.getGuiGraphics());}
        @SubscribeEvent public static void tooltip(ItemTooltipEvent e){if(!e.getItemStack().isEmpty()&&ClientProgress.locked().contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).getNamespace()))e.getToolTip().add(Component.translatable("gui.realmprogression.tooltip").withStyle(net.minecraft.ChatFormatting.GOLD));}
    }
}
