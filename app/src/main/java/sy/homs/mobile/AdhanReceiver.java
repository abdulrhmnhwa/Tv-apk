package sy.homs.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
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
                boolean snd = o.optInt("pres", 1) == 1 && Takbeer.has(c, "pre_adhan.mp3");
                notifyNow(c, 200 + k, snd ? "adhan" : "remind", "بعد " + Scheduler.PRE + " دقيقة موعد " + name, city);
                if (snd) {
                    try {
                        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homs:pre").acquire(40000);
                    } catch (Exception ignored) {}
                    final PendingResult pr = goAsync();
                    Takbeer.playFile(c.getApplicationContext(), "pre_adhan.mp3", pr::finish);
                }
            } else if (kind == 2) {
                notifyNow(c, 300 + k, "remind", "حانت إقامة صلاة " + name, "استعدوا للصلاة · " + city);
            } else if (kind == 3) {
                notifyNow(c, 403, "remind", "☀️ حان وقت أذكار الصباح", "ابدأ يومك بذكر الله · اضغط لفتح الأذكار", "m");
            } else if (kind == 4) {
                notifyNow(c, 404, "remind", "🌙 حان وقت أذكار المساء", "حصّن نفسك بأذكار المساء · اضغط لفتح الأذكار", "e");
            } else if (kind == 5) {
                notifyNow(c, 405, "remind", "🌤️ حان وقت صلاة الضحى", "ركعتان فأكثر تجزئ عن صدقة كل مفصل في الجسم · " + city, "");
            } else if (kind == 6) {
                notifyNow(c, 406, "remind", "🌌 الثلث الأخير من الليل", "وقت الدعاء والاستغفار والقيام · اضغط لفتح الأذكار", "tab");
            }
        }
        WidgetProvider.refresh(c);
        LockNotif.refresh(c);
        Scheduler.schedule(c);
    }

    /** قناة التذكيرات بحسب النغمة المختارة (صوت القناة لا يتغيّر بعد إنشائها، فلكل نغمة قناة) */
    private static String toneChannel(NotificationManager nm, String tone) {
        if (tone.isEmpty()) return "remind";
        String id = "remind_t" + Integer.toHexString(tone.hashCode());
        try {
            for (NotificationChannel ch : nm.getNotificationChannels()) {
                String n = ch.getId();
                if (n.startsWith("remind_t") && !n.equals(id)) nm.deleteNotificationChannel(n);
            }
            if (nm.getNotificationChannel(id) == null) {
                NotificationChannel ch = new NotificationChannel(id, "التذكيرات (نغمة مخصصة)", NotificationManager.IMPORTANCE_HIGH);
                if (tone.equals("silent")) ch.setSound(null, null);
                else ch.setSound(Uri.parse(tone), new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                nm.createNotificationChannel(ch);
            }
            return id;
        } catch (Exception e) { return "remind"; }
    }

    private void notifyNow(Context c, int id, String channel, String title, String text) {
        notifyNow(c, id, channel, title, text, null);
    }

    /** open: مفتاح قسم الأذكار الذي يُفتح عند الضغط على الإشعار ("" = فتح التطبيق فقط، null = كالمعتاد) */
    private void notifyNow(Context c, int id, String channel, String title, String text, String open) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            String tone = Prefs.obj(c).optString("tone", "");
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel a = new NotificationChannel("adhan", "تنبيه الأذان", NotificationManager.IMPORTANCE_HIGH);
                a.setSound(null, null);
                nm.createNotificationChannel(a);
                nm.createNotificationChannel(new NotificationChannel("remind", "التذكيرات", NotificationManager.IMPORTANCE_HIGH));
                if (channel.equals("remind")) channel = toneChannel(nm, tone);
                b = new Notification.Builder(c, channel);
            } else {
                b = new Notification.Builder(c);
                if (channel.equals("remind")) {
                    if (tone.isEmpty()) b.setDefaults(Notification.DEFAULT_SOUND);
                    else if (!tone.equals("silent")) b.setSound(Uri.parse(tone));
                }
            }
            Intent oi = new Intent(c, MainActivity.class);
            if (open != null && !open.isEmpty()) oi.putExtra("open", open);
            PendingIntent pi = PendingIntent.getActivity(c, open == null ? 2 : id, oi, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle(title).setContentText(text)
                    .setContentIntent(pi).setAutoCancel(true);
            nm.notify(id, b.build());
        } catch (Exception ignored) {}
    }
}
