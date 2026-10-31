package sy.homs.dash;

import android.content.Context;

public class Prefs {
    public static boolean on(Context c) { return c.getSharedPreferences("p", 0).getBoolean("adhan", true); }
    public static void set(Context c, boolean v) { c.getSharedPreferences("p", 0).edit().putBoolean("adhan", v).apply(); }
}
