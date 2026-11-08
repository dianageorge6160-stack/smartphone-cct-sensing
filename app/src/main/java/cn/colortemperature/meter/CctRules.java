package cn.colortemperature.meter;

import java.util.Locale;

/** Vendor CCT events are not an Android-standard data layout. Never infer CCT from lux/RGB. */
public final class CctRules {
    private CctRules() {}
    public static String descriptor(String name, String type) {
        return ((name == null ? "" : name) + " " + (type == null ? "" : type)).toLowerCase(Locale.ROOT);
    }
    public static boolean isCct(String name, String type) {
        String s = descriptor(name, type);
        return s.contains("cct") || s.contains("color_temperature") || s.contains("color temperature")
                || s.contains("colour temperature") || s.contains("色温");
    }
    public static boolean isRawStream(String name, String type) {
        String s = descriptor(name, type);
        return s.contains("factory") || s.contains("strm") || s.contains("stream") || s.contains("raw");
    }
    public static int score(String name, String type, boolean wakeup) {
        if (!isCct(name, type)) return -10000;
        String s = descriptor(name, type);
        int score = 100;
        if (s.contains("back") || s.contains("rear")) score += 100;
        if (s.contains("front")) score -= 30;
        if (isRawStream(name, type)) score -= 1000;
        if (!wakeup) score += 10;
        return score;
    }
    /** Experimental default: values[0] of a named, non-stream CCT sensor, interpreted as kelvin. */
    public static float kelvin(float[] values, int channel) {
        if (values == null || channel < 0 || channel >= values.length) return Float.NaN;
        float n = values[channel];
        return Float.isFinite(n) && n >= 1000f && n <= 25000f ? n : Float.NaN;
    }
}
