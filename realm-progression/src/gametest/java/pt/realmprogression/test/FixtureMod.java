package pt.realmprogression.test;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
/** Controlled crafting outputs. Added to metadata only for native tests, never to a production JAR. */
@Mod("progressionfixture")
public final class FixtureMod {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,"progressionfixture");
    public static final java.util.function.Supplier<Item> TOKEN=ITEMS.register("clockwork",()->new Item(new Item.Properties()));
    public FixtureMod(IEventBus bus){ITEMS.register(bus);}
}
