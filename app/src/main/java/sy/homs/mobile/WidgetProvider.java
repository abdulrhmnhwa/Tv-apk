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

public class WidgetProvider extends AppWidgetProvider {
    static final String ACT = "sy.homs.mobile.WIDGET";
    static final int GOLD = 0xFFD6B25E, MUTED = 0xFFA9C2B4, WHITE = 0xFFF4F1E8;
    static final int[] IDX = {0, 2, 3, 4, 5};
    static final int[] NID = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4};
    static final int[] TID = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }
    @Override public void onEnabled(Context c) { refresh(c); }
    @Override
    public void onReceive(Context c, Intent i) {
        if (ACT.equals(i.getAction())) refresh(c); else super.onReceive(c, i);
    }

    public static void refresh(Context c) {
        try {
            AppWidgetManager wm = AppWidgetManager.getInstance(c);
            int[] ids = wm.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
            if (ids == null || ids.length == 0) return;
            JSONObject o = Prefs.obj(c);
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
            String label, name;
            long target;
            int hi;
            if (lastI >= 0 && iq > 0 && now < lastT + iq * 60000L) {
                label = "الإقامة بعد"; name = Scheduler.NAMES[lastI]; target = lastT + iq * 60000L; hi = lastI;
            } else {
                long nt = Long.MAX_VALUE;
                int ni = 0;
                for (long[] r : days) for (int i = 0; i < 6; i++) if (r[i] > now && r[i] < nt) { nt = r[i]; ni = i; }
                label = "الصلاة القادمة"; name = Scheduler.NAMES[ni]; target = nt; hi = ni == 1 ? 2 : ni;
            }
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            rv.setTextViewText(R.id.wcity, o.optString("cn", "حمص"));
            rv.setTextViewText(R.id.wlab, label);
            rv.setTextViewText(R.id.wnext, name);
            if (Build.VERSION.SDK_INT >= 24) {
                rv.setChronometer(R.id.wcd, SystemClock.elapsedRealtime() + (target - now), null, true);
                rv.setChronometerCountDown(R.id.wcd, true);
                rv.setViewVisibility(R.id.wcd, View.VISIBLE);
                rv.setViewVisibility(R.id.wcdt, View.GONE);
            } else {
                rv.setViewVisibility(R.id.wcd, View.GONE);
                rv.setViewVisibility(R.id.wcdt, View.VISIBLE);
                rv.setTextViewText(R.id.wcdt, Scheduler.fmt(target));
            }
            for (int j = 0; j < 5; j++) {
                int i = IDX[j];
                rv.setTextViewText(NID[j], Scheduler.NAMES[i]);
                rv.setTextViewText(TID[j], Scheduler.fmt(days[1][i]));
                rv.setTextColor(NID[j], i == hi ? GOLD : MUTED);
                rv.setTextColor(TID[j], i == hi ? GOLD : WHITE);
            }
            rv.setOnClickPendingIntent(R.id.wroot, PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            wm.updateAppWidget(ids, rv);

            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent p = PendingIntent.getBroadcast(c, 7, new Intent(c, WidgetProvider.class).setAction(ACT),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target + 1000, p); }
            catch (Exception e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target + 1000, p); }
        } catch (Exception ignored) {}
    }
}
