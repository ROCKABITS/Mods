package pt.realmidentity.domain;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/** Immutable, fixed-size monochrome ink in the Chronicles signature wire format. */
public final class InkDrawing {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 96;
    public static final int BYTES = WIDTH * HEIGHT / Byte.SIZE;
    public static final String PREFIX = "ink1:";
    public static final int MAX_LENGTH = PREFIX.length() + BYTES / 3 * 4;

    private final byte[] pixels;

    private InkDrawing(byte[] pixels) {
        this.pixels = pixels;
    }

    /** Decodes exactly one canonical drawing; an empty value is handled by {@link #parse}. */
    public static InkDrawing decode(String value) {
        if (value == null || value.length() != MAX_LENGTH || !value.startsWith(PREFIX)) {
            throw invalid();
        }
        final byte[] pixels;
        try {
            pixels = Base64.getDecoder().decode(value.substring(PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
        if (pixels.length != BYTES
                || !Base64.getEncoder().encodeToString(pixels).equals(value.substring(PREFIX.length()))) {
            throw invalid();
        }
        return new InkDrawing(pixels);
    }

    /** An empty string means no drawing; a canonical all-zero drawing remains present. */
    public static Optional<InkDrawing> parse(String value) {
        return "".equals(value) ? Optional.empty() : Optional.of(decode(value));
    }

    public String encode() {
        return PREFIX + Base64.getEncoder().encodeToString(pixels);
    }

    /** Pixels run left to right, top to bottom, least significant bit first in each byte. */
    public boolean isSet(int x, int y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            throw new IndexOutOfBoundsException("Pixel is outside the drawing.");
        }
        int index = y * WIDTH + x;
        return (pixels[index / Byte.SIZE] & (1 << (index % Byte.SIZE))) != 0;
    }

    /** Returns a copy so callers cannot change the drawing. */
    public byte[] pixels() {
        return pixels.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof InkDrawing ink && Arrays.equals(pixels, ink.pixels);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pixels);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("The signature drawing is invalid.");
    }
}
