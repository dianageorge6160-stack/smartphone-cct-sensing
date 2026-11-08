package cn.colortemperature.meter;

public final class CctRulesTest {
    private static int checks;
    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(CctRules.isCct("tcs3408 back CCT Non-wakeup", "Xiaomi.sensor.back_cct"), "Xiaomi named CCT");
        check(!CctRules.isCct("tcs3408 back ambient", "xiaomi.sensor.back_lux"), "lux must not become CCT");
        check(!CctRules.isCct("Ambient Temperature", "android.sensor.ambient_temperature"), "Celsius must not become CCT");
        check(CctRules.isCct("", "vendor.color_temperature"), "color temperature spelling");
        check(CctRules.isRawStream("tcs3408 back CCT stream", ""), "raw stream excluded from automatic decode");
        check(CctRules.score("back CCT", "", false) > CctRules.score("front CCT", "", false), "prefer rear");
        check(CctRules.score("back CCT", "", false) > CctRules.score("back CCT", "", true), "prefer non-wakeup");
        check(CctRules.score("back CCT factory strm", "", false) < CctRules.score("front CCT", "", false), "factory is not production CCT");
        check(CctRules.kelvin(new float[]{5600, 200}, 0) == 5600, "read only configured channel");
        check(Float.isNaN(CctRules.kelvin(new float[]{0, 5600}, 0)), "do not guess a different channel");
        check(CctRules.kelvin(new float[]{0, 5600}, 1) == 5600, "manual mapping");
        check(Float.isNaN(CctRules.kelvin(new float[]{5600}, 1)), "short event");
        check(Float.isNaN(CctRules.kelvin(null, 0)), "null event");
        check(Float.isNaN(CctRules.kelvin(new float[]{Float.NaN}, 0)), "NaN");
        check(Float.isNaN(CctRules.kelvin(new float[]{Float.POSITIVE_INFINITY}, 0)), "infinity");
        check(Float.isNaN(CctRules.kelvin(new float[]{999}, 0)), "lower limit");
        check(Float.isNaN(CctRules.kelvin(new float[]{25001}, 0)), "upper limit");
        check(CctRules.kelvin(new float[]{1000}, 0) == 1000, "inclusive lower limit");
        check(CctRules.kelvin(new float[]{25000}, 0) == 25000, "inclusive upper limit");
        check(RearLightRules.matches("rear light", "android.sensor.light", 5), "standard rear light");
        check(RearLightRules.matches("tcs3408 back ambient", "Xiaomi.sensor.back_lux", 65536), "vendor rear illuminance");
        check(RearLightRules.matches("tcs3408_back", "Xiaomi.sensor.lux_b", 65536), "vendor lux_b alias");
        check(RearLightRules.matches("后置光线传感器", "vendor.sensor.als", 65536), "rear light Chinese name");
        check(!RearLightRules.matches("front light", "android.sensor.light", 5), "reject front light");
        check(!RearLightRules.matches("ambient light", "android.sensor.light", 5), "default light does not prove rear location");
        check(!RearLightRules.matches("fused ambient light", "android.sensor.light", 5), "fused light does not prove rear location");
        check(!RearLightRules.matches("tcs3408 back CCT", "Xiaomi.sensor.back_cct", 65536), "CCT must not become lux");
        check(!RearLightRules.matches("back lux factory", "vendor.sensor.back_lux", 65536), "factory stream is not calibrated lux");
        check(!RearLightRules.matches("back ambient stream", "vendor.sensor.back_lux_strm", 65536), "raw stream is not calibrated lux");
        check(!RearLightRules.matches("back light proximity", "android.sensor.proximity", 8), "reject different standard sensor type");
        check(!RearLightRules.matches("background light", "android.sensor.light", 5), "background does not mean rear");
        check(!RearLightRules.matches("display backlight", "vendor.sensor.light", 65536), "backlight does not mean rear");
        check(!RearLightRules.matches("front and back light", "android.sensor.light", 5), "ambiguous combined light is not rear-only");
        check(!RearLightRules.matches("back ambient fusion", "vendor.sensor.back_lux", 65536), "reject explicitly fused rear-labeled sensor");
        check(!RearLightRules.matches("后置融合光线", "vendor.sensor.light", 65536), "reject Chinese fused rear label");
        check(!RearLightRules.matches("rear unknown", "vendor.sensor.other", 65536), "rear sensor must also report illuminance");
        check(RearLightRules.score("rear light", "android.sensor.light", 5, false)
                > RearLightRules.score("rear light", "android.sensor.light", 5, true), "rear prefer non-wakeup");
        check(RearLightRules.score("rear light", "android.sensor.light", 5, true)
                > RearLightRules.score("back lux", "vendor.sensor.back_lux", 65536, false), "prefer standard lux event layout");
        check(RearLightRules.lux(new float[]{0}) == 0, "zero lux is valid darkness");
        check(RearLightRules.lux(new float[]{123.456f}) == 123.456f, "publish single lux sample unchanged");
        check(Float.isNaN(RearLightRules.lux(null)), "null lux event");
        check(Float.isNaN(RearLightRules.lux(new float[0])), "empty lux event");
        check(Float.isNaN(RearLightRules.lux(new float[]{-1})), "negative lux event");
        check(Float.isNaN(RearLightRules.lux(new float[]{Float.NaN})), "NaN lux event");
        check(Float.isNaN(RearLightRules.lux(new float[]{Float.POSITIVE_INFINITY})), "infinite lux event");
        check(RearLightRules.lux(new float[]{12, 5600}) == 12, "only use lux channel zero");
        check(Float.isNaN(RearLightRules.lux(new float[]{-1, 123})), "do not guess another lux channel");
        System.out.println("Passed " + checks + " sensor interpretation checks.");
    }
}
