package pt.contracts.daily;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyJsonTest {
    @Test void boundedNativePayloadsAcceptEscapedStringsAndNestedSettings() {
        assertDoesNotThrow(()->DailyRules.jsonEnvelope("{\"title\":\"Quest \\\"[\\\"\",\"settings\":{\"bonuses\":[1,2,3]}}",16384));
    }
    @Test void deeplyNestedClientDataIsRejectedBeforeJsonParsing() {
        assertThrows(IllegalArgumentException.class,()->DailyRules.jsonEnvelope("{\"v\":"+"[".repeat(17)+"]".repeat(17)+"}",16384));
    }
    @Test void oversizedPayloadsAndTooManyContainersAreRejected() {
        assertThrows(IllegalArgumentException.class,()->DailyRules.jsonEnvelope(" ".repeat(257),256));
        assertThrows(IllegalArgumentException.class,()->DailyRules.jsonEnvelope("{\"v\":["+"{},".repeat(1024)+"{}]}",16384));
    }
    @Test void truncatedJsonEnvelopesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->DailyRules.jsonEnvelope("{\"title\":\"unfinished",16384));
        assertThrows(IllegalArgumentException.class,()->DailyRules.jsonEnvelope("{\"settings\":{}",16384));
    }
}
