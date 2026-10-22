package pt.cronicasrp.domain;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignatureDrawingTest {
    @Test void bitPackingPreservesAllPixelsAndEraseDoesNotDamageNeighbours() {
        byte[] ink = new byte[SignatureDrawing.BYTES];
        for (int y = 0; y < SignatureDrawing.HEIGHT; y++) for (int x = 0; x < SignatureDrawing.WIDTH; x++)
            SignatureDrawing.pixel(ink, x, y, (x + y) % 3 == 0);
        byte[] restored = SignatureDrawing.decode(SignatureDrawing.validate(SignatureDrawing.encode(ink), true));
        assertArrayEquals(ink, restored);
        SignatureDrawing.pixel(restored, 0, 0, false);
        assertFalse(SignatureDrawing.pixel(restored, 0, 0)); assertTrue(SignatureDrawing.pixel(restored, 3, 0));
        SignatureDrawing.pixel(restored, -1, 0, true); SignatureDrawing.pixel(restored, 256, 95, true);
        assertEquals(SignatureDrawing.BYTES, restored.length);
    }
    @Test void oversizedInvalidOrForeignImagesAreRejected() {
        for (String value : Arrays.asList(null, "data:image/png;base64,AAAA", "ink2:" + "A".repeat(4096), "ink1:" + "!".repeat(4096), "ink1:" + "A".repeat(5000)))
            assertThrows(IllegalArgumentException.class, () -> SignatureDrawing.validate(value, true));
        assertEquals("", SignatureDrawing.validate("", false));
        assertThrows(IllegalArgumentException.class, () -> SignatureDrawing.validate("", true));
    }
}
