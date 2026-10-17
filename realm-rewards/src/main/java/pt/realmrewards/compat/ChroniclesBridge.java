package pt.realmrewards.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Optional online-only Chronicles 0.5.2 wallet. Offline earnings stay in our own ledger. */
public final class ChroniclesBridge {
    public static final long MAX_CENTS = 900_000_000_000_000_000L;
    private static volatile Api resolved;
    private ChroniclesBridge() {}

    public static boolean loaded() { return ModList.get().isLoaded("cronicasrp"); }

    public static boolean available(ServerPlayer player) {
        if (!loaded()) return false;
        requireThread(player);
        Api api = api();
        Object profile = player.getData(api.attachment());
        if (!api.profileClass().isInstance(profile)) throw unavailable();
        if (!(boolean) invoke(api.created(), profile)) return false;
        readBalance(api, profile);
        return true;
    }

    public static long balance(ServerPlayer player) {
        requireThread(player);
        Api api = api();
        return readBalance(api, profile(api, player));
    }

    public static void debit(ServerPlayer player, long amount) { change(player, amount, false); }
    public static void credit(ServerPlayer player, long amount) { change(player, amount, true); }

    private static void change(ServerPlayer player, long amount, boolean credit) {
        requireThread(player);
        if (amount <= 0 || amount > MAX_CENTS) throw unavailable();
        Api api = api();
        Object before = profile(api, player);
        long current = readBalance(api, before);
        if (!credit && amount > current) throw new IllegalStateException("error.realmrewards.insufficient_funds");
        if (credit && amount > MAX_CENTS - current) throw new IllegalStateException("error.realmrewards.wallet_full");
        long next = credit ? current + amount : current - amount;
        Object after = invoke(api.withBalance(), before, next);
        if (!api.profileClass().isInstance(after) || !(boolean) invoke(api.created(), after)
                || readBalance(api, after) != next) throw unavailable();
        // A compatible-looking integration must never replace occupation, signature,
        // reputation, biography, or any other record field while changing the balance.
        for (Method field : api.stableFields()) {
            if (!Objects.equals(invoke(field, before), invoke(field, after))) throw unavailable();
        }
        player.setData(api.attachment(), after);
        try { invoke(api.send(), null, player, api.sync(), ""); }
        catch (RuntimeException | LinkageError failure) {
            player.setData(api.attachment(), before);
            try { invoke(api.send(), null, player, api.sync(), ""); }
            catch (RuntimeException | LinkageError restore) { failure.addSuppressed(restore); }
            throw new IllegalStateException("error.realmrewards.chronicles_unavailable", failure);
        }
    }

    private static Object profile(Api api, ServerPlayer player) {
        Object profile = player.getData(api.attachment());
        if (!api.profileClass().isInstance(profile) || !(boolean) invoke(api.created(), profile)) throw unavailable();
        return profile;
    }

    private static long readBalance(Api api, Object profile) {
        long value = (long) invoke(api.balance(), profile);
        if (value < 0 || value > MAX_CENTS) throw unavailable();
        return value;
    }

    private static void requireThread(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) {
            throw new IllegalStateException("Reward wallets require the server thread.");
        }
    }

    private static Api api() {
        if (!loaded()) throw unavailable();
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
            if (!(supplier instanceof Supplier<?> factory) || !(factory.get() instanceof AttachmentType<?> attachment)
                    || money.getField("MAX_CENTS").getLong(null) != MAX_CENTS || !profile.isRecord()) throw unavailable();
            Method created = profile.getMethod("created");
            Method balance = profile.getMethod("balance");
            Method withBalance = profile.getMethod("withBalance", long.class);
            Method send = network.getMethod("send", ServerPlayer.class, int.class, String.class);
            if (created.getReturnType() != boolean.class || balance.getReturnType() != long.class
                    || withBalance.getReturnType() != profile || send.getReturnType() != void.class) throw unavailable();
            List<Method> stable = new ArrayList<>();
            for (var field : profile.getRecordComponents()) {
                if (!field.getName().equals("balance")) stable.add(field.getAccessor());
            }
            return new Api((AttachmentType<Object>) attachment, profile, created, balance, withBalance,
                    send, packets.getField("SYNC").getInt(null), List.copyOf(stable));
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("error.realmrewards.chronicles_unavailable", failure);
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException failure) {
            throw new IllegalStateException("error.realmrewards.chronicles_unavailable", failure.getCause());
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("error.realmrewards.chronicles_unavailable", failure);
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("error.realmrewards.chronicles_unavailable");
    }

    private record Api(AttachmentType<Object> attachment, Class<?> profileClass, Method created,
                       Method balance, Method withBalance, Method send, int sync, List<Method> stableFields) {}
}
