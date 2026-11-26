package sy.homs.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/** ودجت رمضان: عدّ تنازلي للإفطار/الإمساك + أوقاتهما + مؤشر الختمة. خارج رمضان يعرض كم يوماً بقي. */
public class RamadanWidget extends AppWidgetProvider {
    static final String ACT = "sy.homs.mobile.RAMWIDGET";

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }
    @Override public void onEnabled(Context c) { refresh(c); }
    /** عند تغيير حجم الودجت يُعاد قياس النص ليتسع (كودجت الصلاة). */
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle options) { refresh(c); }
    @Override public void onReceive(Context c, Intent i) {
        if (ACT.equals(i.getAction())) refresh(c); else super.onReceive(c, i);
    }

    public static void refresh(Context c) {
        try {
            AppWidgetManager wm = AppWidgetManager.getInstance(c);
            int[] ids = wm.getAppWidgetIds(new ComponentName(c, RamadanWidget.class));
            if (ids == null || ids.length == 0) return;
            JSONObject o = Prefs.obj(c);
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(now + Scheduler.TZ * 3600000L);
            long today = (now + Scheduler.TZ * 3600000L) / 86400000L;
            long rs = o.optLong("rs", -1), re = o.optLong("re", -1);
            boolean in = rs >= 0 && today >= rs && today < re;
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_ram);
            final String[] st = new String[5];
            boolean cdOn = false;
            applyTheme(rv, "light".equals(o.optString("wth", "dark")));
            long target = now + 3600000L;
            if (in) {
                long[] t0 = Scheduler.dayMs(o, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                Calendar nx = (Calendar) cal.clone();
                nx.add(Calendar.DAY_OF_MONTH, 1);
                long[] t1 = Scheduler.dayMs(o, nx.get(Calendar.YEAR), nx.get(Calendar.MONTH), nx.get(Calendar.DAY_OF_MONTH));
                String lab;
                if (now < t0[0]) { target = t0[0]; lab = "المتبقي للإمساك"; }
                else if (now < t0[4]) { target = t0[4]; lab = "المتبقي للإفطار"; }
                else { target = t1[0]; lab = "المتبقي للإمساك"; }
                txt(rv, st, 0, R.id.rtitle, "🌙 رمضان · اليوم " + (today - rs + 1));
                txt(rv, st, 1, R.id.rlab, lab);
                txt(rv, st, 3, R.id.rtimes, "الإمساك " + Scheduler.fmt(t0[0]) + "  ·  الإفطار " + Scheduler.fmt(t0[4]));
                int pg = o.optInt("rk", -1);
                txt(rv, st, 4, R.id.rkh, pg < 0 ? "📖 اضغط لبدء ختمة رمضان"
                        : "📖 الختمة: الجزء " + Math.min(30, pg / 20 + 1) + " · " + pg + "/604 صفحة");
                setCd(rv, target, now);
                cdOn = Build.VERSION.SDK_INT >= 24;
            } else {
                long left = rs >= 0 && rs > today ? rs - today : -1;
                txt(rv, st, 0, R.id.rtitle, "🌙 رمضان");
                txt(rv, st, 1, R.id.rlab, left > 0 ? "بعد " + left + " يوماً بإذن الله" : "افتح التطبيق لتحديث التاريخ");
                txt(rv, st, 3, R.id.rtimes, "تظهر الإمساكية تلقائياً عند بدء الشهر");
                txt(rv, st, 4, R.id.rkh, "");
                rv.setViewVisibility(R.id.rcd, View.GONE);
                rv.setViewVisibility(R.id.rcdt, View.GONE);
                target = ((now + Scheduler.TZ * 3600000L) / 86400000L + 1) * 86400000L - Scheduler.TZ * 3600000L;
            }
            rv.setOnClickPendingIntent(R.id.rroot, PendingIntent.getActivity(c, 5, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            int[] box = widgetBoxDp(c, wm, ids);
            applyFontScale(rv, scaleFor(c, o.optInt("wfs", 100), st, cdOn, box[0], box[1]));
            wm.updateAppWidget(ids, rv);
            long midnight = ((now + Scheduler.TZ * 3600000L) / 86400000L + 1) * 86400000L - Scheduler.TZ * 3600000L;
            long at = Math.min(target, midnight) + 1000;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent p = PendingIntent.getBroadcast(c, 8, new Intent(c, RamadanWidget.class).setAction(ACT),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (Exception ignored) {}
    }

    static final int[] FS_IDS = {R.id.rtitle, R.id.rlab, R.id.rcd, R.id.rcdt, R.id.rtimes, R.id.rkh};
    static final float[] FS_SP = {15, 12, 32, 24, 13, 12};

    /** نفس نطاق ودجت الصلاة: 70% إلى 150%. دالة نقية قابلة لاختبار JVM. */
    static float fontScale(int pct) {
        return Math.max(70, Math.min(150, pct)) / 100f;
    }

    static void txt(RemoteViews rv, String[] st, int i, int id, String t) { st[i] = t; rv.setTextViewText(id, t); }

    /** أصغر مقاس (عرض، ارتفاع بالـdp) بين النسخ الموضوعة حسب الاتجاه الحالي: عمودي = (MIN_WIDTH, MAX_HEIGHT)، أفقي = (MAX_WIDTH, MIN_HEIGHT). 0 إن كان مجهولاً. */
    static int[] widgetBoxDp(Context c, AppWidgetManager wm, int[] ids) {
        boolean land = c.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int w = Integer.MAX_VALUE, h = Integer.MAX_VALUE;
        for (int id : ids) {
            android.os.Bundle b = wm.getAppWidgetOptions(id);
            int vw = b.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int vh = b.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (vw > 0) w = Math.min(w, vw);
            if (vh > 0) h = Math.min(h, vh);
        }
        return new int[]{w == Integer.MAX_VALUE ? 0 : w, h == Integer.MAX_VALUE ? 0 : h};
    }

    /** يقيس النص الفعلي بخط الجهاز (Paint) عند 100% ثم يختار أكبر نسبة تتسع. المراجع: حشو عمودي 8dp وهوامش 6dp (ومقاس السطر = ascent..descent بلا حشو الخط). */
    static int scaleFor(Context c, int userPct, String[] st, boolean cdOn, int widthDp, int heightDp) {
        try {
            float d = c.getResources().getDisplayMetrics().density;
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            float hSum = 0, wMax = 0;
            int[] idx = {0, 1, 3, 4};
            float[] sp = {FS_SP[0], FS_SP[1], FS_SP[4], FS_SP[5]};
            for (int i = 0; i < 4; i++) {
                String t = st[idx[i]];
                if (t == null || t.length() == 0) continue;
                p.setTypeface(i == 0 ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
                p.setTextSize(sp[i] * d);
                android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
                hSum += (fm.descent - fm.ascent) / d;
                wMax = Math.max(wMax, p.measureText(t) / d);
            }
            if (cdOn) {
                p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                p.setTextSize(FS_SP[2] * d);
                android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
                hSum += (fm.descent - fm.ascent) / d;
                wMax = Math.max(wMax, p.measureText("88:88:88") / d);
            }
            return fitPct(userPct, heightDp > 0 ? heightDp - 14f : 0, widthDp > 0 ? widthDp - 28f : 0, hSum, wMax * 1.04f);
        } catch (Throwable t) { return Math.round(fontScale(userPct) * 100); }
    }

    /**
     * الارتفاع/العرض المتاحان للنص (dp) والمقاسان عند 100%. يُخفَّض مقياس المستخدم إلى أكبر ما يتسع،
     * ويصغّر تلقائياً عند ضيق الودجت (حتى MIN_FIT) كي لا يُقتطع النص. القيم 0 = مجهول فلا حدّ.
     */
    static int fitPct(int userPct, float availH, float availW, float textH1, float textW1) {
        int u = Math.round(fontScale(userPct) * 100);
        int cap = 150;
        if (availH > 0 && textH1 > 0) cap = Math.min(cap, (int) Math.floor(availH / textH1 * 100f));
        if (availW > 0 && textW1 > 0) cap = Math.min(cap, (int) Math.floor(availW / textW1 * 100f));
        return Math.min(u, Math.max(MIN_FIT, cap));
    }

    /** أدنى تصغير تلقائي لاتساع النص: 30% (المستخدم نفسه يبقى 70% إلى 150%). */
    static final int MIN_FIT = 30;

    static void applyFontScale(RemoteViews rv, int pct) {
        float k = Math.max(MIN_FIT, Math.min(150, pct)) / 100f;
        for (int i = 0; i < FS_IDS.length; i++)
            rv.setTextViewTextSize(FS_IDS[i], android.util.TypedValue.COMPLEX_UNIT_DIP, FS_SP[i] * k);
    }

    static void applyTheme(RemoteViews rv, boolean light) {
        rv.setInt(R.id.rroot,"setBackgroundResource",light?R.drawable.widget_bg_day:R.drawable.widget_bg);
        int gold=light?0xFFA46A00:0xFFD6B25E, muted=light?0xFF4C6B5C:0xFFA9C2B4, text=light?0xFF173A2C:0xFFF4F1E8;
        for(int id:new int[]{R.id.rtitle,R.id.rtimes})rv.setTextColor(id,gold);
        for(int id:new int[]{R.id.rlab,R.id.rkh})rv.setTextColor(id,muted);
        for(int id:new int[]{R.id.rcd,R.id.rcdt})rv.setTextColor(id,text);
    }

    private static void setCd(RemoteViews rv, long target, long now) {
        if (Build.VERSION.SDK_INT >= 24) {
            rv.setChronometer(R.id.rcd, SystemClock.elapsedRealtime() + (target - now), null, true);
            rv.setChronometerCountDown(R.id.rcd, true);
            rv.setViewVisibility(R.id.rcd, View.VISIBLE);
            rv.setViewVisibility(R.id.rcdt, View.GONE);
        } else {
            rv.setViewVisibility(R.id.rcd, View.GONE);
            rv.setViewVisibility(R.id.rcdt, View.VISIBLE);
            rv.setTextViewText(R.id.rcdt, Scheduler.fmt(target));
        }
    }
}
