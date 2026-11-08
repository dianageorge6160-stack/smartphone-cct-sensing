package cn.colortemperature.meter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import java.util.List;
import java.util.Locale;

final class MeterViews {
    static final class Scale extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float kelvin = Float.NaN;
        int ink, muted;
        Scale(Context c, int ink, int muted) { super(c); this.ink = ink; this.muted = muted; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float x = 3 * d, end = getWidth() - x, y = 8 * d;
            p.setShader(new LinearGradient(x, 0, end, 0, new int[]{0xFFE7A65B, 0xFFFFDBA5, 0xFFF5F5E8, 0xFFB1C9EE, 0xFF8BA0D8}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(x, y, end, y + 14 * d, 7 * d, 7 * d, p);
            p.setShader(null);
            float max = Float.isFinite(kelvin) && kelvin > 12000 ? 25000 : 12000;
            if (Float.isFinite(kelvin)) {
                float position = x + (end - x) * Math.max(0, Math.min(1, (kelvin - 1000) / (max - 1000)));
                p.setColor(ink);
                c.drawRoundRect(position - 2 * d, y - 4 * d, position + 2 * d, y + 18 * d, 2 * d, 2 * d, p);
            }
            p.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setColor(muted);
            c.drawText(getContext().getString(R.string.scale_warm), x, 46 * d, p);
            String last = String.format(Locale.ROOT, getContext().getString(R.string.scale_cool), max);
            c.drawText(last, end - p.measureText(last), 46 * d, p);
        }
    }
    static final class Trend extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path line = new Path(), fill = new Path();
        private final List<SensorEngine.Point> points;
        private final int accent, muted, grid;
        Trend(Context c, List<SensorEngine.Point> points, int accent, int muted, int grid) {
            super(c); this.points = points; this.accent = accent; this.muted = muted; this.grid = grid;
            setContentDescription(getContext().getString(R.string.trend_description));
        }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float left = 48 * d, right = getWidth() - 6 * d, top = 12 * d, bottom = getHeight() - 22 * d;
            float low = 3000, high = 7000;
            if (!points.isEmpty()) {
                low = Float.MAX_VALUE; high = -Float.MAX_VALUE;
                for (SensorEngine.Point point : points) { low = Math.min(low, point.k); high = Math.max(high, point.k); }
                float pad = Math.max(150, (high - low) * .2f);
                low = Math.max(1000, low - pad); high = Math.min(25000, high + pad);
            }
            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
            for (int i = 0; i < 3; i++) {
                float y = top + (bottom - top) * i / 2;
                p.setColor(grid); p.setStrokeWidth(d);
                c.drawLine(left, y, right, y, p);
                p.setColor(muted);
                c.drawText(String.format(Locale.ROOT, "%.0f", high - (high - low) * i / 2), 0, y + 3 * d, p);
            }
            if (points.isEmpty()) {
                p.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
                String text = getContext().getString(R.string.trend_empty);
                c.drawText(text, left + (right - left - p.measureText(text)) / 2, (top + bottom) / 2 + 4 * d, p);
                return;
            }
            long first = points.get(0).time, last = points.get(points.size() - 1).time;
            long span = Math.max(1000, last - first);
            line.reset(); fill.reset();
            for (int i = 0; i < points.size(); i++) {
                SensorEngine.Point point = points.get(i);
                float x = left + (right - left) * (point.time - first) / span;
                float y = bottom - (bottom - top) * (point.k - low) / (high - low);
                if (i == 0) { line.moveTo(x, y); fill.moveTo(x, bottom); fill.lineTo(x, y); }
                else { line.lineTo(x, y); fill.lineTo(x, y); }
            }
            float endX = left + (right - left) * (last - first) / span;
            fill.lineTo(endX, bottom); fill.close();
            p.setShader(new LinearGradient(0, top, 0, bottom, (accent & 0xFFFFFF) | 0x38000000, accent & 0xFFFFFF, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL); c.drawPath(fill, p); p.setShader(null);
            p.setColor(accent); p.setStrokeWidth(2.5f * d); p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND); c.drawPath(line, p);
            SensorEngine.Point latest = points.get(points.size() - 1);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(endX, bottom - (bottom - top) * (latest.k - low) / (high - low), 4 * d, p);
            p.setColor(muted);
            c.drawText(getContext().getString(R.string.earlier), left, getHeight() - 2 * d, p);
            c.drawText(getContext().getString(R.string.latest), right - p.measureText(getContext().getString(R.string.latest)), getHeight() - 2 * d, p);
        }
    }
    static final class Symbol extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String kind;
        int color;
        Symbol(Context context, String kind, int color) { super(context); this.kind = kind; this.color = color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            c.save(); c.scale(getWidth() / 24f, getHeight() / 24f);
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if (kind.equals("measure")) {
                c.drawCircle(12, 12, 4, p);
                for (int i = 0; i < 8; i++) { c.save(); c.rotate(45 * i, 12, 12); c.drawLine(12, 2, 12, 4, p); c.restore(); }
            } else if (kind.equals("history")) {
                c.drawRoundRect(5, 3, 19, 21, 3, 3, p); c.drawLine(9, 8, 15, 8, p); c.drawLine(9, 12, 15, 12, p); c.drawLine(9, 16, 12, 16, p);
            } else if (kind.equals("settings")) {
                Path gear = new Path();
                for (int i = 0; i < 32; i++) {
                    double angle = Math.PI * 2 * i / 32 - Math.PI / 2;
                    float radius = i % 4 == 1 || i % 4 == 2 ? 9.1f : 7.2f;
                    float x = 12 + (float)Math.cos(angle) * radius;
                    float y = 12 + (float)Math.sin(angle) * radius;
                    if (i == 0) gear.moveTo(x, y); else gear.lineTo(x, y);
                }
                gear.close(); c.drawPath(gear, p); c.drawCircle(12, 12, 3, p);
            } else if (kind.equals("language")) {
                c.drawCircle(12, 12, 9, p); c.drawOval(8, 3, 16, 21, p);
                c.drawLine(3, 12, 21, 12, p);
            } else if (kind.equals("info")) {
                c.drawCircle(12, 12, 9, p); c.drawLine(12, 11, 12, 17, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(12, 7, 1, p);
            } else if (kind.equals("privacy")) {
                Path shield = new Path(); shield.moveTo(12, 2); shield.lineTo(20, 6);
                shield.lineTo(19, 15); shield.quadTo(17, 20, 12, 22);
                shield.quadTo(7, 20, 5, 15); shield.lineTo(4, 6); shield.close();
                c.drawPath(shield, p); c.drawLine(8, 12, 11, 15, p); c.drawLine(11, 15, 16, 9, p);
            } else {
                c.drawRoundRect(6, 6, 18, 18, 3, 3, p); c.drawCircle(12, 12, 2, p);
                for (int i = 0; i < 4; i++) { c.save(); c.rotate(90 * i, 12, 12); c.drawLine(10, 2, 10, 5, p); c.drawLine(14, 2, 14, 5, p); c.restore(); }
            }
            c.restore();
        }
    }
}
