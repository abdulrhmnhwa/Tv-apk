package sy.homs.mobile;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/** إشعار دائم (يظهر على شاشة القفل) فيه عدّاد الصلاة القادمة، يعمل ذاتياً بدون تحديث كل ثانية */
public class LockNotif {
    static final String ACT = "sy.homs.mobile.LOCK";
    static final int ID = 500;
    static final int[] IDX = {0, 2, 3, 4, 5};

    public static void refresh(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent al = PendingIntent.getBroadcast(c, 9, new Intent(c, AdhanReceiver.class).setAction(ACT),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            JSONObject o = Prefs.obj(c);
            if (o.optInt("ln", 1) == 0) { nm.cancel(ID); am.cancel(al); return; }

            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(now + Scheduler.TZ * 3600000L);
            long[][] days = new long[3][];
            for (int d = -1; d <= 1; d++) {
                Calendar x = (Calendar) cal.clone();
                x.add(Calendar.DAY_OF_MONTH, d);
                days[d + 1] = Scheduler.dayMs(o, x.get(Calendar.YEAR), x.get(Calendar.MONTH), x.get(Calendar.DAY_OF_MONTH));
            }
            long lastT = -1;
            int lastI = -1;
            for (long[] r : days) for (int i : IDX) if (r[i] <= now && r[i] > lastT) { lastT = r[i]; lastI = i; }
            int iq = lastI >= 0 ? Scheduler.iq(o, lastI) : 0;
            String title, text, city = o.optString("cn", "حمص");
            long target;
            if (lastI >= 0 && iq > 0 && now < lastT + iq * 60000L) {
                target = lastT + iq * 60000L;
                title = "إقامة صلاة " + Scheduler.NAMES[lastI];
                text = "الإقامة عند " + Scheduler.fmt(target) + " · " + city;
            } else {
                long nt = Long.MAX_VALUE;
                int ni = 0;
                for (long[] r : days) for (int i = 0; i < 6; i++) if (r[i] > now && r[i] < nt) { nt = r[i]; ni = i; }
                target = nt;
                title = ni == 1 ? "باقي على الشروق" : "باقي على صلاة " + Scheduler.NAMES[ni];
                text = (ni == 1 ? "الشروق عند " : "الأذان عند ") + Scheduler.fmt(target) + " · " + city;
            }

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("lock", "عدّاد الصلاة القادمة", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(c, "lock");
            } else {
                b = new Notification.Builder(c);
                b.setPriority(Notification.PRIORITY_LOW);
            }
            PendingIntent pi = PendingIntent.getActivity(c, 4, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            b.setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle(title).setContentText(text)
                    .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setShowWhen(true).setWhen(target).setUsesChronometer(true);
            if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
            nm.notify(ID, b.build());

            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target + 1000, al); }
            catch (Exception e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target + 1000, al); }
        } catch (Exception ignored) {}
    }
}
