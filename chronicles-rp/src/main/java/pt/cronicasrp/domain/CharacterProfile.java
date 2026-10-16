package pt.cronicasrp.domain;

public record CharacterProfile(boolean created, String name, int age, String race,
                               String description, String signature, String origin,
                               long initialBalance, long balance, String signatureDrawing, RpProgress progress) {
    public CharacterProfile { Money.checked(initialBalance); Money.checked(balance); }
    public CharacterProfile(boolean created, String name, int age, String race,
                            String description, String signature, String origin,
                            long initialBalance, long balance, String signatureDrawing) {
        this(created, name, age, race, description, signature, origin, initialBalance, balance, signatureDrawing, RpProgress.EMPTY);
    }
    // Profiles from 0.1 retain their identity, handwritten name and money.
    public CharacterProfile(boolean created, String name, int age, String race,
                            String description, String signature, String origin,
                            long initialBalance, long balance) {
        this(created, name, age, race, description, signature, origin, initialBalance, balance, "");
    }
    public static CharacterProfile empty() {
        return new CharacterProfile(false, "", 0, "", "", "", "", 0, 0);
    }
    public CharacterProfile withBalance(long value) {
        if (value < 0 || value > CharacterRules.MAX_BALANCE) throw new IllegalArgumentException("O saldo ultrapassa o limite permitido.");
        return new CharacterProfile(created, name, age, race, description, signature, origin, initialBalance, value, signatureDrawing, progress);
    }
    public CharacterProfile withRace(String value) {
        if (value == null || value.isBlank() || value.length() > CharacterRules.RACE_LIMIT)
            throw new IllegalArgumentException("Escolhe uma raça válida.");
        return new CharacterProfile(created, name, age, value, description, signature, origin, initialBalance, balance, signatureDrawing, progress);
    }
    public CharacterProfile withProgress(RpProgress value) {
        if (!created) throw new IllegalArgumentException("Cria primeiro a tua personagem.");
        java.util.Objects.requireNonNull(value);
        return new CharacterProfile(created, name, age, race, description, signature, origin, initialBalance, balance, signatureDrawing, value);
    }
}
