package pt.cronicasrp.domain;

import java.util.List;
import java.util.Objects;

/** RP accomplishments are awarded by the server, independently of money. */
public record RpProgress(String occupation, int reputation, int missions, int medals, Medal bestMedal) {
    public static final int MAX_COUNTER = 1_000_000;
    public static final List<String> OCCUPATIONS = List.of("Aventureiro", "Estudioso", "Guerreiro", "Mago",
            "Guardião", "Caçador", "Ferreiro", "Mercador", "Curandeiro", "Bardo");
    public static final RpProgress EMPTY = new RpProgress("Aventureiro", 0, 0, 0, Medal.NONE);

    public RpProgress {
        if (!OCCUPATIONS.contains(occupation)) throw new IllegalArgumentException("Escolhe uma ocupação válida.");
        if (reputation < 0 || reputation > 100) throw new IllegalArgumentException("A reputação deve estar entre 0 e 100.");
        if (missions < 0 || missions > MAX_COUNTER || medals < 0 || medals > MAX_COUNTER)
            throw new IllegalArgumentException("O número de conquistas ultrapassa o limite permitido.");
        Objects.requireNonNull(bestMedal);
        if ((medals == 0) != (bestMedal == Medal.NONE)) throw new IllegalArgumentException("A melhor medalha não corresponde ao registo.");
    }
    public RpProgress withOccupation(String value) { return new RpProgress(value, reputation, missions, medals, bestMedal); }
    public RpProgress withReputation(int value) { return new RpProgress(occupation, value, missions, medals, bestMedal); }
    public RpProgress completeMissions(int amount) {
        if (amount < 1 || amount > MAX_COUNTER - missions) throw new IllegalArgumentException("O número de missões ultrapassa o limite permitido.");
        return new RpProgress(occupation, reputation, missions + amount, medals, bestMedal);
    }
    public RpProgress award(Medal medal) {
        if (medal == null || medal == Medal.NONE) throw new IllegalArgumentException("Escolhe bronze, prata ou ouro.");
        if (medals == MAX_COUNTER) throw new IllegalArgumentException("O número de medalhas ultrapassa o limite permitido.");
        return new RpProgress(occupation, reputation, missions, medals + 1,
                medal.ordinal() > bestMedal.ordinal() ? medal : bestMedal);
    }
    public String classification() {
        if (reputation == 100) return "Netherite";
        if (reputation >= 80) return "Diamante";
        if (reputation >= 60) return "Ouro";
        if (reputation >= 40) return "Ferro";
        if (reputation >= 20) return "Cobre";
        return "Carvão";
    }
    public enum Medal {
        NONE("Nenhuma", "nenhuma"), BRONZE("Bronze", "bronze"), SILVER("Prata", "prata"), GOLD("Ouro", "ouro");
        private final String label, id;
        Medal(String label, String id) { this.label = label; this.id = id; }
        public String label() { return label; }
        public String id() { return id; }
        public static Medal fromId(String id) {
            for (Medal value : values()) if (value.id.equals(id)) return value;
            throw new IllegalArgumentException("Medalha desconhecida.");
        }
    }
}
