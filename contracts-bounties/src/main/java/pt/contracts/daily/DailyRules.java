package pt.contracts.daily;

import java.util.*;

/** Prices are cents, fiscal values are basis points, and all clocks are real UTC milliseconds. */
public final class DailyRules {
    public static final long DAY=86_400_000L;
    public enum Category { MINING, COMBAT, FARMING, FISHING, EXPLORATION, DELIVERY }
    public enum Difficulty { EASY, MEDIUM, HARD, ALL }
    public enum State { AVAILABLE, ACTIVE, COMPLETED, PENDING, PAID, EXPIRED }
    public enum Validation { NATURAL_BLOCK, NATURAL_KILL, MATURE_CROP, ROD_CATCH, NEW_BIOME, NEW_STRUCTURE, CONSUME_ITEMS }
    public record Range(long minimum,long maximum) {
        public Range { if(minimum<1||maximum<minimum||maximum>100_000_000L)throw new IllegalArgumentException("Invalid reward range"); }
    }
    public static final class Settings {
        public int easyCount=2,mediumCount=2,hardCount=1,resetMinute=0;
        public long easyMin=1000,easyMax=2500,mediumMin=3000,mediumMax=6000,hardMin=7500,hardMax=12000,dailyCap=30000,allBonus=0;
        public int taxBps=0,taxCapBps=2500,bonusCapBps=2000,reputationLimit=3,easyReputation=0,mediumReputation=1,hardReputation=2;
        public boolean emeraldFallback=false,progressNotices=false;
        public Set<Category> enabled=new HashSet<>(Set.of(Category.values()));
        public Map<Category,Integer> bonuses=new EnumMap<>(Category.class);
        public Settings(){for(var c:Category.values())bonuses.put(c,0);}
        public Range range(Difficulty d){return switch(d){case EASY->new Range(easyMin,easyMax);case MEDIUM->new Range(mediumMin,mediumMax);case HARD,ALL->new Range(hardMin,hardMax);};}
        public int reputation(Difficulty d){return switch(d){case EASY->easyReputation;case MEDIUM->mediumReputation;case HARD->hardReputation;case ALL->0;};}
        public List<Difficulty> distribution(){List<Difficulty> result=new ArrayList<>();for(int i=0;i<easyCount;i++)result.add(Difficulty.EASY);for(int i=0;i<mediumCount;i++)result.add(Difficulty.MEDIUM);for(int i=0;i<hardCount;i++)result.add(Difficulty.HARD);return result;}
        public void validate(){
            if(easyCount<0||mediumCount<0||hardCount<0||easyCount>5||mediumCount>5||hardCount>5||easyCount+mediumCount+hardCount!=5||resetMinute<0||resetMinute>=1440
                    ||taxCapBps<0||taxCapBps>10000||taxBps<0||taxBps>taxCapBps||bonusCapBps<0||bonusCapBps>10000
                    ||reputationLimit<0||reputationLimit>100||easyReputation<0||mediumReputation<0||hardReputation<0||Math.max(easyReputation,Math.max(mediumReputation,hardReputation))>100
                    ||dailyCap<1||dailyCap>1_000_000_000L||allBonus<0||allBonus>dailyCap||enabled==null||enabled.isEmpty()||enabled.contains(null)||bonuses==null||bonuses.size()!=Category.values().length)throw new IllegalArgumentException("Invalid daily policy");
            long required=0;for(var d:distribution())required=Math.addExact(required,range(d).minimum());
            if(dailyCap<required)throw new IllegalArgumentException("Daily cap is below five minimum rewards");
            for(var c:Category.values()){Integer b=bonuses.get(c);if(b==null||b<0||b>bonusCapBps)throw new IllegalArgumentException("Invalid category bonus");}
            if(emeraldFallback&&(dailyCap%100!=0||allBonus%100!=0||easyMin%100!=0||easyMax%100!=0||mediumMin%100!=0||mediumMax%100!=0||hardMin%100!=0||hardMax%100!=0))throw new IllegalArgumentException("Emerald fallback requires whole-euro reward bounds");
        }
        public long day(long now){return Math.floorDiv(now-resetMinute*60_000L,DAY);}
        public long nextReset(long now){return Math.addExact(Math.multiplyExact(day(now)+1,DAY),resetMinute*60_000L);}
    }
    public record Template(String id,String title,Category category,Difficulty difficulty,String target,int quantity,
                           long reward,int weight,Set<String> mods,Set<String> dimensions,Set<String> biomes,boolean enabled,Validation validation) {
        public Template {
            identifier(id);Objects.requireNonNull(category);Objects.requireNonNull(difficulty);
            if(difficulty==Difficulty.ALL||title==null||title.isBlank()||title.length()>120||title.codePoints().anyMatch(Character::isISOControl)
                    ||target==null||target.isBlank()||target.length()>180||quantity<1||quantity>1_000_000||reward< -1||reward>100_000_000L||weight<1||weight>10000)throw new IllegalArgumentException("Invalid quest template");
            if(!target.startsWith("*"))identifier(target.startsWith("#")?target.substring(1):target);
            else if(!Set.of("*hostile","*fish","*treasure","*new_biome","*new_structure").contains(target))throw new IllegalArgumentException("Unknown quest selector");
            mods=Set.copyOf(Objects.requireNonNull(mods));dimensions=Set.copyOf(Objects.requireNonNull(dimensions));biomes=Set.copyOf(Objects.requireNonNull(biomes));
            if(mods.size()>16||dimensions.size()>16||biomes.size()>64)throw new IllegalArgumentException("Too many template requirements");
            dimensions.forEach(DailyRules::identifier);biomes.forEach(DailyRules::identifier);
            for(String mod:mods)if(!mod.matches("[a-z0-9_]{1,64}"))throw new IllegalArgumentException("Invalid mod requirement");
            if(validation==null)validation=switch(category){case MINING->Validation.NATURAL_BLOCK;case COMBAT->Validation.NATURAL_KILL;case FARMING->Validation.MATURE_CROP;case FISHING->Validation.ROD_CATCH;case DELIVERY->Validation.CONSUME_ITEMS;case EXPLORATION->target.equals("*new_structure")?Validation.NEW_STRUCTURE:Validation.NEW_BIOME;};
            boolean valid=switch(category){case MINING->validation==Validation.NATURAL_BLOCK;case COMBAT->validation==Validation.NATURAL_KILL;case FARMING->validation==Validation.MATURE_CROP;case FISHING->validation==Validation.ROD_CATCH;case DELIVERY->validation==Validation.CONSUME_ITEMS;case EXPLORATION->validation==Validation.NEW_BIOME||validation==Validation.NEW_STRUCTURE;};
            if(!valid)throw new IllegalArgumentException("Category and validator do not match");
        }
        public String objectiveKey(){return category+":"+validation+":"+target;}
    }
    public static String identifier(String value){if(value==null||value.length()>180||!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Invalid registry ID");return value;}
    public static long tax(long cents,int bps){if(cents<0||cents>1_000_000_000L||bps<0||bps>10000)throw new IllegalArgumentException("Invalid tax");return cents*bps/10000;}
    public static long tax(long cents,int bps,String currency){return switch(currency){case "EMERALD"->tax(cents/100,bps)*100;case "CHRONICLES"->tax(cents,bps);default->throw new IllegalArgumentException("Invalid daily currency");};}
    public static UUID key(String value){return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public static void jsonEnvelope(String json,int limit){
        if(json==null||json.length()>limit||!json.stripLeading().startsWith("{"))throw new IllegalArgumentException("Invalid daily JSON envelope");
        int depth=0,nodes=0;boolean quoted=false,escaped=false;
        for(int i=0;i<json.length();i++){char c=json.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;}else if(c=='\"')quoted=true;else if(c=='{'||c=='['){if(++depth>16||++nodes>1024)throw new IllegalArgumentException("Daily JSON is too complex");}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Unbalanced daily JSON");}}
        if(quoted||depth!=0)throw new IllegalArgumentException("Unbalanced daily JSON");
    }
}
