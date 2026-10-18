package pt.lawsjustice.compat;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Optional display names only; account UUIDs retain custody authority. */
public final class JusticeNames {
    private JusticeNames(){}
    @SuppressWarnings("unchecked")
    public static String name(ServerPlayer player) {
        if(ModList.get().isLoaded("cronicasrp"))try {
            Object factory=Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null);
            if(factory instanceof Supplier<?> supplier&&supplier.get()instanceof AttachmentType<?> type) {
                Object profile=player.getData((AttachmentType<Object>)type);
                if(Boolean.TRUE.equals(profile.getClass().getMethod("created").invoke(profile))) {
                    Object raw=profile.getClass().getMethod("name").invoke(profile);
                    if(raw instanceof String text&&!text.isBlank()&&text.length()<=64&&text.codePoints().noneMatch(Character::isISOControl))return text;
                }
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
        return player.getGameProfile().getName();
    }
}
