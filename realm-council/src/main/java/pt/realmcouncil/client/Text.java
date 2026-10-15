package pt.realmcouncil.client;
import net.minecraft.network.chat.Component;
import pt.realmcouncil.domain.CouncilView;
public final class Text {
    public static Component c(String key,Object... args){return Component.translatable("gui.realmcouncil."+key,args);}
    public static String s(String key,Object... args){return c(key,args).getString();}
    public static String race(String value){return switch(value){case "Humano"->s("race.human");case "Elfo"->s("race.elf");case "Anão"->s("race.dwarf");case "Orc"->s("race.orc");default->value;};}
    public static String phase(CouncilView view){return view.election()==null?s("phase.none"):s("phase."+view.election().phase().toLowerCase(java.util.Locale.ROOT));}
    public static String date(long millis){return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));}
    public static String remaining(long deadline,long now){long total=Math.max(0,(deadline-now)/1000);long days=total/86400,hours=total/3600%24,minutes=total/60%60;return days>0?s("time.days",days,hours):s("time.hours",hours,minutes);}
}
