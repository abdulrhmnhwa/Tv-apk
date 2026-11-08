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
            int k = Math.max(0, Math.min(5, i.getIntExtra("i", 0)));
            int kind = i.getIntExtra("k", 0);
            String city = o.optString("cn", "حمص"), name = Scheduler.NAMES[k];
            if (kind == 0 && o.optInt("en", 1) == 1) {
                notifyNow(c, 100 + k, "adhan", "حان الآن موعد " + name, city);
                if (o.optInt("snd", 1) == 1) {
                    try {
                        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homs:adhan").acquire(20000);
                    } catch (Exception ignored) {}
                    final PendingResult pr = goAsync();
                    Takbeer.play(c.getApplicationContext(), pr::finish);
                }
            } else if (kind == 1) {
                notifyNow(c, 200 + k, "remind", "بعد " + Scheduler.PRE + " دقيقة موعد " + name, city);
            } else if (kind == 2) {
                notifyNow(c, 300 + k, "remind", "حانت إقامة صلاة " + name, "استعدوا للصلاة · " + city);
            }
        }
        WidgetProvider.refresh(c);
        LockNotif.refresh(c);
        Scheduler.schedule(c);
    }

    private void notifyNow(Context c, int id, String channel, String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel a = new NotificationChannel("adhan", "تنبيه الأذان", NotificationManager.IMPORTANCE_HIGH);
                a.setSound(null, null);
                nm.createNotificationChannel(a);
                nm.createNotificationChannel(new NotificationChannel("remind", "التذكيرات", NotificationManager.IMPORTANCE_HIGH));
                b = new Notification.Builder(c, channel);
            } else {
                b = new Notification.Builder(c);
                if (channel.equals("remind")) b.setDefaults(Notification.DEFAULT_SOUND);
            }
            PendingIntent pi = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            b.setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle(title).setContentText(text)
                    .setContentIntent(pi).setAutoCancel(true);
            nm.notify(id, b.build());
        } catch (Exception ignored) {}
    }
}
