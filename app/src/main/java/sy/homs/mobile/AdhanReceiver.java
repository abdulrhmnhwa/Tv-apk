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
        UpdateCheck.maybe(c); // فحص التحديثات: مرة واحدة باليوم كحدٍّ أقصى
        PrayerSilence.check(c);
        if (Scheduler.ACT.equals(i.getAction())) {
            JSONObject o = Prefs.obj(c);
            int k = Math.max(0, Math.min(5, i.getIntExtra("i", 0)));
            int kind = i.getIntExtra("k", 0);
            String city = o.optString("cn", "حمص"), name = Scheduler.NAMES[k];
            if (kind == 0 && o.optInt("en", 1) == 1) {
                if (o.optInt("snd", 1) == 1 && !Takbeer.silent(c)) {
                    try {
                        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homs:adhan").acquire(20000);
                    } catch (Exception ignored) {}
                    final PendingResult pr = goAsync();
                    Takbeer.play(c.getApplicationContext(), pr::finish);
                }
                notifyNow(c, 100 + k, "adhan", "حان الآن موعد " + name, city);
            } else if (kind == 1) {
                boolean snd = o.optInt("pres", 1) == 1 && !Takbeer.silent(c) && Takbeer.has(c, "pre_adhan.mp3");
                if (snd) {
                    try {
                        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homs:pre").acquire(40000);
                    } catch (Exception ignored) {}
                    final PendingResult pr = goAsync();
                    Takbeer.playFile(c.getApplicationContext(), "pre_adhan.mp3", pr::finish);
                }
                notifyNow(c, 200 + k, snd ? "adhan" : "remind", "بعد " + Scheduler.PRE + " دقيقة موعد " + name, city);
            } else if (kind == 2) {
                boolean snd = o.optInt("iqs", 0) == 1 && !Takbeer.silent(c) && Takbeer.has(c, "iqama.mp3");
                if (snd) {
                    try {
                        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homs:iq").acquire(40000);
                    } catch (Exception ignored) {}
                    final PendingResult pr = goAsync();
                    final Context app = c.getApplicationContext();
                    Takbeer.playFile(app, "iqama.mp3", () -> { PrayerSilence.start(app); pr.finish(); }); // الصامت يبدأ بعد انتهاء صوت الإقامة
                }
                notifyNow(c, 300 + k, snd ? "adhan" : "remind", "حانت إقامة صلاة " + name, "استعدوا للصلاة · " + city);
                if (!snd) PrayerSilence.start(c);
            } else if (kind == 12) {
                PrayerSilence.start(c);
            } else if (kind == 13) {
                PrayerSilence.end(c);
            } else if (kind == 3) {
                notifyNow(c, 403, "remind", "☀️ حان وقت أذكار الصباح", "ابدأ يومك بذكر الله · اضغط لفتح الأذكار", "m");
            } else if (kind == 4) {
                notifyNow(c, 404, "remind", "🌙 حان وقت أذكار المساء", "حصّن نفسك بأذكار المساء · اضغط لفتح الأذكار", "e");
            } else if (kind == 5) {
                notifyNow(c, 405, "remind", "🌤️ حان وقت صلاة الضحى", "ركعتان فأكثر تجزئ عن صدقة كل مفصل في الجسم · " + city, "");
            } else if (kind == 7) {
                notifyNow(c, 407, "remind", "🌙 تذكير: صيام الغد", "يجب عليك صيام يوم غدٍ (قضاء) — انوِ الصيام من الليل · اضغط لفتح قضاء الصيام", "sq");
            } else if (kind == 8) {
                int code = i.getIntExtra("i", 0);
                String[] nm = {"", "الاثنين", "الخميس", "من الأيام البيض", "يوم عرفة", "تاسوعاء", "عاشوراء"};
                String[] ds = {"", "صيام الاثنين والخميس سنّة مستحبّة", "صيام الاثنين والخميس سنّة مستحبّة",
                        "يُستحب صيام الأيام البيض: 13 و14 و15 من الشهر الهجري", "صيامه يكفّر ذنوب السنة الماضية والباقية لغير الحاج",
                        "يُستحب صيامه مع عاشوراء", "صيامه يكفّر ذنوب السنة الماضية"};
                if (code >= 1 && code <= 6)
                    notifyNow(c, 408, "remind", "🌙 غداً " + (code <= 2 ? "يوم " : "") + nm[code] + " — يُستحب صيامه", ds[code] + " · انوِ الصيام من الليل", "");
            } else if (kind == 9) {
                notifyNow(c, 409, "remind", "📖 يوم الجمعة — سورة الكهف", "من السنن المستحبّة اليوم قراءة سورة الكهف والإكثار من الصلاة على النبي ﷺ", "kahf");
            } else if (kind == 10) {
                notifyNow(c, 410, "remind", "🕌 اقترب موعد صلاة الجمعة", "اغتسل وتطيّب وبكّر إلى المسجد، وأكثر من الصلاة على النبي ﷺ · " + city, "");
            } else if (kind == 11) {
                // تنبيه الطقس قبل الذهاب للمسجد: يجلب الطقس الحالي (خلفية) ثم يقرّر
                final Context app = c.getApplicationContext();
                final int pk = k;
                final PendingResult pr2 = goAsync();
                new Thread(() -> {
                    try {
                        if (Weather.age(app) > 15 * 60000L) Weather.fetch(app);
                        if (Weather.age(app) <= 3 * 3600000L) {
                            int[] w = Weather.cur(app);
                            if (w != null) {
                                String[] al = Weather.mosqueAlert(w[0], w[1], w[2], Scheduler.NAMES[pk]);
                                if (al != null) notifyNow(app, 411, "remind", al[0], al[1], "");
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        try { WidgetProvider.refresh(app); } catch (Throwable ignored) {}
                        pr2.finish();
                    }
                }).start();
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

    private static final int NOTE_ID = 4000; // معرّف موحّد: كل إشعار جديد يحلّ محلّ السابق

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
                    if (Takbeer.silent(c)) { /* صامت */ }
                    else if (tone.isEmpty()) b.setDefaults(Notification.DEFAULT_SOUND);
                    else if (!tone.equals("silent")) b.setSound(Uri.parse(tone));
                }
            }
            Intent oi = new Intent(c, MainActivity.class);
            if (open != null && !open.isEmpty()) oi.putExtra("open", open);
            PendingIntent pi = PendingIntent.getActivity(c, open == null ? 2 : id, oi, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle(title).setContentText(text)
                    .setContentIntent(pi).setAutoCancel(true);
            // يبقى أحدث إشعار فقط: نزيل إشعارات التطبيق السابقة (ما عدا إشعار القفل والمسبحة العائمة)
            if (Build.VERSION.SDK_INT >= 23) {
                try {
                    for (android.service.notification.StatusBarNotification sn : nm.getActiveNotifications()) {
                        int i = sn.getId();
                        if (i != LockNotif.ID && i != FloatTasbih.NID) nm.cancel(i);
                    }
                } catch (Throwable ignored) {}
            }
            nm.notify(NOTE_ID, b.build());
        } catch (Exception ignored) {}
    }
}
