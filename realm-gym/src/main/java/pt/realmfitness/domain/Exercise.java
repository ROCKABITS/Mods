package pt.realmfitness.domain;

import java.util.Locale;

public enum Exercise {
    BENCH(true, .10), RUN(false, .08), BOX(false, .08), PULL(false, .20), ROW(false, .15), SQUAT(true, .15);
    public final boolean weighted;
    public final double maximumBonus;
    Exercise(boolean weighted, double bonus) { this.weighted = weighted; this.maximumBonus = bonus; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public int minimumLoad() { return weighted ? 20 : 1; }
    public int maximumLoad(int level) { return weighted ? 20 + Math.clamp(level, 0, 20) * 5 : 1 + Math.clamp(level, 0, 20) / 4; }
    public void checkLoad(int load, int level) {
        if (load < minimumLoad() || load > maximumLoad(level) || weighted && load % 5 != 0)
            throw new IllegalArgumentException("fitness.load_locked");
    }
}
