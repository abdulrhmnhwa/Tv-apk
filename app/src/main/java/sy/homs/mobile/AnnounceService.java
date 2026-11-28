package sy.homs.mobile;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Receives owner announcements (data-only FCM on topic "announcements"). */
public class AnnounceService extends FirebaseMessagingService {
    public static final String TOPIC = "announcements";
    public static final String CHANNEL = "announcements";

    /** Called once from MainActivity: channel, topic subscription, Android 13+ permission. */
    public static void init(Activity a) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(new NotificationChannel(CHANNEL, "إعلانات التطبيق", NotificationManager.IMPORTANCE_DEFAULT));
                }
            }
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC);
            FirebaseMessaging.getInstance().subscribeToTopic(WidgetMsg.TOPIC);
        } catch (Throwable ignored) {}
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7301);
            }
        } catch (Throwable ignored) {}
    }

    private static AnnounceDedupe dedupe(Context c) {
        final android.content.SharedPreferences p =
                c.getApplicationContext().getSharedPreferences("announce_seen", Context.MODE_PRIVATE);
        return new AnnounceDedupe(new AnnounceDedupe.Store() {
            @Override public Set<String> load() {
                Set<String> raw = p.getStringSet("ids", null);
                return raw == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(raw);
            }
            @Override public boolean persist(Set<String> ids) {
                return p.edit().putStringSet("ids", new LinkedHashSet<String>(ids)).commit();
            }
        });
    }

    @Override
    public void onMessageReceived(RemoteMessage m) {
        try {
            Map<String, String> d = m.getData();
            if (d == null) return;
            if ("widget".equals(d.get("kind"))) { // رسالة ودجت مؤقتة: بلا إشعار
                long until = WidgetMsg.accept(this, d.get("msg_id"), d.get("title"), d.get("text"), System.currentTimeMillis());
                if (until > 0) WidgetProvider.refresh((Context) this);
                return;
            }
            String msgId = d.get("msg_id");
            long sentAtMs;
            try { sentAtMs = Long.parseLong(String.valueOf(d.get("sent_at"))); } catch (Exception e) { sentAtMs = 0; }
            if (!dedupe(this).checkAndRecord(msgId, sentAtMs, System.currentTimeMillis())) return;

            String title = d.get("title");
            if (title == null || title.trim().length() == 0) title = "مواقيت الصلاة سوريا";
            String body = d.get("body");
            if (body == null || body.trim().length() == 0) return;

            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL)
                    : new Notification.Builder(this);
            nb.setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) { nm.notify(msgId.hashCode(), nb.build()); NotifLog.add(this, "announce", title, body); }
        } catch (Throwable ignored) {}
    }
}
