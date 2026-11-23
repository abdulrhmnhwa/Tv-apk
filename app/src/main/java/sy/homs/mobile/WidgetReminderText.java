package sy.homs.mobile;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/** Shared measurement for RemoteViews, including Android 5 and 6 hosts. */
final class WidgetReminderText {
    static int height(android.content.Context c, float k) {
        android.widget.TextView probe = new android.widget.TextView(c);
        probe.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13 * k);
        probe.setLineSpacing(0, 0.9f);
        probe.setText("تذكير الصلاة\nتذكير الصلاة");
        probe.measure(android.view.View.MeasureSpec.makeMeasureSpec(10000, android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        return probe.getMeasuredHeight();
    }
    @SuppressWarnings("deprecation")
    static float fit(CharSequence text, int widthPx, int heightPx, float maxPx) {
        TextPaint p = new TextPaint(3);
        float lo = 1, hi = maxPx;
        for (int i = 0; i < 16; i++) {
            float mid = (lo + hi) / 2;
            p.setTextSize(mid);
            StaticLayout l;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                StaticLayout.Builder builder = StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, widthPx))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                        .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
                if (android.os.Build.VERSION.SDK_INT >= 28) builder.setUseLineSpacingFromFallbacks(true);
                l = builder.build();
            } else {
                l = new StaticLayout(text, p, Math.max(1, widthPx), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            }
            if (l.getHeight() <= heightPx - 6) lo = mid; else hi = mid;
        }
        // Small width/font rounding reserve, important across launcher processes.
        return lo * 0.97f;
    }
}
