package sy.homs.dash;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

public class Prefs {
    public static boolean on(Context c) { return c.getSharedPreferences("p", 0).getBoolean("adhan", true); }
    public static void set(Context c, boolean v) { c.getSharedPreferences("p", 0).edit().putBoolean("adhan", v).apply(); }
    public static String cfg(Context c) { return c.getSharedPreferences("p", 0).getString("cfg", "{}"); }
    public static void setCfg(Context c, String j) { c.getSharedPreferences("p", 0).edit().putString("cfg", j).apply(); }
    /** تعديل الدقائق لكل صلاة: الفجر، الشروق، الظهر، العصر، المغرب، العشاء */
    public static double[] adj(Context c) {
        double[] r = new double[6];
        try {
            JSONArray a = new JSONObject(cfg(c)).optJSONArray("adj");
            if (a != null) for (int i = 0; i < 6; i++) r[i] = a.optDouble(i, 0);
        } catch (Exception ignored) {}
        return r;
    }
}
