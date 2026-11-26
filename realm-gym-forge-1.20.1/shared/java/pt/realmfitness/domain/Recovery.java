package pt.realmfitness.domain;

/** One shared in-game fatigue recovery per training day, for both supplement foods. */
public final class Recovery {
    private Recovery() {}

    public static final class Config {
        public boolean enabled=true;
        public double fatigueReduction=.10;
        public boolean dynamicNutrition;
        public double proteinBonusMaximum=.05;

        public void validate(){
            if(!validFraction(fatigueReduction)||!validFraction(proteinBonusMaximum))
                throw new IllegalArgumentException("fitness.invalid_settings");
        }

        private static boolean validFraction(double value){
            return Double.isFinite(value)&&value>=0&&value<=1;
        }
    }

    public enum Status { DISABLED, NEEDS_SET, NO_FATIGUE, READY, USED }

    public record Snapshot(Status status,Exercise attribute,double reduction,long resetsAt) {}
    public record Result(Exercise attribute,double before,double after) {}

    public static Snapshot snapshot(FitnessLedger.Athlete athlete,FitnessLedger.Settings settings,long now){
        long day=settings.day(now);
        Exercise attribute=athlete.latestTrainedAttribute;
        double reduction=settings.supplements.fatigueReduction;
        Status status=!settings.supplements.enabled||reduction==0?Status.DISABLED
            :athlete.supplementDay>=day?Status.USED
            :attribute==null?Status.NEEDS_SET
            :athlete.fatigue.get(attribute)<=.0001?Status.NO_FATIGUE:Status.READY;
        return new Snapshot(status,attribute,reduction,settings.resetAt(now));
    }

    public static Result consume(FitnessLedger.Athlete athlete,FitnessLedger.Settings settings,long now,double protein){
        Snapshot snapshot=snapshot(athlete,settings,now);
        if(snapshot.status()!=Status.READY)return null;
        double bonus=settings.supplements.dynamicNutrition&&Double.isFinite(protein)
            ? Math.clamp(protein,0,1)*settings.supplements.proteinBonusMaximum : 0;
        Exercise attribute=snapshot.attribute();
        double before=athlete.fatigue.get(attribute);
        double after=Math.max(0,before-snapshot.reduction()-bonus);
        athlete.fatigue.put(attribute,after);
        athlete.supplementDay=settings.day(now);
        return new Result(attribute,before,after);
    }
}
