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
    /** غيّر OWNER/REPO إلى مستودعك (يُجرَّب الفرعان main ثم master تلقائياً) */
    public static final String URL = "https://raw.githubusercontent.com/abdulrhmnhwa/Tv-apk/main/update.json";
    /** عدة مصادر: raw.githubusercontent محجوب عند بعض مزوّدي الإنترنت (يفتح github.com ولا يفتح raw)، فنجرّب مرايا أخرى ثم واجهة GitHub API */
    static final String[] URLS = {
        URL,
        "https://cdn.jsdelivr.net/gh/abdulrhmnhwa/Tv-apk@main/update.json",
        "https://fastly.jsdelivr.net/gh/abdulrhmnhwa/Tv-apk@main/update.json",
        "https://gcore.jsdelivr.net/gh/abdulrhmnhwa/Tv-apk@main/update.json",
        URL.replace("/main/", "/master/"),
        "https://api.github.com/repos/abdulrhmnhwa/Tv-apk/releases/latest"
    };

    /** مقارنة أرقام النسخ (2.6.3 مقابل 2.6.10) */
    static boolean newerName(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = 0, q = 0;
            try { p = i < x.length ? Integer.parseInt(x[i]) : 0; } catch (Exception e) {}
            try { q = i < y.length ? Integer.parseInt(y[i]) : 0; } catch (Exception e) {}
            if (p != q) return p > q;
        }
        return false;
    }
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

    static final String REL = "https://github.com/abdulrhmnhwa/Tv-apk/releases/latest";

    /** نتيجة مصدر واحد: {versionName, versionCode, url, notes} أو null */
    private static String[] one(String u) {
        try {
            if (u.equals(REL)) { // صفحة github.com نفسها: التحويل إلى /releases/tag/vX.Y.Z يعطي رقم آخر نسخة (أحدث مصدر وغير مخزَّن مؤقتاً)
                HttpURLConnection h = (HttpURLConnection) new java.net.URL(u).openConnection();
                h.setInstanceFollowRedirects(false);
                h.setConnectTimeout(6000);
                h.setReadTimeout(6000);
                h.setRequestProperty("User-Agent", "HomsPrayerApp");
                String loc = h.getHeaderField("Location");
                h.disconnect();
                if (loc == null || !loc.contains("/tag/")) return null;
                String vn = loc.substring(loc.lastIndexOf('/') + 1).replaceAll("[^0-9.]", "");
                return vn.isEmpty() ? null : new String[]{vn, "0", REL, ""};
            }
            boolean api = u.contains("api.github.com");
            HttpURLConnection h = (HttpURLConnection) new java.net.URL(api ? u : u + "?t=" + System.currentTimeMillis()).openConnection();
            h.setConnectTimeout(6000);
            h.setReadTimeout(6000);
            h.setUseCaches(false);
            h.setRequestProperty("User-Agent", "HomsPrayerApp");
            h.setRequestProperty("Cache-Control", "no-cache");
            if (h.getResponseCode() != 200) return null;
            InputStream in = h.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n, tot = 0;
            while ((n = in.read(buf)) > 0 && tot < 65536) { bo.write(buf, 0, n); tot += n; }
            in.close();
            JSONObject j = new JSONObject(bo.toString("UTF-8"));
            if (api) {
                String vn = j.optString("tag_name", "").replaceAll("[^0-9.]", "");
                return vn.isEmpty() ? null : new String[]{vn, "0", j.optString("html_url", REL), ""};
            }
            if (j.optInt("versionCode", 0) <= 0) return null;
            return new String[]{j.optString("versionName", "").replaceAll("[^0-9.]", ""), String.valueOf(j.optInt("versionCode")),
                    j.optString("url", ""), j.optString("notes", "")};
        } catch (Throwable e) { return null; }
    }

    /** يجلب أحدث نسخة من كل المصادر بالتوازي ويأخذ الأعلى (مرايا jsDelivr قد تكون قديمة، وraw محجوب عند بعض المزوّدين).
     *  يعيد JSON نصياً: ok, newer, vc, vn, url, notes, err (cfg | json | net) */
    static String fetch(Context c) {
        JSONObject r = new JSONObject();
        try {
            int cur = current(c);
            String curName = currentName(c);
            r.put("cur", cur);
            if (!configured()) { r.put("ok", false); r.put("err", "cfg"); return r.toString(); }
            final String[] srcs = new String[URLS.length + 1];
            System.arraycopy(URLS, 0, srcs, 0, URLS.length);
            srcs[URLS.length] = REL;
            final String[][] res = new String[srcs.length][];
            Thread[] th = new Thread[srcs.length];
            for (int k = 0; k < srcs.length; k++) {
                final int kk = k;
                th[k] = new Thread(() -> res[kk] = one(srcs[kk]));
                th[k].start();
            }
            for (Thread t : th) t.join(9000);
            String[] best = null;
            for (String[] x : res) {
                if (x == null || x[0].isEmpty()) continue;
                if (best == null || newerName(x[0], best[0])) best = x;
            }
            if (best == null) { r.put("ok", false); r.put("err", "net"); return r.toString(); }
            // بيانات إضافية (ملاحظات/رابط/رقم) من ملف update.json إن كان من النسخة نفسها
            String url = best[2], notes = best[3];
            int vc = Integer.parseInt(best[1]);
            for (String[] x : res) {
                if (x != null && x[0].equals(best[0]) && Integer.parseInt(x[1]) > 0) { vc = Integer.parseInt(x[1]); url = x[2]; notes = x[3]; break; }
            }
            r.put("ok", true);
            r.put("vc", vc);
            r.put("vn", best[0]);
            r.put("url", url);
            r.put("notes", notes);
            r.put("newer", vc > 0 ? vc > cur : newerName(best[0], curName));
            return r.toString();
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
        // عند الفشل لا نعيد المحاولة قبل 3 ساعات (يوفّر البطارية والبيانات)
        if (System.currentTimeMillis() - sp.getLong("tried", 0) < 3 * 3600000L) return;
        sp.edit().putLong("tried", System.currentTimeMillis()).apply();
        busy = true;
        new Thread(() -> {
            try {
                JSONObject r = new JSONObject(fetch(c));
                if (!r.optBoolean("ok")) return; // فشل الاتصال: نعيد المحاولة عند أول فرصة
                sp.edit().putString("checked", today).apply();
                int vc = r.optInt("vc", 0);
                String vn = r.optString("vn");
                // تنبيه مرة واحدة لكل نسخة: بالرقم إن وُجد، وإلا (مصدر GitHub API) باسم النسخة
                boolean fresh = vc > 0 ? vc > sp.getInt("notified_vc", 0) : !vn.equals(sp.getString("notified_vn", ""));
                if (r.optBoolean("newer") && fresh) {
                    notifyNew(c, vn, r.optString("url"), r.optString("notes"));
                    sp.edit().putInt("notified_vc", vc).putString("notified_vn", vn).apply();
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
            NotifLog.add(c, "update", "تحديث جديد لمواقيت الصلاة", txt);
        } catch (Exception ignored) {}
    }
}
