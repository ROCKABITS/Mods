package pt.realmfitness.domain;

import java.util.Locale;

/** Equipment IDs are append-only; variants share one of the six permanent attributes. */
public enum Exercise {
    BENCH(true,.10), RUN(false,.08), BOX(false,.08), PULL(false,.20), ROW(false,.15), SQUAT(true,.15),
    LEG_PRESS(true,.15), TRICEPS(true,.10);
    public final boolean weighted;
    public final double maximumBonus;
    Exercise(boolean weighted,double bonus){this.weighted=weighted;this.maximumBonus=bonus;}
    public String id(){return name().toLowerCase(Locale.ROOT);}
    public Exercise attribute(){return switch(this){case LEG_PRESS->SQUAT;case TRICEPS->BENCH;default->this;};}
    public static Exercise[] attributes(){return new Exercise[]{BENCH,RUN,BOX,PULL,ROW,SQUAT};}
    public int minimumLoad(){return this==LEG_PRESS?40:this==TRICEPS?5:weighted?20:1;}
    public int loadStep(){return this==LEG_PRESS?10:weighted?5:1;}
    public int maximumLoad(int level){int n=Math.clamp(level,0,20);return this==TRICEPS?5+n/2*5:weighted?minimumLoad()+n*loadStep():1+n/4;}
    public void checkLoad(int load,int level){if(load<minimumLoad()||load>maximumLoad(level)||weighted&&(load-minimumLoad())%loadStep()!=0)throw new IllegalArgumentException("fitness.load_locked");}
}
