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
    public static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    static int idx = 0;

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
        double D = n.getTimeInMillis() / 86400000.0 + 2440587.5 - 2451545;
        double g = ((357.529 + .98560028 * D) % 360) * R;
        double q = ((280.459 + .98564736 * D) % 360 + 360) % 360;
        double L = (q + 1.915 * Math.sin(g) + .02 * Math.sin(2 * g)) * R;
        double e = (23.439 - 4e-7 * D) * R;
        double dec = Math.asin(Math.sin(e) * Math.sin(L));
        double ra = Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L)) / R / 15;
        ra = (ra + 24) % 24;
        double eq = q / 15 - ra;
        eq = ((eq + 12) % 24 + 24) % 24 - 12;
        double noon = 12 + TZ - lng / 15 - eq + .017;
        double asr = Math.atan(1 / (af + Math.tan(Math.abs(lat * R - dec)))) / R;
        double mg = noon + h(-.833, dec, lat);
        double is = im > 0 ? mg + im / 60 : noon + h(-ia, dec, lat);
        return new double[]{noon - h(-fa, dec, lat), noon - h(-.833, dec, lat), noon, noon + h(asr, dec, lat), mg, is};
    }

    static long next(Context c, long now) {
        JSONObject o = Prefs.obj(c);
        JSONArray adj = o.optJSONArray("adj"), on = o.optJSONArray("on");
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(now + TZ * 3600000L);
        for (int add = 0; add < 3; add++) {
            Calendar day = (Calendar) cal.clone();
            day.add(Calendar.DAY_OF_MONTH, add);
            int y = day.get(Calendar.YEAR), m = day.get(Calendar.MONTH), d = day.get(Calendar.DAY_OF_MONTH);
            double[] t = times(o, y, m, d);
            Calendar mid = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            mid.clear();
            mid.set(y, m, d, 0, 0, 0);
            long base = mid.getTimeInMillis() - TZ * 3600000L;
            for (int i : new int[]{0, 2, 3, 4, 5}) {
                if (on != null && on.optInt(i, 1) == 0) continue;
                double a = adj != null ? adj.optDouble(i, 0) : 0;
                long tr = base + (long) ((t[i] * 60 + a) * 60000);
                if (tr > now + 20000) { idx = i; return tr; }
            }
        }
        return -1;
    }

    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACT);
        JSONObject o = Prefs.obj(c);
        long t = o.optInt("en", 1) == 0 ? -1 : next(c, System.currentTimeMillis());
        i.putExtra("i", idx);
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
