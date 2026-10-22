package pt.cronicasrp;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import pt.cronicasrp.domain.CharacterProfile;
import pt.cronicasrp.domain.ProfileStorage;
import java.util.function.Supplier;

public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CronicasRP.MOD_ID);
    public static final Supplier<AttachmentType<CharacterProfile>> PROFILE = REGISTER.register("profile", () -> AttachmentType.builder(CharacterProfile::empty).serialize(ProfileStorage.CODEC).copyOnDeath().build());
    private ModAttachments() {}
    public static void register(IEventBus bus) { REGISTER.register(bus); }
}
