package pt.contracts.daily;

import java.util.*;

/** Cosmetics only. A finale waits behind individual celebrations for its own daily set. */
public final class CelebrationQueue<T> {
    public record Event<T>(UUID id,long day,boolean finale,T value){}
    private final List<Event<T>> pending=new ArrayList<>();
    private final LinkedHashSet<UUID> seen=new LinkedHashSet<>();
    public boolean offer(Event<T> event){
        if(seen.contains(event.id())||pending.size()>=32)return false;seen.add(event.id());while(seen.size()>512)seen.removeFirst();
        int index=pending.size();
        if(!event.finale())for(int i=0;i<pending.size();i++)if(pending.get(i).finale()&&pending.get(i).day()==event.day()){index=i;break;}
        pending.add(index,event);return true;
    }
    public Event<T> poll(){return pending.isEmpty()?null:pending.removeFirst();}
    public int size(){return pending.size();}
    public void clear(){pending.clear();seen.clear();}
}
