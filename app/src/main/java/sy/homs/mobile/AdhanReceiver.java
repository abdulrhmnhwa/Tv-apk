package sy.homs.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import org.json.JSONObject;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context c, Intent i) {
        if (Scheduler.ACT.equals(i.getAction())) {
            JSONObject o = Prefs.obj(c);
            if (o.optInt("en", 1) == 1) {
                int k = Math.max(0, Math.min(5, i.getIntExtra("i", 0)));
                notifyNow(c, k, o.optString("cn", "حمص"));
                if (o.optInt("snd", 1) == 1) {
                    try {
                        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homs:adhan").acquire(20000);
                    } catch (Exception ignored) {}
                    final PendingResult pr = goAsync();
                    Takbeer.play(c.getApplicationContext(), pr::finish);
                }
            }
        }
        Scheduler.schedule(c);
    }

    private void notifyNow(Context c, int k, String city) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("adhan", "مواقيت الصلاة", NotificationManager.IMPORTANCE_HIGH);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(c, "adhan");
            } else {
                b = new Notification.Builder(c);
            }
            PendingIntent pi = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            b.setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("حان الآن موعد " + Scheduler.NAMES[k])
                    .setContentText(city)
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(100 + k, b.build());
        } catch (Exception ignored) {}
    }
}
