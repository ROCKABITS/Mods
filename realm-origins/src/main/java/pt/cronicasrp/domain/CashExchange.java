package pt.cronicasrp.domain;

import java.util.ArrayList;
import java.util.List;

/** Immutable transaction planning: either the entire exchange fits or nothing is committed. */
public final class CashExchange {
    public static final int STACK_LIMIT = 64;
    public record Slot(CurrencyDenomination money, int count, boolean mergeable, boolean acceptsMoney) {
        public Slot { if (count < 0) throw new IllegalArgumentException("Pilha inválida."); }
        public static Slot empty() { return new Slot(null, 0, false, true); }
        public static Slot blocked() { return new Slot(null, 1, false, true); }
        public static Slot money(CurrencyDenomination denomination, int count) { return new Slot(denomination, count, true, true); }
        public boolean emptySlot() { return count == 0; }
    }
    public record Transaction(CharacterProfile profile, List<Slot> slots, long amount) {
        public Transaction { slots = List.copyOf(slots); }
    }
    private CashExchange() {}
    public static long total(List<Slot> slots) {
        long total = 0;
        for (Slot slot : slots) if (slot.money() != null) total = Math.addExact(total, (long) slot.money().cents() * slot.count());
        return total;
    }
    public static long count(List<Slot> slots, CurrencyDenomination denomination) {
        return slots.stream().filter(s -> s.money() == denomination).mapToLong(Slot::count).sum();
    }
    private static void validate(CharacterProfile profile, long amount) {
        if (!profile.created()) throw new IllegalArgumentException("Cria primeiro a tua personagem com /rp ficha.");
        Money.checked(amount);
        if (amount == 0) throw new IllegalArgumentException("O valor deve ser pelo menos 0,01 €.");
    }
    public static long piecesValue(CurrencyDenomination denomination, int count) {
        if (denomination == null || count <= 0 || count > 1_000_000) throw new IllegalArgumentException("Escolhe entre 1 e 1.000.000 de peças.");
        return (long) denomination.cents() * count;
    }
    public static Transaction withdraw(CharacterProfile profile, List<Slot> inventory, long amount) {
        validate(profile, amount);
        if (profile.balance() < amount) throw new IllegalArgumentException("Não tens saldo digital suficiente.");
        var slots = new ArrayList<>(inventory);
        addChange(slots, amount);
        return new Transaction(profile.withBalance(profile.balance() - amount), slots, amount);
    }
    public static Transaction withdrawPieces(CharacterProfile profile, List<Slot> inventory, CurrencyDenomination denomination, int count) {
        long amount = piecesValue(denomination, count); validate(profile, amount);
        if (profile.balance() < amount) throw new IllegalArgumentException("Não tens saldo digital suficiente.");
        var slots = new ArrayList<>(inventory); add(slots, denomination, count);
        return new Transaction(profile.withBalance(profile.balance() - amount), slots, amount);
    }
    public static Transaction deposit(CharacterProfile profile, List<Slot> inventory, long amount) {
        validate(profile, amount);
        if (total(inventory) < amount) throw new IllegalArgumentException("Não tens dinheiro físico suficiente no inventário.");
        var updated = credited(profile, amount);
        var slots = new ArrayList<>(inventory); long remaining = amount;
        // Use exact smaller pieces first. Only break a larger note/coin when needed.
        for (var denomination : CurrencyDenomination.DESCENDING) {
            long used = Math.min(count(slots, denomination), remaining / denomination.cents());
            remove(slots, denomination, used); remaining -= used * denomination.cents();
        }
        if (remaining > 0) {
            final long needed = remaining;
            var larger = CurrencyDenomination.ASCENDING.stream().filter(d -> d.cents() >= needed && count(slots, d) > 0).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Não tens dinheiro físico suficiente."));
            remove(slots, larger, 1); addChange(slots, larger.cents() - remaining);
        }
        return new Transaction(updated, slots, amount);
    }
    public static Transaction depositAll(CharacterProfile profile, List<Slot> inventory) {
        long amount = total(inventory); validate(profile, amount);
        var slots = new ArrayList<>(inventory);
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).money() != null) slots.set(i, cleared(slots.get(i)));
        return new Transaction(credited(profile, amount), slots, amount);
    }
    public static Transaction depositPieces(CharacterProfile profile, List<Slot> inventory, CurrencyDenomination denomination, int count) {
        long amount = piecesValue(denomination, count); validate(profile, amount);
        if (count(inventory, denomination) < count) throw new IllegalArgumentException("Não tens essa quantidade de moedas ou notas.");
        var slots = new ArrayList<>(inventory); remove(slots, denomination, count);
        return new Transaction(credited(profile, amount), slots, amount);
    }
    public static Transaction depositSlot(CharacterProfile profile, List<Slot> inventory, int index, int count) {
        if (index < 0 || index >= inventory.size()) throw new IllegalArgumentException("Mão inválida.");
        Slot slot = inventory.get(index);
        long amount = piecesValue(slot.money(), count); validate(profile, amount);
        if (slot.count() < count) throw new IllegalArgumentException("Essa pilha já não tem a quantidade pedida.");
        var slots = new ArrayList<>(inventory); slots.set(index, reduced(slot, count));
        return new Transaction(credited(profile, amount), slots, amount);
    }
    private static CharacterProfile credited(CharacterProfile profile, long amount) {
        try { return profile.withBalance(Math.addExact(profile.balance(), amount)); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Esse depósito ultrapassa o limite de saldo."); }
    }
    private static Slot cleared(Slot slot) { return new Slot(null, 0, false, slot.acceptsMoney()); }
    private static Slot reduced(Slot slot, int used) {
        return slot.count() == used ? cleared(slot) : new Slot(slot.money(), slot.count() - used, slot.mergeable(), slot.acceptsMoney());
    }
    private static void remove(List<Slot> slots, CurrencyDenomination denomination, long count) {
        for (int i = 0; i < slots.size() && count > 0; i++) {
            Slot slot = slots.get(i);
            if (slot.money() != denomination) continue;
            int used = (int) Math.min(count, slot.count()); slots.set(i, reduced(slot, used)); count -= used;
        }
        if (count != 0) throw new IllegalArgumentException("Não tens essa quantidade de moedas ou notas.");
    }
    private static void addChange(List<Slot> slots, long cents) {
        for (var denomination : CurrencyDenomination.DESCENDING) {
            long count = cents / denomination.cents(); cents %= denomination.cents();
            if (count > 0) add(slots, denomination, count);
        }
    }
    private static void add(List<Slot> slots, CurrencyDenomination denomination, long count) {
        for (int i = 0; i < slots.size() && count > 0; i++) {
            Slot slot = slots.get(i);
            if (!slot.acceptsMoney() || slot.money() != denomination || !slot.mergeable()) continue;
            int added = (int) Math.min(count, Math.max(0, STACK_LIMIT - slot.count()));
            slots.set(i, Slot.money(denomination, slot.count() + added)); count -= added;
        }
        for (int i = 0; i < slots.size() && count > 0; i++) {
            Slot slot = slots.get(i);
            if (!slot.acceptsMoney() || !slot.emptySlot()) continue;
            int added = (int) Math.min(count, STACK_LIMIT); slots.set(i, Slot.money(denomination, added)); count -= added;
        }
        if (count != 0) throw new IllegalArgumentException("Não há espaço no inventário para o dinheiro ou troco. Liberta algumas posições.");
    }
}
