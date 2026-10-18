package pt.lawsjustice.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pt.lawsjustice.domain.JusticeLedger.*;

class JusticeLedgerTest {
    private static final String WORLD = "minecraft:overworld";
    private static final UUID PRISON = id(1), CELL = id(2), PLAYER = id(3), AUTHORITY = id(4), SENTENCE = id(5);
    private static UUID id(long value) { return new UUID(0, value); }
    private static Point p(int x, int y, int z) { return new Point(x, y, z); }
    private static Area area(int min, int max) { return new Area(WORLD, p(min, 60, min), p(max, 70, max)); }
    private static Cell cell() { return new Cell(CELL, "North cell", area(2, 4), p(3, 61, 3), 2); }
    private static Prison prison() { return new Prison(PRISON, "Crown prison", area(0, 20), p(21, 61, 10), List.of(cell()), 0); }
    private static JusticeLedger registered() { return JusticeLedger.empty().putPrison(prison()); }
    private static Sentence sentence(long ticks) { return sentence(SENTENCE, PLAYER, ticks); }
    private static Sentence sentence(UUID sentence, UUID player, long ticks) {
        return new Sentence(sentence, player, "Aetherion", PRISON, CELL, AUTHORITY, "President", "Trespassing", 1000, ticks, 0, 0, 0);
    }
    private static JusticeLedger sentenced(long ticks) { return registered().sentence(sentence(ticks)); }
    private static Prison withRevision(Prison source, long revision) {
        return new Prison(source.id(), source.name(), source.area(), source.release(), source.cells(), revision);
    }
    private static Decision decision(UUID id, UUID sentence, UUID player, String action, long original, long served) {
        return new Decision(id, sentence, player, "Aetherion", AUTHORITY, "President", action, "Trespassing", 1000, original, served, 0);
    }

    @Test void aCellArrivalKeepsBothFeetAndHeadInsideItsRegisteredHeight() {
        Area cellArea = area(2, 4);
        assertThrows(IllegalArgumentException.class, () -> new Cell(CELL, "No headroom", cellArea, p(3, 70, 3), 1));
        assertDoesNotThrow(() -> new Cell(CELL, "Headroom", cellArea, p(3, 69, 3), 1));
    }

