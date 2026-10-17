package pt.realmtitles.compat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;

/** The adapter resolves only created() and name(); no private character or profile-file access. */
public final class ChroniclesBridge {
    public record PublicProfile(String name, boolean eligible) {}
    private static volatile AttachmentType<Object> attachment;
    private ChroniclesBridge() {}
    public static boolean loaded() { return ModList.get().isLoaded("cronicasrp"); }
    public static PublicProfile read(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread())
            throw new IllegalStateException("gui.realmtitles.invalid_request");
        String fallback = player.getGameProfile().getName();
        if (!loaded()) return new PublicProfile(fallback, true);
        try {
            Object profile = player.getData(attachment());
            if (profile == null || !profile.getClass().getName().equals("pt.cronicasrp.domain.CharacterProfile")
                    || !profile.getClass().isRecord()) throw new IllegalStateException();
            Method created = getter(profile.getClass(), "created", boolean.class);
            Method name = getter(profile.getClass(), "name", String.class);
            if (!(boolean) created.invoke(profile)) return new PublicProfile(fallback, false);
            String publicName = (String) name.invoke(profile);
            if (publicName == null || publicName.isBlank() || publicName.length() > 64
                    || publicName.codePoints().anyMatch(Character::isISOControl)) throw new IllegalStateException();
            new pt.realmtitles.domain.TitleLedger.Citizen(player.getUUID(), publicName, true);
            return new PublicProfile(publicName, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // A broken optional adapter cannot make an unverified character eligible.
            return new PublicProfile(fallback, false);
        }
    }
    private static Method getter(Class<?> owner, String name, Class<?> result) throws NoSuchMethodException {
        Method method = owner.getMethod(name);
        if (method.getReturnType() != result || Modifier.isStatic(method.getModifiers())
                || method.getDeclaringClass() != owner || Arrays.stream(owner.getRecordComponents()).noneMatch(
                        entry -> entry.getName().equals(name) && entry.getType() == result)) throw new NoSuchMethodException(name);
        return method;
    }
    @SuppressWarnings("unchecked") private static AttachmentType<Object> attachment() throws ReflectiveOperationException {
        if (attachment != null) return attachment;
        synchronized (ChroniclesBridge.class) {
            if (attachment == null) {
                Object exported = Class.forName("pt.cronicasrp.ModAttachments").getField("PROFILE").get(null);
                if (!(exported instanceof Supplier<?> supplier) || !(supplier.get() instanceof AttachmentType<?> type))
                    throw new IllegalStateException();
                attachment = (AttachmentType<Object>) type;
            }
            return attachment;
        }
    }
}
