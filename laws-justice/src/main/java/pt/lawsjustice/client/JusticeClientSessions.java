package pt.lawsjustice.client;

import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Late periodic snapshots may update an open menu, but may never reopen a deliberately closed session. */
public final class JusticeClientSessions {
    private static final LinkedHashSet<UUID> CLOSED=new LinkedHashSet<>();
    private JusticeClientSessions(){}
    public static void close(JsonObject state){UUID id=id(state);if(id==null)return;CLOSED.add(id);while(CLOSED.size()>256)CLOSED.remove(CLOSED.iterator().next());}
    public static boolean accepts(JsonObject state){UUID id=id(state);return id==null||!CLOSED.contains(id);}
    private static UUID id(JsonObject state){try{return state.has("session")?UUID.fromString(state.get("session").getAsString()):null;}catch(RuntimeException invalid){return null;}}
}
