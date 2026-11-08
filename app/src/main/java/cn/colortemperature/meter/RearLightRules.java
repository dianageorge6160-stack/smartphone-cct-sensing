package cn.colortemperature.meter;

/** Recognize rear illuminance interfaces without substituting a front or fused default sensor. */
public final class RearLightRules {
    private RearLightRules() {}
    public static boolean matches(String name, String type, int sensorType) {
        if (CctRules.isCct(name, type) || CctRules.isRawStream(name, type)) return false;
        // Standard Android sensors other than TYPE_LIGHT are not illuminance sensors.
        if (sensorType < 65536 && sensorType != 5) return false;
        String s = CctRules.descriptor(name, type);
        String words = " " + s.replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", " ") + " ";
        if (words.contains(" front ") || words.contains(" fused ") || words.contains(" fusion ")
                || s.contains("前置") || s.contains("融合")) return false;
        boolean rear = words.contains(" back ") || words.contains(" rear ")
                || words.contains(" lux b ") || s.contains("后置") || s.contains("背面");
        boolean light = sensorType == 5 || words.contains(" lux ") || words.contains(" ambient ")
                || words.contains(" light ") || words.contains(" als ") || s.contains("光线") || s.contains("照度");
        return rear && light;
    }
    public static int score(String name, String type, int sensorType, boolean wakeup) {
        if (!matches(name, type, sensorType)) return -10000;
        int score = 100;
        if (sensorType == 5) score += 50; // Android-standard lux layout takes priority.
        if (!wakeup) score += 10;
        return score;
    }
    /** TYPE_LIGHT reports lux in values[0]; named vendor rear-lux interfaces need device verification. */
    public static float lux(float[] values) {
        if (values == null || values.length == 0) return Float.NaN;
        float value = values[0];
        return Float.isFinite(value) && value >= 0 ? value : Float.NaN;
    }
}
