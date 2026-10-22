package pt.cronicasrp.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class ProfileStorage {
    private ProfileStorage() {}
    private static final Codec<RpProgress.Medal> MEDAL_CODEC = Codec.STRING.comapFlatMap(id -> {
        try { return DataResult.success(RpProgress.Medal.fromId(id)); }
        catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
    }, RpProgress.Medal::id);
    public static final Codec<RpProgress> PROGRESS_CODEC = RecordCodecBuilder.<RpProgress>create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("occupation", "Aventureiro").forGetter(RpProgress::occupation),
            Codec.intRange(0, 100).optionalFieldOf("reputation", 0).forGetter(RpProgress::reputation),
            Codec.intRange(0, RpProgress.MAX_COUNTER).optionalFieldOf("missions", 0).forGetter(RpProgress::missions),
            Codec.intRange(0, RpProgress.MAX_COUNTER).optionalFieldOf("medals", 0).forGetter(RpProgress::medals),
            MEDAL_CODEC.optionalFieldOf("bestMedal", RpProgress.Medal.NONE).forGetter(RpProgress::bestMedal)
    ).apply(instance, RpProgress::new));
    private record StoredProfile(boolean created, String name, int age, String race, String description,
                                 String signature, String origin, long initialBalance, long balance,
                                 String signatureDrawing, RpProgress progress, int moneyVersion) {
        CharacterProfile profile() {
            if (moneyVersion != 0 && moneyVersion != 1) throw new IllegalArgumentException("Versão de dinheiro desconhecida.");
            return new CharacterProfile(created, name, age, race, description, signature, origin,
                    moneyVersion == 0 ? Money.fromEuros(initialBalance) : initialBalance,
                    moneyVersion == 0 ? Money.fromEuros(balance) : balance, signatureDrawing, progress);
        }
        static StoredProfile current(CharacterProfile p) {
            return new StoredProfile(p.created(), p.name(), p.age(), p.race(), p.description(), p.signature(),
                    p.origin(), p.initialBalance(), p.balance(), p.signatureDrawing(), p.progress(), 1);
        }
    }
    private static final Codec<StoredProfile> STORED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("created", false).forGetter(StoredProfile::created),
            Codec.STRING.optionalFieldOf("name", "").forGetter(StoredProfile::name),
            Codec.INT.optionalFieldOf("age", 0).forGetter(StoredProfile::age),
            Codec.STRING.optionalFieldOf("race", "").forGetter(StoredProfile::race),
            Codec.STRING.optionalFieldOf("description", "").forGetter(StoredProfile::description),
            Codec.STRING.optionalFieldOf("signature", "").forGetter(StoredProfile::signature),
            Codec.STRING.optionalFieldOf("origin", "").forGetter(StoredProfile::origin),
            Codec.LONG.optionalFieldOf("initialBalance", 0L).forGetter(StoredProfile::initialBalance),
            Codec.LONG.optionalFieldOf("balance", 0L).forGetter(StoredProfile::balance),
            Codec.STRING.optionalFieldOf("signatureDrawing", "").forGetter(StoredProfile::signatureDrawing),
            PROGRESS_CODEC.optionalFieldOf("progress", RpProgress.EMPTY).forGetter(StoredProfile::progress),
            Codec.INT.optionalFieldOf("moneyVersion", 0).forGetter(StoredProfile::moneyVersion)
    ).apply(instance, StoredProfile::new));
    public static final Codec<CharacterProfile> CODEC = STORED_CODEC.flatXmap(stored -> {
        try { return DataResult.success(stored.profile()); }
        catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
    }, profile -> DataResult.success(StoredProfile.current(profile)));
}
