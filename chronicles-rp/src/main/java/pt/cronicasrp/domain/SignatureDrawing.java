package pt.cronicasrp.domain;

import java.util.Base64;

/** Fixed-size monochrome ink; never accepts image files, arbitrary dimensions or URLs. */
public final class SignatureDrawing {
    public static final int WIDTH = 256, HEIGHT = 96, BYTES = WIDTH * HEIGHT / 8;
    public static final String PREFIX = "ink1:";
    public static final int MAX_LENGTH = PREFIX.length() + BYTES / 3 * 4;
    private SignatureDrawing() {}

    public static byte[] decode(String value) {
        if (value == null) throw new IllegalArgumentException("A assinatura está em falta.");
        if (value.isEmpty()) return new byte[BYTES];
        if (value.length() != MAX_LENGTH || !value.startsWith(PREFIX)) throw invalid();
        try {
            byte[] ink = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (ink.length != BYTES || !encode(ink).equals(value)) throw invalid();
            return ink;
        } catch (IllegalArgumentException ex) { throw invalid(); }
    }

    public static String encode(byte[] ink) {
        if (ink.length != BYTES) throw invalid();
        return PREFIX + Base64.getEncoder().encodeToString(ink);
    }

    public static String validate(String value, boolean required) {
        byte[] ink = decode(value);
        if (!hasInk(ink)) {
            if (required) throw new IllegalArgumentException("Desenha a tua assinatura no pergaminho.");
            return "";
        }
        return encode(ink);
    }

    public static boolean hasInk(byte[] ink) {
        for (byte b : ink) if (b != 0) return true;
        return false;
    }

    public static boolean pixel(byte[] ink, int x, int y) {
        int index = y * WIDTH + x;
        return (ink[index >>> 3] & (1 << (index & 7))) != 0;
    }

    public static void pixel(byte[] ink, int x, int y, boolean draw) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) return;
        int index = y * WIDTH + x, mask = 1 << (index & 7);
        if (draw) ink[index >>> 3] |= (byte) mask;
        else ink[index >>> 3] &= (byte) ~mask;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("O desenho da assinatura não é válido.");
    }
}
