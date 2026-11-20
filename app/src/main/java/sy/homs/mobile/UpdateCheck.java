package sy.homs.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * التحقق من وجود نسخة جديدة.
 * يقرأ ملف update.json من الرابط URL (انظر README_AR.md)، ويقارن versionCode بنسخة الجهاز.
 * التنبيه التلقائي: مرة واحدة فقط لكل نسخة جديدة (يُفحص الرابط مرة باليوم كحدٍّ أقصى).
 */
public class UpdateCheck {
    /** غيّر OWNER/REPO إلى رابط ملف update.json الخاص بك (مثال: https://raw.githubusercontent.com/اسمك/مستودعك/main/update.json) */
    public static final String URL = "https://raw.githubusercontent.com/abdulrhmnhwa/Tv-apk/main/update.json";
    static final String CH = "update";
    static final int NID = 9001;
    private static volatile boolean busy = false;

    static boolean configured() { return !URL.contains("OWNER/REPO"); }

    static int current(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) { return 0; }
    }

    static String currentName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    /** يجلب update.json (يُستدعى من خيط خلفي) ويعيد كائن JSON نصياً: ok, newer, vc, vn, url, notes, err */
    static String fetch(Context c) {
        JSONObject r = new JSONObject();
        try {
            int cur = current(c);
            r.put("cur", cur);
            if (!configured()) { r.put("ok", false); r.put("err", "cfg"); return r.toString(); }
            HttpURLConnection h = (HttpURLConnection) new java.net.URL(URL + "?t=" + System.currentTimeMillis()).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            h.setUseCaches(false);
            if (h.getResponseCode() != 200) throw new Exception("http " + h.getResponseCode());
            InputStream in = h.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, tot = 0;
            while ((n = in.read(buf)) > 0 && tot < 65536) { bo.write(buf, 0, n); tot += n; }
            in.close();
            JSONObject j = new JSONObject(bo.toString("UTF-8"));
            int vc = j.optInt("versionCode", 0);
            r.put("ok", vc > 0);
            r.put("vc", vc);
            r.put("vn", j.optString("versionName", ""));
            r.put("url", j.optString("url", ""));
            r.put("notes", j.optString("notes", ""));
            r.put("newer", vc > cur);
        } catch (Exception e) {
            try { r.put("ok", false); r.put("err", "net"); } catch (Exception ig) {}
        }
        return r.toString();
    }

    /** فحص صامت: يتحقق مرة واحدة باليوم كحدٍّ أقصى، وينبّه مرة واحدة فقط لكل نسخة جديدة (لا تكرار). */
    public static void maybe(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (!configured() || busy) return;
        final SharedPreferences sp = c.getSharedPreferences("upd", 0);
        final String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (today.equals(sp.getString("checked", ""))) return;
        busy = true;
        new Thread(() -> {
            try {
                JSONObject r = new JSONObject(fetch(c));
                if (!r.optBoolean("ok")) return; // فشل الاتصال: نعيد المحاولة عند أول فرصة
                sp.edit().putString("checked", today).apply();
                int vc = r.optInt("vc", 0);
                if (r.optBoolean("newer") && vc > sp.getInt("notified_vc", 0)) {
                    notifyNew(c, r.optString("vn"), r.optString("url"), r.optString("notes"));
                    sp.edit().putInt("notified_vc", vc).apply();
                }
            } catch (Exception ignored) {
            } finally { busy = false; }
        }).start();
    }

    static void notifyNew(Context c, String vn, String url, String notes) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Intent it;
            if (url != null && url.startsWith("https://")) it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            else it = new Intent(c, MainActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, NID, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(new NotificationChannel(CH, "تحديثات التطبيق", NotificationManager.IMPORTANCE_DEFAULT));
                b = new Notification.Builder(c, CH);
            } else {
                b = new Notification.Builder(c);
            }
            String txt = "النسخة " + (vn == null || vn.isEmpty() ? "جديدة" : vn) + " متوفرة — اضغط للتحميل";
            b.setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle("تحديث جديد لمواقيت الصلاة").setContentText(txt)
                    .setStyle(new Notification.BigTextStyle().bigText(notes == null || notes.isEmpty() ? txt : txt + "\n" + notes))
                    .setContentIntent(pi).setAutoCancel(true);
            nm.notify(NID, b.build());
        } catch (Exception ignored) {}
    }
}
