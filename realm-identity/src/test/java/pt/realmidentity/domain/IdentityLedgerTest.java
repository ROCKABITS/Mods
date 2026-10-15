package pt.realmidentity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityLedgerTest {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID SEAL = new UUID(0, 3);
    private static final ProfileSnapshot PROFILE = new ProfileSnapshot(ProfileSnapshot.Source.MINECRAFT,
            "Alice", 0, "", "", "", "");

    @Test void replacingAnOwnerInvalidatesEveryCopyOfTheOldId() {
        IdentityLedger ledger = new IdentityLedger();
        Document old = ledger.issue(ALICE, PROFILE, 100);
        Document secondOwner = ledger.issue(BOB, PROFILE, 101);
        Document renewed = ledger.issue(ALICE, PROFILE, 102);

        assertNotEquals(old.id(), renewed.id());
        assertTrue(ledger.lookup(old.id()).isEmpty());
        assertEquals(renewed, ledger.lookup(renewed.id()).orElseThrow());
        assertEquals(renewed, ledger.current(ALICE).orElseThrow());
        assertEquals(secondOwner, ledger.lookup(secondOwner.id()).orElseThrow());
        assertEquals(3, renewed.serial());
        assertEquals(4, ledger.nextSerial());
        assertEquals(2, ledger.documents().size());
    }

    @Test void unknownAndOtherWorldIdsAreInvalid() {
        IdentityLedger firstWorld = new IdentityLedger();
        IdentityLedger secondWorld = new IdentityLedger();
        Document document = firstWorld.issue(ALICE, PROFILE, 100);

        assertNotEquals(firstWorld.seal(), secondWorld.seal());
        assertTrue(firstWorld.lookup(UUID.randomUUID()).isEmpty());
        assertTrue(secondWorld.lookup(document.id()).isEmpty());
        assertTrue(secondWorld.current(ALICE).isEmpty());
    }

    @Test void trialIssueNeverRevokesTheLiveDocumentUntilTheCopyIsCommitted() {
        IdentityLedger live = new IdentityLedger();
        Document original = live.issue(ALICE, PROFILE, 100);
        IdentityLedger.Snapshot before = live.snapshot();
        IdentityLedger trial = live.copy();
        Document replacement = trial.issue(ALICE, PROFILE, 101);

        assertEquals(before, live.snapshot());
        assertEquals(original, live.lookup(original.id()).orElseThrow());
        assertTrue(live.lookup(replacement.id()).isEmpty());
        assertTrue(trial.lookup(original.id()).isEmpty());
        assertEquals(live.seal(), trial.seal());
        assertEquals(replacement, trial.current(ALICE).orElseThrow());
    }

    @Test void invalidIssueArgumentsDoNotMutateTheExistingRegistry() {
        IdentityLedger ledger = new IdentityLedger();
        ledger.issue(ALICE, PROFILE, 100);
        IdentityLedger.Snapshot before = ledger.snapshot();

        assertThrows(NullPointerException.class, () -> ledger.issue(null, PROFILE, 101));
        assertThrows(NullPointerException.class, () -> ledger.issue(ALICE, null, 101));
        assertThrows(IllegalArgumentException.class, () -> ledger.issue(ALICE, PROFILE, -1));
        assertEquals(before, ledger.snapshot());
    }

    @Test void fullRegistryRejectsNewOwnersWithoutEvictingAndPermitsExistingOwnerReplacement() {
        IdentityLedger ledger = new IdentityLedger(1);
        Document original = ledger.issue(ALICE, PROFILE, 100);
        IdentityLedger.Snapshot before = ledger.snapshot();

        assertThrows(IllegalStateException.class, () -> ledger.issue(BOB, PROFILE, 101));
        assertEquals(before, ledger.snapshot());
        assertEquals(original, ledger.lookup(original.id()).orElseThrow());
        Document replacement = ledger.issue(ALICE, PROFILE, 102);
        assertEquals(2, replacement.serial());
        assertEquals(1, ledger.documents().size());
        assertTrue(ledger.lookup(original.id()).isEmpty());
        assertThrows(IllegalStateException.class, () -> ledger.copy().issue(BOB, PROFILE, 103));
    }

    @Test void serialExhaustionNeverWrapsOrRevokesTheExistingDocument() {
        Document original = new Document(new UUID(0, 4), ALICE, Long.MAX_VALUE - 2, 100, PROFILE);
        IdentityLedger ledger = new IdentityLedger(SEAL, Long.MAX_VALUE - 1, List.of(original));
        Document last = ledger.issue(ALICE, PROFILE, 101);
        assertEquals(Long.MAX_VALUE - 1, last.serial());
        assertEquals(Long.MAX_VALUE, ledger.nextSerial());
        IdentityLedger.Snapshot exhausted = ledger.snapshot();

        assertThrows(IllegalStateException.class, () -> ledger.issue(ALICE, PROFILE, 102));
        assertEquals(exhausted, ledger.snapshot());
        assertEquals(last, ledger.lookup(last.id()).orElseThrow());
    }

    @Test void restoredRegistryPreservesSealSerialAndSnapshotWhileRejectingDuplicates() {
        IdentityLedger ledger = new IdentityLedger(SEAL, 1, List.of());
        Document issued = ledger.issue(ALICE, PROFILE, 100);
        List<Document> supplied = new ArrayList<>(ledger.documents());
        IdentityLedger restored = new IdentityLedger(ledger.seal(), ledger.nextSerial(), supplied);
        supplied.clear();

        assertEquals(ledger.snapshot(), restored.snapshot());
        assertEquals(SEAL, restored.seal());
        assertThrows(UnsupportedOperationException.class, () -> restored.documents().clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.snapshot().documents().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityLedger(SEAL, 2, List.of(issued, issued)));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityLedger(SEAL, 3, List.of(issued,
                        new Document(UUID.randomUUID(), ALICE, 2, 100, PROFILE))));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityLedger(SEAL, 3, List.of(issued,
                        new Document(UUID.randomUUID(), BOB, 1, 100, PROFILE))));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityLedger(SEAL, 1, List.of(issued)));
    }

    @Test void invalidPersistentStateAndInvalidDocumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new IdentityLedger(SEAL, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new IdentityLedger(0));
        assertThrows(IllegalArgumentException.class, () -> new IdentityLedger(IdentityLedger.MAX_OWNERS + 1));
        assertThrows(NullPointerException.class, () -> new IdentityLedger(null, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Document(UUID.randomUUID(), ALICE, 0, 100, PROFILE));
        assertThrows(IllegalArgumentException.class, () -> new Document(UUID.randomUUID(), ALICE, 1, -1, PROFILE));
        assertThrows(NullPointerException.class, () -> new Document(null, ALICE, 1, 100, PROFILE));
        assertThrows(NullPointerException.class, () -> new Document(UUID.randomUUID(), null, 1, 100, PROFILE));
        assertThrows(NullPointerException.class, () -> new Document(UUID.randomUUID(), ALICE, 1, 100, null));
    }
}
