package pt.crownledger.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Treasury.Currency;

/** Optional server-bound capability: only paid Markets sales can fund this escrow. */
public final class MarketsBridge {
    private static Api api;
    private static Object provider;
    private static MinecraftServer boundServer;

    private MarketsBridge() {}

    public static boolean loaded() { return ModList.get().isLoaded("realmmarkets"); }
    public static boolean available() { return api != null && provider != null && boundServer != null; }

    public static void start(MinecraftServer server) {
        requireThread(server);
        if (!loaded()) return;
        if (available()) {
            if (boundServer != server) throw unavailable();
            return;
        }
        try {
            ClassLoader loader = MarketsBridge.class.getClassLoader();
            Class<?> type = Class.forName("pt.realmmarkets.api.MarketsApi", true, loader);
            Class<?> providerType = Class.forName("pt.realmmarkets.api.MarketsApi$TreasuryProvider", true, loader);
            if (type.getField("API_VERSION").getInt(null) != 1
                    || providerType.getMethod("salesTaxBasisPoints", MinecraftServer.class).getReturnType() != int.class)
                throw unavailable();
            Api resolved = new Api(type, providerType,
                    type.getMethod("unregisterTreasury", providerType),
                    type.getMethod("treasuryAvailable"),
                    type.getMethod("taxBalance", MinecraftServer.class, String.class),
                    type.getMethod("treasuryDebit", MinecraftServer.class, providerType, String.class, long.class));
            Method register = type.getMethod("registerTreasury", providerType);
            Object instance = Proxy.newProxyInstance(loader, new Class<?>[] {providerType}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "salesTaxBasisPoints" -> {
                        MinecraftServer requested = (MinecraftServer) args[0];
                        requireThread(requested);
                        if (requested != server || requested != boundServer || proxy != provider) throw unavailable();
                        yield TreasuryData.get(requested).treasury.policy().marketTaxBasisPoints();
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Crown Ledger Markets TreasuryProvider";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            // Publish our local capability before registration so synchronous provider validation is safe.
            api = resolved;
            provider = instance;
            boundServer = server;
            try { invoke(register, instance); }
            catch (RuntimeException | LinkageError ex) {
                api = null;
                provider = null;
                boundServer = null;
                throw ex;
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("gui.kingdomtreasury.markets_unavailable", ex);
        }
    }

    public static void stop() {
        if (boundServer != null) requireThread(boundServer);
        Api current = api;
        Object capability = provider;
        try {
            // Markets may already have cleared its capability in its own server-stop listener.
            if (current != null && capability != null && (boolean) invoke(current.available()))
                invoke(current.unregister(), capability);
        } finally {
            provider = null;
            api = null;
            boundServer = null;
        }
    }

    public static long balance(MinecraftServer server, Currency currency) {
        Api current = requireApi(server);
        if (currency == null) throw unavailable();
        long amount = (long) invoke(current.balance(), server, currency.name());
        if (amount < 0 || amount > ChroniclesBridge.MAX_CENTS) throw unavailable();
        return amount;
    }

    public static long debit(MinecraftServer server, Currency currency, long amount) {
        Api current = requireApi(server);
        if (currency == null || amount < 1 || amount > ChroniclesBridge.MAX_CENTS) throw unavailable();
        return (long) invoke(current.debit(), server, provider, currency.name(), amount);
    }

    private static Api requireApi(MinecraftServer server) {
        requireThread(server);
        if (!available() || boundServer != server) throw unavailable();
        return api;
    }

    private static void requireThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) throw unavailable();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("gui.kingdomtreasury.markets_unavailable");
    }

    private static Object invoke(Method method, Object... arguments) {
        try { return method.invoke(null, arguments); }
        catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("gui.kingdomtreasury.markets_unavailable", cause);
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("gui.kingdomtreasury.markets_unavailable", ex);
        }
    }

    private record Api(Class<?> type, Class<?> providerType, Method unregister, Method available, Method balance, Method debit) {}
}
