package sy.homs.mobile;

import android.content.Context;
import android.util.TypedValue;
import android.widget.RemoteViews;

/** All widths and text sizes use the explicit widget percentage, not a second system font multiplier. */
final class WidgetWidthFit {
    static float size(Context c, String text, float base, float k, float widthDp, boolean bold) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        float requested = base * k;
        float w = WidgetProvider.textW(text, requested, bold, m);
        return requested * Math.min(1f, Math.max(1, widthDp * m.density - 3 * m.density) / Math.max(1, w));
    }
    static void apply(Context c, RemoteViews rv, int widthDp, float k, String date, String city, String tom) {
        float half = (widthDp - 24) / 2f - 4;
        rv.setTextViewTextSize(R.id.wcity, TypedValue.COMPLEX_UNIT_DIP, size(c, city, 14, k, half, false));
        rv.setTextViewTextSize(R.id.wtom, TypedValue.COMPLEX_UNIT_DIP, size(c, tom, 11, k, half, false));
        rv.setTextViewTextSize(R.id.wdate, TypedValue.COMPLEX_UNIT_DIP, size(c, date, 11, k, half, false));
        float chip = (widthDp - 24) / 5f - 4;
        float name = 12 * k, time = 13 * k;
        // A shared chip size keeps the five columns even, independent of the current minute.
        for (String n : new String[]{"الفجر", "الظهر", "العصر", "المغرب", "العشاء"})
            name = Math.min(name, size(c, n, 12, k, chip, false));
        for (String t : new String[]{"12:58 ص", "12:58 م", "10:00 ص", "11:59 م"})
            time = Math.min(time, size(c, t, 13, k, chip, true));
        for (int id : WidgetProvider.NID) rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, name);
        for (int i=0; i<5; i++) rv.setTextViewTextSize(WidgetProvider.TID[i], TypedValue.COMPLEX_UNIT_DIP, time);
    }
}
