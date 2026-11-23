package sy.homs.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

/** ودجت رمضان: عدّ تنازلي للإفطار/الإمساك + أوقاتهما + مؤشر الختمة. خارج رمضان يعرض كم يوماً بقي. */
public class RamadanWidget extends AppWidgetProvider {
    static final String ACT = "sy.homs.mobile.RAMWIDGET";

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }
    @Override public void onEnabled(Context c) { refresh(c); }
    @Override public void onReceive(Context c, Intent i) {
        if (ACT.equals(i.getAction())) refresh(c); else super.onReceive(c, i);
    }

    public static void refresh(Context c) {
        try {
            AppWidgetManager wm = AppWidgetManager.getInstance(c);
            int[] ids = wm.getAppWidgetIds(new ComponentName(c, RamadanWidget.class));
            if (ids == null || ids.length == 0) return;
            JSONObject o = Prefs.obj(c);
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(now + Scheduler.TZ * 3600000L);
            long today = (now + Scheduler.TZ * 3600000L) / 86400000L;
            long rs = o.optLong("rs", -1), re = o.optLong("re", -1);
            boolean in = rs >= 0 && today >= rs && today < re;
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_ram);
            applyTheme(rv, "light".equals(o.optString("wth", "dark")));
            long target = now + 3600000L;
            if (in) {
                long[] t0 = Scheduler.dayMs(o, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                Calendar nx = (Calendar) cal.clone();
                nx.add(Calendar.DAY_OF_MONTH, 1);
                long[] t1 = Scheduler.dayMs(o, nx.get(Calendar.YEAR), nx.get(Calendar.MONTH), nx.get(Calendar.DAY_OF_MONTH));
                String lab;
                if (now < t0[0]) { target = t0[0]; lab = "المتبقي للإمساك"; }
                else if (now < t0[4]) { target = t0[4]; lab = "المتبقي للإفطار"; }
                else { target = t1[0]; lab = "المتبقي للإمساك"; }
                rv.setTextViewText(R.id.rtitle, "🌙 رمضان · اليوم " + (today - rs + 1));
                rv.setTextViewText(R.id.rlab, lab);
                rv.setTextViewText(R.id.rtimes, "الإمساك " + Scheduler.fmt(t0[0]) + "  ·  الإفطار " + Scheduler.fmt(t0[4]));
                int pg = o.optInt("rk", -1);
                rv.setTextViewText(R.id.rkh, pg < 0 ? "📖 اضغط لبدء ختمة رمضان"
                        : "📖 الختمة: الجزء " + Math.min(30, pg / 20 + 1) + " · " + pg + "/604 صفحة");
                setCd(rv, target, now);
            } else {
                long left = rs >= 0 && rs > today ? rs - today : -1;
                rv.setTextViewText(R.id.rtitle, "🌙 رمضان");
                rv.setTextViewText(R.id.rlab, left > 0 ? "بعد " + left + " يوماً بإذن الله" : "افتح التطبيق لتحديث التاريخ");
                rv.setTextViewText(R.id.rtimes, "تظهر الإمساكية تلقائياً عند بدء الشهر");
                rv.setTextViewText(R.id.rkh, "");
                rv.setViewVisibility(R.id.rcd, View.GONE);
                rv.setViewVisibility(R.id.rcdt, View.GONE);
                target = ((now + Scheduler.TZ * 3600000L) / 86400000L + 1) * 86400000L - Scheduler.TZ * 3600000L;
            }
            rv.setOnClickPendingIntent(R.id.rroot, PendingIntent.getActivity(c, 5, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            wm.updateAppWidget(ids, rv);
            long midnight = ((now + Scheduler.TZ * 3600000L) / 86400000L + 1) * 86400000L - Scheduler.TZ * 3600000L;
            long at = Math.min(target, midnight) + 1000;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent p = PendingIntent.getBroadcast(c, 8, new Intent(c, RamadanWidget.class).setAction(ACT),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (Exception ignored) {}
    }

    static void applyTheme(RemoteViews rv, boolean light) {
        rv.setInt(R.id.rroot,"setBackgroundResource",light?R.drawable.widget_bg_day:R.drawable.widget_bg);
        int gold=light?0xFFA46A00:0xFFD6B25E, muted=light?0xFF4C6B5C:0xFFA9C2B4, text=light?0xFF173A2C:0xFFF4F1E8;
        for(int id:new int[]{R.id.rtitle,R.id.rtimes})rv.setTextColor(id,gold);
        for(int id:new int[]{R.id.rlab,R.id.rkh})rv.setTextColor(id,muted);
        for(int id:new int[]{R.id.rcd,R.id.rcdt})rv.setTextColor(id,text);
    }

    private static void setCd(RemoteViews rv, long target, long now) {
        if (Build.VERSION.SDK_INT >= 24) {
            rv.setChronometer(R.id.rcd, SystemClock.elapsedRealtime() + (target - now), null, true);
            rv.setChronometerCountDown(R.id.rcd, true);
            rv.setViewVisibility(R.id.rcd, View.VISIBLE);
            rv.setViewVisibility(R.id.rcdt, View.GONE);
        } else {
            rv.setViewVisibility(R.id.rcd, View.GONE);
            rv.setViewVisibility(R.id.rcdt, View.VISIBLE);
            rv.setTextViewText(R.id.rcdt, Scheduler.fmt(target));
        }
    }
}
