package pt.contracts.daily;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/** Original short fanfares, synthesized specifically for the four parchment celebrations. */
public final class DailySounds {
    private static final DeferredRegister<SoundEvent> REGISTER=DeferredRegister.create(Registries.SOUND_EVENT,"contracts");
    private static final Map<DailyRules.Difficulty,Supplier<SoundEvent>> SOUNDS=new EnumMap<>(DailyRules.Difficulty.class);
    static{for(var difficulty:DailyRules.Difficulty.values()){String id="daily_"+difficulty.name().toLowerCase(Locale.ROOT);SOUNDS.put(difficulty,REGISTER.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("contracts:"+id))));}}
    private DailySounds(){}
    public static void register(IEventBus bus){REGISTER.register(bus);}
    public static SoundEvent sound(DailyRules.Difficulty difficulty){return SOUNDS.get(difficulty).get();}
}
