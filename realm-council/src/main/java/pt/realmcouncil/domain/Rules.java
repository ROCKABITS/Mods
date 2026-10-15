package pt.realmcouncil.domain;

public final class Rules {
    private Rules() {}
    public static String text(String value, int min, int max, boolean multiline) {
        if (value == null) throw new RuleException("text");
        String result=value.strip();
        if (result.length()<min || result.length()>max || result.codePoints().anyMatch(c -> c=='§' || Character.isISOControl(c) && !(multiline && c=='\n'))) throw new RuleException("text");
        return result;
    }
    public static long duration(long minutes) {
        if (minutes<1 || minutes>525600) throw new RuleException("duration");
        return Math.multiplyExact(minutes,60000);
    }
    public static void require(boolean condition,String code) { if(!condition)throw new RuleException(code); }
    public static final class RuleException extends IllegalArgumentException {
        public final String code;
        public RuleException(String code){super(code);this.code=code;}
    }
}
