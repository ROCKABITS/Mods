package pt.realmcasino.blockentity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pt.realmcasino.block.BlockRegistry;

public final class CasinoBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "realmcasino");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CasinoBlockEntity>> MACHINE = TYPES.register("machine",
            () -> BlockEntityType.Builder.of(CasinoBlockEntity::new, BlockRegistry.SLOT_MACHINE.get(),
                    BlockRegistry.ROULETTE_TABLE.get(), BlockRegistry.BLACKJACK_TABLE.get()).build(null));
    private CasinoBlockEntities() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
