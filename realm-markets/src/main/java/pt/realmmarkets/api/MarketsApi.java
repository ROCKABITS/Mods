package pt.realmmarkets.api;

import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import pt.realmmarkets.data.MarketData;
import pt.realmmarkets.domain.Currency;
import pt.realmmarkets.domain.MarketLedger;

/** Server-only access to paid sales taxes. No player endpoint can create treasury credit. */
public final class MarketsApi {
    public static final int API_VERSION = 1;
    public static final String TREASURY_MOD_ID = "kingdomtreasury";
    private static volatile TreasuryProvider treasury;

    private MarketsApi() {}

    public interface TreasuryProvider {
        int salesTaxBasisPoints(MinecraftServer server);
    }

    public static synchronized void registerTreasury(TreasuryProvider provider) {
        Objects.requireNonNull(provider, "Treasury provider");
        if (treasury != null && treasury != provider) throw new IllegalStateException("A treasury provider is already registered.");
        treasury = provider;
    }

    public static synchronized void unregisterTreasury(TreasuryProvider provider) {
        if (provider == null || treasury != provider) throw new IllegalStateException("Only the registered treasury provider can unregister itself.");
        treasury = null;
    }

    public static boolean treasuryAvailable() { return treasury != null; }

    /** A broken installed treasury disables purchases instead of silently collecting no tax. */
    public static int salesTaxBasisPoints(MinecraftServer server) {
        requireServerThread(server);
        TreasuryProvider provider = treasury;
        if (provider == null) {
            if (ModList.get().isLoaded(TREASURY_MOD_ID)) throw unavailable(null);
            return 0;
        }
        try {
            int basisPoints = provider.salesTaxBasisPoints(server);
            if (basisPoints < 0 || basisPoints > 10_000) throw new IllegalArgumentException("Invalid treasury sales tax rate.");
            return basisPoints;
        } catch (RuntimeException | LinkageError failure) {
            throw unavailable(failure);
        }
    }

    public static long taxBalance(MinecraftServer server, String currency) {
        requireServerThread(server);
        return MarketData.get(server).ledger.taxBalance(Currency.valueOf(currency));
    }

    /** Caller must preflight its destination before taking this exact, already-funded escrow. */
    public static long treasuryDebit(MinecraftServer server, TreasuryProvider provider, String currencyName, long amount) {
        requireServerThread(server);
        if (provider == null || provider != treasury) throw new IllegalStateException("Only the registered treasury provider can transfer sales tax.");
        if (amount < 1 || amount > MarketLedger.MAX_EARNINGS) throw new IllegalArgumentException("Invalid sales tax transfer amount.");
        Currency currency = Currency.valueOf(currencyName);
        MarketData data = MarketData.get(server);
        MarketLedger next = data.ledger.copy();
        next.debitTax(currency, amount);
        data.replace(next);
        return amount;
    }

    public static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) throw new IllegalStateException("Markets API requires the main server thread.");
    }

    /** Releases stale providers after a server stops; counterpart stop hooks are idempotent. */
    public static synchronized void stop(MinecraftServer server) {
        requireServerThread(server);
        treasury = null;
    }

    private static IllegalStateException unavailable(Throwable cause) {
        return new IllegalStateException("gui.realmmarkets.treasury_unavailable", cause);
    }
}
