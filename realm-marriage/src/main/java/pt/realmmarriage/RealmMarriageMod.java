package pt.realmmarriage;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.realmmarriage.block.BlockRegistry;
import pt.realmmarriage.network.MarriageNetwork;
import pt.realmmarriage.service.*;
@Mod(RealmMarriageMod.ID)
public final class RealmMarriageMod {
    public static final String ID="realmmarriage";
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    static{TABS.register("realm_marriage",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.realmmarriage")).icon(()->new ItemStack(BlockRegistry.ALTAR_ITEM.get())).displayItems((p,out)->{out.accept(BlockRegistry.ALTAR_ITEM.get());out.accept(BlockRegistry.ENGAGEMENT_RING.get());out.accept(BlockRegistry.WEDDING_RING.get());out.accept(BlockRegistry.CERTIFICATE.get());}).build());}
    public RealmMarriageMod(IEventBus bus){BlockRegistry.register(bus);TABS.register(bus);MarriageSounds.register(bus);bus.addListener(MarriageNetwork::register);NeoForge.EVENT_BUS.addListener(RealmMarriageMod::tick);NeoForge.EVENT_BUS.addListener(RealmMarriageMod::login);NeoForge.EVENT_BUS.addListener(RealmMarriageMod::logout);NeoForge.EVENT_BUS.addListener(RealmMarriageMod::cloned);NeoForge.EVENT_BUS.addListener(RealmMarriageMod::started);NeoForge.EVENT_BUS.addListener(RealmMarriageMod::stopped);NeoForge.EVENT_BUS.addListener(RealmMarriageMod::commands);}
    private static void tick(ServerTickEvent.Post e){MarriageService.tick(e.getServer());}
    private static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity()instanceof net.minecraft.server.level.ServerPlayer p)MarriageService.login(p);}
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e){MarriageService.logout(e.getEntity().getUUID());}
    private static void cloned(PlayerEvent.Clone e){if(e.getOriginal().getPersistentData().get(WalletJournal.RECEIPT)instanceof net.minecraft.nbt.CompoundTag t)e.getEntity().getPersistentData().put(WalletJournal.RECEIPT,t.copy());}
    private static void started(ServerStartedEvent e){MarriageService.started(e.getServer());}
    private static void stopped(ServerStoppedEvent e){MarriageService.clear();}
    private static void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("marriage").executes(c->{c.getSource().sendSuccess(()->Component.translatable("marriage.help"),false);return 1;}).then(Commands.literal("altar").requires(c->c.hasPermission(2)).executes(c->{var p=c.getSource().getPlayerOrException();if(!p.getInventory().add(new ItemStack(BlockRegistry.ALTAR_ITEM.get())))p.drop(new ItemStack(BlockRegistry.ALTAR_ITEM.get()),false);return 1;})));}
}
