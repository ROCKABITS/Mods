package pt.realmnotifications.domain;

/** Client presentation only. An uninterrupted burst gets at most one summary card. */
public final class NotificationBurst {
    public static final int INDIVIDUAL_LIMIT = 3;
    public static final long QUIET_MILLIS = 3_000, STALE_MILLIS = 30_000;
    public enum Action { INDIVIDUAL, SUMMARIZE, UPDATE_SUMMARY }
    public record Decision(long group, int total, Action action) {}
    private long group, lastReceived;
    private int total;

    public Decision receive(long now) {
        if (total == 0 || now - lastReceived >= QUIET_MILLIS) {
            group++;
            total = 0;
        }
        lastReceived = now;
        if (total < Integer.MAX_VALUE) total++;
        return new Decision(group, total, total <= INDIVIDUAL_LIMIT ? Action.INDIVIDUAL
                : total == INDIVIDUAL_LIMIT + 1 ? Action.SUMMARIZE : Action.UPDATE_SUMMARY);
    }

    public void clear() { group++; total = 0; }
    public static boolean stale(long receivedAt, long now) { return now - receivedAt >= STALE_MILLIS; }
}
