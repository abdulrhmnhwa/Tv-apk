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
        float k = Math.max(70, Math.min(150, pct)) / 100f;
        for (int i = 0; i < FS_IDS.length; i++)
            rv.setTextViewTextSize(FS_IDS[i], android.util.TypedValue.COMPLEX_UNIT_SP, FS_SP[i] * k);
    }

    // Double the visible width from the code82 trial, without taking space from text.
    // Scale both dimensions with the existing widget text setting.
    static android.graphics.Bitmap scaledIndicator(SunMoonIndicator.State state, int gold,
            int pct, android.util.DisplayMetrics metrics) {
        float k = Math.max(70, Math.min(150, pct)) / 100f;
        int w = Math.max(1, Math.round(32f * k * metrics.density));
        int h = Math.max(1, Math.round(60f * k * metrics.density));
        android.graphics.Bitmap source = SunMoonIndicator.draw(state, gold);
        android.graphics.Bitmap result = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        result.setDensity(metrics.densityDpi);
        android.graphics.Canvas canvas = new android.graphics.Canvas(result);
        float scale = w / 32f * (56f / 96f);
        float verticalScale = h / 96f;
        canvas.translate((w - 48f * scale * 2f) / 2f, 0f);
        canvas.scale(scale * 2f, verticalScale);
        canvas.drawBitmap(source, 0f, 0f, new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG));
        source.recycle();
        return result;
    }

    /** أيام العيد (1–3 شوال، 10–13 ذو الحجة): يدفعها التطبيق في الإعداد ed كأزواج [بداية، نهاية-حصرية] بأيام يونكس */
    static boolean isEid(JSONObject o, long day) { return inWin(o, "ed", day); }

    static boolean inWin(JSONObject o, String key, long day) {
        org.json.JSONArray a = o.optJSONArray(key);
        if (a == null) return false;
        for (int i = 0; i + 1 < a.length(); i += 2) if (day >= a.optLong(i) && day < a.optLong(i + 1)) return true;
        return false;
    }

    public static void refresh(Context c) {
        try { RamadanWidget.refresh(c); } catch (Throwable ignored) {}
        try {
            AppWidgetManager wm = AppWidgetManager.getInstance(c);
            int[] ids = wm.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
            if (ids == null || ids.length == 0) return;
            JSONObject o = Prefs.obj(c);
            boolean light = "light".equals(o.optString("wth", "dark"));
            int gold = light ? 0xFFA46A00 : GOLD;
            int muted = light ? 0xFF4C6B5C : MUTED;
            int text = light ? 0xFF173A2C : WHITE;
            int pastName = light ? 0xFF4C6B5C : PAST_N;
            int pastTime = light ? 0xFF4C6B5C : PAST_T;
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
            // RemoteViews may reuse the existing layout, so reset both palettes explicitly.
            rv.setInt(R.id.wroot, "setBackgroundResource", light ? R.drawable.widget_bg_day : R.drawable.widget_bg);
            rv.setInt(R.id.wtip, "setBackgroundResource", light ? R.drawable.tip_bg_day : R.drawable.tip_bg);
            for (int id : new int[]{R.id.wcity, R.id.wlab}) rv.setTextColor(id, muted);
            for (int id : new int[]{R.id.wtom, R.id.wdate}) rv.setTextColor(id, light ? muted : 0xFF8FB09E);
            for (int id : new int[]{R.id.wclk, R.id.wat}) rv.setTextColor(id, text);
            for (int id : new int[]{R.id.wap, R.id.wnext, R.id.wcd, R.id.wcdt}) rv.setTextColor(id, gold);
            String city = o.optString("cn", "حمص"), wl = Weather.line(c, wide(wm, ids[0]));
            String tw = Weather.tomorrow(c); // يُقصّ من الآخر إن ضاق المكان فلا يؤثر على شيء
            rv.setTextViewText(R.id.wcity, wl.isEmpty() ? city : city + "  " + wl);
            rv.setTextViewText(R.id.wtom, tw);
            rv.setViewVisibility(R.id.wtom, tw.isEmpty() ? View.GONE : View.VISIBLE);
            rv.setTextViewText(R.id.wdate, WD[cal.get(Calendar.DAY_OF_WEEK) - 1] + " " + cal.get(Calendar.DAY_OF_MONTH) + " " + MO[cal.get(Calendar.MONTH)]);
            SunMoonIndicator.State sky = SunMoonIndicator.state(now, days);
            rv.setImageViewBitmap(R.id.wsky, scaledIndicator(sky, gold, o.optInt("wfs", 100), c.getResources().getDisplayMetrics()));
            rv.setContentDescription(R.id.wsky, SunMoonIndicator.description(sky));
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
                rv.setTextColor(NID[j], i == hi ? gold : past ? pastName : muted);
                rv.setTextColor(TID[j], i == hi ? gold : past ? pastTime : text);
                rv.setInt(CID[j], "setBackgroundResource", i == hi ? (light ? R.drawable.chip_on_day : R.drawable.chip_on) : 0);
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
            if (isEid(o, (now + Scheduler.TZ * 3600000L) / 86400000L)) tl.add(new String[]{"🎉 عيد مبارك", "كل عام وأنتم بخير، تقبّل الله طاعتكم", ""});
            long dayN = (now + Scheduler.TZ * 3600000L) / 86400000L;
            if (inWin(o, "lt", dayN)) tl.add(new String[]{"🌙 العشر الأواخر", "تحرَّ ليلة القدر في الوتر، وأكثر من: «اللهم إنك عفوٌّ تحب العفو فاعفُ عني»", ""});
            if (inWin(o, "dh", dayN)) tl.add(new String[]{"🕋 عشر ذي الحجة", "أفضل أيام السنة: صُم ما تيسّر منها (ويوم عرفة آكدها) وأكثر من التكبير والتهليل", ""});
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
                if (light) {
                    cv.setTextColor(R.id.wtl, gold);
                    cv.setTextColor(R.id.wtx, text);
                }
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
            // Refresh the progress marker without waking a sleeping phone.
            // Existing prayer/dhikr wake-up alarms above stay unchanged.
            PendingIntent skyTick = PendingIntent.getBroadcast(c, 8,
                    new Intent(c, WidgetProvider.class).setAction(ACT),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.set(AlarmManager.RTC, (now / 300000L + 1) * 300000L, skyTick);
            Weather.ensure(c);
        } catch (Exception ignored) {}
    }
}

