package sy.homs.dash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context c, Intent i) {
        String act = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(act) || "android.intent.action.QUICKBOOT_POWERON".equals(act)) {
            try {
                c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
        }
        if (Scheduler.ACT.equals(i.getAction()) && Prefs.on(c)) {
            c.getSharedPreferences("p", 0).edit().putLong("last", System.currentTimeMillis()).apply();
            try {
                PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "homs:adhan");
                wl.acquire(30000);
            } catch (Exception ignored) {}
            try {
                c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
            MainActivity.wakeNow();
            final PendingResult pr = goAsync();
            Takbeer.play(c.getApplicationContext(), pr::finish);
        }
        Scheduler.schedule(c);
    }
}
