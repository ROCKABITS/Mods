package pt.crownledger.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private static volatile ProgressApi resolvedProgress;
    private static final int MAX_REPUTATION = 100;
    private static final int MAX_MISSIONS = 1_000_000;

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

    public static String publicName(ServerPlayer player){
        if(!available(player))return player.getGameProfile().getName();
        try{Object value=api().profileClass().getMethod("name").invoke(profile(api(),player));return value instanceof String name&&!name.isBlank()?name:player.getGameProfile().getName();}
        catch(ReflectiveOperationException error){return player.getGameProfile().getName();}
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

    /** Current RP reputation; a created character and the supported progress API are required. */
    public static int reputation(ServerPlayer player) {
        requireServerThread(player);
        Api api = api();
        Object profile = profile(api, player);
        ProgressApi progressApi = progressApi(api);
        Object progress = checkedProgress(progressApi, invoke(progressApi.progress(), profile));
        return (int) invoke(progressApi.reputation(), progress);
    }

    /**
     * Adds a retained contract award without replacing any other character data.
     * Totals saturate at the Chronicles limits. A zero award performs no write or sync.
     */
    public static void applyProgress(ServerPlayer player, int reputationPoints, int missions) {
        requireServerThread(player);
        if (reputationPoints < 0 || missions < 0) {
            throw new IllegalStateException("A reputação e as missões atribuídas não podem ser negativas.");
        }
        Api api = api();
        Object before = profile(api, player);
        ProgressApi progressApi = progressApi(api);
        Object beforeProgress = checkedProgress(progressApi, invoke(progressApi.progress(), before));
        // Also reject corrupt money before constructing a new profile: progress must
        // never make an otherwise invalid economy state appear successfully applied.
        readBalance(api, before);
        int currentReputation = (int) invoke(progressApi.reputation(), beforeProgress);
        int currentMissions = (int) invoke(progressApi.missions(), beforeProgress);
        int nextReputation = (int) Math.min(MAX_REPUTATION, (long) currentReputation + reputationPoints);
        int missionDelta = Math.min(MAX_MISSIONS - currentMissions, missions);
        if (nextReputation == currentReputation && missionDelta == 0) return;

        Object afterProgress = beforeProgress;
        if (nextReputation != currentReputation) {
            afterProgress = invoke(progressApi.withReputation(), afterProgress, nextReputation);
        }
        // RpProgress.completeMissions requires a strictly positive amount.
        if (missionDelta > 0) {
            afterProgress = invoke(progressApi.completeMissions(), afterProgress, missionDelta);
        }
        checkedProgress(progressApi, afterProgress);
        if ((int) invoke(progressApi.reputation(), afterProgress) != nextReputation
                || (int) invoke(progressApi.missions(), afterProgress) != currentMissions + missionDelta) {
            throw new IllegalStateException("O Chronicles RP devolveu progresso inválido; operação cancelada.");
        }
        unchanged(progressApi.progressFields(), beforeProgress, afterProgress);
        Object after = invoke(progressApi.withProgress(), before, afterProgress);
        if (!api.profileClass().isInstance(after) || !created(api, after)
                || !Objects.equals(invoke(progressApi.progress(), after), afterProgress)) {
            throw new IllegalStateException("O Chronicles RP devolveu um perfil inválido; operação cancelada.");
        }
        unchanged(progressApi.profileFields(), before, after);
        readBalance(api, after);

        // All reflective calls and preservation checks above finish before the
        // attachment is changed. A failed sync must leave the caller's award pending.
        player.setData(api.attachment(), after);
        try {
            invoke(api.send(), null, player, api.sync(), "");
        } catch (RuntimeException | LinkageError failure) {
            try {
                player.setData(api.attachment(), before);
                invoke(api.send(), null, player, api.sync(), "");
            } catch (RuntimeException | LinkageError restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw new IllegalStateException("Não foi possível sincronizar o Chronicles RP; progresso reposto.", failure);
        }
    }

    private static Object checkedProgress(ProgressApi api, Object progress) {
        if (!api.progressClass().isInstance(progress)) {
            throw new IllegalStateException("O progresso do Chronicles RP não é compatível.");
        }
        int reputation = (int) invoke(api.reputation(), progress);
        int missions = (int) invoke(api.missions(), progress);
        if (reputation < 0 || reputation > MAX_REPUTATION || missions < 0 || missions > MAX_MISSIONS) {
            throw new IllegalStateException("O progresso do Chronicles RP está fora dos limites permitidos.");
        }
        return progress;
    }

    private static void unchanged(List<Method> fields, Object before, Object after) {
        for (Method field : fields) {
            if (!Objects.equals(invoke(field, before), invoke(field, after))) {
                throw new IllegalStateException("O Chronicles RP alteraria outros dados da personagem; operação cancelada.");
            }
        }
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

    /** Keep progress compatibility separate so unsupported progress APIs cannot break money operations. */
    private static ProgressApi progressApi(Api api) {
        ProgressApi value = resolvedProgress;
        if (value != null) return value;
        synchronized (ChroniclesBridge.class) {
            if (resolvedProgress == null) resolvedProgress = resolveProgress(api);
            return resolvedProgress;
        }
    }

    private static ProgressApi resolveProgress(Api api) {
        try {
            Class<?> progressClass = Class.forName("pt.cronicasrp.domain.RpProgress", true,
                    ChroniclesBridge.class.getClassLoader());
            Method progress = api.profileClass().getMethod("progress");
            Method withProgress = api.profileClass().getMethod("withProgress", progressClass);
            Method reputation = progressClass.getMethod("reputation");
            Method missions = progressClass.getMethod("missions");
            Method withReputation = progressClass.getMethod("withReputation", int.class);
            Method completeMissions = progressClass.getMethod("completeMissions", int.class);
            if (progress.getReturnType() != progressClass || withProgress.getReturnType() != api.profileClass()
                    || reputation.getReturnType() != int.class || missions.getReturnType() != int.class
                    || withReputation.getReturnType() != progressClass || completeMissions.getReturnType() != progressClass
                    || progressClass.getField("MAX_COUNTER").getInt(null) != MAX_MISSIONS) {
                throw new IllegalStateException("A API de progresso do Chronicles RP não é compatível com esta versão.");
            }
            return new ProgressApi(progressClass, progress, withProgress, reputation, missions,
                    withReputation, completeMissions, stableRecordFields(api.profileClass(), "progress"),
                    stableRecordFields(progressClass, "reputation", "missions"));
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("Não foi possível preparar o progresso do Chronicles RP 0.5.2.", ex);
        }
    }

    private static List<Method> stableRecordFields(Class<?> type, String... changedFields) {
        if (!type.isRecord()) {
            throw new IllegalStateException("Os dados do Chronicles RP não são compatíveis com esta versão.");
        }
        var fields = new ArrayList<Method>();
        var changed = List.of(changedFields);
        for (var component : type.getRecordComponents()) {
            if (!changed.contains(component.getName())) fields.add(component.getAccessor());
        }
        return List.copyOf(fields);
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

    private record ProgressApi(Class<?> progressClass, Method progress, Method withProgress,
                               Method reputation, Method missions, Method withReputation,
                               Method completeMissions, List<Method> profileFields, List<Method> progressFields) {}
}