/** Day/night time progress, not astronomical sun/moon altitude or moon phase. */
final class SunMoonIndicator {
    static final class State {
        final boolean day;
        final float progress;
        State(boolean day, float progress) { this.day = day; this.progress = progress; }
    }

    static State state(long now, long[][] days) {
        long sunrise = days[1][1], sunset = days[1][4];
        if (now >= sunrise && now < sunset) return between(true, now, sunrise, sunset);
        // Night ends at sunrise, including the interval after Fajr before sunrise.
        return now < sunrise
                ? between(false, now, days[0][4], sunrise)
                : between(false, now, sunset, days[2][1]);
    }

    private static State between(boolean day, long now, long start, long end) {
        float p = end > start ? (float) ((double) (now - start) / (end - start)) : 0f;
        return new State(day, Math.max(0f, Math.min(1f, p)));
    }

    static String description(State s) {
        return (s.day ? "تقدّم النهار، شمس، " : "تقدّم الليل، هلال، ")
                + Math.round(s.progress * 100) + "٪، ليس موقعاً فلكياً";
    }

    static android.graphics.Bitmap draw(State s, int gold) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(48, 96, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(gold); p.setStrokeWidth(3f); p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        float x = 24f, top = 12f, bottom = 84f, y = top + (bottom - top) * s.progress;
        p.setAlpha(65); c.drawLine(x, top, x, bottom, p);
        p.setAlpha(210); c.drawLine(x, top, x, y, p);
        p.setAlpha(30); c.drawCircle(x, y, 12f, p);
        p.setAlpha(255);
        if (s.day) {
            c.drawCircle(x, y, 5.3f, p);
            p.setStrokeWidth(1.4f);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(x + (float)Math.cos(a)*7.4f, y + (float)Math.sin(a)*7.4f,
                        x + (float)Math.cos(a)*9.2f, y + (float)Math.sin(a)*9.2f, p);
            }
        } else {
            // Transparent crescent, so both original widget gradients show through.
            int save = c.saveLayer(12, y-12, 36, y+12, null);
            c.drawCircle(x, y, 8f, p);
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            c.drawCircle(x+4f, y-3f, 7.4f, p);
            p.setXfermode(null); c.restoreToCount(save);
        }
        return b;
    }
}
