package pt.cronicasrp.domain;

public final class Wallet {
    public record Transfer(CharacterProfile sender, CharacterProfile receiver) {}
    private Wallet() {}
    public static Transfer transfer(CharacterProfile sender, CharacterProfile receiver, long amount) {
        if (!sender.created() || !receiver.created()) throw new IllegalArgumentException("As duas personagens precisam de estar criadas.");
        if (amount <= 0) throw new IllegalArgumentException("O valor deve ser positivo.");
        if (sender.balance() < amount) throw new IllegalArgumentException("Não tens saldo digital suficiente.");
        final long received;
        try { received = Math.addExact(receiver.balance(), amount); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("O destinatário atingiu o limite de saldo."); }
        CharacterProfile updatedReceiver = receiver.withBalance(received);
        CharacterProfile updatedSender = sender.withBalance(sender.balance() - amount);
        return new Transfer(updatedSender, updatedReceiver);
    }
}
