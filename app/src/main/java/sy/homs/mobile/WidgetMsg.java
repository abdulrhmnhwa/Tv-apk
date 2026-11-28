package sy.homs.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;

/**
 * رسالة مؤقتة تظهر في شريط الودجت فقط (بلا إشعار). تظهر 10 دقائق من لحظة استلام الجهاز لها ثم تختفي.
 * الاستلام عبر FCM (data فقط) من WidgetMsgService. المنطق هنا صرف وقابل للاختبار على JVM.
 */
public final class WidgetMsg {
    public static final String TOPIC = "awqat_widget_private";
    public static final long SHOW_MS = 10L * 60L * 1000L;
    public static final int MAX_TITLE = 40;
    public static final int MAX_TEXT = 200;
    private static final String PREF = "widget_msg";
    private static final int KEEP_IDS = 30;

    private WidgetMsg() {}

    /** الرسالة فعّالة فقط ما دام الوقت قبل الانتهاء، ولا تتجاوز المدة المسموحة (يحمي من تغيّر ساعة الجهاز). */
    public static boolean isActive(long until, long now) {
        return until > 0 && now < until && until - now <= SHOW_MS;
    }

    /** موضع الرسالة في تبديل الشريط: الثالثة بعد عنصرين، وإن قلّت العناصر ففي آخرها. */
    public static int insertIndex(int size) {
        return size >= 2 ? 2 : size;
    }

    /** يقصّ النص ويزيل المسافات الزائدة ورموز التحكم. فارغ => null. */
    public static String clean(String s, int max) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append(ch == '\n' || ch == '\r' || ch == '\t' || Character.isISOControl(ch) ? ' ' : ch);
        }
        String t = b.toString().trim().replaceAll(" {2,}", " ");
        if (t.length() == 0) return null;
        if (t.length() > max) t = t.substring(0, max).trim();
        return t;
    }

    /** معرّف مكرر؟ القائمة تُحدَّث بالمعرّف الجديد (الأقدم يُحذف عند تجاوز الحد). */
    public static boolean seenBefore(ArrayList<String> ids, String id) {
        if (ids.contains(id)) return true;
        ids.add(id);
        while (ids.size() > KEEP_IDS) ids.remove(0);
        return false;
    }

    /** يحفظ الرسالة الجديدة (تستبدل السابقة). يعيد وقت الانتهاء، أو -1 إن رُفضت (مكررة/فارغة/معرّف غير صالح). */
    public static long accept(Context c, String id, String title, String text, long now) {
        try {
            if (id == null || !id.matches("[A-Za-z0-9_-]{8,64}")) return -1;
            String t = clean(text, MAX_TEXT);
            if (t == null) return -1;
            String ti = clean(title, MAX_TITLE);
            SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
            synchronized (WidgetMsg.class) {
                String raw = p.getString("ids", "");
                ArrayList<String> ids = new ArrayList<>();
                if (raw.length() > 0) for (String x : raw.split(",")) ids.add(x);
                if (seenBefore(ids, id)) return -1;
                long until = now + SHOW_MS;
                boolean ok = p.edit().putString("ids", join(ids)).putString("title", ti == null ? "" : ti)
                        .putString("text", t).putLong("until", until).commit();
                return ok ? until : -1;
            }
        } catch (Throwable e) {
            return -1;
        }
    }

    private static String join(ArrayList<String> ids) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) { if (i > 0) b.append(','); b.append(ids.get(i)); }
        return b.toString();
    }

    /** وقت انتهاء الرسالة الفعّالة، أو 0 إن لم توجد. */
    public static long activeUntil(Context c, long now) {
        try {
            SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
            long u = p.getLong("until", 0);
            return isActive(u, now) ? u : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    /** عنصر الشريط {العنوان، النص، ""} أو null. */
    public static String[] item(Context c, long now) {
        try {
            if (activeUntil(c, now) == 0) return null;
            SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String text = p.getString("text", "");
            if (text.length() == 0) return null;
            String title = p.getString("title", "");
            return new String[]{title.length() == 0 ? "📢 رسالة" : title, text, ""};
        } catch (Throwable e) {
            return null;
        }
    }
}
