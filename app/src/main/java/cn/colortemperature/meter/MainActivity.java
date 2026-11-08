package cn.colortemperature.meter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.Sensor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private SensorEngine engine;
    private SharedPreferences store;
    private LinearLayout root, page;
    private IconDock dock;
    private FrameLayout pageHost;
    private TextView status, value, category, guidance, luxValue, luxHint, sensorName, sampleAge, pause, save, stats;
    private TextView rawText, sensorStatus;
    private MeterViews.Scale scale;
    private MeterViews.Trend trend;
    private int tab;
    private int settingsPage;
    private boolean dark, resumed, refreshPending;
    private int bg, ink, muted, surface, surfaceHigh, primary, onPrimary, tint, onTint, outline;
    private final Runnable update = () -> {
        refreshPending = false;
        if (resumed) refresh();
    };

    @Override protected void attachBaseContext(Context base) {
        String language = base.getSharedPreferences("app_settings", MODE_PRIVATE).getString("language", "zh");
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale("en".equals(language) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE);
        super.attachBaseContext(base.createConfigurationContext(configuration));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = getSharedPreferences("readings", MODE_PRIVATE);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bg = color(dark ? "#11150E" : "#F7F9F0");
        ink = color(dark ? "#E2E7D8" : "#192014");
        muted = color(dark ? "#B8C0AD" : "#626B58");
        surface = color(dark ? "#1D2319" : "#EDF1E4");
        surfaceHigh = color(dark ? "#293022" : "#E3E9D8");
        primary = color(dark ? "#B3D18C" : "#4C662B");
        onPrimary = color(dark ? "#213607" : "#FFFFFF");
        tint = color(dark ? "#30451A" : "#D7EEB4");
        onTint = color(dark ? "#D7EEB4" : "#203408");
        outline = color(dark ? "#3D4634" : "#D3DBC7");
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setDecorFitsSystemWindows(false);
        root = column(); root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            if (dock != null) {
                dock.setPadding(0, 0, 0, bars.bottom);
                ViewGroup.LayoutParams layout = dock.getLayoutParams();
                if (layout.height != dp(64) + bars.bottom) {
                    layout.height = dp(64) + bars.bottom;
                    dock.setLayoutParams(layout);
                }
            }
            return insets;
        });
        LinearLayout header = row(); header.setPadding(dp(24), dp(14), dp(16), dp(12));
        LinearLayout branding = column();
        branding.addView(label(s(R.string.app_name), 27, ink, true));
        TextView subtitle = label(s(R.string.app_tagline), 12, muted, false); top(subtitle, 3); branding.addView(subtitle);
        header.addView(branding, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1));
        dock = new IconDock(this, surface, outline, primary, muted, index -> showTab(index));
        root.addView(dock, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);
        root.post(() -> {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            int lightBars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (controller != null) controller.setSystemBarsAppearance(dark ? 0 : lightBars, lightBars);
        });
        engine = new SensorEngine(this, () -> scheduleRefresh());
        if (state != null) {
            tab = state.getInt("tab", 0);
            settingsPage = state.getInt("settingsPage", 0);
            engine.paused = state.getBoolean("paused", false);
        }
        renderPage();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> navigateBack());
        }
        root.requestApplyInsets();
    }
    @Override protected void onResume() { super.onResume(); resumed = true; if (engine != null) engine.start(); refresh(); }
    @Override protected void onPause() {
        resumed = false;
        if (engine != null) engine.stop();
        root.removeCallbacks(update);
        refreshPending = false;
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", tab); out.putInt("settingsPage", settingsPage);
        out.putBoolean("paused", engine.paused); super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() { navigateBack(); }
    private void navigateBack() {
        if (tab == 2 && settingsPage != 0) showSettings(settingsPage >= 4 ? 3 : 0);
        else if (tab != 0) showTab(0);
        else finish();
    }
    private void scheduleRefresh() {
        if (!resumed || refreshPending) return;
        refreshPending = true;
        // Draw the latest event at the next display frame, without delaying or combining values.
        root.postOnAnimation(update);
    }
    private void showTab(int next) {
        tab = next;
        settingsPage = 0;
        renderPage();
    }
    private void showSettings(int section) {
        tab = 2;
        settingsPage = section;
        renderPage();
    }
    private void renderPage() {
        pageHost.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setClipToPadding(false);
        page = column(); page.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        pageHost.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        value = null; rawText = null;
        if (tab == 0) buildMeasure();
        else if (tab == 1) buildHistory();
        else if (settingsPage == 1) buildSensors();
        else if (settingsPage == 2) buildLanguage();
        else if (settingsPage == 3) buildAbout();
        else if (settingsPage == 4 || settingsPage == 5) buildLegal(settingsPage == 4);
        else buildSettings();
        dock.select(tab);
        refresh();
    }
    private void buildMeasure() {
        LinearLayout hero = card(tint, 36, 24);
        LinearLayout titleRow = row();
        TextView caption = label(s(R.string.cct), 14, onTint, true);
        titleRow.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chip = label("CCT", 11, onTint, true); chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setBackground(shape(dark ? color("#425A2B") : color("#C2DCA0"), 20)); titleRow.addView(chip);
        hero.addView(titleRow);
        status = label(s(R.string.wait_sensor), 12, onTint, false); top(status, 20); hero.addView(status);
        LinearLayout number = row(); number.setGravity(Gravity.BOTTOM);
        value = label("—", 80, onTint, true);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setMaxLines(1); value.setAutoSizeTextTypeUniformWithConfiguration(40, 80, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        value.setLetterSpacing(-.035f); value.setIncludeFontPadding(false);
        number.addView(value, new LinearLayout.LayoutParams(0, dp(105), 1));
        TextView unit = label("K", 28, onTint, false); unit.setPadding(dp(8), 0, 0, dp(11)); number.addView(unit);
        hero.addView(number);
        category = label(s(R.string.wait_light), 20, onTint, true); top(category, 2); hero.addView(category);
        scale = new MeterViews.Scale(this, onTint, onTint); hero.addView(scale, new LinearLayout.LayoutParams(-1, dp(53))); top(scale, 16);
        sampleAge = label(s(R.string.no_valid_sample), 11, onTint, false); top(sampleAge, 8); hero.addView(sampleAge);
        page.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = row(); top(actions, 14);
        pause = button(s(R.string.pause_measure), primary, onPrimary, () -> {
            if (engine.stale()) engine.restart(); else engine.togglePause(); refresh();
        });
        save = button(s(R.string.save_reading), surfaceHigh, ink, () -> saveReading());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(58), 1); half.setMargins(0, 0, dp(10), 0);
        actions.addView(pause, half); actions.addView(save, new LinearLayout.LayoutParams(0, dp(58), 1)); page.addView(actions);

        LinearLayout small = row(); top(small, 16);
        LinearLayout luxCard = card(surface, 26, 18);
        luxCard.addView(label(s(R.string.illuminance), 12, muted, false));
        luxValue = label("— lx", 25, ink, true); top(luxValue, 8); luxCard.addView(luxValue);
        luxHint = label(s(R.string.rear_light), 10, muted, false); luxHint.setMaxLines(2); top(luxHint, 6); luxCard.addView(luxHint);
        luxCard.setOnClickListener(v -> showSettings(1)); luxCard.setContentDescription(s(R.string.view_rear_sensor));
        LinearLayout.LayoutParams smallHalf = new LinearLayout.LayoutParams(0, -1, 1); smallHalf.setMargins(0, 0, dp(10), 0);
        small.addView(luxCard, smallHalf);
        LinearLayout sourceCard = card(surface, 26, 18);
        sourceCard.addView(label(s(R.string.source), 12, muted, false));
        sensorName = label(s(R.string.detecting), 17, ink, true); sensorName.setMaxLines(2); top(sensorName, 8); sourceCard.addView(sensorName);
        TextView sourceHint = label(s(R.string.view_sensors), 10, primary, true); top(sourceHint, 7); sourceCard.addView(sourceHint);
        sourceCard.setOnClickListener(v -> showSettings(1)); sourceCard.setContentDescription(s(R.string.view_cct_sensor));
        small.addView(sourceCard, new LinearLayout.LayoutParams(0, -1, 1)); page.addView(small);

        LinearLayout plot = card(surface, 28, 20); top(plot, 16);
        LinearLayout plotHeader = row(); plotHeader.addView(label(s(R.string.light_changes), 16, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        plotHeader.addView(label(s(R.string.last_samples), 10, muted, false)); plot.addView(plotHeader);
        trend = new MeterViews.Trend(this, engine.points, primary, muted, outline);
        plot.addView(trend, new LinearLayout.LayoutParams(-1, dp(120))); top(trend, 12);
        stats = label(s(R.string.wait_data), 11, muted, false); top(stats, 12); plot.addView(stats); page.addView(plot);
        guidance = label(s(R.string.measure_guidance), 12, muted, false);
        guidance.setLineSpacing(dp(3), 1); guidance.setPadding(dp(4), dp(18), dp(4), 0); page.addView(guidance);
        TextView beta = label(s(R.string.cct_beta), 10, muted, false); beta.setPadding(dp(4), dp(8), dp(4), 0); page.addView(beta);
    }
    private void refresh() {
        if (engine == null) return;
        if (tab == 0 && value != null) {
            boolean valid = engine.valid();
            value.setText(valid ? String.format(Locale.ROOT, "%.0f", engine.kelvin) : "—");
            status.setText((valid && !engine.paused ? "●  " : "○  ") + engine.status());
            category.setText(valid ? lightName(engine.kelvin) : s(R.string.wait_light));
            guidance.setText(engine.guidance());
            luxValue.setText(engine.luxValid() ? String.format(Locale.ROOT, engine.lux < 100 ? "%.1f lx" : "%.0f lx", engine.lux) : "— lx");
            luxHint.setText(engine.luxValid() ? s(R.string.rear_prefix) + engine.luxSource.sensor.getName() : engine.luxStatus());
            sensorName.setText(engine.source != null ? (isRear(engine.source) ? s(R.string.rear_cct) : s(R.string.cct_sensor))
                    : engine.candidates().isEmpty() ? s(R.string.unavailable) : s(R.string.wait_response));
            sampleAge.setText(engine.sampleTime > 0 ? s(R.string.sample_prefix) + time(engine.sampleTime, "HH:mm:ss.SSS") + (engine.paused ? s(R.string.paused_suffix) : s(R.string.single_suffix)) : s(R.string.no_valid_sample));
            pause.setText(engine.paused || engine.stale() ? s(R.string.continue_measure) : s(R.string.pause_measure));
            save.setEnabled(valid); save.setAlpha(valid ? 1f : .42f);
            scale.kelvin = valid ? engine.kelvin : Float.NaN; scale.invalidate(); trend.invalidate();
            if (engine.points.isEmpty()) stats.setText(s(R.string.wait_data));
            else {
                float low = Float.MAX_VALUE, high = 0;
                for (SensorEngine.Point p : engine.points) { low = Math.min(low, p.k); high = Math.max(high, p.k); }
                stats.setText(String.format(Locale.ROOT, s(R.string.stats), low, high, engine.points.size()));
            }
        }
        if (tab == 2 && settingsPage == 1 && rawText != null) {
            sensorStatus.setText(s(R.string.cct_status_prefix) + engine.status() + s(R.string.lux_status_prefix) + engine.luxStatus() + "\n" + engine.guidance());
            StringBuilder b = new StringBuilder();
            for (SensorEngine.Probe p : engine.probes) {
                if (!p.cct && !p.rearLux) continue;
                b.append(p.rearLux ? s(R.string.rear_lux_tag) : "[CCT] ").append(p.sensor.getName()).append("\n").append(p.error).append("\n");
                if (p.raw == null) b.append(s(R.string.no_events_yet));
                else {
                    for (int i = 0; i < p.raw.length; i++) b.append('[').append(i).append("] ").append(String.format(Locale.ROOT, "%.3f", p.raw[i])).append("  ");
                    b.append(s(R.string.event_count_prefix)).append(p.events).append(s(R.string.channel_suffix)).append(p.rearLux ? 0 : engine.channel(p));
                    if (p == engine.luxSource) b.append(s(R.string.lux_sample_prefix)).append(time(engine.luxSampleTime, "HH:mm:ss.SSS"));
                }
                b.append("\n\n");
            }
            if (b.length() == 0) b.append(s(R.string.no_interfaces));
            rawText.setText(b.toString().trim());
        }
    }
    private boolean isRear(SensorEngine.Probe p) {
        String s = CctRules.descriptor(p.sensor.getName(), p.sensor.getStringType());
        return s.contains("back") || s.contains("rear");
    }
    private void buildHistory() {
        LinearLayout heading = row(); heading.addView(label(s(R.string.measurement_records), 24, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView share = button(s(R.string.export), surfaceHigh, ink, () -> exportReadings());
        heading.addView(share, new LinearLayout.LayoutParams(dp(72), dp(48))); page.addView(heading);
        TextView caption = label(s(R.string.local_record_limit), 12, muted, false); top(caption, 4); page.addView(caption);
        JSONArray list = readings();
        if (list.length() == 0) {
            LinearLayout empty = card(surface, 32, 28); top(empty, 24);
            empty.addView(label(s(R.string.empty_title), 24, ink, true));
            TextView text = label(s(R.string.empty_body), 14, muted, false); top(text, 12); text.setLineSpacing(dp(4), 1); empty.addView(text);
            TextView back = button(s(R.string.start_measure), primary, onPrimary, () -> showTab(0)); top(back, 24); empty.addView(back, new LinearLayout.LayoutParams(-1, dp(54))); page.addView(empty);
            return;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i); if (item == null) continue;
            final int index = i;
            LinearLayout card = card(surface, 26, 20); top(card, 12);
            LinearLayout line = row();
            line.addView(label(String.format(Locale.ROOT, "%.0f K", item.optDouble("kelvin")), 30, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(label(lightName((float)item.optDouble("kelvin")), 12, primary, true)); card.addView(line);
            String note = item.optString("note", s(R.string.ambient_measurement));
            TextView name = label(note.isEmpty() ? s(R.string.ambient_measurement) : note, 14, ink, false); top(name, 6); card.addView(name);
            TextView timestamp = label(time(item.optLong("sampleTime"), s(R.string.history_date_pattern)) + " · " + (item.has("lux") ? String.format(Locale.ROOT, "%.0f lx", item.optDouble("lux")) : s(R.string.no_lux)), 11, muted, false);
            top(timestamp, 8); card.addView(timestamp);
            card.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(s(R.string.measurement_records))
                    .setMessage(recordDetails(item)).setPositiveButton(s(R.string.close), null)
                    .setNeutralButton(s(R.string.copy), (d, w) -> copy(recordDetails(item), s(R.string.reading_copied)))
                    .setNegativeButton(s(R.string.delete), (d, w) -> {
                        JSONArray current = readings(); current.remove(index);
                        if (store.edit().putString("items", current.toString()).commit()) showTab(1);
                        else toast(s(R.string.delete_failed));
                    }).show());
            page.addView(card);
        }
    }
    private String recordDetails(JSONObject item) {
        String details = String.format(Locale.ROOT, s(R.string.record_details),
                item.optDouble("kelvin"), lightName((float)item.optDouble("kelvin")), item.optString("note"),
                time(item.optLong("sampleTime"), "yyyy-MM-dd HH:mm:ss"), item.optDouble("rawKelvin"),
                item.optString("sensor"), item.optInt("channel"));
        if (item.has("lux")) {
            details += String.format(Locale.ROOT, s(R.string.record_lux), item.optDouble("lux"), item.optString("luxSensor", s(R.string.source_not_recorded)));
            if (item.has("luxSampleTime")) details += s(R.string.record_lux_time) + time(item.optLong("luxSampleTime"), "yyyy-MM-dd HH:mm:ss.SSS");
        }
        return details;
    }
    private void saveReading() {
        if (!engine.valid()) { toast(s(R.string.no_reading_to_save)); return; }
        // Snapshot now: typing a note must not silently save a later light condition.
        final JSONObject item = new JSONObject();
        try {
            item.put("kelvin", engine.kelvin); item.put("rawKelvin", engine.rawKelvin);
            item.put("sampleTime", engine.sampleTime); item.put("savedTime", System.currentTimeMillis());
            if (engine.luxValid()) {
                item.put("lux", engine.lux);
                item.put("luxSensor", engine.luxSource.sensor.getName());
                item.put("luxType", engine.luxSource.sensor.getStringType());
                item.put("luxSampleTime", engine.luxSampleTime);
            }
            item.put("sensor", engine.source.sensor.getName()); item.put("type", engine.source.sensor.getStringType());
            item.put("channel", engine.channel(engine.source));
        } catch (Exception e) { toast(s(R.string.save_unavailable)); return; }
        EditText note = new EditText(this); note.setSingleLine(true); note.setHint(s(R.string.note_hint));
        note.setTextColor(ink); note.setHintTextColor(muted); note.setPadding(dp(24), dp(12), dp(24), dp(12));
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(String.format(Locale.ROOT, s(R.string.save_title), item.optDouble("kelvin")))
                .setView(note).setNegativeButton(s(R.string.cancel), null).setPositiveButton(s(R.string.save), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                item.put("note", note.getText().toString().trim());
                JSONArray previous = readings(), next = new JSONArray(); next.put(item);
                for (int i = 0; i < Math.min(previous.length(), 99); i++) next.put(previous.get(i));
                if (store.edit().putString("items", next.toString()).commit()) { dialog.dismiss(); toast(s(R.string.saved)); }
                else toast(s(R.string.save_failed));
            } catch (Exception e) { toast(s(R.string.save_failed)); }
        })); dialog.show();
    }
    private JSONArray readings() {
        try { return new JSONArray(store.getString("items", "[]")); }
        catch (Exception e) { toast(s(R.string.history_read_failed)); return new JSONArray(); }
    }
    private void exportReadings() {
        JSONArray items = readings();
        if (items.length() == 0) { toast(s(R.string.nothing_to_export)); return; }
        StringBuilder text = new StringBuilder(s(R.string.export_header));
        for (int i = 0; i < items.length(); i++) if (items.optJSONObject(i) != null) text.append(recordDetails(items.optJSONObject(i))).append("\n\n");
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text.toString());
        try { startActivity(Intent.createChooser(send, s(R.string.export_records))); }
        catch (android.content.ActivityNotFoundException e) { copy(text.toString(), s(R.string.records_copied)); }
    }
    private void buildSensors() {
        subpageTitle(s(R.string.sensors));
        TextView subtitle = label(s(R.string.sensor_subtitle), 12, muted, false); top(subtitle, 5); page.addView(subtitle);
        LinearLayout state = card(tint, 28, 22); top(state, 20);
        sensorStatus = label(engine.status(), 14, onTint, true); sensorStatus.setLineSpacing(dp(4), 1); state.addView(sensorStatus);
        TextView restart = button(s(R.string.rescan), primary, onPrimary, () -> { engine.stop(); engine.scan(); engine.restart(); showSettings(1); });
        top(restart, 16); state.addView(restart, new LinearLayout.LayoutParams(-1, dp(52))); page.addView(state);
        LinearLayout actions = row(); top(actions, 14);
        TextView report = button(s(R.string.copy_diagnostics), primary, onPrimary, () -> copy(engine.report(), s(R.string.diagnostics_copied)));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(54), 1); half.setMargins(0, 0, dp(10), 0); actions.addView(report, half);
        TextView config = button(s(R.string.mapping_settings), surfaceHigh, ink, () -> chooseSource()); actions.addView(config, new LinearLayout.LayoutParams(0, dp(54), 1)); page.addView(actions);
        LinearLayout rawCard = card(surface, 28, 20); top(rawCard, 16);
        rawCard.addView(label(s(R.string.raw_events), 16, ink, true));
        rawText = label(s(R.string.waiting_event), 12, muted, false); rawText.setTypeface(Typeface.MONOSPACE); rawText.setTextIsSelectable(true); rawText.setLineSpacing(dp(4), 1); top(rawText, 14); rawCard.addView(rawText); page.addView(rawCard);
        TextView title = label(s(R.string.visible_sensor_prefix) + engine.probes.size(), 16, ink, true); top(title, 24); page.addView(title);
        for (SensorEngine.Probe p : engine.probes) {
            LinearLayout card = card(surface, 20, 16); top(card, 8);
            card.addView(label((p.cct ? "CCT  ·  " : p.rearLux ? s(R.string.rear_lux_label) : "") + p.sensor.getName(), 13, ink, true));
            TextView detail = label(p.sensor.getStringType() + "\n" + p.sensor.getVendor() + s(R.string.type_suffix) + p.sensor.getType() + (p.sensor.isWakeUpSensor() ? s(R.string.wakeup_suffix) : ""), 11, muted, false);
            detail.setTextIsSelectable(true); top(detail, 6); card.addView(detail); page.addView(card);
        }
        TextView info = label(s(R.string.sensor_notes), 12, muted, false);
        info.setLineSpacing(dp(4), 1); top(info, 22); page.addView(info);
    }
    private void chooseSource() {
        List<SensorEngine.Probe> candidates = engine.candidates();
        String[] names = new String[candidates.size() + 1]; names[0] = s(R.string.auto_rear_cct);
        int checked = 0;
        for (int i = 0; i < candidates.size(); i++) {
            SensorEngine.Probe p = candidates.get(i);
            names[i + 1] = p.sensor.getName() + (p.sensor.isWakeUpSensor() ? s(R.string.wakeup_brackets) : "") + (p.stream() ? s(R.string.raw_stream_suffix) : "");
            if (engine.preferred().equals(p.key)) checked = i + 1;
        }
        new AlertDialog.Builder(this).setTitle(s(R.string.choose_cct_source)).setSingleChoiceItems(names, checked, (d, which) -> {
            d.dismiss();
            if (which == 0) { engine.configure("", 0); showSettings(1); return; }
            chooseChannel(candidates.get(which - 1));
        }).setNegativeButton(s(R.string.cancel), null).show();
    }
    private void chooseChannel(SensorEngine.Probe p) {
        int count = p.raw == null ? 16 : Math.max(1, p.raw.length);
        String[] channels = new String[count];
        for (int i = 0; i < count; i++) channels[i] = s(R.string.channel_prefix) + i + " · values[" + i + "]" + (p.raw != null ? " = " + p.raw[i] : "");
        final int[] selected = {Math.min(count - 1, engine.channel(p))};
        new AlertDialog.Builder(this).setTitle(s(R.string.choose_kelvin_channel))
                .setSingleChoiceItems(channels, selected[0], (d, w) -> selected[0] = w)
                .setNeutralButton(s(R.string.mapping_help), (d, w) -> new AlertDialog.Builder(this).setTitle(s(R.string.vendor_channels))
                        .setMessage(s(R.string.mapping_body))
                        .setPositiveButton(s(R.string.got_it), null).show())
                .setNegativeButton(s(R.string.cancel), null).setPositiveButton(s(R.string.apply), (d, w) -> {
                    engine.configure(p.key, selected[0]); showSettings(1);
                }).show();
    }
    private void buildSettings() {
        page.addView(label(s(R.string.settings), 24, ink, true));
        TextView caption = label(s(R.string.settings_caption), 12, muted, false); top(caption, 6); page.addView(caption);
        settingRow(s(R.string.sensors), s(R.string.sensor_subtitle), "sensor", () -> showSettings(1));
        String language = getSharedPreferences("app_settings", MODE_PRIVATE).getString("language", "zh");
        settingRow(s(R.string.language), "en".equals(language) ? "English" : "中文", "language", () -> showSettings(2));
        settingRow(s(R.string.about), s(R.string.version_value, AppInfo.version(this)), "info", () -> showSettings(3));
    }
    private void settingRow(String title, String subtitle, String icon, Runnable action) {
        LinearLayout item = row(); item.setPadding(dp(18), dp(20), dp(18), dp(20));
        item.setBackground(ripple(surface, 24)); item.setMinimumHeight(dp(84));
        MeterViews.Symbol symbol = new MeterViews.Symbol(this, icon, primary);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26)); iconParams.rightMargin = dp(18);
        item.addView(symbol, iconParams);
        LinearLayout text = column(); text.addView(label(title, 17, ink, true));
        TextView detail = label(subtitle, 12, muted, false); top(detail, 5); text.addView(detail);
        item.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        item.addView(label("›", 25, muted, false));
        item.setFocusable(true); item.setContentDescription(title + ", " + subtitle);
        item.setOnClickListener(v -> action.run()); top(item, 14); page.addView(item);
    }
    private void subpageTitle(String title) {
        LinearLayout line = row();
        TextView back = button("‹", surface, ink, () -> showSettings(settingsPage >= 4 ? 3 : 0));
        back.setTextSize(27); back.setContentDescription(s(R.string.back));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(48), dp(48)); backParams.rightMargin = dp(12);
        line.addView(back, backParams); line.addView(label(title, 24, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(line);
    }
    private void buildLanguage() {
        subpageTitle(s(R.string.language));
        TextView caption = label(s(R.string.language_caption), 13, muted, false); top(caption, 18); page.addView(caption);
        String selected = getSharedPreferences("app_settings", MODE_PRIVATE).getString("language", "zh");
        languageRow("中文", "简体中文", "zh", selected);
        languageRow("English", "English", "en", selected);
    }
    private void languageRow(String name, String description, String code, String selected) {
        LinearLayout item = row(); item.setPadding(dp(22), dp(20), dp(22), dp(20));
        item.setBackground(ripple(code.equals(selected) ? tint : surface, 24));
        item.addView(label(name, 18, code.equals(selected) ? onTint : ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        item.addView(label(code.equals(selected) ? "✓" : "", 22, primary, true));
        item.setFocusable(true); item.setSelected(code.equals(selected)); item.setContentDescription(description);
        item.setOnClickListener(v -> {
            if (code.equals(selected)) return;
            if (getSharedPreferences("app_settings", MODE_PRIVATE).edit().putString("language", code).commit()) recreate();
            else toast(s(R.string.language_failed));
        }); top(item, 14); page.addView(item);
    }
    private void buildAbout() {
        subpageTitle(s(R.string.about));
        LinearLayout identity = card(tint, 30, 24); top(identity, 20);
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72)); iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        identity.addView(icon, iconParams);
        TextView name = label(s(R.string.app_name), 25, onTint, true); name.setGravity(Gravity.CENTER); top(name, 12); identity.addView(name);
        TextView version = label(s(R.string.version_value, AppInfo.version(this)), 13, onTint, false); version.setGravity(Gravity.CENTER); top(version, 7); identity.addView(version);
        page.addView(identity);
        LinearLayout credits = card(surface, 26, 20); top(credits, 16);
        creditRow(credits, s(R.string.production)); creditRow(credits, s(R.string.design));
        creditRow(credits, s(R.string.engineering)); creditRow(credits, s(R.string.testing)); page.addView(credits);
        settingRow(s(R.string.privacy), s(R.string.privacy_subtitle), "privacy", () -> showSettings(4));
        settingRow(s(R.string.terms), s(R.string.terms_subtitle), "history", () -> showSettings(5));
    }
    private void creditRow(LinearLayout parent, String role) {
        LinearLayout line = row(); line.setPadding(0, dp(11), 0, dp(11));
        line.addView(label(role, 14, muted, false), new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(label("PATRICK STAR", 14, ink, true)); parent.addView(line);
    }
    private void buildLegal(boolean privacy) {
        subpageTitle(s(privacy ? R.string.privacy : R.string.terms));
        TextView date = label(s(R.string.legal_date), 12, muted, false); top(date, 20); page.addView(date);
        TextView body = label(s(privacy ? R.string.privacy_body : R.string.terms_body), 15, ink, false);
        body.setTextIsSelectable(true); body.setLineSpacing(dp(6), 1); top(body, 18); page.addView(body);
    }
    private String s(int id, Object... args) { return args.length == 0 ? getString(id) : getString(id, args); }
    private String lightName(float k) {
        if (!Float.isFinite(k)) return s(R.string.wait_light);
        if (k < 3300) return s(R.string.light_warm);
        if (k < 5300) return s(R.string.light_neutral);
        return s(R.string.light_cool);
    }
    private void copy(String text, String message) {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText(s(R.string.app_name), text)); toast(message); }
    }
    private int color(String hex) { return Color.parseColor(hex); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout card(int color, int radius, int padding) {
        LinearLayout v = column(); v.setPadding(dp(padding), dp(padding), dp(padding), dp(padding)); v.setBackground(shape(color, radius)); return v;
    }
    private GradientDrawable shape(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private RippleDrawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(dark ? 0x25FFFFFF : 0x16000000), shape(color, radius), shape(Color.WHITE, radius)); }
    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); return v;
    }
    private TextView button(String text, int background, int foreground, Runnable action) {
        TextView v = label(text, 14, foreground, true); v.setGravity(Gravity.CENTER); v.setMinHeight(dp(48));
        v.setBackground(ripple(background, 28)); v.setFocusable(true);
        v.setOnClickListener(view -> { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); action.run(); });
        return v;
    }
    private void top(View v, int margin) {
        ViewGroup.LayoutParams old = v.getLayoutParams();
        LinearLayout.LayoutParams p = old instanceof LinearLayout.LayoutParams ? (LinearLayout.LayoutParams)old : new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(margin); v.setLayoutParams(p);
    }
    private String time(long timestamp, String pattern) { return new SimpleDateFormat(pattern, getResources().getConfiguration().getLocales().get(0)).format(new Date(timestamp)); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
