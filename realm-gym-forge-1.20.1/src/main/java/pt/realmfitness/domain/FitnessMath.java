package pt.realmfitness.domain;

/** Java 17 equivalent of the clamp calls used by the shared Java 21 rules. */
public final class FitnessMath {
    private FitnessMath() {}
    public static int clamp(int value,int min,int max){if(min>max)throw new IllegalArgumentException("Invalid bounds");return Math.max(min,Math.min(max,value));}
    public static float clamp(float value,float min,float max){if(!(min<=max))throw new IllegalArgumentException("Invalid bounds");return Math.max(min,Math.min(max,value));}
    public static double clamp(double value,double min,double max){if(!(min<=max))throw new IllegalArgumentException("Invalid bounds");return Math.max(min,Math.min(max,value));}
}
