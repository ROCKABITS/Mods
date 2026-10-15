package pt.contracts.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * Optional, server-thread-only adapter for Chronicles RP 0.5.2.
 * Amounts are integer cents. This class never loads another mod's types unless
 * that mod is present, and never falls back to another currency on an error.
 * Offline rewards belong in our own persistent claims, not player NBT edits.
 */
public final class ChroniclesBridge {
    private static final String MOD_ID = "cronicasrp";
    public static final long MAX_CENTS = 900_000_000_000_000_000L;
    private static volatile Api resolved;

    private ChroniclesBridge() {}

    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** False means absent mod or no character; an incompatible API is an error. */
    public static boolean available(ServerPlayer player) {
        if (!loaded()) return false;
        requireServerThread(player);
        Api api = api();
        return created(api, player.getData(api.attachment()));
    }

    public static long balance(ServerPlayer player) {
        requireServerThread(player);
        Api api = api();
        return readBalance(api, profile(api, player));
    }

    public static void debit(ServerPlayer player, long cents) {
        change(player, cents, false);
    }

    public static void credit(ServerPlayer player, long cents) {
        change(player, cents, true);
    }

    private static void change(ServerPlayer player, long cents, boolean credit) {
        requireServerThread(player);
        if (cents <= 0 || cents > MAX_CENTS) {
            throw new IllegalStateException("O valor deve estar entre 0,01 € e o limite do Chronicles RP.");
        }
        // Resolve and validate the complete integration before changing any money.
        Api api = api();
        Object before = profile(api, player);
        long current = readBalance(api, before);
        if (!credit && current < cents) {
            throw new IllegalStateException("Não tens saldo digital suficiente no Chronicles RP.");
        }
        if (credit && cents > MAX_CENTS - current) {
            throw new IllegalStateException("O saldo digital ultrapassaria o limite do Chronicles RP.");
        }
        long next = credit ? current + cents : current - cents;
        Object after = invoke(api.withBalance(), before, next);
        if (!api.profileClass().isInstance(after) || readBalance(api, after) != next) {
            throw new IllegalStateException("O Chronicles RP devolveu um saldo inválido; operação cancelada.");
        }
        player.setData(api.attachment(), after);
        try {
            invoke(api.send(), null, player, api.sync(), "");
        } catch (RuntimeException | LinkageError failure) {
            // Callers must be able to keep the claim or cancel publication on
            // failure without a completed debit/credit being counted twice.
            player.setData(api.attachment(), before);
            try {
                invoke(api.send(), null, player, api.sync(), "");
            } catch (RuntimeException | LinkageError resyncFailure) {
                failure.addSuppressed(resyncFailure);
            }
            throw new IllegalStateException("Não foi possível sincronizar o Chronicles RP; saldo reposto.", failure);
        }
    }

    private static Object profile(Api api, ServerPlayer player) {
        Object profile = player.getData(api.attachment());
        if (!created(api, profile)) {
            throw new IllegalStateException("Cria primeiro a tua personagem no Chronicles RP.");
        }
        return profile;
    }

    private static boolean created(Api api, Object profile) {
        if (!api.profileClass().isInstance(profile)) {
            throw new IllegalStateException("O perfil do Chronicles RP não é compatível.");
        }
        return (boolean) invoke(api.created(), profile);
    }

    private static long readBalance(Api api, Object profile) {
        long cents = (long) invoke(api.balance(), profile);
        if (cents < 0 || cents > MAX_CENTS) {
            throw new IllegalStateException("O saldo do Chronicles RP está fora do limite permitido.");
        }
        return cents;
    }

    private static void requireServerThread(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) {
            throw new IllegalStateException("A economia só pode ser usada na thread principal do servidor.");
        }
    }

    private static Api api() {
        if (!loaded()) throw new IllegalStateException("O Chronicles RP não está instalado.");
        Api value = resolved;
        if (value != null) return value;
        synchronized (ChroniclesBridge.class) {
            if (resolved == null) resolved = resolve();
            return resolved;
        }
    }

    @SuppressWarnings("unchecked")
    private static Api resolve() {
        try {
            ClassLoader loader = ChroniclesBridge.class.getClassLoader();
            Class<?> profile = Class.forName("pt.cronicasrp.domain.CharacterProfile", true, loader);
            Class<?> attachments = Class.forName("pt.cronicasrp.ModAttachments", true, loader);
            Class<?> money = Class.forName("pt.cronicasrp.domain.Money", true, loader);
            Class<?> network = Class.forName("pt.cronicasrp.network.ProfileNetwork", true, loader);
            Class<?> packets = Class.forName("pt.cronicasrp.network.ProfilePackets", true, loader);
            Object supplier = attachments.getField("PROFILE").get(null);
            if (!(supplier instanceof Supplier<?> factory)
                    || !(factory.get() instanceof AttachmentType<?> attachment)
                    || money.getField("MAX_CENTS").getLong(null) != MAX_CENTS) {
                throw new IllegalStateException("A economia do Chronicles RP não é compatível com esta versão.");
            }
            Method created = profile.getMethod("created");
            Method balance = profile.getMethod("balance");
            Method withBalance = profile.getMethod("withBalance", long.class);
            Method send = network.getMethod("send", ServerPlayer.class, int.class, String.class);
            if (created.getReturnType() != boolean.class || balance.getReturnType() != long.class
                    || withBalance.getReturnType() != profile || send.getReturnType() != void.class) {
                throw new IllegalStateException("A API do Chronicles RP não é compatível com esta versão.");
            }
            int sync = packets.getField("SYNC").getInt(null);
            return new Api((AttachmentType<Object>) attachment, profile, created, balance, withBalance, send, sync);
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("Não foi possível preparar a integração com o Chronicles RP 0.5.2.", ex);
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IllegalStateException("Falhou a operação no Chronicles RP: " + method.getName(), cause);
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("A integração com o Chronicles RP está indisponível.", ex);
        }
    }

    private record Api(AttachmentType<Object> attachment, Class<?> profileClass, Method created,
                       Method balance, Method withBalance, Method send, int sync) {}
}
