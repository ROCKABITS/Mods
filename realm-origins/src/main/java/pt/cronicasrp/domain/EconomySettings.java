package pt.cronicasrp.domain;

import java.util.List;
import java.util.random.RandomGenerator;

public record EconomySettings(List<Band> bands) {
    public record Band(String name, int weight, int minimum, int maximum) {
        public Band {
            if (weight < 0 || weight > 1000 || minimum < 0 || maximum < minimum || maximum > 100_000_000) throw new IllegalArgumentException("Configuração económica inválida.");
        }
    }
    public record StartingMoney(String origin, long amount) {}
    public EconomySettings {
        bands = List.copyOf(bands);
        if (bands.isEmpty() || bands.stream().mapToInt(Band::weight).sum() <= 0) throw new IllegalArgumentException("É necessária pelo menos uma origem ativa.");
    }
    public static EconomySettings defaults() {
        return new EconomySettings(List.of(
                new Band("Desamparada", 8, 20, 40), new Band("Humilde", 24, 50, 80),
                new Band("Comum", 36, 100, 140), new Band("Confortável", 18, 170, 220),
                new Band("Abastada", 9, 260, 340), new Band("Nobre", 4, 450, 600),
                new Band("Herdeira", 1, 800, 1000)));
    }
    public StartingMoney roll(RandomGenerator random) {
        int ticket = random.nextInt(bands.stream().mapToInt(Band::weight).sum());
        for (Band band : bands) {
            if (ticket < band.weight()) return new StartingMoney(band.name(), random.nextInt(band.minimum(), band.maximum() + 1));
            ticket -= band.weight();
        }
        throw new IllegalStateException("Falha no sorteio da origem.");
    }
}
