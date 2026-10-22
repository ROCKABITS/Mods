package pt.cronicasrp.domain;

public record CharacterDraft(String name, int age, String race, String description, String signature, String signatureDrawing, String occupation) {
    public CharacterDraft(String name, int age, String race, String description, String signature, String signatureDrawing) {
        this(name, age, race, description, signature, signatureDrawing, "Aventureiro");
    }
    public CharacterDraft(String name, int age, String race, String description, String signature) {
        this(name, age, race, description, signature, "");
    }
}
