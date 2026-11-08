package cn.colortemperature.meter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Flat, full-width icon navigation. Only the icons animate on press. */
final class IconDock extends LinearLayout {
    interface OnTabSelected { void selected(int tab); }
    private final FrameLayout[] buttons = new FrameLayout[3];
    private final MeterViews.Symbol[] icons = new MeterViews.Symbol[3];
    private final View[] markers = new View[3];
    private final int selectedColor, restingColor;
    private int selected;

    IconDock(Context context, int surface, int outline, int selectedColor,
             int restingColor, OnTabSelected listener) {
        super(context);
        this.selectedColor = selectedColor;
        this.restingColor = restingColor;
        setOrientation(VERTICAL);
        setBackgroundColor(surface);
        setElevation(0);
        setStateListAnimator(null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        View divider = new View(context);
        divider.setBackgroundColor(outline);
        divider.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout items = new LinearLayout(context);
        items.setOrientation(HORIZONTAL);
        addView(items, new LinearLayout.LayoutParams(-1, 0, 1));
        String[] descriptions = {context.getString(R.string.measure), context.getString(R.string.history), context.getString(R.string.settings)};
        String[] symbols = {"measure", "history", "settings"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            FrameLayout button = new FrameLayout(context);
            button.setContentDescription(descriptions[i]);
            button.setFocusable(true);
            button.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setElevation(0);
            button.setStateListAnimator(null);
            button.setAccessibilityDelegate(new AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.Button");
                    info.setSelected(index == selected);
                }
            });
            MeterViews.Symbol icon = new MeterViews.Symbol(context, symbols[i], restingColor);
            button.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
            View marker = new View(context);
            GradientDrawable markerShape = new GradientDrawable();
            markerShape.setColor(selectedColor); markerShape.setCornerRadius(dp(2));
            marker.setBackground(markerShape);
            marker.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams markerParams = new FrameLayout.LayoutParams(dp(18), dp(3), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            markerParams.bottomMargin = dp(6);
            button.addView(marker, markerParams);
            button.setOnClickListener(v -> {
                if (index == selected) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                listener.selected(index);
            });
            button.setOnTouchListener((v, event) -> {
                if (!ValueAnimator.areAnimatorsEnabled()) return false;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    icon.animate().cancel();
                    icon.animate().scaleX(.86f).scaleY(.86f)
                            .setInterpolator(new DecelerateInterpolator()).setDuration(100).start();
                }
                else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    icon.animate().cancel();
                    icon.animate().scaleX(1f).scaleY(1f)
                            .setInterpolator(new OvershootInterpolator(1.8f)).setDuration(260).start();
                }
                return false;
            });
            buttons[i] = button; icons[i] = icon; markers[i] = marker;
            items.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
        }
        select(0);
    }
    void select(int tab) {
        selected = Math.max(0, Math.min(2, tab));
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setSelected(i == selected);
            icons[i].color = i == selected ? selectedColor : restingColor;
            icons[i].invalidate();
            markers[i].setVisibility(i == selected ? VISIBLE : INVISIBLE);
        }
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
