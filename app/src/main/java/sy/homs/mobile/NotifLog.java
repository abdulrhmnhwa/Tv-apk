package sy.homs.mobile;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** سجل آخر 10 إشعارات أرسلها التطبيق (من وقت التحديث فقط). الأحدث أولاً. يسجّل الإرسال بعد نجاح الاستدعاء، لا عرض النظام. */
public class NotifLog {
    static final int MAX = 10;

    /** منطق نقي (قابل لاختبار JVM): العنصر الجديد في المقدمة وإبقاء أحدث max فقط. */
    static java.util.List<String[]> pushEntries(java.util.List<String[]> old, String[] e, int max) {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<String[]>();
        out.add(e);
        if (old != null) for (String[] x : old) { if (out.size() >= max) break; out.add(x); }
        return out;
    }

    /** إضافة عنصر في المقدمة وإبقاء أحدث MAX فقط. يعيد النص الجديد (JSON). */
    static String push(String json, long ts, String kind, String title, String text, int max) {
        try {
            JSONArray old;
            try { old = new JSONArray(json == null || json.isEmpty() ? "[]" : json); } catch (Exception e) { old = new JSONArray(); }
            java.util.ArrayList<String[]> l = new java.util.ArrayList<String[]>();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                l.add(new String[]{String.valueOf(o.optLong("t")), o.optString("k"), o.optString("a"), o.optString("b")});
            }
            java.util.List<String[]> r = pushEntries(l, new String[]{String.valueOf(ts), kind == null ? "" : kind, title == null ? "" : title, text == null ? "" : text}, max);
            JSONArray out = new JSONArray();
            for (String[] x : r) {
                JSONObject o = new JSONObject();
                o.put("t", Long.parseLong(x[0])); o.put("k", x[1]); o.put("a", x[2]); o.put("b", x[3]);
                out.put(o);
            }
            return out.toString();
        } catch (Exception e) { return json == null ? "[]" : json; }
    }

    static synchronized void add(Context c, String kind, String title, String text) {
        try {
            android.content.SharedPreferences sp = c.getApplicationContext().getSharedPreferences("nlog", 0);
            sp.edit().putString("l", push(sp.getString("l", "[]"), System.currentTimeMillis(), kind, title, text, MAX)).apply();
        } catch (Throwable ignored) {}
    }

    static synchronized String get(Context c) {
        try { return c.getApplicationContext().getSharedPreferences("nlog", 0).getString("l", "[]"); }
        catch (Throwable t) { return "[]"; }
    }
}
