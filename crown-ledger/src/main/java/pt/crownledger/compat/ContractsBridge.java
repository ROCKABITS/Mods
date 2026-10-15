package pt.crownledger.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import pt.crownledger.data.TreasuryData;
import pt.crownledger.domain.Policy;
import pt.crownledger.domain.Treasury.Currency;
import pt.crownledger.service.CrownService;

/** Optional reflection boundary: Crown Ledger has no hard dependency on Contracts classes. */
public final class ContractsBridge {
    private static Api api;
    private static Object provider;
    private ContractsBridge() {}

    public static boolean loaded() { return ModList.get().isLoaded("contracts"); }
    public static boolean available() { return api != null && provider != null; }

    public static void start(MinecraftServer server) {
        if (!loaded()) return;
        if (!server.isSameThread()) throw new IllegalStateException("gui.kingdomtreasury.invalid_request");
        if (available()) return;
        try {
            ClassLoader loader = ContractsBridge.class.getClassLoader();
            Class<?> type = Class.forName("pt.contracts.api.ContractsApi", true, loader);
            Class<?> providerType = Class.forName("pt.contracts.api.ContractsApi$TreasuryProvider", true, loader);
            Class<?> policyType = Class.forName("pt.contracts.api.ContractsApi$Policy", true, loader);
            if (type.getField("API_VERSION").getInt(null) < 2)
                throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable");
            Constructor<?> policyConstructor = policyType.getConstructor(int.class, int.class, int.class, int.class);
            Object instance = Proxy.newProxyInstance(loader, new Class<?>[] {providerType}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "policy" -> {
                        MinecraftServer current = (MinecraftServer) args[0];
                        Policy policy = TreasuryData.get(current).treasury.policy();
                        yield policyConstructor.newInstance(policy.minimumReputation(), policy.contractTaxBasisPoints(),
                                policy.reputationPerPublicContract(), policy.dailyReputationLimit());
                    }
                    case "mayManage" -> CrownService.mayManage((ServerPlayer) args[0]);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Crown Ledger TreasuryProvider";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
            Api resolved = new Api(type, providerType);
            invoke(type.getMethod("registerTreasury", providerType), null, instance);
            api = resolved;
            provider = instance;
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable", ex);
        }
    }

    public static void stop() {
        if (!available()) return;
        try { call("unregisterTreasury", new Class<?>[] {api.providerType()}, provider); }
        finally { provider = null; api = null; }
    }

    public static long balance(MinecraftServer server, Currency currency) {
        return (long) call("kingdomBalance", new Class<?>[] {MinecraftServer.class, String.class}, server, currency.name());
    }

    public static void credit(MinecraftServer server, Currency currency, long amount) {
        Api current = requireApi();
        call("treasuryCredit", new Class<?>[] {MinecraftServer.class, current.providerType(), String.class, long.class},
                server, provider, currency.name(), amount);
    }

    public static long debit(MinecraftServer server, Currency currency, long amount) {
        Api current = requireApi();
        return (long) call("treasuryDebit", new Class<?>[] {MinecraftServer.class, current.providerType(), String.class, long.class},
                server, provider, currency.name(), amount);
    }

    public static UUID publish(ServerPlayer manager, String title, String item, int quantity, long reward) {
        Object contract = call("publishPublic", new Class<?>[] {ServerPlayer.class, String.class, String.class, int.class, long.class},
                manager, title, item, quantity, reward);
        return (UUID) accessor(contract, "id");
    }

    public static void cancel(ServerPlayer manager, UUID id) {
        call("cancelPublic", new Class<?>[] {ServerPlayer.class, UUID.class}, manager, id);
    }

    public static int collect(ServerPlayer manager) {
        return (int) call("collectKingdom", new Class<?>[] {ServerPlayer.class}, manager);
    }

    public static List<Map<String, Object>> contracts(MinecraftServer server) {
        List<?> contracts = (List<?>) call("kingdomContracts", new Class<?>[] {MinecraftServer.class}, server);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object contract : contracts) {
            // Keep every cancellable public escrow visible before applying the snapshot limit.
            if (!"OPEN".equals(accessor(contract, "status").toString())) continue;
            Map<String, Object> values = new LinkedHashMap<>();
            for (String name : List.of("id", "title", "item", "quantity", "reward", "currency", "tax", "netReward", "status"))
                values.put(name, accessor(contract, name));
            result.add(values);
        }
        java.util.Collections.reverse(result);
        return result.stream().limit(16).toList();
    }

    private static Object accessor(Object value, String name) {
        try { return invoke(value.getClass().getMethod(name), value); }
        catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable", ex);
        }
    }

    private static Api requireApi() {
        if (!available()) throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable");
        return api;
    }

    private static Object call(String name, Class<?>[] types, Object... arguments) {
        Api current = requireApi();
        try { return invoke(current.type().getMethod(name, types), null, arguments); }
        catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable", ex);
        }
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable", cause);
        } catch (ReflectiveOperationException | LinkageError ex) {
            throw new IllegalStateException("gui.kingdomtreasury.contracts_unavailable", ex);
        }
    }

    private record Api(Class<?> type, Class<?> providerType) {}
}
