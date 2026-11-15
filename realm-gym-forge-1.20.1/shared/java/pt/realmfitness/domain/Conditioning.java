package pt.realmfitness.domain;

/** Optional physical condition, independent of lifetime experience and equipment unlocks. */
public final class Conditioning {
    private Conditioning() {}
    private static final long DAY=86_400_000L;

    public static final class Config {
        public boolean enabled=false, countOfflineTime=true;
        public int graceDays=7, setsRequired=2;
        public double lossPerDay=5, minimumPercent=60, recoveryPerDay=20;
        public void validate() {
            if(graceDays<0||graceDays>365||setsRequired<1||setsRequired>24
                ||!Double.isFinite(lossPerDay)||lossPerDay<0||lossPerDay>100
                ||!Double.isFinite(minimumPercent)||minimumPercent<0||minimumPercent>100
                ||!Double.isFinite(recoveryPerDay)||recoveryPerDay<0||recoveryPerDay>100)
                throw new IllegalArgumentException("fitness.invalid_settings");
        }
        public boolean sameSchedule(Config other) {
            return graceDays==other.graceDays&&lossPerDay==other.lossPerDay
                &&minimumPercent==other.minimumPercent&&countOfflineTime==other.countOfflineTime;
        }
    }

    /** Persist elapsed training time; the live session flag deliberately does not survive reloads. */
    public static final class Clock {
        public long elapsed, observedAt=-1;
        public transient boolean online;
        public void advance(long now,Config config) {
            if(observedAt>=0&&config.enabled&&(config.countOfflineTime||online)) {
                long delta=Math.max(0,now-observedAt);
                elapsed=delta>Long.MAX_VALUE-elapsed?Long.MAX_VALUE:elapsed+delta;
            }
            observedAt=Math.max(observedAt,now);
        }
        public void validate() {
            if(elapsed<0||observedAt< -1)throw new IllegalArgumentException("fitness.corrupt_data");
        }
    }

    public static final class State {
        public double percent=100;
        public long maintainedAt, lossDays, setsDay=Long.MIN_VALUE, recoveredDay=Long.MIN_VALUE;
        public int sets;
        public State() {}
        public State(long clock) { maintainedAt=clock; }
        public void refresh(long clock,Config config) {
            long due=Math.max(0,Math.max(0,clock-maintainedAt)/DAY-config.graceDays);
            if(due>lossDays)percent=Math.max(config.minimumPercent,percent-(due-lossDays)*config.lossPerDay);
            percent=Math.max(config.minimumPercent,Math.min(100,percent));
            lossDays=Math.max(lossDays,due);
        }
        public void train(long clock,long day,Config config) {
            refresh(clock,config);
            if(day>setsDay){setsDay=day;sets=0;}
            sets=Math.min(config.setsRequired,sets+1);
            if(sets>=config.setsRequired&&day>recoveredDay) {
                percent=Math.min(100,percent+config.recoveryPerDay);
                maintainedAt=clock;lossDays=0;recoveredDay=day;
            }
        }
        public long nextLossIn(long clock,Config config) {
            if(percent<=config.minimumPercent||config.lossPerDay==0)return -1;
            // First loss is after the grace period plus one complete day.
            long days=config.graceDays+lossDays+1;
            long elapsed=Math.max(0,clock-maintainedAt);
            return Math.max(0,days*DAY-elapsed);
        }
        public void validate(long clock) {
            if(!Double.isFinite(percent)||percent<0||percent>100||maintainedAt<0||maintainedAt>clock
                ||lossDays<0||lossDays>clock/DAY||sets<0||sets>24)
                throw new IllegalArgumentException("fitness.corrupt_data");
        }
    }
}
