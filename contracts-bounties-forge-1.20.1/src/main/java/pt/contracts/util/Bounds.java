package pt.contracts.util;
/** Java 17 equivalents of the bounded values used by the 1.21 interface and ledger. */
public final class Bounds {
    private Bounds() {}
    public static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    public static long clamp(long value,long min,long max){return Math.max(min,Math.min(max,value));}
    public static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    public static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}
