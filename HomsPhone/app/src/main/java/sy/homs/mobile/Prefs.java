package sy.homs.mobile;

import android.content.Context;
import org.json.JSONObject;

public class Prefs {
    public static String cfg(Context c) { return c.getSharedPreferences("p", 0).getString("cfg", "{}"); }
    public static void setCfg(Context c, String j) { c.getSharedPreferences("p", 0).edit().putString("cfg", j).apply(); }
    public static JSONObject obj(Context c) {
        try { return new JSONObject(cfg(c)); } catch (Exception e) { return new JSONObject(); }
    }
}