    @Test void pointAllowsWorldEdgesAndRejectsEveryOutsideAxis() {
        assertDoesNotThrow(() -> p(-29_999_984, -2048, 29_999_984));
        assertDoesNotThrow(() -> p(29_999_984, 2047, -29_999_984));
        assertThrows(IllegalArgumentException.class, () -> p(-29_999_985, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> p(29_999_985, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> p(0, -2049, 0));
        assertThrows(IllegalArgumentException.class, () -> p(0, 2048, 0));
        assertThrows(IllegalArgumentException.class, () -> p(0, 0, Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> p(0, 0, Integer.MAX_VALUE));
    }

    @Test void betweenNormalizesAllThreeAxesWithoutMutatingTheInputs() {
        Point a = p(10, 80, -20), b = p(-10, 60, 30);
        Area result = Area.between(WORLD, a, b);
        assertEquals(p(-10, 60, -20), result.min());
        assertEquals(p(10, 80, 30), result.max());
        assertEquals(p(10, 80, -20), a);
    }

    @Test void directAreaRestorationRejectsReversedBoundsInsteadOfSilentlyRepairingThem() {
        assertThrows(IllegalArgumentException.class, () -> new Area(WORLD, p(2, 1, 1), p(1, 2, 2)));
        assertThrows(IllegalArgumentException.class, () -> new Area(WORLD, p(1, 2, 1), p(2, 1, 2)));
        assertThrows(IllegalArgumentException.class, () -> new Area(WORLD, p(1, 1, 2), p(2, 2, 1)));
    }

    @Test void dimensionsUseStrictBoundedMinecraftResourceIdentifiers() {
        assertDoesNotThrow(() -> new Area("other_mod:underworld/region.1", p(0, 0, 0), p(1, 1, 1)));
        for (String dimension : List.of("Overworld", "minecraft:Overworld", "minecraft:", ":overworld", "a:b:c", "a:b c", "a:" + "x".repeat(255)))
            assertThrows(IllegalArgumentException.class, () -> new Area(dimension, p(0, 0, 0), p(1, 1, 1)));
    }

    @Test void inclusiveExtentAndVolumeBoundariesAreEnforcedWithoutMultiplicationOverflow() {
        assertDoesNotThrow(() -> new Area(WORLD, p(0, 0, 0), p(1023, 15, 1023)));
        assertDoesNotThrow(() -> new Area(WORLD, p(0, 0, 0), p(255, 255, 255)));
        assertThrows(IllegalArgumentException.class, () -> new Area(WORLD, p(0, 0, 0), p(1024, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Area(WORLD, p(0, 0, 0), p(1023, 16, 1023)));
        assertThrows(IllegalArgumentException.class, () -> new Area(WORLD, p(-29_999_984, -2048, -29_999_984), p(29_999_984, 2047, 29_999_984)));
    }

    @Test void containmentIncludesTheBoundaryAndIntersectionsAreDimensionAware() {
        Area a = area(0, 10), sameBoundary = area(10, 20), adjacent = area(11, 20);
        assertTrue(a.contains(p(0, 60, 0))); assertTrue(a.contains(p(10, 70, 10)));
        assertFalse(a.contains(p(10, 71, 10))); assertFalse(a.contains(p(11, 70, 10)));
        assertTrue(a.intersects(sameBoundary)); assertTrue(sameBoundary.intersects(a));
        assertFalse(a.intersects(adjacent));
        Area elsewhere = new Area("minecraft:the_nether", a.min(), a.max());
        assertFalse(a.intersects(elsewhere)); assertFalse(a.contains(elsewhere));
    }

    @Test void cellRequiresItsSpawnInsideAndAReasonableCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Cell(CELL, "Cell", area(2, 4), p(5, 61, 3), 1));
        assertThrows(IllegalArgumentException.class, () -> new Cell(CELL, "Cell", area(2, 4), p(3, 61, 3), 0));
        assertThrows(IllegalArgumentException.class, () -> new Cell(CELL, "Cell", area(2, 4), p(3, 61, 3), 17));
        assertDoesNotThrow(() -> new Cell(CELL, "Cell", area(2, 4), p(2, 60, 2), 16));
    }

    @Test void prisonReleaseCannotBeInsideOrOnThePrisonBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new Prison(PRISON, "Prison", area(0, 20), p(0, 60, 0), List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new Prison(PRISON, "Prison", area(0, 20), p(20, 70, 20), List.of(), 0));
        assertDoesNotThrow(() -> new Prison(PRISON, "Prison", area(0, 20), p(21, 60, 20), List.of(), 0));
    }

    @Test void cellsCannotCrossThePrisonAreaOrUseAnotherDimension() {
        Cell outside = new Cell(CELL, "Outside", area(19, 21), p(20, 61, 20), 1);
        Cell elsewhere = new Cell(CELL, "Nether", new Area("minecraft:the_nether", cell().area().min(), cell().area().max()), p(3, 61, 3), 1);
        assertThrows(IllegalArgumentException.class, () -> new Prison(PRISON, "Prison", area(0, 20), p(21, 61, 1), List.of(outside), 0));
        assertThrows(IllegalArgumentException.class, () -> new Prison(PRISON, "Prison", area(0, 20), p(21, 61, 1), List.of(elsewhere), 0));
    }

    @Test void overlappingCellsAndDuplicateCellIdsAreRejectedBeforeRegistration() {
        Cell overlap = new Cell(id(9), "Overlap", area(4, 6), p(5, 61, 5), 1);
        Cell duplicate = new Cell(CELL, "Duplicate", area(6, 8), p(7, 61, 7), 1);
        assertThrows(IllegalArgumentException.class, () -> new Prison(PRISON, "Prison", area(0, 20), p(21, 61, 1), List.of(cell(), overlap), 0));
        assertThrows(IllegalArgumentException.class, () -> new Prison(PRISON, "Prison", area(0, 20), p(21, 61, 1), List.of(cell(), duplicate), 0));
    }

    @Test void geometryNamesReasonsAndReferencesRejectNullOrInvisibleInput() {
        assertThrows(IllegalArgumentException.class, () -> new Cell(null, "Cell", area(2, 4), p(3, 61, 3), 1));
        assertThrows(IllegalArgumentException.class, () -> new Cell(CELL, "  ", area(2, 4), p(3, 61, 3), 1));
        assertThrows(IllegalArgumentException.class, () -> new Cell(CELL, "\nCell", area(2, 4), p(3, 61, 3), 1));
        assertThrows(IllegalArgumentException.class, () -> new Cell(CELL, "x".repeat(49), area(2, 4), p(3, 61, 3), 1));
        assertThrows(IllegalArgumentException.class, () -> new Sentence(SENTENCE, PLAYER, "Player", PRISON, CELL, AUTHORITY, "Admin", "", 0, 1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Sentence(SENTENCE, PLAYER, "x".repeat(65), PRISON, CELL, AUTHORITY, "Admin", "Reason", 0, 1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Sentence(SENTENCE, PLAYER, "Player", PRISON, CELL, AUTHORITY, "Admin", "x".repeat(301), 0, 1, 0, 0, 0));
    }

    @Test void registeringAndEditingPrisonsUsesOptimisticRevisions() {
        JusticeLedger initial = JusticeLedger.empty();
        JusticeLedger created = initial.putPrison(prison());
        assertEquals(1, created.prisons().get(PRISON).revision()); assertTrue(initial.prisons().isEmpty());
        assertThrows(IllegalStateException.class, () -> created.putPrison(prison()));
        Prison current = created.prisons().get(PRISON);
        JusticeLedger edited = created.putPrison(new Prison(PRISON, "New prison", current.area(), current.release(), current.cells(), 1));
        assertEquals("New prison", edited.prisons().get(PRISON).name()); assertEquals(2, edited.prisons().get(PRISON).revision());
        assertEquals("Crown prison", created.prisons().get(PRISON).name());
        assertThrows(IllegalArgumentException.class, () -> initial.putPrison(withRevision(prison(), 3)));
    }

    @Test void prisonOverlapIsRejectedButDifferentDimensionsRemainIndependent() {
        Prison overlapping = new Prison(id(10), "Other", area(20, 30), p(31, 61, 1), List.of(), 0);
        assertThrows(IllegalArgumentException.class, () -> registered().putPrison(overlapping));
        Area nether = new Area("minecraft:the_nether", prison().area().min(), prison().area().max());
        assertEquals(2, registered().putPrison(new Prison(id(10), "Nether", nether, prison().release(), List.of(), 0)).prisons().size());
    }

    @Test void aCellIdCannotBeReusedByAnotherPrison() {
        Cell duplicate = new Cell(CELL, "Copy", area(32, 34), p(33, 61, 33), 1);
        Prison other = new Prison(id(10), "Other", area(30, 50), p(51, 61, 31), List.of(duplicate), 0);
        assertThrows(IllegalArgumentException.class, () -> registered().putPrison(other));
    }

    @Test void addingUpdatingAndRemovingCellsIsImmutableAndIncrementsOnePrisonRevision() {
        Cell added = new Cell(id(11), "South cell", area(6, 8), p(7, 61, 7), 1);
        JusticeLedger before = registered();
        JusticeLedger after = before.putCell(PRISON, added);
        assertEquals(2, after.prisons().get(PRISON).revision()); assertEquals(2, after.prisons().get(PRISON).cells().size());
        assertEquals(1, before.prisons().get(PRISON).cells().size());
        Cell edited = new Cell(added.id(), "Renamed", added.area(), added.spawn(), 2);
        JusticeLedger renamed = after.putCell(PRISON, edited);
        assertEquals(3, renamed.prisons().get(PRISON).revision()); assertEquals(2, renamed.prisons().get(PRISON).cells().size());
        JusticeLedger removed = renamed.removeCell(PRISON, added.id());
        assertEquals(4, removed.prisons().get(PRISON).revision()); assertEquals(List.of(cell()), removed.prisons().get(PRISON).cells());
    }

    @Test void unknownPrisonsAndCellsNeverCreatePhantomEntries() {
        JusticeLedger before = registered();
        assertThrows(IllegalStateException.class, () -> before.putCell(id(999), cell()));
        assertThrows(IllegalStateException.class, () -> before.removeCell(PRISON, id(999)));
        assertThrows(IllegalStateException.class, () -> before.removePrison(id(999)));
        assertEquals(1, before.prisons().size()); assertEquals(1, before.prisons().get(PRISON).cells().size());
    }

    @Test void occupiedPrisonsAndTheirCellsCannotBeMovedEditedOrRemoved() {
        JusticeLedger occupied = sentenced(1200);
        Prison current = occupied.prisons().get(PRISON);
        assertThrows(IllegalStateException.class, () -> occupied.putPrison(current));
        assertThrows(IllegalStateException.class, () -> occupied.putCell(PRISON, cell()));
        assertThrows(IllegalStateException.class, () -> occupied.removeCell(PRISON, CELL));
        assertThrows(IllegalStateException.class, () -> occupied.removePrison(PRISON));
        JusticeLedger released = occupied.release(PLAYER, AUTHORITY, "Admin", "Emergency", 1001);
        assertTrue(released.removePrison(PRISON).prisons().isEmpty());
        assertEquals(2, released.history().size());
    }

    @Test void sentencingLogsOneDecisionWithoutChangingTheInputState() {
        JusticeLedger before = registered();
        JusticeLedger after = before.sentence(sentence(1200));
        assertTrue(before.active().isEmpty()); assertTrue(before.history().isEmpty());
        assertEquals(sentence(1200), after.active().get(PLAYER));
        Decision entry = after.history().getFirst();
        assertEquals(SENTENCED, entry.action()); assertEquals(SENTENCE, entry.sentence()); assertEquals(PLAYER, entry.player());
        assertEquals(AUTHORITY, entry.authority()); assertEquals("Trespassing", entry.reason()); assertEquals(1000, entry.at());
    }

    @Test void sentenceCannotUseMissingPrisonsOrACellFromAnotherPrison() {
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.empty().sentence(sentence(1200)));
        Sentence wrongCell = new Sentence(SENTENCE, PLAYER, "Player", PRISON, id(999), AUTHORITY, "Admin", "Reason", 0, 100, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> registered().sentence(wrongCell));
    }

    @Test void sentenceCannotStartAlreadyServedReducedOrWithAnOldRevision() {
        for (long[] values : new long[][]{{1, 0}, {0, 1}}) {
            Sentence invalid = new Sentence(SENTENCE, PLAYER, "Player", PRISON, CELL, AUTHORITY, "Admin", "Reason", 0, 100, values[0], 0, values[1]);
            assertThrows(IllegalArgumentException.class, () -> registered().sentence(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new Sentence(SENTENCE, PLAYER, "Player", PRISON, CELL, AUTHORITY, "Admin", "Reason", 0, 100, 0, 1, 0));
    }

    @Test void durationsDatesAndProgressHaveStrictBounds() {
        assertDoesNotThrow(() -> sentence(1)); assertDoesNotThrow(() -> sentence(MAX_TICKS));
        assertThrows(IllegalArgumentException.class, () -> sentence(0));
        assertThrows(IllegalArgumentException.class, () -> sentence(MAX_TICKS + 1));
        assertThrows(IllegalArgumentException.class, () -> new Sentence(SENTENCE, PLAYER, "Player", PRISON, CELL, AUTHORITY, "Admin", "Reason", -1, 100, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Sentence(SENTENCE, PLAYER, "Player", PRISON, CELL, AUTHORITY, "Admin", "Reason", 0, 100, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Sentence(SENTENCE, PLAYER, "Player", PRISON, CELL, AUTHORITY, "Admin", "Reason", 0, 100, 101, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Sentence(SENTENCE, PLAYER, "Player", PRISON, CELL, AUTHORITY, "Admin", "Reason", 0, 100, 0, 0, -1));
    }

    @Test void duplicatePlayersAndSentenceIdsCannotAcquireTwoConcurrentSentences() {
        JusticeLedger current = sentenced(100);
        assertThrows(IllegalStateException.class, () -> current.sentence(sentence(id(99), PLAYER, 100)));
        assertThrows(IllegalStateException.class, () -> current.sentence(sentence(SENTENCE, id(99), 100)));
        assertEquals(1, current.active().size()); assertEquals(1, current.history().size());
    }

    @Test void cellCapacityIsReservedBeforeAcceptingAnotherSentence() {
        JusticeLedger full = sentenced(100).sentence(sentence(id(20), id(21), 100));
        assertEquals(2, full.active().size());
        assertThrows(IllegalArgumentException.class, () -> full.sentence(sentence(id(22), id(23), 100)));
        JusticeLedger oneFree = full.advance(PLAYER, 100, 1001);
        assertEquals(2, oneFree.sentence(sentence(id(22), id(23), 100)).active().size());
    }

    @Test void servingTimeRequiresExplicitOnlineTicksRatherThanTimeSinceIssue() {
        JusticeLedger current = sentenced(1200);
        assertSame(current, current.advance(PLAYER, 0, Long.MAX_VALUE));
        assertEquals(1200, current.active().get(PLAYER).remainingTicks());
        JusticeLedger advanced = current.advance(PLAYER, 20, Long.MAX_VALUE);
        assertEquals(1180, advanced.active().get(PLAYER).remainingTicks());
        assertEquals(20, advanced.active().get(PLAYER).servedTicks());
        assertEquals(1, advanced.active().get(PLAYER).revision());
        assertEquals(1200, current.active().get(PLAYER).remainingTicks());
    }

    @Test void restoringAfterDisconnectOrRestartDoesNotGiveOfflineCredit() {
        JusticeLedger before = sentenced(1200).advance(PLAYER, 200, 2000);
        JusticeLedger reopened = JusticeLedger.restore(before.prisons(), before.active(), before.history());
        assertEquals(1000, reopened.active().get(PLAYER).remainingTicks());
        assertEquals(999, reopened.advance(PLAYER, 1, Long.MAX_VALUE).active().get(PLAYER).remainingTicks());
    }

    @Test void completingAnExactSentenceRemovesCustodyAndAppendsOneFinalDecision() {
        JusticeLedger current = sentenced(40).advance(PLAYER, 20, 2000);
        assertEquals(1, current.history().size());
        JusticeLedger complete = current.advance(PLAYER, 20, 2001);
        assertFalse(complete.active().containsKey(PLAYER)); assertEquals(2, complete.history().size());
        Decision result = complete.history().getLast();
        assertEquals(COMPLETED, result.action()); assertEquals(40, result.servedTicks());
        assertEquals(40, result.originalTicks()); assertEquals(0, result.reductionTicks());
        assertEquals(SENTENCE, result.sentence()); assertEquals(2001, result.at());
        assertSame(complete, complete.advance(PLAYER, 20, 2002));
    }

    @Test void excessiveElapsedTicksClampWithoutOverflowOrNegativeTime() {
        JusticeLedger complete = sentenced(MAX_TICKS).advance(PLAYER, Long.MAX_VALUE, 2000);
        assertTrue(complete.active().isEmpty()); assertEquals(MAX_TICKS, complete.history().getLast().servedTicks());
        assertSame(complete, complete.advance(id(999), Long.MAX_VALUE, 2001));
    }

    @Test void invalidTickArgumentsDoNotMutateCustody() {
        JusticeLedger current = sentenced(100);
        assertThrows(IllegalArgumentException.class, () -> current.advance(PLAYER, -1, 2000));
        assertThrows(IllegalArgumentException.class, () -> current.advance(PLAYER, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> current.advance(null, 1, 2000));
        assertEquals(0, current.active().get(PLAYER).servedTicks());
    }

    @Test void aWallClockRollbackCannotPreventAutomaticRelease() {
        JusticeLedger complete = sentenced(1).advance(PLAYER, 1, 0);
        assertTrue(complete.active().isEmpty()); assertEquals(0, complete.history().getLast().at());
    }

    @Test void evenASaturatedRestoredRevisionCannotTrapAPrisoner() {
        Sentence original = sentence(2);
        Sentence saturated = new Sentence(original.id(), original.player(), original.playerName(), original.prison(), original.cell(),
                original.authority(), original.authorityName(), original.reason(), original.issuedAt(), 2, 0, 0, Long.MAX_VALUE);
        JusticeLedger restored = JusticeLedger.restore(registered().prisons(), Map.of(PLAYER, saturated), List.of());
        JusticeLedger partServed = restored.advance(PLAYER, 1, 2000);
        assertEquals(1, partServed.active().get(PLAYER).remainingTicks());
        assertEquals(Long.MAX_VALUE, partServed.active().get(PLAYER).revision());
        assertTrue(partServed.advance(PLAYER, 1, 2001).active().isEmpty());
    }

    @Test void emergencyReleaseRecordsTheActualActorReasonAndServedTimeExactlyOnce() {
        JusticeLedger current = sentenced(1200).advance(PLAYER, 25, 2000);
        JusticeLedger released = current.release(PLAYER, id(90), "Emergency admin", "Unsafe spawn", 2001);
        assertTrue(released.active().isEmpty());
        Decision entry = released.history().getLast();
        assertEquals(EMERGENCY_RELEASE, entry.action()); assertEquals(id(90), entry.authority());
        assertEquals("Emergency admin", entry.authorityName()); assertEquals("Unsafe spawn", entry.reason());
        assertEquals(25, entry.servedTicks()); assertEquals(1200, entry.originalTicks());
        assertThrows(IllegalStateException.class, () -> released.release(PLAYER, id(90), "Admin", "Again", 2002));
        assertEquals(2, released.history().size());
    }

    @Test void completedOrReleasedSentenceIdsCannotBeReusedWhileTheirAuditIsRetained() {
        JusticeLedger complete = sentenced(1).advance(PLAYER, 1, 2000);
        assertThrows(IllegalStateException.class, () -> complete.sentence(sentence(100)));
        JusticeLedger released = sentenced(100).release(PLAYER, AUTHORITY, "Admin", "Emergency", 2000);
        assertThrows(IllegalStateException.class, () -> released.sentence(sentence(100)));
        assertEquals(1, complete.sentence(sentence(id(88), PLAYER, 100)).active().size());
    }

    @Test void recordCollectionsAndLedgerViewsCannotBeUsedToChangeCustody() {
        ArrayList<Cell> suppliedCells = new ArrayList<>(List.of(cell()));
        Prison supplied = new Prison(PRISON, "Prison", prison().area(), prison().release(), suppliedCells, 0);
        suppliedCells.clear(); assertEquals(1, supplied.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> supplied.cells().clear());
        JusticeLedger original = sentenced(1200);
        LinkedHashMap<UUID, Prison> prisons = new LinkedHashMap<>(original.prisons());
        LinkedHashMap<UUID, Sentence> active = new LinkedHashMap<>(original.active());
        ArrayList<Decision> history = new ArrayList<>(original.history());
        JusticeLedger restored = JusticeLedger.restore(prisons, active, history);
        prisons.clear(); active.clear(); history.clear();
        assertEquals(1, restored.prisons().size()); assertEquals(1, restored.active().size()); assertEquals(1, restored.history().size());
        assertThrows(UnsupportedOperationException.class, () -> restored.prisons().clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.active().clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.history().clear());
    }

    @Test void restorationRejectsMismatchedMapKeysAndOrphanedCustody() {
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(id(999), prison()), Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), Map.of(id(999), sentence(100)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(), Map.of(PLAYER, sentence(100)), List.of()));
    }

    @Test void restorationRejectsCompletedSentencesRemainingInTheActiveMap() {
        Sentence done = new Sentence(SENTENCE, PLAYER, "Aetherion", PRISON, CELL, AUTHORITY, "President", "Trespassing", 1000, 100, 100, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), Map.of(PLAYER, done), List.of()));
    }

    @Test void restorationRejectsDuplicateSentenceIdsAndOverbookedCells() {
        Map<UUID, Sentence> duplicate = Map.of(PLAYER, sentence(100), id(9), sentence(SENTENCE, id(9), 100));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), duplicate, List.of()));
        Map<UUID, Sentence> overbooked = Map.of(PLAYER, sentence(100), id(9), sentence(id(10), id(9), 100), id(11), sentence(id(12), id(11), 100));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), overbooked, List.of()));
    }

    @Test void restoredPrisonsCannotOverlapEvenIfCreatedInSeparateSnapshots() {
        Prison other = new Prison(id(10), "Other", area(20, 30), p(31, 61, 1), List.of(), 0);
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(PRISON, prison(), other.id(), other), Map.of(), List.of()));
    }

    @Test void aRestoredActiveSentenceCannotAlsoHaveACompletedOrEmergencyDecision() {
        Decision complete = decision(id(40), SENTENCE, PLAYER, COMPLETED, 100, 100);
        Decision released = decision(id(40), SENTENCE, PLAYER, EMERGENCY_RELEASE, 100, 1);
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), Map.of(PLAYER, sentence(100)), List.of(complete)));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), Map.of(PLAYER, sentence(100)), List.of(released)));
    }

    @Test void activeSentenceAuditMustAgreeWithItsImmutableCondemnationDetails() {
        Decision wrongPlayer = decision(id(40), SENTENCE, id(77), SENTENCED, 100, 0);
        Decision wrongDuration = decision(id(40), SENTENCE, PLAYER, SENTENCED, 101, 0);
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), Map.of(PLAYER, sentence(100)), List.of(wrongPlayer)));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), Map.of(PLAYER, sentence(100)), List.of(wrongDuration)));
    }

    @Test void malformedHistoryCannotBeSilentlyDroppedReorderedOrDoubleFinalized() {
        Decision start = decision(id(40), SENTENCE, PLAYER, SENTENCED, 100, 0);
        Decision complete = decision(id(41), SENTENCE, PLAYER, COMPLETED, 100, 100);
        Decision released = decision(id(42), SENTENCE, PLAYER, EMERGENCY_RELEASE, 100, 1);
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(), Map.of(), List.of(start, start)));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(), Map.of(), List.of(complete, start)));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(), Map.of(), List.of(start, complete, released)));
        Decision wrongPlayer = decision(id(43), SENTENCE, id(99), COMPLETED, 100, 100);
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(), Map.of(), List.of(start, wrongPlayer)));
    }

    @Test void historyAcceptsATerminalDecisionWhoseCreationWasPrunedByTheBound() {
        Decision complete = decision(id(41), SENTENCE, PLAYER, COMPLETED, 100, 100);
        JusticeLedger restored = JusticeLedger.restore(Map.of(), Map.of(), List.of(complete));
        assertEquals(List.of(complete), restored.history());
    }

    @Test void restorationRejectsAnUnclosedCondemnationWhoseActiveSentenceDisappeared() {
        Decision start = decision(id(40), SENTENCE, PLAYER, SENTENCED, 100, 0);
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(), Map.of(), List.of(start)));
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(registered().prisons(),
                Map.of(PLAYER, sentence(id(99), PLAYER, 100)), List.of(start)));
    }

    @Test void restorationRejectsTwoOverlappingHistoricalCondemnationsForTheSamePlayer() {
        Decision first = decision(id(40), SENTENCE, PLAYER, SENTENCED, 100, 0);
        Decision second = decision(id(41), id(99), PLAYER, SENTENCED, 100, 0);
        Decision completeFirst = decision(id(42), SENTENCE, PLAYER, COMPLETED, 100, 100);
        Decision completeSecond = decision(id(43), id(99), PLAYER, COMPLETED, 100, 100);
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(), Map.of(),
                List.of(first, second, completeFirst, completeSecond)));
        assertDoesNotThrow(() -> JusticeLedger.restore(Map.of(), Map.of(),
                List.of(first, completeFirst, second, completeSecond)));
    }

    @Test void decisionActionAndProgressMustDescribeAPossiblePhaseOneCustodyTransition() {
        assertThrows(IllegalArgumentException.class, () -> decision(id(40), SENTENCE, PLAYER, "FINE", 100, 0));
        assertThrows(IllegalArgumentException.class, () -> decision(id(40), SENTENCE, PLAYER, SENTENCED, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> decision(id(40), SENTENCE, PLAYER, COMPLETED, 100, 99));
        assertThrows(IllegalArgumentException.class, () -> decision(id(40), SENTENCE, PLAYER, EMERGENCY_RELEASE, 100, 100));
    }

    @Test void boundedHistoryPrunesOnlyTheOldestValidRecordWhenAppending() {
        ArrayList<Decision> history = new ArrayList<>();
        for (int i = 0; i < MAX_HISTORY; i++) history.add(decision(id(10000 + i), id(20000 + i), id(30000 + i), COMPLETED, 100, 100));
        JusticeLedger restored = JusticeLedger.restore(registered().prisons(), Map.of(), history);
        JusticeLedger current = restored.sentence(sentence(100));
        assertEquals(MAX_HISTORY, current.history().size()); assertEquals(history.get(1), current.history().getFirst());
        assertEquals(SENTENCED, current.history().getLast().action()); assertEquals(history, restored.history());
        ArrayList<Decision> tooMany = new ArrayList<>(current.history());
        tooMany.add(history.getFirst());
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(Map.of(), Map.of(), tooMany));
    }

    @Test void aLongLivedSentenceMayOutlastItsCreationAuditWithoutLosingItsRemainingTime() {
        JusticeLedger current = sentenced(1200).advance(PLAYER, 200, 2000);
        ArrayList<Decision> newer = new ArrayList<>();
        for (int i = 0; i < MAX_HISTORY; i++) newer.add(decision(id(10000 + i), id(20000 + i), id(30000 + i), COMPLETED, 100, 100));
        JusticeLedger restored = JusticeLedger.restore(current.prisons(), current.active(), newer);
        assertEquals(1000, restored.active().get(PLAYER).remainingTicks());
        assertTrue(restored.advance(PLAYER, 1000, 2001).active().isEmpty());
    }

    @Test void prisonCountAndCellCountLimitsRejectNewEntriesWithoutChangingTheSnapshot() {
        LinkedHashMap<UUID, Prison> prisons = new LinkedHashMap<>();
        for (int i = 0; i < MAX_PRISONS; i++) {
            int coordinate = i * 4;
            Area area = area(coordinate, coordinate + 1);
            UUID id = id(1000 + i);
            prisons.put(id, new Prison(id, "Prison", area, p(coordinate + 2, 60, coordinate), List.of(), 0));
        }
        JusticeLedger full = JusticeLedger.restore(prisons, Map.of(), List.of());
        Prison extra = new Prison(PRISON, "Extra", area(1000, 1001), p(1002, 60, 1000), List.of(), 0);
        assertThrows(IllegalArgumentException.class, () -> full.putPrison(extra));
        assertEquals(MAX_PRISONS, full.prisons().size());
        ArrayList<Cell> cells = new ArrayList<>();
        for (int i = 0; i < MAX_CELLS; i++) {
            Point point = p(i, 61, 0);
            cells.add(new Cell(id(10000 + i), "Cell", new Area(WORLD, point, p(point.x(),point.y()+1,point.z())), point, 1));
        }
        Prison maxCells = new Prison(PRISON, "Prison", new Area(WORLD, p(0, 60, 0), p(100, 70, 100)), p(101, 60, 0), cells, 0);
        JusticeLedger cellsFull = JusticeLedger.empty().putPrison(maxCells);
        Point point = p(65, 61, 0);
        Cell cell = new Cell(id(20000), "Extra cell", new Area(WORLD, point, p(point.x(),point.y()+1,point.z())), point, 1);
        assertThrows(IllegalArgumentException.class, () -> cellsFull.putCell(PRISON, cell));
        assertEquals(MAX_CELLS, cellsFull.prisons().get(PRISON).cells().size());
    }

    @Test void activeCapacityIsValidatedOnRestoreAndOnSentencing() {
        LinkedHashMap<UUID, Prison> prisons = new LinkedHashMap<>();
        LinkedHashMap<UUID, Sentence> active = new LinkedHashMap<>();
        int player = 0;
        for (int prisonIndex = 0; prisonIndex < 5; prisonIndex++) {
            int offset = prisonIndex * 200;
            UUID prisonId = id(1000 + prisonIndex);
            ArrayList<Cell> cells = new ArrayList<>();
            for (int cellIndex = 0; cellIndex < MAX_CELLS; cellIndex++) {
                Point point = p(offset + cellIndex, 61, 1);
                UUID cellId = id(2000 + prisonIndex * 100 + cellIndex);
                cells.add(new Cell(cellId, "Cell", new Area(WORLD, point, p(point.x(),point.y()+1,point.z())), point, 16));
                for (int occupant = 0; occupant < 16; occupant++) {
                    if (player > MAX_ACTIVE) break;
                    UUID playerId = id(10000 + player), sentenceId = id(20000 + player);
                    active.put(playerId, new Sentence(sentenceId, playerId, "Prisoner", prisonId, cellId, AUTHORITY, "Admin", "Reason", 0, 100, 0, 0, 0));
                    player++;
                }
            }
            prisons.put(prisonId, new Prison(prisonId, "Prison", new Area(WORLD, p(offset, 60, 0), p(offset + 100, 70, 100)), p(offset + 101, 60, 0), cells, 0));
        }
        assertEquals(MAX_ACTIVE + 1, active.size());
        assertThrows(IllegalArgumentException.class, () -> JusticeLedger.restore(prisons, active, List.of()));
        UUID extraKey = id(10000 + MAX_ACTIVE);
        Sentence extra = active.remove(extraKey);
        JusticeLedger full = JusticeLedger.restore(prisons, active, List.of());
        assertThrows(IllegalArgumentException.class, () -> full.sentence(extra));
        assertEquals(MAX_ACTIVE, full.active().size());
    }

    @Test void removingAnEmptyPrisonRetainsItsHistoricalDecisions() {
        JusticeLedger complete = sentenced(1).advance(PLAYER, 1, 2000);
        JusticeLedger removed = complete.removePrison(PRISON);
        assertTrue(removed.prisons().isEmpty()); assertEquals(complete.history(), removed.history());
        assertDoesNotThrow(() -> JusticeLedger.restore(removed.prisons(), removed.active(), removed.history()));
    }

    @Test void failedMutationsDoNotCorruptEarlierAuditOrOccupancy() {
        JusticeLedger before = sentenced(100);
        List<Decision> audit = before.history();
        assertThrows(IllegalStateException.class, () -> before.sentence(sentence(100)));
        assertThrows(IllegalStateException.class, () -> before.removePrison(PRISON));
        assertThrows(IllegalArgumentException.class, () -> before.advance(PLAYER, -100, 2000));
        assertEquals(audit, before.history()); assertEquals(100, before.active().get(PLAYER).remainingTicks());
        assertEquals(1, before.prisons().get(PRISON).revision());
    }
}
