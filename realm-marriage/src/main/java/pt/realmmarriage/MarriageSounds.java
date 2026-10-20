package pt.realmmarriage;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
public final class MarriageSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"realmmarriage");
    private static final Map<String,DeferredHolder<SoundEvent,SoundEvent>> EVENTS=new HashMap<>();
    static{for(String id:List.of("opening","countdown","objection","married"))EVENTS.put(id,SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(ResourceLocation.parse("realmmarriage:"+id))));}
    private MarriageSounds(){}
    public static void register(IEventBus bus){SOUNDS.register(bus);}
    public static SoundEvent sound(String id){return EVENTS.get(id).get();}
}
