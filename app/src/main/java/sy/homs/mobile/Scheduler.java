package sy.homs.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

public class Scheduler {
    public static final String ACT = "sy.homs.mobile.ADHAN";
    static final double R = Math.PI / 180;
    static final int TZ = 3;
    static final double DDH = 1, DMG = 2; // احتياط الظهر +1 د والمغرب +2 د (معايرة وزارة الأوقاف)
    static final int PRE = 15; // دقائق التذكير قبل الأذان
    public static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    static final int[] IQ_DEF = {15, 0, 15, 15, 5, 15};
    static int evIdx = 0, evKind = 0; // kind: 0 أذان، 1 تذكير قبل الأذان، 2 تذكير الإقامة

    static double h(double a, double dec, double lat) {
        return Math.acos((Math.sin(a * R) - Math.sin(lat * R) * Math.sin(dec)) / (Math.cos(lat * R) * Math.cos(dec))) / R / 15;
    }

    /** الفجر، الشروق، الظهر، العصر، المغرب، العشاء بالساعات (توقيت سوريا، بدون تعديل الدقائق) */
    static double[] times(JSONObject o, int y, int m, int d) {
        double lat = o.optDouble("lat", 34.7324), lng = o.optDouble("lng", 36.7137);
        double fa = o.optDouble("fa", 18), ia = o.optDouble("ia", 17), im = o.optDouble("im", 0), af = o.optDouble("asr", 1);
        Calendar n = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        n.clear();
        n.set(y, m, d, 12, 0, 0);
        double D = n.getTimeInMillis() / 86400000.0 + 2440587.5 - 2451545, T = D / 36525;
        // حساب فلكي دقيق لموقع الشمس (معادلات NOAA) + معايرة على جدول وزارة الأوقاف لمدينة حمص
        double L0 = ((280.46646 + 36000.76983 * T + .0003032 * T * T) % 360 + 360) % 360;
        double M = (357.52911 + 35999.05029 * T - .0001537 * T * T) * R;
        double C = (1.914602 - .004817 * T - .000014 * T * T) * Math.sin(M) + (.019993 - .000101 * T) * Math.sin(2 * M) + .000289 * Math.sin(3 * M);
        double om = (125.04 - 1934.136 * T) * R;
        double lam = (L0 + C - .00569 - .00478 * Math.sin(om)) * R;
        double ep = (23.439291 - .0130042 * T + .00256 * Math.cos(om)) * R;
        double dec = Math.asin(Math.sin(ep) * Math.sin(lam));
        double yy = Math.pow(Math.tan(ep / 2), 2), l0 = L0 * R, ec = .016708634 - .000042037 * T;
        double eq = 4 * (yy * Math.sin(2 * l0) - 2 * ec * Math.sin(M) + 4 * ec * yy * Math.sin(M) * Math.cos(2 * l0)
                - .5 * yy * yy * Math.sin(4 * l0) - 1.25 * ec * ec * Math.sin(2 * M)) / R / 60;
        double noon = 12 + TZ - lng / 15 - eq;
        double asr = Math.atan(1 / (af + Math.tan(Math.abs(lat * R - dec)))) / R;
        double mg = noon + h(-.833, dec, lat) + DMG / 60;
        double is = im > 0 ? mg + im / 60 : noon + h(-ia, dec, lat);
        return new double[]{noon - h(-fa, dec, lat), noon - h(-.833, dec, lat), noon + DDH / 60, noon + h(asr, dec, lat), mg, is};
    }

    /** أوقات اليوم بالميلي ثانية (UTC) بعد تطبيق تعديل الدقائق */
    static long[] dayMs(JSONObject o, int y, int m, int d) {
        double[] t = times(o, y, m, d);
        JSONArray adj = o.optJSONArray("adj");
        Calendar mid = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        mid.clear();
        mid.set(y, m, d, 0, 0, 0);
        long base = mid.getTimeInMillis() - TZ * 3600000L;
        long[] r = new long[6];
        for (int i = 0; i < 6; i++)
            r[i] = base + Math.round(t[i] * 60 + (adj != null ? adj.optDouble(i, 0) : 0)) * 60000L; // تقريب لأقرب دقيقة كما في واجهة التطبيق
        return r;
    }

    static int iq(JSONObject o, int i) {
        JSONArray a = o.optJSONArray("iq");
        return a != null ? a.optInt(i, IQ_DEF[i]) : IQ_DEF[i];
    }

    static String fmt(long ms) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(ms + TZ * 3600000L);
        int H = c.get(Calendar.HOUR_OF_DAY), M = c.get(Calendar.MINUTE);
        return (H % 12 == 0 ? 12 : H % 12) + ":" + (M < 10 ? "0" : "") + M + (H >= 12 ? " م" : " ص");
    }

    static long next(Context c, long now) {
        JSONObject o = Prefs.obj(c);
        JSONArray on = o.optJSONArray("on");
        boolean en = o.optInt("en", 1) == 1, pre = o.optInt("pre", 0) == 1, iqr = o.optInt("iqr", 0) == 1;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(now + TZ * 3600000L);
        long best = Long.MAX_VALUE;
        for (int add = 0; add < 3; add++) {
            Calendar day = (Calendar) cal.clone();
            day.add(Calendar.DAY_OF_MONTH, add);
            long[] r = dayMs(o, day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
            for (int i : new int[]{0, 2, 3, 4, 5}) {
                if (on != null && on.optInt(i, 1) == 0) continue;
                long[] cand = {
                        pre ? r[i] - PRE * 60000L : -1,
                        en ? r[i] : -1,
                        iqr && iq(o, i) > 0 ? r[i] + iq(o, i) * 60000L : -1};
                int[] kind = {1, 0, 2};
                for (int k = 0; k < 3; k++) {
                    if (cand[k] > now + 20000 && cand[k] < best) { best = cand[k]; evIdx = i; evKind = kind[k]; }
                }
            }
            // تذكيرات الأذكار والضحى: 3 = أذكار الصباح، 4 = أذكار المساء، 5 = صلاة الضحى
            // 6 = الثلث الأخير من الليل (من مغرب أمس إلى فجر اليوم)
            Calendar pd = (Calendar) day.clone();
            pd.add(Calendar.DAY_OF_MONTH, -1);
            long[] pr = dayMs(o, pd.get(Calendar.YEAR), pd.get(Calendar.MONTH), pd.get(Calendar.DAY_OF_MONTH));
            long[] ex = {WidgetTip.morningAt(r), WidgetTip.eveningAt(r), WidgetTip.dhuhaAt(r), WidgetTip.thirdAt(pr[4], r[0], o.optInt("lb", 0))};
            int[] ek = {3, 4, 5, 6};
            boolean[] eo = {o.optInt("rm", 1) == 1, o.optInt("re", 1) == 1, o.optInt("rd", 1) == 1, o.optInt("rl", 0) == 1};
            for (int k = 0; k < 4; k++) {
                if (eo[k] && ex[k] > now + 20000 && ex[k] < best) { best = ex[k]; evIdx = 0; evKind = ek[k]; }
            }
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long t = next(c, System.currentTimeMillis());
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACT);
        i.putExtra("i", evIdx);
        i.putExtra("k", evKind);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (t < 0) { am.cancel(pi); return; }
        try {
            PendingIntent show = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), pi);
        } catch (Exception e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }
}
