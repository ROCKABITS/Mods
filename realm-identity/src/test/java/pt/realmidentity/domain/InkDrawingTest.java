package pt.realmidentity.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class InkDrawingTest {
    @Test
    void canonicalDimensionsAndLengthMatchChroniclesProtocol() {
        assertEquals(256, InkDrawing.WIDTH);
        assertEquals(96, InkDrawing.HEIGHT);
        assertEquals(3072, InkDrawing.BYTES);
        assertEquals("ink1:", InkDrawing.PREFIX);
        assertEquals(4101, InkDrawing.MAX_LENGTH);
        assertEquals(InkDrawing.MAX_LENGTH, encoded(new byte[InkDrawing.BYTES]).length());
    }

    @Test
    void roundTripPreservesEveryPixelWithLeastSignificantBitFirst() {
        byte[] pixels = new byte[InkDrawing.BYTES];
        new Random(192619).nextBytes(pixels);
        InkDrawing drawing = InkDrawing.decode(encoded(pixels));

        assertArrayEquals(pixels, drawing.pixels());
        assertEquals(encoded(pixels), drawing.encode());
        for (int y = 0; y < InkDrawing.HEIGHT; y++) {
            for (int x = 0; x < InkDrawing.WIDTH; x++) {
                int bit = y * InkDrawing.WIDTH + x;
                assertEquals((pixels[bit / 8] & (1 << (bit % 8))) != 0, drawing.isSet(x, y));
            }
        }
    }

    @Test
    void byteAndRowBoundariesDoNotShiftPixels() {
        byte[] pixels = new byte[InkDrawing.BYTES];
        pixels[0] = (byte) 0x81;
        pixels[1] = 1;
        pixels[31] = (byte) 0x80;
        pixels[32] = 1;
        pixels[InkDrawing.BYTES - 1] = (byte) 0x80;
        InkDrawing drawing = InkDrawing.decode(encoded(pixels));

        assertTrue(drawing.isSet(0, 0));
        assertFalse(drawing.isSet(1, 0));
        assertTrue(drawing.isSet(7, 0));
        assertTrue(drawing.isSet(8, 0));
        assertTrue(drawing.isSet(255, 0));
        assertTrue(drawing.isSet(0, 1));
        assertTrue(drawing.isSet(255, 95));
    }

    @Test
    void emptyValueIsAbsentButAnEncodedBlankDrawingIsPresent() {
        assertTrue(InkDrawing.parse("").isEmpty());
        String blank = encoded(new byte[InkDrawing.BYTES]);
        InkDrawing drawing = InkDrawing.parse(blank).orElseThrow();
        assertFalse(drawing.isSet(0, 0));
        assertFalse(drawing.isSet(255, 95));
        assertEquals(blank, drawing.encode());
        assertThrows(IllegalArgumentException.class, () -> InkDrawing.decode(""));
        assertThrows(IllegalArgumentException.class, () -> InkDrawing.decode(null));
        assertThrows(IllegalArgumentException.class, () -> InkDrawing.parse(null));
    }

    @Test
    void malformedTruncatedOversizedAndForeignValuesAreRejected() {
        String blank = encoded(new byte[InkDrawing.BYTES]);
        for (String malformed : List.of(
                "data:image/png;base64," + "A".repeat(4096),
                "ink2:" + "A".repeat(4096),
                "chronink1:" + "A".repeat(4096),
                blank.substring(0, blank.length() - 1),
                blank + "A",
                InkDrawing.PREFIX + "A".repeat(5000),
                InkDrawing.PREFIX + "!".repeat(4096),
                InkDrawing.PREFIX + "A".repeat(4095) + "\n",
                InkDrawing.PREFIX + "\0" + "A".repeat(4095),
                InkDrawing.PREFIX + "A".repeat(4095) + "\u00e9")) {
            assertThrows(IllegalArgumentException.class, () -> InkDrawing.decode(malformed));
            assertThrows(IllegalArgumentException.class, () -> InkDrawing.parse(malformed));
        }
    }

    @Test
    void alternatePaddingAndUrlSafeBase64AreRejected() {
        String blank = encoded(new byte[InkDrawing.BYTES]);
        assertThrows(IllegalArgumentException.class, () -> InkDrawing.decode(blank + "="));
        assertThrows(IllegalArgumentException.class,
                () -> InkDrawing.decode(blank.substring(0, blank.length() - 2) + "=="));

        byte[] filled = new byte[InkDrawing.BYTES];
        Arrays.fill(filled, (byte) 0xff);
        String canonical = encoded(filled);
        assertEquals(canonical, InkDrawing.decode(canonical).encode());
        assertThrows(IllegalArgumentException.class,
                () -> InkDrawing.decode(canonical.replace('/', '_')));
    }

    @Test
    void pixelDataIsDefensivelyCopied() {
        byte[] pixels = new byte[InkDrawing.BYTES];
        pixels[0] = 1;
        String canonical = encoded(pixels);
        InkDrawing drawing = InkDrawing.decode(canonical);
        byte[] exposed = drawing.pixels();
        exposed[0] = 0;
        exposed[1] = (byte) 0xff;

        assertTrue(drawing.isSet(0, 0));
        assertFalse(drawing.isSet(8, 0));
        assertEquals(canonical, drawing.encode());
        assertArrayEquals(pixels, drawing.pixels());
        assertNotSame(exposed, drawing.pixels());
        assertEquals(drawing, InkDrawing.decode(canonical));
        assertEquals(drawing.hashCode(), InkDrawing.decode(canonical).hashCode());
    }

    @Test
    void coordinatesOutsideTheCanvasAreRejectedBeforeIndexing() {
        InkDrawing drawing = InkDrawing.decode(encoded(new byte[InkDrawing.BYTES]));
        for (int[] coordinate : List.of(
                new int[]{-1, 0}, new int[]{256, 0}, new int[]{0, -1}, new int[]{0, 96},
                new int[]{Integer.MIN_VALUE, 0}, new int[]{0, Integer.MAX_VALUE})) {
            assertThrows(IndexOutOfBoundsException.class,
                    () -> drawing.isSet(coordinate[0], coordinate[1]));
        }
    }

    private static String encoded(byte[] pixels) {
        return "ink1:" + Base64.getEncoder().encodeToString(pixels);
    }
}
