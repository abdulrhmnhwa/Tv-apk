package sy.homs.mobile;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

/** Measures the middle boxes in the same dp-based text units used by RemoteViews.
 * Prayer name stays fixed at the requested percentage; its full time line below
 * shrinks continuously to the box width without changing wording at thresholds.
 */
final class WidgetMiddleFit {
    private WidgetMiddleFit() {}

    static final class Fit {
        final float cntScale;
        final float atScale;
        final boolean showAt;
        final String atText;
        Fit(float c, float a, boolean s, String t) { cntScale = c; atScale = a; showAt = s; atText = t; }
    }

    private static TextView probe(Context c, String text, float sp, boolean bold) {
        TextView p = new TextView(c);
        p.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        if (bold) p.setTypeface(null, Typeface.BOLD);
        p.setSingleLine(true);
        p.setIncludeFontPadding(false);
        p.setText(text);
        p.measure(View.MeasureSpec.makeMeasureSpec(1 << 24, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return p;
    }

    private static int lineH(Context c, String text, float sp, boolean bold) {
        return probe(c, text, sp, bold).getMeasuredHeight();
    }

    private static int lineW(Context c, String text, float sp, boolean bold) {
        return probe(c, text, sp, bold).getMeasuredWidth();
    }

    private static int paddedLineH(Context c, String text, float sp, boolean bold) {
        TextView p = probe(c, text, sp, bold);
        p.setIncludeFontPadding(true);
        p.measure(View.MeasureSpec.makeMeasureSpec(1 << 24, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return p.getMeasuredHeight();
    }

    /** Inner content height (px) available inside each middle box, or -1 when unknown. */
    static int boxInner(Context c, int heightDp, float fk, boolean tomHidden, boolean arcVisible) {
        if (heightDp <= 0) return -1;
        float d = c.getResources().getDisplayMetrics().density;
        int total = Math.round(heightDp * d);
        int rootPad = Math.round(18 * d); // 8 top + 10 bottom
        int topRow = Math.max(
                paddedLineH(c, "حمص", 14 * fk, false) + (tomHidden ? 0 : lineH(c, "غداً", 11 * fk, false)),
                lineH(c, "8:49", 22 * fk, true) + lineH(c, "الاثنين", 11 * fk, false));
        if (arcVisible) topRow = Math.max(topRow, Math.round(40 * fk * d));
        topRow += Math.round(3.5f * d); // فراغ تحت اليوم والتاريخ فوق المستطيلين
        int chips = Math.round(3.5f * d) + Math.round(2 * d)
                + paddedLineH(c, "الظهر", 12 * fk, false) + Math.round(1 * d)
                + lineH(c, "12:21", 13 * fk, true) + Math.round(2 * d);
        int tip = Math.round(3.5f * d) + Math.round(2 * d) + WidgetReminderText.height(c, fk);
        int boxH = total - rootPad - topRow - chips - tip;
        // box vertical padding 1+1dp, plus a small measurement reserve
        return boxH - Math.round(2 * d) - Math.round(1.5f * d);
    }

    /** Inner content width (px) inside each middle box; Integer.MAX_VALUE when unknown. */
    private static int boxInnerW(Context c, int widthDp) {
        if (widthDp <= 0) return Integer.MAX_VALUE;
        float d = c.getResources().getDisplayMetrics().density;
        return Math.round(((widthDp - 24) / 2f - 4 - 20) * d);
    }

    static Fit compute(Context c, int widthDp, int heightDp, float fk,
                       String name, String at, String timeOnly, String label,
                       boolean tomHidden, boolean arcVisible) {
        // Launcher minimum height is a resize bound, not necessarily the rendered height.
        // Never disable width fitting or shrink the countdown from that estimate.
        float d = c.getResources().getDisplayMetrics().density;
        int innerW = boxInnerW(c, widthDp > 0 ? widthDp : 250);
        // countdown box: one shared scale for label + digits, reduced only on overflow
        float cntH1 = lineH(c, label, 12 * fk, false) + lineH(c, "9:59:59", 28 * fk, true);
        float cntW1 = Math.max(lineW(c, label, 12 * fk, false), lineW(c, "9:59:59", 28 * fk, true));
        float cntS = 1f;
        if (innerW != Integer.MAX_VALUE) cntS = Math.min(cntS, innerW / cntW1);
        cntS = Math.max(0.4f, Math.min(1f, cntS * 0.98f));
        // suffix beside the fixed-size prayer name
        String chosen = at;
        float atS = 1f;
        boolean show = true;
        if (innerW != Integer.MAX_VALUE) {
            float avail = Math.max(1, innerW - 2 * d);
            float w1 = lineW(c, at, 14 * fk, false);
            atS = Math.min(1f, avail / Math.max(1, w1) * 0.97f);
        }
        return new Fit(cntS, atS, show, chosen);
    }

    /** Reads the real widget size and applies the fitted sizes after applyFontScale. */
    static void apply(Context c, AppWidgetManager wm, int widgetId, RemoteViews rv, float fk,
                      String name, String at, String timeOnly, String label,
                      boolean tomHidden, boolean arcVisible) {
        try {
            Bundle b = wm.getAppWidgetOptions(widgetId);
            int wDp = b != null ? b.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
            int hDp = b != null ? b.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) : 0;
            Fit f = compute(c, wDp, hDp, fk, name, at, timeOnly, label, tomHidden, arcVisible);
            rv.setTextViewTextSize(R.id.wlab, TypedValue.COMPLEX_UNIT_DIP, 12 * fk * f.cntScale);
            rv.setTextViewTextSize(R.id.wcd, TypedValue.COMPLEX_UNIT_DIP, 28 * fk * f.cntScale);
            rv.setTextViewTextSize(R.id.wcdt, TypedValue.COMPLEX_UNIT_DIP, 22 * fk * f.cntScale);
            rv.setTextViewText(R.id.wat, f.atText);
            rv.setViewPadding(R.id.wat, 0, WidgetLineGap.prayerPadding(c,fk,name,f.atText,label,f.atScale,f.cntScale), 0, 0);
            if (f.showAt) {
                rv.setViewVisibility(R.id.wat, View.VISIBLE);
                rv.setTextViewTextSize(R.id.wat, TypedValue.COMPLEX_UNIT_DIP, 14 * fk * f.atScale);
            } else {
                rv.setViewVisibility(R.id.wat, View.GONE);
            }
        } catch (Throwable ignored) {}
    }
}
