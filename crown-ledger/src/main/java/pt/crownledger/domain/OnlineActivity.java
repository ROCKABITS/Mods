package pt.crownledger.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runtime-only connected time. Neither reconnects nor saved world time can supply elapsed minutes. */
public final class OnlineActivity {
    public static final long MINUTE_NANOS = 60_000_000_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private final Map<UUID, Long> countedThrough = new HashMap<>();

    public void login(UUID player, long nowNanos) {
        countedThrough.put(Objects.requireNonNull(player, "Player"), nowNanos);
    }

    /** A first observation starts a clock; later observations preserve its uncounted remainder. */
    public void observe(UUID player, long nowNanos) {
        countedThrough.putIfAbsent(Objects.requireNonNull(player, "Player"), nowNanos);
    }

    /** Session fragments are deliberately discarded instead of combining them across reconnects. */
    public void logout(UUID player) {
        countedThrough.remove(Objects.requireNonNull(player, "Player"));
    }

    public int elapsedMinutes(UUID player, long nowNanos) {
        Objects.requireNonNull(player, "Player");
        Long previous = countedThrough.putIfAbsent(player, nowNanos);
        if (previous == null) return 0;
        long elapsed = nowNanos - previous;
        if (elapsed < 0) {
            countedThrough.put(player, nowNanos);
            return 0;
        }
        int minutes = (int) (elapsed / MINUTE_NANOS);
        if (minutes > 0) countedThrough.put(player, previous + minutes * MINUTE_NANOS);
        return minutes;
    }

    /**
     * Credits completed minutes before the caller closes the period for every player together.
     * A minute ending exactly at the deadline belongs to that closing period. Delayed samples
     * discard the post-deadline gap and partial minute; they cannot backfill a future period.
     */
    public int recordCompletedMinutes(Treasury treasury, UUID player, String name, long nowMillis, long nowNanos) {
        Objects.requireNonNull(treasury, "Treasury");
        if (nowMillis < 0) throw new IllegalArgumentException("Invalid activity time");
        long deadline = treasury.nextCycleAt();
        if (deadline < 0 || treasury.pendingBatch() != null || nowMillis < treasury.periodStartedAt()) {
            login(player, nowNanos);
            return 0;
        }
        // Funding a frozen payroll can start a period between activity samples.
        // Time spent waiting for that funding cannot become activity in the new period.
        long periodAgeNanos = nanos(nowMillis - treasury.periodStartedAt());
        long periodStartNanos = nowNanos - periodAgeNanos;
        Long previous = countedThrough.get(player);
        if (previous != null && periodStartNanos - previous > 0) countedThrough.put(player, periodStartNanos);
        boolean closing = nowMillis >= deadline;
        long sampleNanos = nowNanos;
        if (closing) {
            sampleNanos -= nanos(nowMillis - deadline);
        }
        int minutes = elapsedMinutes(player, sampleNanos);
        if (closing) login(player, nowNanos);
        if (minutes > 0) treasury.recordActivity(player, name, minutes, closing ? deadline - 1 : nowMillis);
        return minutes;
    }

    private static long nanos(long millis) {
        return millis > Long.MAX_VALUE / NANOS_PER_MILLI ? Long.MAX_VALUE : millis * NANOS_PER_MILLI;
    }
}
