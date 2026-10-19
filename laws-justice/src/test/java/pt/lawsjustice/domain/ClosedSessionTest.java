package pt.lawsjustice.domain;
import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pt.lawsjustice.client.JusticeClientSessions;
class ClosedSessionTest {
 @Test void anInFlightMutationSnapshotCannotReopenAClosedPhysicalSession(){var old=new JsonObject();old.addProperty("session",UUID.randomUUID().toString());old.addProperty("nonce",UUID.randomUUID().toString());assertTrue(JusticeClientSessions.accepts(old));JusticeClientSessions.close(old);var late=old.deepCopy();late.addProperty("nonce",UUID.randomUUID().toString());late.addProperty("version",42);assertFalse(JusticeClientSessions.accepts(late));var reopened=late.deepCopy();reopened.addProperty("session",UUID.randomUUID().toString());assertTrue(JusticeClientSessions.accepts(reopened));}
}
