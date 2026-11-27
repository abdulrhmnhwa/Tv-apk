package sy.homs.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * ودجت الختمة: حلقة تقدّم + «اليوم N من M» + ورد اليوم الثابت (مقروء/مطلوب).
 * RemoteViews فقط (TextView/ImageView/LinearLayout)، بلا setGravity ولا setInt غير الموجودة في ودجت الصلاة، ولا Chronometer.
 * الحلقة تُرسم Bitmap صغيراً. الإعدادات المشتركة: wfs (الخط) و wth (نهاري/ليلي) كالودجتين الآخرين.
 */
public class KhatmaWidget extends AppWidgetProvider {
    static final String ACT = "sy.homs.mobile.KHATWIDGET";
    static final int KP = 604;

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }
    @Override public void onEnabled(Context c) { refresh(c); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle o) { refresh(c); }
    @Override public void onReceive(Context c, Intent i) {
        if (ACT.equals(i.getAction())) refresh(c); else super.onReceive(c, i);
    }

    /** حالة العرض (نقية، قابلة لاختبار JVM). */
    static final class State {
        boolean has;       // توجد ختمة
        int pages, pct, juz, dayN, days, goal, read;
        boolean done, finished, overPlan;
    }

    /** كل الأيام أرقام أيام محلية (بتوقيت سوريا). dd/db: يوم آخر ترسيخ وصفحات بداية ذلك اليوم. */
    static State compute(boolean has, int pages, long startDay, double days, long dd, int db, long today) {
        State s = new State();
        s.has = has;
        if (!has) return s;
        s.pages = Math.max(0, Math.min(KP, pages));
        s.pct = Math.round(s.pages * 100f / KP);
        s.juz = Math.min(30, khFin(s.pages) + 1);
        s.days = (int) Math.max(1, Math.round(days));
        s.dayN = (int) Math.max(1, today - startDay + 1);
        s.overPlan = s.dayN > s.days;
        s.goal = goal(days);
        // اليوم المسجّل ≠ اليوم الحالي: لا قراءة مسجّلة بعد، فالمقروء اليوم = 0 (الصفحات لم تتغيّر منذ آخر تسجيل).
        s.read = dd == today ? Math.max(0, s.pages - db) : 0;
        s.finished = s.pages >= KP;
        s.done = s.finished || s.read >= s.goal;
        return s;
    }

    /** الورد اليومي الثابت = 604 ÷ عدد أيام الختمة (تقريب للأعلى). الوحدة الوحيدة التي تُعدَّل إن اختار المستخدم رقماً خاصاً. */
    static int goal(double days) { return (int) Math.max(1, Math.min(KP, Math.ceil(KP / Math.max(1.0, Math.round(days))))); }

    static int khFin(int p) {
        int n = 0;
        while (n < 30 && keEnd(n) <= p) n++;
        return n;
    }
    /** نهاية كل جزء بالصفحات كما في التطبيق: 21 ثم +20 حتى 604. */
    static int keEnd(int n) { return n == 0 ? 21 : (n == 29 ? 604 : 21 + n * 20); }

    public static void refresh(Context c) {
        try {
            AppWidgetManager wm = AppWidgetManager.getInstance(c);
            int[] ids = wm.getAppWidgetIds(new ComponentName(c, KhatmaWidget.class));
            if (ids == null || ids.length == 0) return;
            JSONObject o = Prefs.obj(c);
            long now = System.currentTimeMillis();
            long today = (now + Scheduler.TZ * 3600000L) / 86400000L;
            JSONObject k = o.optJSONObject("kw");
            State st = k == null ? compute(false, 0, 0, 30, 0, 0, today)
                    : compute(true, k.optInt("p", 0), Math.round(k.optDouble("s", 0) / 86400000.0), k.optDouble("d", 30), k.optLong("dd", -1), k.optInt("db", 0), today);
            boolean light = "light".equals(o.optString("wth", "dark"));
            int userPct = o.optInt("wfs", 100);
            for (int id : ids) {
                int[] box = box(c, wm, id);
                boolean large = box[1] >= 170;
                RemoteViews rv = new RemoteViews(c.getPackageName(), large ? R.layout.widget_khat_l : R.layout.widget_khat);
                build(c, rv, st, k == null ? "" : k.optString("n", ""), light, userPct, box[0], box[1], large);
                rv.setOnClickPendingIntent(R.id.kroot, PendingIntent.getActivity(c, 6, new Intent(c, MainActivity.class).putExtra("open", "kh").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                wm.updateAppWidget(id, rv);
            }
            long midnight = (today + 1) * 86400000L - Scheduler.TZ * 3600000L;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent p = PendingIntent.getBroadcast(c, 9, new Intent(c, KhatmaWidget.class).setAction(ACT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight + 1000, p);
            else am.set(AlarmManager.RTC_WAKEUP, midnight + 1000, p);
        } catch (Exception ignored) {}
    }

    /** مقاس الودجت بالـdp حسب الاتجاه (كودجت رمضان). 0 = مجهول. */
    static int[] box(Context c, AppWidgetManager wm, int id) {
        boolean land = c.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        android.os.Bundle b = wm.getAppWidgetOptions(id);
        int w = b == null ? 0 : b.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int h = b == null ? 0 : b.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        return new int[]{w > 0 ? w : 250, h > 0 ? h : 110};
    }

    static String[] texts(State s, boolean large, boolean showMore, String name) {
        String[] t = new String[4];
        String nm = name == null ? "" : name.replaceAll("\\s+", " ").trim();
        t[0] = nm.length() == 0 ? "الختمة" : nm;
        if (!s.has) {
            t[1] = "لا توجد ختمة جارية"; t[2] = "افتح التطبيق لبدء ختمة"; t[3] = "";
            return t;
        }
        t[1] = "اليوم " + s.dayN + " من " + s.days;
        t[2] = s.finished ? "أتممت الختمة ✓" : (s.done ? "أتممت ورد اليوم ✓" + (s.read > s.goal ? " (قرأت " + s.read + ")" : "") : "قرأت اليوم " + s.read + " من " + s.goal + " صفحة");
        t[3] = s.pages + " من " + KP + " صفحة" + (large ? " · الجزء " + s.juz : "");
        if (!showMore) t[3] = "";
        return t;
    }

    static void build(Context c, RemoteViews rv, State s, String name, boolean light, int userPct, int wDp, int hDp, boolean large) {
        float d = c.getResources().getDisplayMetrics().density;
        int gold = light ? 0xFFA46A00 : 0xFFE6B94C, muted = light ? 0xFF4C6B5C : 0xFFA9C2B4, text = light ? 0xFF173A2C : 0xFFF4F1E8;
        int ok = light ? 0xFF1F7A4A : 0xFF7FD6A0, track = light ? 0xFFC9D8C6 : 0xFF1D5A45;
        rv.setInt(R.id.kroot, "setBackgroundResource", light ? R.drawable.widget_bg_day : R.drawable.widget_bg);
        boolean showMore = hDp >= 90 && wDp >= 150;
        boolean narrow = !large && wDp < 150;
        String[] t = texts(s, large, showMore, name);
        if (narrow && s.has) {
            t[2] = s.finished ? "أتممت الختمة ✓" : (s.done ? "أتممت الورد ✓" + (s.read > s.goal ? " (" + s.read + ")" : "") : "قرأت " + s.read + " من " + s.goal);
            t[3] = s.pct + "%";
        }
        // تحجيم الخط: نسبة المستخدم المشتركة، ثم تصغير تلقائي ليتسع النص (كودجت رمضان).
        float[] sp = {15, 12, 12, 12};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float ringDp = large ? Math.max(40, Math.min(wDp - 40, hDp - 110)) : Math.max(36, Math.min(hDp - 16, wDp * 0.36f));
        float colW = large ? wDp - 28 : (narrow ? wDp - 28 : wDp - 20 - ringDp - 8 - 4);
        float hSum = 0, wMax = 0;
        for (int i = 0; i < 4; i++) {
            if (t[i].length() == 0) continue;
            p.setTypeface(i == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextSize(sp[i] * d);
            Paint.FontMetrics fm = p.getFontMetrics();
            hSum += (fm.descent - fm.ascent) / d + 2;
            if (i > 0) wMax = Math.max(wMax, p.measureText(t[i]) / d);
        }
        boolean showBar = s.has && !narrow;
        if (showBar) hSum += 9;
        float availH = large ? Math.max(40, hDp - 12 - ringDp - 12) : hDp - 12;
        int pct = RamadanWidget.fitPct(userPct, availH, colW, hSum, wMax * 1.04f);
        float k = Math.max(RamadanWidget.MIN_FIT, Math.min(150, pct)) / 100f;
        int[] ids = {R.id.ktitle, R.id.kl1, R.id.kl2, R.id.kl3};
        int[] cols = {gold, muted, s.has && s.done ? ok : gold, muted};
        float tsp = sp[0] * k;
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(tsp * d);
        float tw = p.measureText(t[0]) / d;
        if (tw > colW) tsp = Math.max(Math.min(tsp, 11f), tsp * colW / tw);
        for (int i = 0; i < 4; i++) {
            rv.setTextViewText(ids[i], t[i]);
            rv.setTextColor(ids[i], cols[i]);
            rv.setTextViewTextSize(ids[i], android.util.TypedValue.COMPLEX_UNIT_DIP, i == 0 ? tsp : sp[i] * k);
            rv.setViewVisibility(ids[i], t[i].length() == 0 ? View.GONE : View.VISIBLE);
        }
        rv.setViewVisibility(R.id.kbar, showBar ? View.VISIBLE : View.GONE);
        if (showBar) {
            float bw = Math.max(60, Math.min(colW, 150f));
            float frac = s.finished ? 1f : Math.min(1f, s.read / (float) Math.max(1, s.goal));
            rv.setImageViewBitmap(R.id.kbar, bar(Math.round(bw * d), Math.round(7 * d), frac, s.done ? ok : gold, track));
        }
        int px = Math.max(24, Math.round(ringDp * d));
        rv.setViewVisibility(R.id.kring, narrow ? View.GONE : View.VISIBLE);
        rv.setImageViewBitmap(R.id.kring, ring(px, s.has ? s.pct : 0, s.has ? s.pct + "%" : "", gold, track, text, s.has && s.finished ? ok : gold, k));
    }

    /** شريط ورد اليوم: يمتلئ من اليمين (اتجاه عربي). ذهبي أثناء القراءة، أخضر بعد إتمام الورد. */
    static Bitmap bar(int w, int h, float frac, int fill, int track) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setDensity(Bitmap.DENSITY_NONE);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = h / 2f;
        p.setColor(track);
        cv.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
        if (frac > 0) {
            float fw = Math.max(h, w * frac);
            p.setColor(fill);
            cv.drawRoundRect(new RectF(w - fw, 0, w, h), r, r, p);
        }
        return b;
    }

    static Bitmap ring(int px, int pct, String label, int gold, int track, int text, int arc, float k) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        b.setDensity(Bitmap.DENSITY_NONE);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float sw = Math.max(4f, px * 0.11f);
        RectF r = new RectF(sw / 2, sw / 2, px - sw / 2, px - sw / 2);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setColor(track);
        cv.drawArc(r, 0, 360, false, p);
        if (pct > 0) {
            p.setColor(arc);
            p.setStrokeCap(Paint.Cap.ROUND);
            cv.drawArc(r, -90, 360f * Math.min(100, pct) / 100f, false, p);
        }
        if (label.length() > 0) {
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(text);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextAlign(Paint.Align.CENTER);
            float size = px * 0.26f;
            tp.setTextSize(size);
            float maxW = px - 2 * sw - px * 0.06f;
            float w = tp.measureText(label);
            if (w > maxW) tp.setTextSize(size * maxW / w);
            Paint.FontMetrics fm = tp.getFontMetrics();
            cv.drawText(label, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, tp);
        }
        return b;
    }
}
