package pt.lawsjustice;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.lawsjustice.block.BlockRegistry;
import pt.lawsjustice.service.JusticeService;
import pt.lawsjustice.service.CourtService;
import pt.lawsjustice.service.WorkService;
import pt.lawsjustice.network.JusticeNetwork;

@Mod(LawsJusticeMod.ID)
public final class LawsJusticeMod {
    public static final String ID="lawsjustice";
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    static {
        TABS.register("laws_justice",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.lawsjustice"))
                .icon(()->new ItemStack(BlockRegistry.PRISON_CONTROL_ITEM.get()))
                .displayItems((parameters,out)->{
                    out.accept(BlockRegistry.PRISON_DOOR_ITEM.get());out.accept(BlockRegistry.PRISON_BARS_ITEM.get());
                    out.accept(BlockRegistry.REINFORCED_LOCK_ITEM.get());out.accept(BlockRegistry.PRISON_CONTROL_ITEM.get());out.accept(BlockRegistry.AREA_SELECTOR.get());out.accept(BlockRegistry.JUSTICE_DESK_ITEM.get());out.accept(BlockRegistry.HANDCUFFS.get());out.accept(BlockRegistry.WARRANT.get());
                    for(var item:workItems())out.accept(item);out.accept(BlockRegistry.WORK_BUNDLE.get());
                }).build());
    }
    private static java.util.List<net.minecraft.world.item.Item> workItems(){return java.util.List.of(BlockRegistry.WORK_BOARD_ITEM.get(),BlockRegistry.DELIVERY_BOX_ITEM.get(),BlockRegistry.MINING_POST_ITEM.get(),BlockRegistry.AGRICULTURAL_POST_ITEM.get(),BlockRegistry.WORKSHOP_TABLE_ITEM.get(),BlockRegistry.MAINTENANCE_POST_ITEM.get(),BlockRegistry.WAREHOUSE_POST_ITEM.get());}
    public LawsJusticeMod(IEventBus bus){
        BlockRegistry.register(bus);TABS.register(bus);bus.addListener(JusticeNetwork::register);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::commands);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::login);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::logout);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::respawn);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::dimension);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::tick);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::stopped);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::breakBlock);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::placeBlock);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::interact);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::item);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::explosion);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::piston);
        NeoForge.EVENT_BUS.addListener(LawsJusticeMod::fluid);
    }
    private static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("justice").executes(c->{c.getSource().sendSuccess(()->Component.translatable("lawsjustice.command.help"),false);return 1;})
                .then(Commands.literal("blocks").requires(s->s.hasPermission(2)).executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();for(var item:java.util.stream.Stream.concat(java.util.List.of(BlockRegistry.PRISON_DOOR_ITEM.get(),BlockRegistry.PRISON_BARS_ITEM.get(),BlockRegistry.REINFORCED_LOCK_ITEM.get(),BlockRegistry.PRISON_CONTROL_ITEM.get(),BlockRegistry.AREA_SELECTOR.get(),BlockRegistry.JUSTICE_DESK_ITEM.get(),BlockRegistry.HANDCUFFS.get()).stream(),workItems().stream()).toList()){ItemStack stack=new ItemStack(item);if(!p.getInventory().add(stack))p.drop(stack,false);}return 1;})));
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity()instanceof ServerPlayer p){JusticeService.login(p);CourtService.login(p);}}
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity()instanceof ServerPlayer p){WorkService.logout(p);CourtService.logout(p);JusticeService.logout(p);}}
    private static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity()instanceof ServerPlayer p){JusticeService.login(p);CourtService.login(p);}}
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity()instanceof ServerPlayer p){JusticeService.login(p);CourtService.login(p);}}
    private static void tick(ServerTickEvent.Post e){JusticeService.tick(e.getServer());CourtService.tick(e.getServer());WorkService.tick(e.getServer());}
    private static void stopped(ServerStoppedEvent e){WorkService.stop(e.getServer());CourtService.stop(e.getServer());JusticeService.stop(e.getServer());}
    private static void breakBlock(BlockEvent.BreakEvent e){if(e.getPlayer()instanceof ServerPlayer p){if(WorkService.harvest(p,e.getPos())){e.setCanceled(true);return;}if(!JusticeService.canBreak(p,e.getPos()))e.setCanceled(true);else WorkService.destroyed(p,e.getPos());}}
    private static void placeBlock(BlockEvent.EntityPlaceEvent e){if(e.getEntity()instanceof ServerPlayer p){boolean allow=JusticeService.canPlace(p,e.getPos());if(e instanceof BlockEvent.EntityMultiPlaceEvent multi)allow=allow&&multi.getReplacedBlockSnapshots().stream().allMatch(s->JusticeService.canPlace(p,s.getPos()));if(!allow)e.setCanceled(true);else{WorkService.placed(p,e.getPos());if(e instanceof BlockEvent.EntityMultiPlaceEvent multi)for(var snapshot:multi.getReplacedBlockSnapshots())WorkService.placed(p,snapshot.getPos());}}}
    private static void interact(PlayerInteractEvent.RightClickBlock e){if(e.getEntity()instanceof ServerPlayer p){if(e.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND&&WorkService.clean(p,e.getPos())){e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);}else if(!JusticeService.canInteract(p,e.getPos())){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}}
    private static void item(PlayerInteractEvent.RightClickItem e){if(e.getEntity()instanceof ServerPlayer p&&(JusticeService.imprisoned(p)||CourtService.restricted(p))&&(e.getItemStack().getItem()instanceof BucketItem||e.getItemStack().is(Items.POWDER_SNOW_BUCKET)||e.getItemStack().is(Items.ENDER_PEARL)||e.getItemStack().is(Items.CHORUS_FRUIT))){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    private static void explosion(ExplosionEvent.Detonate e){if(e.getLevel()instanceof ServerLevel l)e.getAffectedBlocks().removeIf(pos->JusticeService.protectedArea(l,pos)||e.getLevel().getBlockState(pos).getBlock()instanceof pt.lawsjustice.block.PrisonDoorBlock||e.getLevel().getBlockState(pos).getBlock()instanceof pt.lawsjustice.block.PrisonControlBlock||e.getLevel().getBlockState(pos).getBlock()instanceof pt.lawsjustice.block.PrisonBarsBlock||e.getLevel().getBlockState(pos).getBlock()instanceof pt.lawsjustice.block.ReinforcedLockBlock||e.getLevel().getBlockState(pos).getBlock()instanceof pt.lawsjustice.block.JusticeDeskBlock||e.getLevel().getBlockState(pos).getBlock()instanceof pt.lawsjustice.block.WorkStationBlock);}
    private static void piston(PistonEvent.Pre e){if(e.getLevel()instanceof ServerLevel l){boolean protectedBlock=JusticeService.protectedArea(l,e.getPos())||JusticeService.protectedArea(l,e.getFaceOffsetPos());var resolver=e.getStructureHelper();if(resolver!=null&&resolver.resolve())protectedBlock=protectedBlock||resolver.getToPush().stream().anyMatch(p->JusticeService.protectedArea(l,p)||JusticeService.protectedArea(l,p.relative(e.getDirection())))||resolver.getToDestroy().stream().anyMatch(p->JusticeService.protectedArea(l,p));if(protectedBlock)e.setCanceled(true);}}
    private static void fluid(BlockEvent.FluidPlaceBlockEvent e){if(e.getLevel()instanceof ServerLevel l&&JusticeService.protectedArea(l,e.getPos()))e.setCanceled(true);}
}
