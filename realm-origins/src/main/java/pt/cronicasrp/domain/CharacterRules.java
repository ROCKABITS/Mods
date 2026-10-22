package pt.cronicasrp.domain;

import java.util.Set;
import java.util.random.RandomGenerator;

public final class CharacterRules {
    public static final int NAME_LIMIT = 40, DESCRIPTION_LIMIT = 600, SIGNATURE_LIMIT = 60;
    public static final int RACE_LIMIT = 256;
    public static final long MAX_BALANCE = Money.MAX_CENTS;
    public static final Set<String> RACES = Set.of("Humano", "Elfo", "Anão", "Orc");
    private CharacterRules() {}
    public static CharacterProfile create(CharacterProfile current, CharacterDraft draft, EconomySettings settings, RandomGenerator random) {
        return create(current, draft, settings, random, RACES);
    }
    public static CharacterProfile create(CharacterProfile current, CharacterDraft draft, EconomySettings settings, RandomGenerator random, Set<String> allowedRaces) {
        if (current.created()) throw new IllegalArgumentException("A tua personagem já está criada.");
        String name = clean(draft.name(), NAME_LIMIT, false);
        if (name.length() < 2) throw new IllegalArgumentException("O nome precisa de pelo menos 2 caracteres.");
        if (draft.age() < 1 || draft.age() > 999) throw new IllegalArgumentException("A idade deve estar entre 1 e 999.");
        if (draft.race() == null || draft.race().length() > RACE_LIMIT || !allowedRaces.contains(draft.race()))
            throw new IllegalArgumentException("Escolhe uma raça válida.");
        RpProgress progress = RpProgress.EMPTY.withOccupation(draft.occupation());
        String description = clean(draft.description(), DESCRIPTION_LIMIT, true);
        String signature = clean(draft.signature(), SIGNATURE_LIMIT, false);
        if (signature.isBlank()) signature = name;
        String drawing = SignatureDrawing.validate(draft.signatureDrawing(), true);
        EconomySettings.StartingMoney money = settings.roll(random);
        long cents = Money.fromEuros(money.amount());
        return new CharacterProfile(true, name, draft.age(), draft.race(), description, signature, money.origin(), cents, cents, drawing, progress);
    }
    public static CharacterProfile editStory(CharacterProfile current, String description, String signature) {
        return editStory(current, description, signature, current.signatureDrawing());
    }
    public static CharacterProfile editStory(CharacterProfile current, String description, String signature, String signatureDrawing) {
        return editStory(current, description, signature, signatureDrawing, current.progress().occupation());
    }
    public static CharacterProfile editStory(CharacterProfile current, String description, String signature, String signatureDrawing, String occupation) {
        if (!current.created()) throw new IllegalArgumentException("Cria primeiro a tua personagem.");
        String newDescription = clean(description, DESCRIPTION_LIMIT, true);
        String newSignature = clean(signature, SIGNATURE_LIMIT, false);
        if (newSignature.isBlank()) throw new IllegalArgumentException("Preenche a assinatura.");
        String drawing = SignatureDrawing.validate(signatureDrawing, !current.signatureDrawing().isEmpty());
        return new CharacterProfile(true, current.name(), current.age(), current.race(), newDescription, newSignature, current.origin(), current.initialBalance(), current.balance(), drawing, current.progress().withOccupation(occupation));
    }
    private static String clean(String text, int limit, boolean multiline) {
        if (text == null) throw new IllegalArgumentException("Há campos em falta.");
        String value = text.strip();
        if (value.length() > limit) throw new IllegalArgumentException("Um dos textos ultrapassa o limite permitido.");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '§' || (Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\t')))) throw new IllegalArgumentException("O texto contém caracteres não permitidos.");
        }
        return value;
    }
}
