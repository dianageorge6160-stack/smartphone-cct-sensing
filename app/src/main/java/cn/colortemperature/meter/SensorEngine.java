package cn.colortemperature.meter;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SensorEngine implements SensorEventListener {
    interface Listener { void changed(); }
    static final class Probe {
        final Sensor sensor;
        final String key;
        final boolean cct;
        final int score;
        final boolean rearLux;
        final int luxScore;
        boolean registered;
        String error = "";
        float[] raw;
        int events;
        long received;
        int accuracy = -1;
        Probe(Sensor sensor) {
            this.sensor = sensor;
            key = sensor.getStringType() + "|" + sensor.getName() + "|" + sensor.isWakeUpSensor();
            cct = CctRules.isCct(sensor.getName(), sensor.getStringType());
            score = CctRules.score(sensor.getName(), sensor.getStringType(), sensor.isWakeUpSensor());
            rearLux = RearLightRules.matches(sensor.getName(), sensor.getStringType(), sensor.getType());
            luxScore = RearLightRules.score(sensor.getName(), sensor.getStringType(), sensor.getType(), sensor.isWakeUpSensor());
        }
        boolean stream() { return CctRules.isRawStream(sensor.getName(), sensor.getStringType()); }
    }
    static final class Point {
        final long time;
        final float k;
        Point(long time, float k) { this.time = time; this.k = k; }
    }
    private final SensorManager manager;
    private final Context context;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    final List<Probe> probes = new ArrayList<>();
    final List<Point> points = new ArrayList<>();
    Probe source, luxSource;
    float kelvin = Float.NaN, rawKelvin = Float.NaN, lux = Float.NaN;
    long sampleTime, luxSampleTime, started;
    boolean active, paused;
    String scanError = "";
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!active) return;
            listener.changed();
            handler.postDelayed(this, 1000);
        }
    };
    SensorEngine(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        prefs = context.getSharedPreferences("sensor_config", Context.MODE_PRIVATE);
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        scan();
    }
    void scan() {
        probes.clear();
        scanError = "";
        try {
            if (manager != null) for (Sensor sensor : manager.getSensorList(Sensor.TYPE_ALL)) {
                Probe probe = new Probe(sensor); probe.error = s(R.string.not_subscribed); probes.add(probe);
            }
            else scanError = s(R.string.sensor_service_unavailable);
        } catch (RuntimeException e) { scanError = e.getClass().getSimpleName() + ": " + e.getMessage(); }
        Collections.sort(probes, (a, b) -> Integer.compare(b.score, a.score));
    }
    List<Probe> candidates() {
        List<Probe> result = new ArrayList<>();
        for (Probe p : probes) if (p.cct) result.add(p);
        return result;
    }
    String preferred() { return prefs.getString("source", ""); }
    int channel(Probe p) { return prefs.getInt("channel:" + p.key, 0); }
    void configure(String key, int channel) {
        SharedPreferences.Editor e = prefs.edit().putString("source", key);
        if (!key.isEmpty()) e.putInt("channel:" + key, channel);
        e.apply();
        restart();
    }
    void resetSession() {
        source = null;
        luxSource = null;
        kelvin = rawKelvin = lux = Float.NaN;
        sampleTime = luxSampleTime = 0;
        points.clear();
        for (Probe p : probes) { p.events = 0; p.raw = null; p.received = 0; p.accuracy = -1; }
    }
    void start() {
        if (paused || active) return;
        resetSession();
        active = true;
        started = SystemClock.elapsedRealtime();
        String selected = preferred();
        for (Probe p : probes) {
            boolean wanted = p.cct && (selected.isEmpty() ? !p.stream() : selected.equals(p.key));
            p.error = p.cct ? (p.stream() ? s(R.string.raw_manual_only) : s(R.string.not_selected)) : s(R.string.not_subscribed);
            if (!wanted && !p.rearLux) continue;
            try {
                // Request low-latency events with no batching; the actual rate is hardware-dependent.
                p.registered = manager.registerListener(this, p.sensor, SensorManager.SENSOR_DELAY_GAME, 0, handler);
                p.error = p.registered ? s(R.string.subscribed_waiting) : s(R.string.subscription_rejected);
            } catch (RuntimeException e) {
                p.registered = false;
                p.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }
        handler.post(ticker);
        listener.changed();
    }
    void stop() {
        active = false;
        handler.removeCallbacks(ticker);
        if (manager != null) manager.unregisterListener(this);
        for (Probe p : probes) p.registered = false;
    }
    void restart() {
        stop();
        paused = false;
        start();
    }
    void togglePause() {
        if (paused) { paused = false; start(); }
        else {
            if (luxStale()) lux = Float.NaN;
            paused = true; stop(); listener.changed();
        }
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!active || paused) return;
        Probe p = null;
        for (Probe item : probes) if (item.sensor.equals(event.sensor)) { p = item; break; }
        if (p == null) return;
        p.raw = event.values.clone();
        p.events++;
        p.received = SystemClock.elapsedRealtime();
        p.accuracy = event.accuracy;
        p.error = s(R.string.receiving_events);
        long epoch = System.currentTimeMillis() - Math.max(0L,
                (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1000000L);
        if (p.rearLux) {
            float value = RearLightRules.lux(p.raw);
            if (!Float.isFinite(value)) {
                p.error = s(R.string.invalid_rear_event);
                if (luxSource == p) lux = Float.NaN;
            } else if (luxSource == null || !Float.isFinite(lux) || luxSource == p
                    || p.luxScore > luxSource.luxScore || luxStale()) {
                luxSource = p;
                lux = value;
                luxSampleTime = epoch;
            }
        }
        if (p.cct) {
            float k = CctRules.kelvin(p.raw, channel(p));
            // A different sensor's raw channels never become a guessed kelvin value.
            if (!Float.isFinite(k)) {
                p.error = s(R.string.invalid_cct_event);
                if (source == p) kelvin = rawKelvin = Float.NaN;
            } else if (source == null || !Float.isFinite(kelvin) || source == p || p.score > source.score || stale()) {
                if (source != p) points.clear();
                source = p;
                // Publish this event directly. No averaging, median window, or smoothing.
                kelvin = rawKelvin = k;
                sampleTime = epoch;
                points.add(new Point(epoch, k));
                while (points.size() > 60) points.remove(0);
            }
        }
        listener.changed();
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        for (Probe p : probes) if (p.sensor.equals(sensor)) p.accuracy = accuracy;
    }
    boolean stale() {
        // On-change sensors legitimately send no events while the value remains unchanged.
        return !paused && source != null && source.sensor.getReportingMode() == Sensor.REPORTING_MODE_CONTINUOUS
                && SystemClock.elapsedRealtime() - source.received > 10000;
    }
    boolean valid() { return Float.isFinite(kelvin) && source != null && !stale(); }
    boolean luxStale() {
        return !paused && luxSource != null && luxSource.sensor.getReportingMode() == Sensor.REPORTING_MODE_CONTINUOUS
                && SystemClock.elapsedRealtime() - luxSource.received > 10000;
    }
    boolean luxValid() { return luxSource != null && Float.isFinite(lux) && !luxStale(); }
    String luxStatus() {
        if (paused) return luxValid() ? s(R.string.paused_rear_lux) : s(R.string.paused);
        if (luxValid()) return s(R.string.rear_lux_live);
        if (luxStale()) return s(R.string.rear_lux_stopped);
        boolean found = false, registered = false, events = false;
        for (Probe p : probes) if (p.rearLux) {
            found = true; registered |= p.registered; events |= p.events > 0;
        }
        if (!found) return s(R.string.rear_interface_unavailable);
        if (events) return s(R.string.rear_lux_invalid);
        if (!registered) return s(R.string.rear_subscription_failed);
        return SystemClock.elapsedRealtime() - started > 8000 ? s(R.string.rear_lux_no_data) : s(R.string.wait_rear_light);
    }
    String status() {
        if (paused) return valid() ? s(R.string.paused_retained) : s(R.string.paused);
        if (!scanError.isEmpty()) return s(R.string.sensor_service_error);
        if (valid()) return s(R.string.measuring);
        if (stale()) return s(R.string.readings_stopped);
        List<Probe> cct = candidates();
        if (cct.isEmpty()) return s(R.string.cct_interface_unavailable);
        boolean registered = false, events = false, production = false;
        for (Probe p : cct) { registered |= p.registered; events |= p.events > 0; production |= !p.stream(); }
        if (events) return s(R.string.raw_needs_mapping);
        if (preferred().isEmpty() && !production) return s(R.string.only_raw_cct);
        if (!registered) return s(R.string.cct_subscription_failed);
        return SystemClock.elapsedRealtime() - started > 8000 ? s(R.string.no_cct_data) : s(R.string.wait_sensor);
    }
    String guidance() {
        if (paused) return s(R.string.resume_guidance);
        if (valid()) return s(R.string.measure_guidance);
        if (candidates().isEmpty()) return s(R.string.no_cct_guidance);
        if (stale()) return s(R.string.stale_guidance);
        return s(R.string.wait_guidance);
    }
    String report() {
        StringBuilder b = new StringBuilder(s(R.string.report_title, AppInfo.version(context)) + "\n");
        b.append(s(R.string.report_device)).append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\nAndroid ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT)
                .append(s(R.string.report_system)).append(Build.DISPLAY).append(s(R.string.report_status)).append(status())
                .append(s(R.string.report_scan_error)).append(scanError).append(s(R.string.report_selection)).append(preferred().isEmpty() ? s(R.string.automatic) : preferred())
                .append(s(R.string.report_cct_rule))
                .append(s(R.string.report_sampling))
                .append(s(R.string.report_current)).append(kelvin).append(s(R.string.report_raw)).append(rawKelvin).append(" K\n")
                .append(s(R.string.report_rear_status)).append(luxStatus())
                .append(s(R.string.report_rear_source)).append(luxSource == null ? s(R.string.none) : luxSource.sensor.getName())
                .append(s(R.string.report_rear_interface)).append(luxSource == null ? s(R.string.none) : luxSource.sensor.getStringType())
                .append(s(R.string.report_rear_raw)).append(lux).append(s(R.string.report_sample_timestamp)).append(luxSampleTime)
                .append(s(R.string.report_lux_rule));
        for (Probe p : probes) {
            b.append(p.cct ? "[CCT] " : p.rearLux ? s(R.string.rear_lux_tag) : "").append(p.sensor.getName()).append('\n')
                    .append("type=").append(p.sensor.getType()).append(" / ").append(p.sensor.getStringType())
                    .append("\nvendor=").append(p.sensor.getVendor()).append(" / version=").append(p.sensor.getVersion())
                    .append("\nid=").append(p.sensor.getId()).append(" wakeup=").append(p.sensor.isWakeUpSensor())
                    .append(" mode=").append(p.sensor.getReportingMode())
                    .append("\nrange=").append(p.sensor.getMaximumRange()).append(" resolution=").append(p.sensor.getResolution())
                    .append(" minDelayUs=").append(p.sensor.getMinDelay())
                    .append(s(R.string.report_subscription)).append(p.registered).append(" / ").append(p.error)
                    .append("\nevents=").append(p.events).append(" accuracy=").append(p.accuracy)
                    .append(" channel=").append(channel(p)).append("\nvalues=");
            if (p.raw == null) b.append(s(R.string.no_event));
            else for (int i = 0; i < p.raw.length; i++) b.append(i).append(':').append(p.raw[i]).append(' ');
            if (p.received > 0) b.append(s(R.string.report_event_age)).append((SystemClock.elapsedRealtime() - p.received) / 1000).append(s(R.string.seconds_suffix));
            b.append("\n\n");
        }
        return b.toString();
    }
    private String s(int id, Object... args) { return args.length == 0 ? context.getString(id) : context.getString(id, args); }
}
