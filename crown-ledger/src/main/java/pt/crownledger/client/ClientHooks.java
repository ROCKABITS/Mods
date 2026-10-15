package pt.crownledger.client;

/** Client entry point; only server-authorized physical chest sessions open a menu. */
public final class ClientHooks {
    private ClientHooks() {}
    public static void open(String json) { TreasuryScreen.receive(json); }
}
