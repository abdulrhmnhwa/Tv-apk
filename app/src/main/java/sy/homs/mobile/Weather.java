package sy.homs.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.TimeZone;

/** جلب الطقس الحالي للودجت (Open-Meteo) وتخزينه */
public class Weather {
    static final long FRESH = 30 * 60000L, RETRY = 5 * 60000L;

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("w", 0); }

    static boolean stale(Context c) {
        SharedPreferences p = sp(c);
        String cn = Prefs.obj(c).optString("cn", "حمص");
        long now = System.currentTimeMillis();
        boolean old = !cn.equals(p.getString("cn", "")) || now - p.getLong("at", 0) > FRESH;
        return old && now - p.getLong("try", 0) > RETRY;
    }

    /** يُنفَّذ خارج الخيط الرئيسي */
    static void fetch(Context c) {
        sp(c).edit().putLong("try", System.currentTimeMillis()).apply();
        HttpURLConnection h = null;
        try {
            JSONObject o = Prefs.obj(c);
            URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + o.optDouble("lat", 34.7324)
                    + "&longitude=" + o.optDouble("lng", 36.7137) + "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto");
            h = (HttpURLConnection) u.openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject all = new JSONObject(sb.toString());
            JSONObject cur = all.getJSONObject("current");
            store(c, (int) Math.round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"));
            try {
                JSONObject d = all.getJSONObject("daily");
                storeTomorrow(c, d.getJSONArray("weather_code").getInt(1),
                        (int) Math.round(d.getJSONArray("temperature_2m_max").getDouble(1)),
                        (int) Math.round(d.getJSONArray("temperature_2m_min").getDouble(1)));
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        } finally {
            if (h != null) h.disconnect();
        }
    }

    static void storeTomorrow(Context c, int code, int max, int min) {
        sp(c).edit().putInt("tc", code).putInt("tmax", max).putInt("tmin", min).putLong("tat", System.currentTimeMillis())
                .putString("tcn", Prefs.obj(c).optString("cn", "حمص")).apply();
    }

    /** طقس الغد للودجت، مثل «غداً 🌧️ 12–19°»، وفارغ إن لم تتوفر بيانات حديثة */
    static String tomorrow(Context c) {
        SharedPreferences p = sp(c);
        long at = p.getLong("tat", 0);
        if (at == 0 || System.currentTimeMillis() - at > 8 * 3600000L) return "";
        if (!Prefs.obj(c).optString("cn", "حمص").equals(p.getString("tcn", ""))) return "";
        return "غداً " + describe(p.getInt("tc", 0))[1] + " " + p.getInt("tmin", 0) + "–" + p.getInt("tmax", 0) + "°";
    }

    static void store(Context c, int temp, int code) {
        sp(c).edit().putInt("t", temp).putInt("code", code).putLong("at", System.currentTimeMillis())
                .putString("cn", Prefs.obj(c).optString("cn", "حمص")).apply();
    }

    /** يطلب تحديثاً بالخلفية إذا كانت البيانات قديمة، ثم يحدّث الودجت */
    static void ensure(final Context c) {
        if (!stale(c)) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> { fetch(app); WidgetProvider.refresh(app); }).start();
    }

    /** مثال: «⛅ 17°» أو «⛅ 17° غائم جزئياً»، وفارغ إن لم تتوفر بيانات حديثة */
    static String line(Context c, boolean desc) {
        SharedPreferences p = sp(c);
        long at = p.getLong("at", 0);
        if (at == 0 || System.currentTimeMillis() - at > 4 * 3600000L) return "";
        if (!Prefs.obj(c).optString("cn", "حمص").equals(p.getString("cn", ""))) return "";
        int code = p.getInt("code", 0);
        String[] w = describe(code);
        String emoji = w[1];
        if (code == 0 && isNight(c)) emoji = "🌙";
        return emoji + " " + p.getInt("t", 0) + "°" + (desc ? " " + w[0] : "");
    }

    /** {رمز الطقس، الحرارة} إن كانت البيانات حديثة، وإلا null */
    static int[] cur(Context c) {
        SharedPreferences p = sp(c);
        long at = p.getLong("at", 0);
        if (at == 0 || System.currentTimeMillis() - at > 4 * 3600000L) return null;
        if (!Prefs.obj(c).optString("cn", "حمص").equals(p.getString("cn", ""))) return null;
        return new int[]{p.getInt("code", 0), p.getInt("t", 0)};
    }

    static boolean isNight(Context c) {
        try {
            JSONObject o = Prefs.obj(c);
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(now + Scheduler.TZ * 3600000L);
            long[] r = Scheduler.dayMs(o, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            return now < r[1] || now > r[4];
        } catch (Throwable e) { return false; }
    }

    static String[] describe(int c) {
        if (c == 0) return new String[]{"صافٍ", "☀️"};
        if (c < 3) return new String[]{"غائم جزئياً", "⛅"};
        if (c == 3) return new String[]{"غائم", "☁️"};
        if (c < 50) return new String[]{"ضباب", "🌫️"};
        if (c < 68) return new String[]{"مطر", "🌧️"};
        if (c < 78) return new String[]{"ثلوج", "❄️"};
        if (c < 83) return new String[]{"زخات مطر", "🌦️"};
        return new String[]{"عاصفة رعدية", "⛈️"};
    }
}
