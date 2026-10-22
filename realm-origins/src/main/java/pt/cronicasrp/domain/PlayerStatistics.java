package pt.cronicasrp.domain;

/** Snapshot of the player's real vanilla statistics in this world. */
public record PlayerStatistics(long playTicks, int creaturesDefeated, int bossesDefeated) {
    public static final PlayerStatistics EMPTY = new PlayerStatistics(0, 0, 0);
    public PlayerStatistics {
        if (playTicks < 0 || creaturesDefeated < 0 || bossesDefeated < 0) throw new IllegalArgumentException("Estatísticas negativas.");
    }
    public String playTime() {
        long minutes = playTicks / 1_200;
        return minutes / 60 + "h " + String.format(java.util.Locale.ROOT, "%02d", minutes % 60) + "min";
    }
}
