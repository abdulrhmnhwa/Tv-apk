package sy.homs.dash;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;

/** تنفيذ الأذان: يمنع التكرار إذا جاء من المنبّه ومن الخدمة معاً */
public class Fire {
    static synchronized boolean claim(Context c, long t) {
        SharedPreferences p = c.getSharedPreferences("p", 0);
        if (t > 0 && p.getLong("firedT", 0) == t) return false;
        p.edit().putLong("firedT", t).putLong("last", System.currentTimeMillis()).commit();
        return true;
    }

    static void go(Context c, Runnable done) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                    | PowerManager.ON_AFTER_RELEASE, "homs:adhan").acquire(30000);
        } catch (Exception ignored) {}
        if (MainActivity.cur != null) {
            MainActivity.wakeNow();
        } else {
            try { c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored) {}
        }
        Takbeer.play(c, done);
    }
}
