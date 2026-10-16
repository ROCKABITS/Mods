package pt.cronicasrp.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalletTest {
    private static CharacterProfile player(String name, long balance) {
        return new CharacterProfile(true, name, 24, "Humano", "", name, "Comum", 100, balance);
    }

    @Test void paymentConservesMoneyAndPreservesIdentity() {
        var sender = player("Ari", 120); var receiver = player("Lia", 80);
        var transfer = Wallet.transfer(sender, receiver, 45);
        assertEquals(75, transfer.sender().balance()); assertEquals(125, transfer.receiver().balance());
        assertEquals(sender.balance() + receiver.balance(), transfer.sender().balance() + transfer.receiver().balance());
        assertEquals(sender.initialBalance(), transfer.sender().initialBalance());
        assertEquals(receiver.origin(), transfer.receiver().origin());
        assertEquals(120, sender.balance()); assertEquals(80, receiver.balance());
    }

    @Test void failuresNeverChangeEitherBalance() {
        var sender = player("Ari", 10); var receiver = player("Lia", CharacterRules.MAX_BALANCE);
        assertThrows(IllegalArgumentException.class, () -> Wallet.transfer(sender, receiver, 11));
        assertThrows(IllegalArgumentException.class, () -> Wallet.transfer(sender, receiver, 1));
        assertThrows(IllegalArgumentException.class, () -> Wallet.transfer(sender, receiver, 0));
        assertThrows(IllegalArgumentException.class, () -> Wallet.transfer(sender, receiver, -1));
        assertEquals(10, sender.balance()); assertEquals(CharacterRules.MAX_BALANCE, receiver.balance());
    }

    @Test void uncreatedPlayersCannotSendOrReceiveMoney() {
        assertThrows(IllegalArgumentException.class, () -> Wallet.transfer(CharacterProfile.empty(), player("Lia", 10), 1));
        assertThrows(IllegalArgumentException.class, () -> Wallet.transfer(player("Ari", 10), CharacterProfile.empty(), 1));
    }
}
