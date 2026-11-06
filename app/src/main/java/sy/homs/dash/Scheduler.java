package sy.homs.dash;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.TimeZone;

public class Scheduler {
    public static final String ACT = "sy.homs.dash.ADHAN";
    static final double LAT = 34.7324, LNG = 36.7137, R = Math.PI / 180;
    static final int TZ = 3;
    static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    static int idx = 0;

    static String fmt(long ms) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(ms + TZ * 3600000L);
        int H = c.get(Calendar.HOUR_OF_DAY), M = c.get(Calendar.MINUTE);
        return (H % 12 == 0 ? 12 : H % 12) + ":" + (M < 10 ? "0" : "") + M + (H >= 12 ? " م" : " ص");
    }

    /** الفجر، الشروق، الظهر، العصر، المغرب، العشاء بالساعات (توقيت سوريا) */
    static double[] times(int y, int m, int d) {
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
        double noon = 12 + TZ - LNG / 15 - eq + .017;
        double asr = Math.atan(1 / (1 + Math.tan(Math.abs(LAT * R - dec)))) / R;
        return new double[]{noon - h(-18, dec), noon - h(-.833, dec), noon, noon + h(asr, dec), noon + h(-.833, dec), noon + h(-17, dec)};
    }

    private static double h(double a, double dec) {
        return Math.acos((Math.sin(a * R) - Math.sin(LAT * R) * Math.sin(dec)) / (Math.cos(LAT * R) * Math.cos(dec))) / R / 15;
    }

    static long next(long now, double[] adj) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(now + TZ * 3600000L);
        for (int add = 0; add < 3; add++) {
            Calendar day = (Calendar) c.clone();
            day.add(Calendar.DAY_OF_MONTH, add);
            int y = day.get(Calendar.YEAR), m = day.get(Calendar.MONTH), d = day.get(Calendar.DAY_OF_MONTH);
            double[] t = times(y, m, d);
            Calendar mid = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            mid.clear();
            mid.set(y, m, d, 0, 0, 0);
            long base = mid.getTimeInMillis() - TZ * 3600000L;
            for (int i : new int[]{0, 2, 3, 4, 5}) {
                long tr = base + (long) ((t[i] * 60 + adj[i]) * 60000);
                if (tr > now + 20000) { idx = i; return tr; }
            }
        }
        return -1;
    }

    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long t = next(System.currentTimeMillis(), Prefs.adj(c));
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACT);
        i.putExtra("t", t);
        i.putExtra("i", idx);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!Prefs.on(c) || t < 0) {
            am.cancel(pi);
            c.getSharedPreferences("p", 0).edit().putLong("next", 0).apply();
            return;
        }
        c.getSharedPreferences("p", 0).edit().putLong("next", t).putString("nn", NAMES[idx]).apply();
        try {
            PendingIntent show = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), pi);
        } catch (Exception e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }
}
