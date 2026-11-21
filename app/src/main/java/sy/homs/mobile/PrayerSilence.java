package sy.homs.mobile;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

/**
 * وضع الصامت أثناء الصلاة: يُفعَّل عند الإقامة ويُعاد الجوال لحالته السابقة بعد LEN دقائق.
 * يتطلب إذن «عدم الإزعاج» (الوصول إلى سياسة الإشعارات). إن كان الجوال صامتاً أصلاً لا نغيّر شيئاً.
 */
public class PrayerSilence {
    static final int LEN = 5; // دقائق

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("ps", 0); }

    static boolean hasAccess(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return android.os.Build.VERSION.SDK_INT < 23 || nm.isNotificationPolicyAccessGranted();
        } catch (Throwable e) { return false; }
    }

    /** بدء الصامت عند الإقامة (ينتهي بعد LEN دقائق) */
    static void start(Context c) {
        try {
            if (Prefs.obj(c).optInt("slt", 0) != 1 || !hasAccess(c)) return;
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            SharedPreferences p = sp(c);
            long end = System.currentTimeMillis() + LEN * 60000L;
            if (p.getBoolean("ours", false)) { p.edit().putLong("end", end).apply(); return; } // جارٍ بالفعل: نمدّ المدة فقط
            int cur = am.getRingerMode();
            if (cur != AudioManager.RINGER_MODE_NORMAL) return; // صامت/اهتزاز أصلاً: لا نغيّر ولا نعيد
            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            p.edit().putBoolean("ours", true).putInt("prev", cur).putLong("end", end).apply();
        } catch (Throwable ignored) {}
    }

    /** إنهاء الصامت وإعادة الحالة السابقة (إن لم يغيّرها المستخدم بنفسه أثناء المدة) */
    static void end(Context c) {
        try {
            SharedPreferences p = sp(c);
            if (!p.getBoolean("ours", false)) return;
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am.getRingerMode() == AudioManager.RINGER_MODE_SILENT && hasAccess(c)) am.setRingerMode(p.getInt("prev", AudioManager.RINGER_MODE_NORMAL));
            p.edit().putBoolean("ours", false).apply();
        } catch (Throwable ignored) {}
    }

    /** احتياط: إن انتهت المدة ولم يُستدعَ الإنهاء (إعادة تشغيل، إيقاف المنبّه…) نعيد الحالة */
    static void check(Context c) {
        try {
            SharedPreferences p = sp(c);
            if (p.getBoolean("ours", false) && (System.currentTimeMillis() >= p.getLong("end", 0) || Prefs.obj(c).optInt("slt", 0) != 1)) end(c);
        } catch (Throwable ignored) {}
    }
}
