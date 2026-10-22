package pt.cronicasrp.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pt.cronicasrp.domain.CurrencyDenomination.*;

class CashExchangeTest {
    private static CharacterProfile player(long cents) { return new CharacterProfile(true, "Ari", 24, "Elfo", "", "Ari", "Comum", 11_000, cents); }
    private static List<CashExchange.Slot> empty(int size) { return new ArrayList<>(java.util.Collections.nCopies(size, CashExchange.Slot.empty())); }
    private static void conserved(CharacterProfile before, List<CashExchange.Slot> inventory, CashExchange.Transaction after) {
        assertEquals(before.balance() + CashExchange.total(inventory), after.profile().balance() + CashExchange.total(after.slots()));
        assertEquals(before.name(), after.profile().name()); assertEquals(before.initialBalance(), after.profile().initialBalance());
    }
    @Test void everyRequestedDenominationCanBeWithdrawnAndDeposited() {
        assertEquals(15, CurrencyDenomination.values().length);
        long sum = CurrencyDenomination.ASCENDING.stream().mapToLong(CurrencyDenomination::cents).sum();
        var before = player(sum); List<CashExchange.Slot> inventory = empty(36); var current = before;
        for (var denomination : CurrencyDenomination.values()) {
            var result = CashExchange.withdrawPieces(current, inventory, denomination, 1); conserved(current, inventory, result);
            current = result.profile(); inventory = result.slots(); assertEquals(1, CashExchange.count(inventory, denomination));
        }
        assertEquals(0, current.balance()); assertEquals(sum, CashExchange.total(inventory));
        var deposited = CashExchange.depositAll(current, inventory); conserved(current, inventory, deposited);
        assertEquals(before, deposited.profile()); assertEquals(0, CashExchange.total(deposited.slots()));
    }
    @Test void valueWithdrawalUsesLargeNotesAndExactCents() {
        var before = player(100_000); var inventory = empty(36);
        var result = CashExchange.withdraw(before, inventory, 58_888); conserved(before, inventory, result);
        assertEquals(41_112, result.profile().balance()); assertEquals(58_888, CashExchange.total(result.slots()));
        assertEquals(1, CashExchange.count(result.slots(), NOTE_500)); assertEquals(1, CashExchange.count(result.slots(), CENT_1));
        assertEquals(1, CashExchange.count(result.slots(), CENT_2)); assertEquals(1, CashExchange.count(result.slots(), CENT_5));
    }
    @Test void partialDepositBreaksANoteAndReturnsExactChange() {
        var inventory = empty(36); inventory.set(0, CashExchange.Slot.money(NOTE_20, 1)); var before = player(300);
        var result = CashExchange.deposit(before, inventory, 1_250); conserved(before, inventory, result);
        assertEquals(1550, result.profile().balance()); assertEquals(750, CashExchange.total(result.slots()));
        assertEquals(1, CashExchange.count(result.slots(), NOTE_5)); assertEquals(1, CashExchange.count(result.slots(), EURO_2));
        assertEquals(1, CashExchange.count(result.slots(), CENT_50));
    }
    @Test void depositsPreferAnExactCombinationBeforeBreakingALargeNote() {
        var inventory = List.of(CashExchange.Slot.money(NOTE_500, 1), CashExchange.Slot.money(NOTE_10, 1), CashExchange.Slot.money(NOTE_5, 1));
        var result = CashExchange.deposit(player(0), inventory, 1500);
        assertEquals(1, CashExchange.count(result.slots(), NOTE_500)); assertEquals(0, CashExchange.count(result.slots(), NOTE_5));
    }
    @Test void fullInventoryCanUseSpaceInExistingIdenticalStacks() {
        var inventory = new ArrayList<>(java.util.Collections.nCopies(36, CashExchange.Slot.blocked()));
        inventory.set(3, CashExchange.Slot.money(EURO_1, 60));
        var result = CashExchange.withdrawPieces(player(400), inventory, EURO_1, 4);
        assertEquals(64, result.slots().get(3).count()); assertEquals(0, result.profile().balance());
    }
    @Test void fullInventoryAndImpossibleChangeCannotLoseOrCreditAnything() {
        var inventory = new ArrayList<>(java.util.Collections.nCopies(36, CashExchange.Slot.blocked()));
        inventory.set(0, CashExchange.Slot.money(NOTE_20, 64)); var copy = List.copyOf(inventory); var before = player(5000);
        assertThrows(IllegalArgumentException.class, () -> CashExchange.withdraw(before, inventory, 1250));
        assertThrows(IllegalArgumentException.class, () -> CashExchange.deposit(before, inventory, 1250));
        assertEquals(copy, inventory); assertEquals(5000, before.balance()); assertEquals(128_000, CashExchange.total(inventory));
    }
    @Test void fullInventoryStillAllowsAnExactDepositAndDepositAll() {
        var inventory = List.of(CashExchange.Slot.money(NOTE_20, 64)); var before = player(0);
        assertEquals(2000, CashExchange.deposit(before, inventory, 2000).profile().balance());
        assertEquals(128_000, CashExchange.depositAll(before, inventory).profile().balance());
    }
    @Test void namedMoneyIsConsumedButNotMergedWithFreshItems() {
        var named = new CashExchange.Slot(NOTE_5, 60, false, true); var inventory = List.of(named);
        assertThrows(IllegalArgumentException.class, () -> CashExchange.withdrawPieces(player(500), inventory, NOTE_5, 1));
        var deposited = CashExchange.depositPieces(player(0), inventory, NOTE_5, 2);
        assertEquals(58, deposited.slots().getFirst().count()); assertFalse(deposited.slots().getFirst().mergeable());
    }
    @Test void offhandMoneyCanBeDepositedAndIsNotUsedForNewStacks() {
        var offhand = new CashExchange.Slot(NOTE_20, 1, true, false);
        assertEquals(2000, CashExchange.depositAll(player(0), List.of(offhand)).profile().balance());
        assertThrows(IllegalArgumentException.class, () -> CashExchange.withdraw(player(100), List.of(new CashExchange.Slot(null, 0, false, false)), 100));
        assertEquals(2000, CashExchange.depositSlot(player(0), List.of(offhand), 0, 1).profile().balance());
    }
    @Test void missingFundsLimitsAndMalformedRequestsNeverMutateOriginals() {
        var inventory = List.of(CashExchange.Slot.money(CENT_1, 1));
        var maximum = player(Money.MAX_CENTS);
        assertThrows(IllegalArgumentException.class, () -> CashExchange.depositAll(maximum, inventory));
        assertThrows(IllegalArgumentException.class, () -> CashExchange.withdraw(player(0), inventory, 1));
        assertThrows(IllegalArgumentException.class, () -> CashExchange.deposit(player(0), inventory, 2));
        assertThrows(IllegalArgumentException.class, () -> CashExchange.withdraw(player(1), inventory, -1));
        assertThrows(IllegalArgumentException.class, () -> CashExchange.withdraw(player(1), inventory, 0));
        assertThrows(IllegalArgumentException.class, () -> CashExchange.depositPieces(player(0), inventory, NOTE_500, 1));
        assertThrows(IllegalArgumentException.class, () -> CashExchange.withdrawPieces(maximum, inventory, NOTE_500, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> CashExchange.depositAll(CharacterProfile.empty(), inventory));
        assertEquals(Money.MAX_CENTS, maximum.balance()); assertEquals(1, CashExchange.total(inventory));
    }
    @Test void enormousWithdrawalsFailWithoutCreatingAnEnormousIntermediateList() {
        var maximum = player(Money.MAX_CENTS); var inventory = empty(36);
        assertThrows(IllegalArgumentException.class, () -> CashExchange.withdraw(maximum, inventory, Money.MAX_CENTS));
        assertEquals(0, CashExchange.total(inventory)); assertEquals(Money.MAX_CENTS, maximum.balance());
    }
    @Test void rightClickDepositsOnlyTheRequestedSlot() {
        var inventory = List.of(CashExchange.Slot.money(NOTE_5, 10), CashExchange.Slot.money(NOTE_5, 3));
        var result = CashExchange.depositSlot(player(0), inventory, 1, 1);
        assertEquals(10, result.slots().get(0).count()); assertEquals(2, result.slots().get(1).count()); assertEquals(500, result.profile().balance());
    }
    @Test void repeatedDepositsCannotCreditAnAlreadyConsumedStack() {
        var inventory = List.of(CashExchange.Slot.money(NOTE_500, 1)); var first = CashExchange.depositAll(player(0), inventory);
        assertThrows(IllegalArgumentException.class, () -> CashExchange.depositAll(first.profile(), first.slots()));
        assertEquals(50_000, first.profile().balance()); assertEquals(0, CashExchange.total(first.slots()));
    }
    @Test void randomizedRoundtripsAndPartialDepositsConserveTotalValue() {
        var random = new Random(20261011);
        for (int i = 0; i < 2000; i++) {
            var before = player(5_000_000); var inventory = empty(36); long amount = random.nextLong(1, 1_000_001);
            var withdrawn = CashExchange.withdraw(before, inventory, amount); conserved(before, inventory, withdrawn);
            long partial = random.nextLong(1, amount + 1);
            var deposited = CashExchange.deposit(withdrawn.profile(), withdrawn.slots(), partial); conserved(withdrawn.profile(), withdrawn.slots(), deposited);
            if (CashExchange.total(deposited.slots()) > 0) {
                var last = CashExchange.depositAll(deposited.profile(), deposited.slots()); conserved(deposited.profile(), deposited.slots(), last); assertEquals(before, last.profile());
            } else assertEquals(before, deposited.profile());
            assertEquals(0, CashExchange.total(inventory));
        }
    }
}
