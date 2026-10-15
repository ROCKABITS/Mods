package pt.contracts.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import pt.contracts.ContractsData;
import pt.contracts.ContractsService;
import pt.contracts.domain.Ledger;
import pt.contracts.domain.Ledger.Contract;
import pt.contracts.domain.Ledger.Currency;
import pt.contracts.domain.Ledger.Delivery;

/**
 * Server-only integration contract for the future Kingdom Treasury mod.
 * No client packet may register a provider or change policy. A provider must verify
 * the current elected ruler on every management request, rather than cache a UUID.
 * Its physical treasury menu must validate its own block session before calling
 * management operations here. All balances use the existing escrow ledger. The
 * registered provider alone can transfer funded treasury balances into or out of
 * that escrow; player endpoints cannot mint money or withdraw public funds.
 */
public final class ContractsApi {
    public static final int API_VERSION = 2;
    public static final String TREASURY_MOD_ID = "kingdomtreasury";
    private static volatile TreasuryProvider treasury;

    private ContractsApi() {}

    public record Policy(int minimumReputation, int taxBasisPoints,
                         int reputationPerPublicContract, int dailyReputationLimit) {
        public static final Policy DEFAULT = new Policy(0, 0, 1, 3);

        public Policy {
            if (minimumReputation < 0 || minimumReputation > 100
                    || taxBasisPoints < 0 || taxBasisPoints > 10_000
                    || reputationPerPublicContract < 0 || reputationPerPublicContract > 100
                    || dailyReputationLimit < 0 || dailyReputationLimit > 100)
                throw new IllegalArgumentException("Treasury policy values are outside their supported limits");
        }
    }

    public interface TreasuryProvider {
        Policy policy(MinecraftServer server);
        boolean mayManage(ServerPlayer player);
    }

    /** Register once during mod initialization. Conflicting integrations fail closed. */
    public static synchronized void registerTreasury(TreasuryProvider provider) {
        Objects.requireNonNull(provider, "Treasury provider");
        if (treasury != null && treasury != provider)
            throw new IllegalStateException("A treasury provider is already registered");
        treasury = provider;
    }

    /** Only the registering provider may unregister itself; also supports isolated server fixtures. */
    public static synchronized void unregisterTreasury(TreasuryProvider provider) {
        if (provider == null || treasury != provider)
            throw new IllegalStateException("Only the registered treasury provider can unregister itself");
        treasury = null;
    }

    public static boolean treasuryAvailable() { return treasury != null; }

    public static Policy policy(MinecraftServer server) {
        requireServerThread(server);
        TreasuryProvider provider = treasury;
        if (provider == null) {
            if (ModList.get().isLoaded(TREASURY_MOD_ID))
                throw new IllegalStateException("gui.contracts.treasury_unavailable");
            return Policy.DEFAULT;
        }
        try {
            Policy policy = provider.policy(server);
            if (policy == null) throw new IllegalStateException("gui.contracts.treasury_unavailable");
            return policy;
        } catch (RuntimeException | LinkageError ex) {
            throw new IllegalStateException("gui.contracts.treasury_unavailable", ex);
        }
    }

    public static void requireManager(ServerPlayer player) {
        if (player == null) throw new IllegalStateException("gui.contracts.treasury_forbidden");
        requireServerThread(player.getServer());
        TreasuryProvider provider = treasury;
        if (player.isSpectator() || provider == null)
            throw new IllegalStateException("gui.contracts.treasury_forbidden");
        try {
            if (!provider.mayManage(player)) throw new IllegalStateException("gui.contracts.treasury_forbidden");
        } catch (RuntimeException | LinkageError ex) {
            throw new IllegalStateException("gui.contracts.treasury_forbidden", ex);
        }
    }

    public static Contract publishPublic(ServerPlayer manager, String title, String item,
                                         int quantity, long reward) {
        return ContractsService.publishPublic(manager, title, item, quantity, reward);
    }

    /** Donation debits the donor's actual wallet/inventory; it never creates credit from a supplied balance. */
    public static void depositKingdom(ServerPlayer donor, long amount, String currency) {
        ContractsService.depositKingdom(donor, amount, currency);
    }

    public static long kingdomBalance(MinecraftServer server, String currency) {
        return ContractsService.kingdomBalance(server, currency);
    }

    public static List<Delivery> kingdomDeliveries(MinecraftServer server) {
        return ContractsService.kingdomDeliveries(server);
    }

    /** Public treasury accounting view, never a player or client-supplied ledger. */
    public static List<Contract> kingdomContracts(MinecraftServer server) {
        requireServerThread(server);
        return ContractsData.get(server).ledger.list().stream()
                .filter(contract -> contract.funding() == Ledger.Funding.KINGDOM).toList();
    }

    public static void cancelPublic(ServerPlayer manager, UUID contract) {
        ContractsService.cancelPublic(manager, contract);
    }

    public static int collectKingdom(ServerPlayer manager) {
        return ContractsService.collectKingdom(manager);
    }

    /**
     * Moves already-funded Crown Ledger money into contract escrow. Only the exact
     * registered provider may invoke this transfer; it must debit its own persisted
     * treasury in the same server-thread transaction. This is not a player endpoint.
     */
    public static void treasuryCredit(MinecraftServer server, TreasuryProvider provider,
                                      String currencyName, long amount) {
        requireFundingProvider(server, provider, amount);
        Currency currency = Currency.valueOf(currencyName);
        ContractsData data = ContractsData.get(server);
        Ledger next = data.ledger.copy();
        next.addCredit(Ledger.KINGDOM_ACCOUNT, currency, amount);
        data.ledger = next;
        data.setDirty();
    }

    /**
     * Moves an exact tax/refund balance back to the registered treasury. The caller
     * must preflight destination overflow before taking this credit. On failure the
     * contracts ledger is unchanged; there is no partial transfer or currency conversion.
     */
    public static long treasuryDebit(MinecraftServer server, TreasuryProvider provider,
                                     String currencyName, long amount) {
        requireFundingProvider(server, provider, amount);
        Currency currency = Currency.valueOf(currencyName);
        ContractsData data = ContractsData.get(server);
        Ledger next = data.ledger.copy();
        next.takeCredit(Ledger.KINGDOM_ACCOUNT, currency, amount);
        data.ledger = next;
        data.setDirty();
        return amount;
    }

    private static void requireFundingProvider(MinecraftServer server, TreasuryProvider provider, long amount) {
        requireServerThread(server);
        if (provider == null || treasury != provider)
            throw new IllegalStateException("Only the registered treasury provider can transfer public funds");
        if (amount < 1 || amount > pt.contracts.compat.ChroniclesBridge.MAX_CENTS)
            throw new IllegalArgumentException("Treasury transfer amount is outside the supported limits");
    }

    public static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread())
            throw new IllegalStateException("Contracts API requires the main server thread");
    }
}
