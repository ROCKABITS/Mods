package pt.realmtitles.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TitleLedgerTest {
    private static final UUID ALICE = citizenId(1);
    private static final UUID BOB = citizenId(2);
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void definitionsKeepStableIdsAndEditsAreVisibleThroughExistingGrants() {
        TitleLedger initial = fixture();
        TitleLedger granted = initial.grant(ALICE, titleId(1), 0, NOW, grantId(1));
        TitleLedger edited = granted.editDefinition(titleId(1), "Keeper of the Seal", "Appointed by the realm.");

        assertEquals(titleId(1), edited.definitions().get(titleId(1)).id());
        assertEquals("Keeper of the Seal", edited.definitions().get(edited.activeGrants(ALICE, NOW).getFirst().titleId()).name());
        assertEquals(granted.grants(), edited.grants());
        assertEquals("Title 1", granted.definitions().get(titleId(1)).name());
        assertTrue(initial.grants().isEmpty());
    }

    @Test
    void definitionNamesAreUniqueIgnoringCaseWithoutDependingOnDefaultLocale() {
        TitleLedger ledger = new TitleLedger().createDefinition(titleId(1), "Keeper", "");
        rejected("duplicate_name", () -> ledger.createDefinition(titleId(2), "KEEPER", ""));
        TitleLedger unicode = new TitleLedger().createDefinition(titleId(1), "I", "");
        rejected("duplicate_name", () -> unicode.createDefinition(titleId(2), "\u0131", ""));
        assertEquals(1, ledger.definitions().size());
    }

    @Test
    void rejectedEditLeavesBothDefinitionsAndAssignmentsIntact() {
        TitleLedger ledger = fixture().grant(ALICE, titleId(1), 1, NOW, grantId(1));
        TitleLedger.Snapshot before = ledger.snapshot();
        rejected("duplicate_name", () -> ledger.editDefinition(titleId(1), "tItLe 2", "Replacement"));
        assertEquals(before, ledger.snapshot());
        assertSame(ledger, ledger.editDefinition(titleId(1), "Title 1", "Description 1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " leading", "trailing ", "bad\nname", "bad\tname", "bad\u0000name",
            "bad\u007fname", "bad\u0085name", "bad\u00a7name", "bad\u2028name", "bad\u2029name",
            "bad\u202ename", "bad\u200bname", "bad\ud800name", "bad\udc00name"})
    void rejectsUnsafeOrEmptyDefinitionNames(String name) {
        rejected("invalid_name", () -> new TitleLedger.Definition(titleId(1), name, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {" leading", "trailing ", "bad\ntext", "bad\ttext", "bad\u001ftext",
            "bad\u00a7text", "bad\u2028text", "bad\u2029text", "bad\u2066text", "bad\ud800text"})
    void rejectsUnsafeDescriptionsRatherThanSilentlyChangingPersistedData(String description) {
        rejected("invalid_description", () -> new TitleLedger.Definition(titleId(1), "Keeper", description));
    }

    @Test
    void textBoundsAllowUnicodeAndRejectOverlongFields() {
        assertEquals("A".repeat(48), new TitleLedger.Definition(titleId(1), "A".repeat(48), "B".repeat(256)).name());
        assertDoesNotThrow(() -> new TitleLedger.Definition(titleId(1), "Garde du Ch\u00e2teau \ud83c\udff0", ""));
        assertDoesNotThrow(() -> new TitleLedger.Citizen(ALICE, "A".repeat(64), true));
        rejected("invalid_name", () -> new TitleLedger.Definition(titleId(1), "A".repeat(49), ""));
        rejected("invalid_description", () -> new TitleLedger.Definition(titleId(1), "Keeper", "B".repeat(257)));
        rejected("invalid_player_name", () -> new TitleLedger.Citizen(ALICE, "A".repeat(65), true));
        rejected("invalid_player_name", () -> new TitleLedger.Citizen(ALICE, "Alice\u202e", true));
    }

    @Test
    void catalogCapacityIsEnforcedForBothCreationAndLoading() {
        List<TitleLedger.Definition> definitions = definitions(256);
        TitleLedger full = new TitleLedger(definitions, List.of(), List.of());
        assertEquals(256, full.definitions().size());
        rejected("definition_limit", () -> full.createDefinition(titleId(257), "One too many", ""));
        assertEquals("Renamed", full.editDefinition(titleId(1), "Renamed", "").definitions().get(titleId(1)).name());
        definitions.add(definition(257));
        rejected("definition_limit", () -> new TitleLedger(definitions, List.of(), List.of()));
    }

    @Test
    void assignmentsRequireKnownEligiblePublicCitizens() {
        TitleLedger unknown = new TitleLedger(List.of(definition(1)), List.of(), List.of());
        rejected("unknown_player", () -> unknown.grant(ALICE, titleId(1), 0, NOW, grantId(1)));
        TitleLedger ineligible = unknown.observeCitizen(ALICE, "Alice", false);
        rejected("ineligible_player", () -> ineligible.grant(ALICE, titleId(1), 0, NOW, grantId(1)));
        TitleLedger eligible = ineligible.observeCitizen(ALICE, "Alice", true);
        assertEquals(1, eligible.grant(ALICE, titleId(1), 0, NOW, grantId(1)).activeGrants(ALICE, NOW).size());
        assertTrue(unknown.citizens().isEmpty());
    }

    @Test
    void directoryUpdatesPreserveUuidAssociationAndPreviouslyGrantedTitles() {
        TitleLedger titled = fixture().grant(ALICE, titleId(1), 0, NOW, grantId(1));
        TitleLedger observed = titled.observeCitizen(ALICE, "Alice of the Valley", false);
        assertEquals("Alice of the Valley", observed.citizens().get(ALICE).name());
        assertEquals(List.of(grantId(1)), observed.activeGrants(ALICE, NOW).stream().map(TitleLedger.Grant::id).toList());
        rejected("ineligible_player", () -> observed.grant(ALICE, titleId(2), 0, NOW, grantId(2)));
        assertSame(observed, observed.observeCitizen(ALICE, "Alice of the Valley", false));
    }

    @Test
    void onePlayerCanHoldMultipleTitlesAndDifferentPlayersCanShareOneTitle() {
        TitleLedger ledger = fixture().grant(ALICE, titleId(1), 0, NOW, grantId(1))
                .grant(ALICE, titleId(2), 1, NOW, grantId(2))
                .grant(BOB, titleId(1), 0, NOW, grantId(3));
        assertEquals(2, ledger.activeGrants(ALICE, NOW).size());
        assertEquals(1, ledger.activeGrants(BOB, NOW).size());
        assertTrue(ledger.activeGrants(citizenId(3), NOW).isEmpty());
    }

    @Test
    void permanentAndOfflineTemporaryTitlesUseExactEpochExpiryBoundary() {
        TitleLedger ledger = fixture().grant(ALICE, titleId(1), 1, NOW, grantId(1))
                .grant(ALICE, titleId(2), 0, NOW, grantId(2));
        long expiry = NOW + TitleLedger.DAY_MILLIS;
        assertEquals(expiry, ledger.grants().get(grantId(1)).expiresAt());
        assertEquals(2, ledger.activeGrants(ALICE, expiry - 1).size());
        assertEquals(List.of(grantId(2)), ledger.activeGrants(ALICE, expiry).stream().map(TitleLedger.Grant::id).toList());
        assertEquals(List.of(grantId(2)), ledger.activeGrants(ALICE, Long.MAX_VALUE).stream().map(TitleLedger.Grant::id).toList());
        assertEquals(2, ledger.grants().size(), "Reading expiry must not mutate persistence.");
    }

    @Test
    void renewalCreatesFreshGrantIdentityAndRejectsAStaleRevoke() {
        TitleLedger original = fixture().grant(ALICE, titleId(1), 1, NOW, grantId(1));
        TitleLedger renewed = original.grant(ALICE, titleId(1), 365, NOW + 10, grantId(2));
        assertEquals(1, renewed.grants().size());
        assertFalse(renewed.grants().containsKey(grantId(1)));
        assertEquals(NOW + 10 + 365 * TitleLedger.DAY_MILLIS, renewed.grants().get(grantId(2)).expiresAt());
        rejected("stale_grant", () -> renewed.revoke(grantId(1), NOW + 11));
        rejected("duplicate_grant", () -> original.grant(ALICE, titleId(1), 0, NOW + 10, grantId(1)));
        assertTrue(renewed.revoke(grantId(2), NOW + 11).activeGrants(ALICE, NOW + 11).isEmpty());
        assertEquals(grantId(1), original.activeGrants(ALICE, NOW).getFirst().id());
    }

    @Test
    void grantIdCollisionCannotOverwriteAnotherPlayersAssignment() {
        TitleLedger ledger = fixture().grant(BOB, titleId(1), 0, NOW, grantId(1));
        rejected("duplicate_grant", () -> ledger.grant(ALICE, titleId(2), 0, NOW, grantId(1)));
        assertEquals(BOB, ledger.grants().get(grantId(1)).owner());
    }

    @Test
    void sixteenTitleOwnerBoundAllowsRenewalAndExpiresWithoutTicking() {
        List<TitleLedger.Grant> grants = new ArrayList<>();
        for (int i = 1; i <= 16; i++) grants.add(new TitleLedger.Grant(grantId(i), ALICE, titleId(i), NOW, i == 16 ? NOW + 1 : 0));
        TitleLedger full = new TitleLedger(definitions(17), grants, List.of(citizen(1)));
        rejected("owner_grant_limit", () -> full.grant(ALICE, titleId(17), 0, NOW, grantId(17)));
        assertEquals(16, full.grant(ALICE, titleId(1), 0, NOW, grantId(18)).activeGrants(ALICE, NOW).size());
        TitleLedger afterExpiry = full.grant(ALICE, titleId(17), 0, NOW + 1, grantId(17));
        assertEquals(16, afterExpiry.activeGrants(ALICE, NOW + 1).size());
        assertFalse(afterExpiry.grants().containsKey(grantId(16)));
        grants.add(new TitleLedger.Grant(grantId(17), ALICE, titleId(17), NOW, 0));
        rejected("owner_grant_limit", () -> new TitleLedger(definitions(17), grants, List.of(citizen(1))));
    }

    @Test
    void deletionRequiresExplicitRevokeOfEveryActiveAssignment() {
        TitleLedger ledger = fixture().grant(ALICE, titleId(1), 0, NOW, grantId(1))
                .grant(BOB, titleId(1), 0, NOW, grantId(2));
        rejected("definition_in_use", () -> ledger.deleteDefinition(titleId(1), NOW));
        TitleLedger onceRevoked = ledger.revoke(grantId(1), NOW);
        rejected("definition_in_use", () -> onceRevoked.deleteDefinition(titleId(1), NOW));
        TitleLedger deleted = onceRevoked.revoke(grantId(2), NOW).deleteDefinition(titleId(1), NOW);
        assertFalse(deleted.definitions().containsKey(titleId(1)));
        assertTrue(deleted.grants().isEmpty());
        assertEquals(2, ledger.grants().size());
    }

    @Test
    void deletionOfExpiredDefinitionRemovesDanglingHistoryAtExactBoundary() {
        TitleLedger ledger = fixture().grant(ALICE, titleId(1), 1, NOW, grantId(1))
                .grant(BOB, titleId(2), 0, NOW, grantId(2));
        long expiry = NOW + TitleLedger.DAY_MILLIS;
        rejected("definition_in_use", () -> ledger.deleteDefinition(titleId(1), expiry - 1));
        TitleLedger deleted = ledger.deleteDefinition(titleId(1), expiry);
        assertEquals(List.of(grantId(2)), List.copyOf(deleted.grants().keySet()));
        assertTrue(deleted.definitions().containsKey(titleId(2)));
        assertEquals(deleted.snapshot(), restore(deleted).snapshot());
    }

    @Test
    void purgeIsIdempotentAndRevokeRejectsExpiredAndUnknownIds() {
        TitleLedger ledger = fixture().grant(ALICE, titleId(1), 1, NOW, grantId(1));
        assertSame(ledger, ledger.purgeExpired(NOW));
        long expiry = NOW + TitleLedger.DAY_MILLIS;
        rejected("stale_grant", () -> ledger.revoke(grantId(1), expiry));
        rejected("stale_grant", () -> ledger.revoke(grantId(2), NOW));
        TitleLedger purged = ledger.purgeExpired(expiry);
        assertTrue(purged.grants().isEmpty());
        assertSame(purged, purged.purgeExpired(expiry));
        assertEquals(1, ledger.grants().size());
    }

    @Test
    void invalidDurationsAndOverflowAreRejectedBeforeAnyCommit() {
        TitleLedger ledger = fixture();
        rejected("invalid_days", () -> ledger.grant(ALICE, titleId(1), -1, NOW, grantId(1)));
        rejected("invalid_days", () -> ledger.grant(ALICE, titleId(1), 366, NOW, grantId(1)));
        rejected("invalid_expiry", () -> ledger.grant(ALICE, titleId(1), 1, Long.MAX_VALUE, grantId(1)));
        assertTrue(ledger.grants().isEmpty());
        assertEquals(0, ledger.grant(ALICE, titleId(1), 0, Long.MAX_VALUE, grantId(1)).grants().get(grantId(1)).expiresAt());
        assertEquals(Long.MAX_VALUE, ledger.grant(ALICE, titleId(1), 1, Long.MAX_VALUE - TitleLedger.DAY_MILLIS,
                grantId(1)).grants().get(grantId(1)).expiresAt());
    }

    @Test
    void negativeEpochTimesAreRejectedForEveryTimeBearingOperation() {
        TitleLedger ledger = fixture().grant(ALICE, titleId(1), 0, NOW, grantId(1));
        rejected("invalid_time", () -> ledger.grant(ALICE, titleId(1), 0, -1, grantId(2)));
        rejected("invalid_time", () -> ledger.revoke(grantId(1), -1));
        rejected("invalid_time", () -> ledger.deleteDefinition(titleId(1), -1));
        rejected("invalid_time", () -> ledger.activeGrants(ALICE, -1));
        rejected("invalid_time", () -> ledger.purgeExpired(-1));
        rejected("invalid_time", () -> new TitleLedger.Grant(grantId(1), ALICE, titleId(1), -1, 0));
        rejected("invalid_expiry", () -> new TitleLedger.Grant(grantId(1), ALICE, titleId(1), NOW, NOW));
        rejected("invalid_expiry", () -> new TitleLedger.Grant(grantId(1), ALICE, titleId(1), NOW, -1));
    }

    @Test
    void strictLoadRejectsDuplicateIdsNamesOwnersAndDanglingReferences() {
        TitleLedger.Definition first = definition(1);
        TitleLedger.Grant grant = new TitleLedger.Grant(grantId(1), ALICE, titleId(1), NOW, 0);
        rejected("duplicate_definition", () -> new TitleLedger(List.of(first, first), List.of(), List.of()));
        rejected("duplicate_name", () -> new TitleLedger(List.of(first,
                new TitleLedger.Definition(titleId(2), "TITLE 1", "")), List.of(), List.of()));
        rejected("duplicate_citizen", () -> new TitleLedger(List.of(first), List.of(), List.of(citizen(1), citizen(1))));
        rejected("duplicate_grant", () -> new TitleLedger(List.of(first), List.of(grant, grant), List.of(citizen(1))));
        rejected("duplicate_assignment", () -> new TitleLedger(List.of(first), List.of(grant,
                new TitleLedger.Grant(grantId(2), ALICE, titleId(1), NOW, NOW + 1)), List.of(citizen(1))));
        rejected("unknown_definition", () -> new TitleLedger(List.of(), List.of(grant), List.of(citizen(1))));
        rejected("unknown_player", () -> new TitleLedger(List.of(first), List.of(grant), List.of()));
    }

    @Test
    void loadedSnapshotsAndAllReturnedCollectionsAreImmutableAndDefensivelyCopied() {
        List<TitleLedger.Definition> definitions = new ArrayList<>(List.of(definition(1)));
        List<TitleLedger.Citizen> citizens = new ArrayList<>(List.of(citizen(1)));
        List<TitleLedger.Grant> grants = new ArrayList<>(List.of(new TitleLedger.Grant(grantId(1), ALICE, titleId(1), NOW, 0)));
        TitleLedger ledger = new TitleLedger(definitions, grants, citizens);
        TitleLedger.Snapshot snapshot = new TitleLedger.Snapshot(definitions, grants, citizens);
        definitions.clear();
        grants.clear();
        citizens.clear();
        assertEquals(1, ledger.definitions().size());
        assertEquals(1, ledger.grants().size());
        assertEquals(1, ledger.citizens().size());
        assertEquals(1, snapshot.grants().size());
        assertThrows(UnsupportedOperationException.class, () -> ledger.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.grants().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.citizens().clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.activeGrants(ALICE, NOW).clear());
        assertThrows(UnsupportedOperationException.class, () -> ledger.snapshot().grants().clear());
        assertEquals(ledger.snapshot(), restore(ledger).snapshot());
    }

    @Test
    void citizenCapacityCannotBeExceededAndExistingCitizenCanStillBeUpdated() {
        List<TitleLedger.Citizen> citizens = new ArrayList<>();
        for (int i = 1; i <= TitleLedger.MAX_CITIZENS; i++) citizens.add(citizen(i));
        TitleLedger ledger = new TitleLedger(List.of(), List.of(), citizens);
        rejected("citizen_limit", () -> ledger.observeCitizen(citizenId(100_001), "New citizen", true));
        assertEquals("Renamed", ledger.observeCitizen(ALICE, "Renamed", false).citizens().get(ALICE).name());
        citizens.add(citizen(100_001));
        rejected("citizen_limit", () -> new TitleLedger(List.of(), List.of(), citizens));
    }

    @Test
    void globalGrantCapacityAllowsRenewalButCannotBeBypassedByAnotherOwner() {
        List<TitleLedger.Citizen> citizens = new ArrayList<>();
        List<TitleLedger.Grant> grants = new ArrayList<>();
        for (int owner = 1; owner <= 6251; owner++) citizens.add(citizen(owner));
        for (int i = 0; i < TitleLedger.MAX_GRANTS; i++) {
            grants.add(new TitleLedger.Grant(grantId(i + 1), citizenId(i / 16 + 1), titleId(i % 16 + 1), NOW, 0));
        }
        TitleLedger full = new TitleLedger(definitions(16), grants, citizens);
        rejected("grant_limit", () -> full.grant(citizenId(6251), titleId(1), 0, NOW, grantId(100_001)));
        TitleLedger renewed = full.grant(ALICE, titleId(1), 1, NOW, grantId(100_001));
        assertEquals(100_000, renewed.grants().size());
        assertFalse(renewed.grants().containsKey(grantId(1)));
        grants.add(new TitleLedger.Grant(grantId(100_001), citizenId(6251), titleId(1), NOW, 0));
        rejected("grant_limit", () -> new TitleLedger(definitions(16), grants, citizens));
    }

    @Test
    void nullsAndUnknownDefinitionsFailClosed() {
        rejected("invalid_data", () -> new TitleLedger(null, List.of(), List.of()));
        rejected("invalid_data", () -> new TitleLedger.Definition(null, "Keeper", ""));
        rejected("invalid_name", () -> new TitleLedger.Definition(titleId(1), null, ""));
        rejected("invalid_description", () -> new TitleLedger.Definition(titleId(1), "Keeper", null));
        rejected("unknown_definition", () -> fixture().editDefinition(titleId(3), "Unknown", ""));
        rejected("unknown_definition", () -> fixture().deleteDefinition(titleId(3), NOW));
        rejected("unknown_definition", () -> fixture().grant(ALICE, titleId(3), 0, NOW, grantId(1)));
        rejected("duplicate_definition", () -> fixture().createDefinition(titleId(1), "Different", ""));
    }

    private static TitleLedger fixture() {
        return new TitleLedger(definitions(2), List.of(), List.of(citizen(1), citizen(2)));
    }

    private static TitleLedger restore(TitleLedger ledger) {
        TitleLedger.Snapshot snapshot = ledger.snapshot();
        return new TitleLedger(snapshot.definitions(), snapshot.grants(), snapshot.citizens());
    }

    private static List<TitleLedger.Definition> definitions(int count) {
        List<TitleLedger.Definition> definitions = new ArrayList<>();
        for (int i = 1; i <= count; i++) definitions.add(definition(i));
        return definitions;
    }

    private static TitleLedger.Definition definition(int n) {
        return new TitleLedger.Definition(titleId(n), "Title " + n, "Description " + n);
    }

    private static TitleLedger.Citizen citizen(int n) {
        return new TitleLedger.Citizen(citizenId(n), "Citizen " + n, true);
    }

    private static UUID titleId(int n) { return new UUID(1, n); }
    private static UUID grantId(int n) { return new UUID(2, n); }
    private static UUID citizenId(int n) { return new UUID(3, n); }

    private static void rejected(String code, Runnable action) {
        DomainException failure = assertThrows(DomainException.class, action::run);
        assertEquals(code, failure.code());
        assertEquals("gui.realmtitles." + code, failure.getMessage());
    }
}
