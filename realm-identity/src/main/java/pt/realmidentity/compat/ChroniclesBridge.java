package pt.realmidentity.compat;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import pt.realmidentity.domain.ProfileSnapshot;

/** Optional, read-only adapter. No profile writers, economy fields, or sync calls are resolved. */
public final class ChroniclesBridge {
    private static volatile AttachmentType<Object> attachment;

    private ChroniclesBridge() {}

    public static boolean loaded() { return ModList.get().isLoaded("cronicasrp"); }

    public static ProfileSnapshot snapshot(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) {
            throw new IllegalStateException("gui.realmidentity.invalid_request");
        }
        if (!loaded()) {
            return new ProfileSnapshot(ProfileSnapshot.Source.MINECRAFT, player.getGameProfile().getName(),
                    0, "", "", "", "");
        }
        try {
            return ChroniclesProfileReader.read(player.getData(attachment()));
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (RuntimeException | LinkageError failure) {
            throw new IllegalStateException("gui.realmidentity.profile_unavailable", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static AttachmentType<Object> attachment() {
        AttachmentType<Object> value = attachment;
        if (value != null) return value;
        synchronized (ChroniclesBridge.class) {
            if (attachment != null) return attachment;
            try {
                Class<?> attachments = Class.forName("pt.cronicasrp.ModAttachments", true,
                        ChroniclesBridge.class.getClassLoader());
                Object exported = attachments.getField("PROFILE").get(null);
                if (!(exported instanceof Supplier<?> supplier)
                        || !(supplier.get() instanceof AttachmentType<?> type)) {
                    throw new IllegalStateException("gui.realmidentity.profile_unavailable");
                }
                attachment = (AttachmentType<Object>) type;
                return attachment;
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("gui.realmidentity.profile_unavailable", failure);
            }
        }
    }
}
