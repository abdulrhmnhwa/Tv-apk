package sy.homs.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

public class WidgetProvider extends AppWidgetProvider {
    static final String ACT = "sy.homs.mobile.WIDGET";
    static final int GOLD = 0xFFD6B25E, MUTED = 0xFFA9C2B4, WHITE = 0xFFF4F1E8, PAST_N = 0xFF6E8A7B, PAST_T = 0xFF93AB9E;
    static final String[] WD = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
    static final String[] MO = {"كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"};
    static final int[] IDX = {0, 2, 3, 4, 5};
    static final int[] NID = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4};
    static final int[] TID = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.wtom};
    static final int[] CID = {R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }
    @Override public void onEnabled(Context c) { refresh(c); }
    @Override
    public void onReceive(final Context c, Intent i) {
        String a = i.getAction();
        if (ACT.equals(a) || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a)) {
            refresh(c);
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try { if (Weather.stale(c)) Weather.fetch(c); } catch (Throwable ignored) {}
                refresh(c);
                pr.finish();
            }).start();
        } else super.onReceive(c, i);
    }

    static boolean wide(AppWidgetManager wm, int id) {
        try {
            Bundle b = wm.getAppWidgetOptions(id);
            return b != null && b.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) >= 300;
        } catch (Throwable e) { return false; }
    }

    /** أحجام الخطوط الأساسية (sp) كما في widget.xml؛ تُضرب بنسبة الإعداد (70–150%) */
    private static final int[] FS_IDS = {R.id.wcity, R.id.wclk, R.id.wap, R.id.wnext, R.id.wat, R.id.wlab, R.id.wcd, R.id.wcdt,
            R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.wdate};
    private static final float[] FS_SP = {14, 22, 12, 30, 14, 12, 28, 22,
            12, 12, 12, 12, 12, 13, 13, 13, 13, 13, 11};

    private static void applyFontScale(RemoteViews rv, int pct) {
        if (pct == 100) return; // الأحجام الافتراضية من ملف التخطيط
        float k = Math.max(70, Math.min(150, pct)) / 100f;
        for (int i = 0; i < FS_IDS.length; i++)
            rv.setTextViewTextSize(FS_IDS[i], android.util.TypedValue.COMPLEX_UNIT_SP, FS_SP[i] * k);
    }

    public static void refresh(Context c) {
        try {
            AppWidgetManager wm = AppWidgetManager.getInstance(c);
            int[] ids = wm.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
            if (ids == null || ids.length == 0) return;
            JSONObject o = Prefs.obj(c);
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(now + Scheduler.TZ * 3600000L);
            long[][] days = new long[3][];
            for (int d = -1; d <= 1; d++) {
                Calendar x = (Calendar) cal.clone();
                x.add(Calendar.DAY_OF_MONTH, d);
                days[d + 1] = Scheduler.dayMs(o, x.get(Calendar.YEAR), x.get(Calendar.MONTH), x.get(Calendar.DAY_OF_MONTH));
            }
            WidgetTip.nightBefore = (int) ((days[1][0] - WidgetTip.thirdAt(days[0][4], days[1][0], o.optInt("lb", 0))) / 60000L);
            long lastT = -1;
            int lastI = -1;
            for (long[] r : days) for (int i : IDX) if (r[i] <= now && r[i] > lastT) { lastT = r[i]; lastI = i; }
            int iq = lastI >= 0 ? Scheduler.iq(o, lastI) : 0;
            String label, name;
            long target;
            int hi;
            boolean iqamaMode = false;
            int nIdx = -1;
            if (lastI >= 0 && iq > 0 && now < lastT + iq * 60000L) {
                iqamaMode = true; label = "المتبقي للإقامة"; name = Scheduler.NAMES[lastI]; target = lastT + iq * 60000L; hi = lastI;
            } else {
                long nt = Long.MAX_VALUE;
                int ni = 0;
                for (long[] r : days) for (int i = 0; i < 6; i++) if (r[i] > now && r[i] < nt) { nt = r[i]; ni = i; }
                nIdx = ni; label = ni == 1 ? "المتبقي للشروق" : "المتبقي للأذان"; name = Scheduler.NAMES[ni]; target = nt; hi = ni == 1 ? 2 : ni;
            }
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            String city = o.optString("cn", "حمص"), wl = Weather.line(c, wide(wm, ids[0]));
            String tw = Weather.tomorrow(c); // يُقصّ من الآخر إن ضاق المكان فلا يؤثر على شيء
            rv.setTextViewText(R.id.wcity, wl.isEmpty() ? city : city + "  " + wl);
            rv.setTextViewText(R.id.wtom, tw);
            rv.setViewVisibility(R.id.wtom, tw.isEmpty() ? View.GONE : View.VISIBLE);
            rv.setTextViewText(R.id.wdate, WD[cal.get(Calendar.DAY_OF_WEEK) - 1] + " " + cal.get(Calendar.DAY_OF_MONTH) + " " + MO[cal.get(Calendar.MONTH)]);
            rv.setTextViewText(R.id.wlab, label);
            rv.setTextViewText(R.id.wnext, name);
            rv.setTextViewText(R.id.wat, (iqamaMode ? "الإقامة عند " : nIdx == 1 ? "الشروق عند " : "الأذان عند ") + Scheduler.fmt(target));
            if (Build.VERSION.SDK_INT >= 24) {
                rv.setChronometer(R.id.wcd, SystemClock.elapsedRealtime() + (target - now), null, true);
                rv.setChronometerCountDown(R.id.wcd, true);
                rv.setViewVisibility(R.id.wcd, View.VISIBLE);
                rv.setViewVisibility(R.id.wcdt, View.GONE);
            } else {
                rv.setViewVisibility(R.id.wcd, View.GONE);
                rv.setViewVisibility(R.id.wcdt, View.VISIBLE);
                rv.setTextViewText(R.id.wcdt, Scheduler.fmt(target));
            }
            for (int j = 0; j < 5; j++) {
                int i = IDX[j];
                rv.setTextViewText(NID[j], Scheduler.NAMES[i]);
                rv.setTextViewText(TID[j], Scheduler.fmt(days[1][i]));
                boolean past = i != hi && days[1][i] <= now; // الصلوات التي مضى وقتها تُخفَّف، والقادمة بالذهبي
                rv.setTextColor(NID[j], i == hi ? GOLD : past ? PAST_N : MUTED);
                rv.setTextColor(TID[j], i == hi ? GOLD : past ? PAST_T : WHITE);
                rv.setInt(CID[j], "setBackgroundResource", i == hi ? R.drawable.chip_on : 0);
            }
            // شريط الذكر الذكي: بحسب الوقت واليوم والطقس
            int[] iqs = new int[6];
            for (int i = 0; i < 6; i++) iqs[i] = Scheduler.iq(o, i);
            int[] wx = Weather.cur(c);
            String[] tip = WidgetTip.pick(now, days[1], iqs, cal.get(Calendar.DAY_OF_WEEK),
                    wx == null ? -1 : wx[0], wx == null ? 20 : wx[1]);
            // الشريط يتبادل كل 10 ثوانٍ (ViewFlipper) بين: التنبيه الذي يحين وقته، وتنبيه الصيام، وتنبيه «ما زال بإمكانك قراءة أذكار الصباح/المساء»
            java.util.ArrayList<String[]> tl = new java.util.ArrayList<>();
            tl.add(tip);
            String[] ft = WidgetTip.fastTip(now, days[1], iqs, Scheduler.fastSqd(o, cal, 1), Scheduler.fastSqd(o, cal, 0),
                    Scheduler.nfdCode(o, cal, 1), Scheduler.nfdCode(o, cal, 0));
            if (ft != null) tl.add(ft);
            String[] fr = WidgetTip.fridayTip(now, days[1], cal.get(Calendar.DAY_OF_WEEK), o.optInt("rfk", 0) == 1, o.optInt("rfg", 0) == 1);
            if (fr != null) tl.add(fr);
            int mi = wx == null ? -1 : WidgetTip.mosqueIdx(now, days[1], iqs);
            if (mi >= 0) { // مطر أو برد شديد أو رياح قوية: من 5 دقائق قبل الأذان حتى الإقامة
                String[] al = Weather.mosqueAlert(wx[0], wx[1], wx.length > 2 ? wx[2] : 0, WidgetTip.NM[mi]);
                if (al != null) tl.add(new String[]{al[0], al[1], ""});
            }
            String[] ak = WidgetTip.adhkarTip(now, days[1], tip[2]);
            if (ak != null) tl.add(ak);
            rv.removeAllViews(R.id.wflip);
            rv.removeAllViews(R.id.wone);
            // تنبيه واحد فقط: يُعرض ثابتاً بلا تبديل (ViewFlipper مع عنصر واحد كان يومض)
            boolean one = tl.size() == 1;
            rv.setViewVisibility(R.id.wflip, one ? android.view.View.GONE : android.view.View.VISIBLE);
            rv.setViewVisibility(R.id.wone, one ? android.view.View.VISIBLE : android.view.View.GONE);
            int fsp = o.optInt("wfs", 100);
            float fk = Math.max(70, Math.min(150, fsp)) / 100f;
            for (int j = 0; j < tl.size(); j++) {
                String[] x = tl.get(j);
                RemoteViews cv = new RemoteViews(c.getPackageName(), R.layout.widget_tip);
                cv.setTextViewText(R.id.wtl, x[0]);
                cv.setTextViewText(R.id.wtx, x[1]);
                if (fsp != 100) {
                    cv.setTextViewTextSize(R.id.wtl, android.util.TypedValue.COMPLEX_UNIT_SP, 13 * fk);
                    cv.setTextViewTextSize(R.id.wtx, android.util.TypedValue.COMPLEX_UNIT_SP, 13 * fk);
                }
                Intent ti = new Intent(c, MainActivity.class);
                if (!x[2].isEmpty()) ti.putExtra("open", x[2]);
                PendingIntent pi = PendingIntent.getActivity(c, 31 + j, ti, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                cv.setOnClickPendingIntent(R.id.wtp, pi);
                rv.addView(one ? R.id.wone : R.id.wflip, cv);
                if (j == 0) rv.setOnClickPendingIntent(R.id.wtip, pi);
            }
            if (!one) rv.setDisplayedChild(R.id.wflip, 0);
            rv.setOnClickPendingIntent(R.id.wroot, PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            applyFontScale(rv, o.optInt("wfs", 100));
            wm.updateAppWidget(ids, rv);

            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent p = PendingIntent.getBroadcast(c, 7, new Intent(c, WidgetProvider.class).setAction(ACT),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            // أقرب تحديث لازم: الصلاة القادمة، أو تغيّر نافذة الأذكار، أو تدوير الذكر (كل نصف ساعة)
            long slot = (now / 1800000L + 1) * 1800000L;
            long tzMs = Scheduler.TZ * 3600000L, midnight = ((now + tzMs) / 86400000L + 1) * 86400000L - tzMs; // لتبديل "غداً" إلى "اليوم" عند منتصف الليل
            long crit = Math.min(Math.min(target, midnight), WidgetTip.nextBoundary(now, new long[][]{days[1], days[2]}, iqs));
            long at = Math.min(crit, slot) + 1000;
            // التوقيت الدقيق فقط عند حدث حقيقي (صلاة/نافذة ذكر)؛ أما تدوير الذكر كل نصف ساعة فيكفيه منبّه غير دقيق يوفّر البطارية
            if (crit <= slot) {
                try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); }
                catch (Exception e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); }
            } else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            Weather.ensure(c);
        } catch (Exception ignored) {}
    }
}
