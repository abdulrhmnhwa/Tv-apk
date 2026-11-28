package sy.homs.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

public class WidgetProvider extends AppWidgetProvider {
    static final String ACT = "sy.homs.mobile.WIDGET";
    static final int GOLD = 0xFFD6B25E, MUTED = 0xFFA9C2B4, WHITE = 0xFFF4F1E8, PAST_N = 0xFF6E8A7B, PAST_T = 0xFF93AB9E;
    static final String[] WD = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
    static final String[] MO = {"كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"};
    static final int[] IDX = {0, 2, 3, 4, 5};
    static final int[] NID = {R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4};
    static final int[] TID = {R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.wtom};
    static final int[] CID = {R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }
    @Override public void onEnabled(Context c) { refresh(c); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) { refresh(c); }
    @Override
    public void onReceive(final Context c, Intent i) {
        String a = i.getAction();
        if (ACT.equals(a) || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a)) {
            refresh(c);
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try { if (Weather.stale(c)) Weather.fetch(c); } catch (Throwable ignored) {}
                refresh(c);
                pr.finish();
            }).start();
        } else super.onReceive(c, i);
    }

    static boolean wide(AppWidgetManager wm, int id) {
        try {
            Bundle b = wm.getAppWidgetOptions(id);
            return b != null && b.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) >= 300;
        } catch (Throwable e) { return false; }
    }

    /** أحجام الخطوط الأساسية (sp) كما في widget.xml؛ تُضرب بنسبة الإعداد (70–150%) */
    static final int[] FS_IDS = {R.id.wcity, R.id.wclk, R.id.wap, R.id.wnext, R.id.wat, R.id.wlab, R.id.wcd, R.id.wcdt,
            R.id.n0, R.id.n1, R.id.n2, R.id.n3, R.id.n4, R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.wdate};
    static final float[] FS_SP = {14, 22, 12, 30, 14, 12, 28, 22,
            12, 12, 12, 12, 12, 13, 13, 13, 13, 13, 11};

    private static void applyFontScale(RemoteViews rv, int pct) {
        float k = Math.max(70, Math.min(150, pct)) / 100f;
        for (int i = 0; i < FS_IDS.length; i++)
            rv.setTextViewTextSize(FS_IDS[i], android.util.TypedValue.COMPLEX_UNIT_DIP, FS_SP[i] * k);
    }

    // Render vector geometry at the display's own pixel size, never enlarge a small bitmap.
    // The arc follows the widget font setting and stays small: it only occupies free space
    // in the top row between the city block and the clock block, so it never adds height.
    static final float ARC_W_DP = 100f, ARC_H_DP = 40f, ARC_MIN_GAP_DP = 60f;

    static android.graphics.Bitmap scaledArc(SunMoonIndicator.State state, int gold,
            int pct, android.util.DisplayMetrics metrics, int gapPx) {
        float k = Math.max(70, Math.min(150, pct)) / 100f;
        float visW = Math.min(ARC_W_DP * k * metrics.density, gapPx);
        int w = Math.max(1, gapPx);
        int h = Math.max(1, Math.round(ARC_H_DP * k * metrics.density));
        android.graphics.Bitmap result = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        result.setDensity(metrics.densityDpi);
        android.graphics.Canvas canvas = new android.graphics.Canvas(result);
        float scale = Math.min(visW / 100f, h / 58f);
        canvas.translate((w - 100f * scale) / 2f, (h - 58f * scale) / 2f);
        canvas.scale(scale, scale);
        SunMoonIndicator.drawArc(canvas, state, gold);
        return result;
    }

    static float textW(String s, float sp, boolean bold, android.util.DisplayMetrics m) {
        android.text.TextPaint p = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sp * m.density);
        if (bold) p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        return s == null ? 0f : p.measureText(s);
    }

    // The arc must never cost a single letter: it is only shown when the estimated free
    // space between the city text and the clock block still fits a useful arc, and it is
    // drawn centered in that free gap. Bigger widget text shrinks the gap until it hides.
    /** خطة القوس: وسطه ثابت فوق الفاصل بين المستطيلين، ونص الطقس يتصغّر ليتّسع بجانبه وإلا اختفى القوس */
    static final class ArcPlan {
        boolean show;
        int bitmapW;
        float citySp; // الحجم النهائي لسطر المدينة/الطقس (sp)
        float tomSp;  // الحجم النهائي لسطر الغد (sp)
        float descScale = 1f;
    }

    static ArcPlan planArc(AppWidgetManager wm, int id, String cityLine, String tomLine, String dateLine,
            int pct, android.util.DisplayMetrics m) {
        float k = Math.max(70, Math.min(150, pct)) / 100f;
        ArcPlan p = new ArcPlan();
        p.citySp = 14 * k; p.tomSp = 11 * k;
        int widthDp = 250; // minResizeWidth: safe floor when the host has not reported a size yet
        try {
            Bundle b = wm.getAppWidgetOptions(id);
            if (b != null) {
                int o = b.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                if (o > 0) widthDp = o;
            }
        } catch (Throwable ignored) {}
        java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("h:mm", java.util.Locale.getDefault());
        tf.setTimeZone(java.util.TimeZone.getTimeZone("GMT+03:00"));
        java.util.Date dnow = new java.util.Date();
        String clk = tf.format(dnow);
        String ap = new java.text.SimpleDateFormat("a", java.util.Locale.getDefault()).format(dnow);
        return planArcCore(widthDp, cityLine, tomLine, dateLine, clk, ap, pct, m);
    }

    static ArcPlan planArcCore(int widthDp, String cityLine, String tomLine, String dateLine,
            String clk, String ap, int pct, android.util.DisplayMetrics m) {
        float k = Math.max(70, Math.min(150, pct)) / 100f;
        ArcPlan p = new ArcPlan();
        p.citySp = 14 * k; p.tomSp = 11 * k;
        float total = Math.max(1, (widthDp - 24) * m.density);
        float half = Math.max(1, total / 2f - 4 * m.density);
        // Text always has the same half-row budget, whether the optional arc is shown or not.
        float cityW = textW(cityLine, 14 * k, false, m);
        float tomW = textW(tomLine, 11 * k, false, m);
        p.citySp *= Math.min(1f, half / Math.max(1, cityW) * .96f);
        p.tomSp *= Math.min(1f, half / Math.max(1, tomW) * .96f);
        float clockW = Math.max(textW(clk, 22 * k, true, m) + 4 * m.density + textW(ap, 12 * k, false, m),
                textW(dateLine, 11 * k, false, m));
        float desired = Math.min(ARC_W_DP * k, 70) * m.density;
        float availableArc = total - 2 * (clockW + 6 * m.density);
        float arc = Math.min(desired, availableArc);
        if (arc >= 32 * m.density) {
            float textBudget = total / 2 - arc / 2 - 6 * m.density;
            int start = descriptionStart(cityLine);
            float prefix = textW(cityLine.substring(0,start), p.citySp, false, m);
            float description = textW(cityLine.substring(start), p.citySp, false, m);
            p.descScale = Math.max(.55f, Math.min(1, (textBudget - prefix) / Math.max(1,description) * .98f));
            float combined = prefix + description * p.descScale;
            if (combined > textBudget) p.citySp *= Math.max(.4f, textBudget / combined * .98f);
            float tomorrow = textW(tomLine,p.tomSp,false,m);
            if (tomorrow > textBudget) p.tomSp *= textBudget / tomorrow * .98f;
            p.bitmapW = Math.max(1, Math.round(arc));
            p.show = true;
        }
        return p;
    }

    static int descriptionStart(String line) {
        int degree = line.indexOf('°');
        return degree < 0 ? line.length() : Math.min(line.length(),degree+1);
    }
    static CharSequence weatherStyled(String line, ArcPlan p, android.util.DisplayMetrics dm) {
        android.text.SpannableString text = new android.text.SpannableString(line);
        int start=descriptionStart(line);
        if(start<line.length()) text.setSpan(new android.text.style.AbsoluteSizeSpan(Math.max(1,Math.round(p.citySp*p.descScale*dm.density))),start,line.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    static int widthOf(AppWidgetManager wm, int id) {
        try { int w = wm.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0); if (w > 0) return w; }
        catch (Throwable ignored) {}
        return 250;
    }

    /** أيام العيد (1–3 شوال، 10–13 ذو الحجة): يدفعها التطبيق في الإعداد ed كأزواج [بداية، نهاية-حصرية] بأيام يونكس */
    static boolean isEid(JSONObject o, long day) { return inWin(o, "ed", day); }

    static boolean inWin(JSONObject o, String key, long day) {
        org.json.JSONArray a = o.optJSONArray(key);
        if (a == null) return false;
        for (int i = 0; i + 1 < a.length(); i += 2) if (day >= a.optLong(i) && day < a.optLong(i + 1)) return true;
        return false;
    }

    public static void refresh(Context c) {
        try { RamadanWidget.refresh(c); } catch (Throwable ignored) {}
        try { KhatmaWidget.refresh(c); } catch (Throwable ignored) {}
        try {
            AppWidgetManager wm = AppWidgetManager.getInstance(c);
            int[] ids = wm.getAppWidgetIds(new ComponentName(c, WidgetProvider.class));
            if (ids == null || ids.length == 0) return;
            JSONObject o = Prefs.obj(c);
            if (!Scheduler.hasAround(o, System.currentTimeMillis())) { // الموقع المختار بلا بيانات صحيحة: لا نعرض أوقاتاً خاطئة
                for (int widgetId : ids) {
                    RemoteViews nv = new RemoteViews(c.getPackageName(), R.layout.widget);
                    nv.setTextViewText(R.id.wcity, o.optString("cn", ""));
                    nv.setTextViewText(R.id.wlab, "مواقيت غير متاحة");
                    nv.setTextViewText(R.id.wnext, "—");
                    nv.setTextViewText(R.id.wat, "يلزم اتصال بالإنترنت");
                    nv.setViewVisibility(R.id.wcd, View.GONE);
                    nv.setViewVisibility(R.id.wcdt, View.GONE);
                    nv.setViewVisibility(R.id.wtom, View.GONE);
                    nv.setTextViewText(R.id.wdate, "");
                    for (int j = 0; j < 5; j++) { nv.setTextViewText(NID[j], Scheduler.NAMES[j == 0 ? 0 : j + 1]); nv.setTextViewText(TID[j], "—"); }
                    wm.updateAppWidget(widgetId, nv);
                }
                return;
            }
            boolean light = "light".equals(o.optString("wth", "dark"));
            int gold = light ? 0xFFA46A00 : GOLD;
            int muted = light ? 0xFF4C6B5C : MUTED;
            int text = light ? 0xFF173A2C : WHITE;
            int pastName = light ? 0xFF4C6B5C : PAST_N;
            int pastTime = light ? 0xFF4C6B5C : PAST_T;
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(now + Scheduler.TZ * 3600000L);
            long[][] days = new long[3][];
            for (int d = -1; d <= 1; d++) {
                Calendar x = (Calendar) cal.clone();
                x.add(Calendar.DAY_OF_MONTH, d);
                days[d + 1] = Scheduler.dayMs(o, x.get(Calendar.YEAR), x.get(Calendar.MONTH), x.get(Calendar.DAY_OF_MONTH));
            }
            WidgetTip.nightBefore = (int) ((days[1][0] - WidgetTip.thirdAt(days[0][4], days[1][0], o.optInt("lb", 0))) / 60000L);
            long lastT = -1;
            int lastI = -1;
            for (long[] r : days) for (int i : IDX) if (r[i] <= now && r[i] > lastT) { lastT = r[i]; lastI = i; }
            int iq = lastI >= 0 ? Scheduler.iq(o, lastI) : 0;
            String label, name;
            long target;
            int hi;
            boolean iqamaMode = false;
            int nIdx = -1;
            if (lastI >= 0 && iq > 0 && now < lastT + iq * 60000L) {
                iqamaMode = true; label = "المتبقي للإقامة"; name = Scheduler.NAMES[lastI]; target = lastT + iq * 60000L; hi = lastI;
            } else {
                long nt = Long.MAX_VALUE;
                int ni = 0;
                for (long[] r : days) for (int i = 0; i < 6; i++) if (r[i] > now && r[i] < nt) { nt = r[i]; ni = i; }
                nIdx = ni; label = ni == 1 ? "المتبقي للشروق" : "المتبقي للأذان"; name = Scheduler.NAMES[ni]; target = nt; hi = ni == 1 ? 2 : ni;
            }
            int[] iqs = new int[6];
            for (int i = 0; i < 6; i++) iqs[i] = Scheduler.iq(o, i);
            for (int widgetId : ids) {
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);
            // RemoteViews may reuse the existing layout, so reset both palettes explicitly.
            rv.setInt(R.id.wroot, "setBackgroundResource", light ? R.drawable.widget_bg_day : R.drawable.widget_bg);
            rv.setInt(R.id.wtip, "setBackgroundResource", light ? R.drawable.tip_bg_day : R.drawable.tip_bg);
            for (int id : new int[]{R.id.wcity, R.id.wlab}) rv.setTextColor(id, muted);
            for (int id : new int[]{R.id.wtom, R.id.wdate}) rv.setTextColor(id, light ? muted : 0xFF8FB09E);
            for (int id : new int[]{R.id.wclk, R.id.wat}) rv.setTextColor(id, text);
            for (int id : new int[]{R.id.wap, R.id.wnext, R.id.wcd, R.id.wcdt}) rv.setTextColor(id, gold);
            String city = o.optString("cn", "حمص"), wl = Weather.line(c, wide(wm, widgetId));
            String tw = Weather.tomorrow(c); // يُقصّ من الآخر إن ضاق المكان فلا يؤثر على شيء
            rv.setTextViewText(R.id.wcity, wl.isEmpty() ? city : city + "  " + wl);
            rv.setTextViewText(R.id.wtom, tw);
            rv.setViewVisibility(R.id.wtom, tw.isEmpty() ? View.GONE : View.VISIBLE);
            String dateLine = WD[cal.get(Calendar.DAY_OF_WEEK) - 1] + " " + cal.get(Calendar.DAY_OF_MONTH) + " " + MO[cal.get(Calendar.MONTH)];
            rv.setTextViewText(R.id.wdate, dateLine);
            int fspc = o.optInt("wfs", 100);
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            SunMoonIndicator.State sky = SunMoonIndicator.state(now, days);
            String cityLine = wl.isEmpty() ? city : city + "  " + wl;
            ArcPlan arc = planArc(wm, widgetId, cityLine, tw, dateLine, fspc, dm);
            if (arc.show) {
                rv.setImageViewBitmap(R.id.warc, scaledArc(sky, gold, fspc, dm, arc.bitmapW));
                rv.setContentDescription(R.id.warc, SunMoonIndicator.description(sky));
                rv.setViewVisibility(R.id.warc, View.VISIBLE);
            } else {
                rv.setViewVisibility(R.id.warc, View.GONE);
            }
            rv.setInt(R.id.wboxn, "setBackgroundResource", light ? R.drawable.box_bg_day : R.drawable.box_bg);
            rv.setInt(R.id.wboxc, "setBackgroundResource", light ? R.drawable.box_bg_day : R.drawable.box_bg);
            rv.setTextViewText(R.id.wlab, label);
            rv.setTextViewText(R.id.wnext, name);
            String timeText = Scheduler.fmt(target);
            String atText = (iqamaMode ? "الإقامة عند " : "عند ") + timeText;
            rv.setTextViewText(R.id.wat, atText);
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
                boolean past = i != hi && days[1][i] <= now; // الصلوات التي مضى وقتها تُخفَّف، والقادمة بالذهبي
                rv.setTextColor(NID[j], i == hi ? gold : past ? pastName : muted);
                rv.setTextColor(TID[j], i == hi ? gold : past ? pastTime : text);
                rv.setInt(CID[j], "setBackgroundResource", i == hi ? (light ? R.drawable.chip_on_day : R.drawable.chip_on) : (light ? R.drawable.chip_off_day : R.drawable.chip_off));
            }
            // شريط الذكر الذكي: بحسب الوقت واليوم والطقس
            int[] wx = Weather.cur(c);
            long dayN = (now + Scheduler.TZ * 3600000L) / 86400000L;
            // 8 كانون الأول بتوقيت الجهاز: تهنئة ذكرى انتصار الثورة السورية طوال اليوم (مقدَّمة على التذكيرات الموسمية الأخرى)
            String[] tip = WidgetTip.pick(now, days[1], iqs, cal.get(Calendar.DAY_OF_WEEK),
                    wx == null ? -1 : wx[0], wx == null ? 20 : wx[1],
                    WidgetTip.isVictoryDay(now) ? 3 : (inWin(o, "lt", dayN) ? 1 : (inWin(o, "sh", dayN) ? 2 : 0)));
            // الشريط يتبادل كل 10 ثوانٍ (ViewFlipper) بين: التنبيه الذي يحين وقته، وتنبيه الصيام، وتنبيه «ما زال بإمكانك قراءة أذكار الصباح/المساء»
            java.util.ArrayList<String[]> tl = new java.util.ArrayList<>();
            tl.add(tip);
            String[] ft = WidgetTip.fastTip(now, days[1], iqs, Scheduler.fastSqd(o, cal, 1), Scheduler.fastSqd(o, cal, 0),
                    Scheduler.nfdCode(o, cal, 1), Scheduler.nfdCode(o, cal, 0));
            if (ft != null) tl.add(ft);
            String[] fr = WidgetTip.fridayTip(now, days[1], cal.get(Calendar.DAY_OF_WEEK), o.optInt("rfk", 0) == 1, o.optInt("rfg", 0) == 1);
            if (fr != null) tl.add(fr);
            int mi = wx == null ? -1 : WidgetTip.mosqueIdx(now, days[1], iqs);
            if (mi >= 0) { // مطر أو برد شديد أو رياح قوية: من 5 دقائق قبل الأذان حتى الإقامة
                String[] al = Weather.mosqueAlert(wx[0], wx[1], wx.length > 2 ? wx[2] : 0, WidgetTip.NM[mi]);
                if (al != null) tl.add(new String[]{al[0], al[1], ""});
            }
            if (isEid(o, (now + Scheduler.TZ * 3600000L) / 86400000L)) tl.add(new String[]{"🎉 عيد مبارك", "كل عام وأنتم بخير، تقبّل الله طاعتكم", ""});
            if (inWin(o, "lt", dayN)) tl.add(new String[]{"🌙 العشر الأواخر", "تحرَّ ليلة القدر في الوتر، وأكثر من: «اللهم إنك عفوٌّ تحب العفو فاعفُ عني»", ""});
            if (inWin(o, "dh", dayN)) tl.add(new String[]{"🕋 عشر ذي الحجة", "أفضل أيام السنة: صُم ما تيسّر منها (ويوم عرفة آكدها) وأكثر من التكبير والتهليل", ""});
            String[] ak = WidgetTip.adhkarTip(now, days[1], tip[2]);
            if (ak != null) tl.add(ak);
            String[] wm0 = WidgetMsg.item(c, now); // رسالة مؤقتة من المالك: الثالثة بعد عنصرين (10 دقائق من الاستلام)
            if (wm0 != null) tl.add(WidgetMsg.insertIndex(tl.size()), wm0);
            rv.removeAllViews(R.id.wflip);
            rv.removeAllViews(R.id.wone);
            // تنبيه واحد فقط: يُعرض ثابتاً بلا تبديل (ViewFlipper مع عنصر واحد كان يومض)
            boolean one = tl.size() == 1;
            rv.setViewVisibility(R.id.wflip, one ? android.view.View.GONE : android.view.View.VISIBLE);
            rv.setViewVisibility(R.id.wone, one ? android.view.View.VISIBLE : android.view.View.GONE);
            int fsp = o.optInt("wfs", 100);
            float fk = Math.max(70, Math.min(150, fsp)) / 100f;
            for (int j = 0; j < tl.size(); j++) {
                String[] x = tl.get(j);
                RemoteViews cv = new RemoteViews(c.getPackageName(), R.layout.widget_tip);
                cv.setTextViewText(R.id.wtl, x[0]);
                CharSequence reminder = x[1];
                cv.setTextViewText(R.id.wtx, reminder);
                if (light) {
                    cv.setTextColor(R.id.wtl, gold);
                    cv.setTextColor(R.id.wtx, text);
                }
                int widthDp = 250;
                Bundle bounds = wm.getAppWidgetOptions(widgetId);
                if (bounds != null && bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) > 0)
                    widthDp = bounds.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                int reminderHeight = WidgetReminderText.height(c, fk);
                android.widget.TextView titleProbe = new android.widget.TextView(c);
                titleProbe.setTypeface(null, android.graphics.Typeface.BOLD);
                titleProbe.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13 * fk);
                titleProbe.setSingleLine(true);
                titleProbe.setText(x[0]);
                titleProbe.measure(View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int reminderWidth = Math.max(1, Math.round((widthDp - 60) * dm.density) - titleProbe.getMeasuredWidth());
                cv.setTextViewTextSize(R.id.wtl, android.util.TypedValue.COMPLEX_UNIT_DIP, 13 * fk);
                cv.setInt(R.id.wtx, "setHeight", reminderHeight);
                cv.setTextViewTextSize(R.id.wtx, android.util.TypedValue.COMPLEX_UNIT_PX,
                        WidgetReminderText.fit(reminder, reminderWidth, reminderHeight, 13 * fk * dm.density));
                Intent ti = new Intent(c, MainActivity.class);
                if (!x[2].isEmpty()) ti.putExtra("open", x[2]);
                PendingIntent pi = PendingIntent.getActivity(c, 31 + j, ti, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                cv.setOnClickPendingIntent(R.id.wtp, pi);
                cv.setOnClickPendingIntent(R.id.wtl, pi);
                cv.setOnClickPendingIntent(R.id.wtx, pi);
                rv.addView(one ? R.id.wone : R.id.wflip, cv);
                // الإطار الخارجي بلا هدف ثابت عند التناوب: الصف يملأ المستطيل كله،
                // فكل نقطة فيه تتبع التنبيه الظاهر حالياً، والهدف الاحتياطي فقط للتنبيه الواحد الثابت
                if (one) rv.setOnClickPendingIntent(R.id.wtip, pi);
            }
            if (!one) rv.setDisplayedChild(R.id.wflip, 0);
            rv.setOnClickPendingIntent(R.id.wroot, PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
            applyFontScale(rv, o.optInt("wfs", 100));
            {
                rv.setTextViewTextSize(R.id.wcity, android.util.TypedValue.COMPLEX_UNIT_DIP, arc.citySp);
                if (!tw.isEmpty()) rv.setTextViewTextSize(R.id.wtom, android.util.TypedValue.COMPLEX_UNIT_DIP, arc.tomSp);
            }
            WidgetWidthFit.apply(c, rv, widthOf(wm, widgetId), fk, dateLine, cityLine, tw);
            rv.setTextViewText(R.id.wcity, weatherStyled(cityLine,arc,dm));
            rv.setTextViewTextSize(R.id.wcity, android.util.TypedValue.COMPLEX_UNIT_DIP, arc.citySp);
            rv.setTextViewTextSize(R.id.wtom, android.util.TypedValue.COMPLEX_UNIT_DIP, arc.tomSp);
            WidgetMiddleFit.apply(c, wm, widgetId, rv, fk, name, atText, timeText, label, tw.isEmpty(), arc.show);
            wm.updateAppWidget(widgetId, rv);
            }

            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            PendingIntent p = PendingIntent.getBroadcast(c, 7, new Intent(c, WidgetProvider.class).setAction(ACT),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            // أقرب تحديث لازم: الصلاة القادمة، أو تغيّر نافذة الأذكار، أو تدوير الذكر (كل نصف ساعة)
            long slot = (now / 1800000L + 1) * 1800000L;
            long tzMs = Scheduler.TZ * 3600000L, midnight = ((now + tzMs) / 86400000L + 1) * 86400000L - tzMs; // لتبديل "غداً" إلى "اليوم" عند منتصف الليل
            long crit = Math.min(Math.min(target, midnight), WidgetTip.nextBoundary(now, new long[][]{days[1], days[2]}, iqs));
            long wmu = WidgetMsg.activeUntil(c, now);
            if (wmu > 0) crit = Math.min(crit, wmu);
            long at = Math.min(crit, slot) + 1000;
            // التوقيت الدقيق فقط عند حدث حقيقي (صلاة/نافذة ذكر)؛ أما تدوير الذكر كل نصف ساعة فيكفيه منبّه غير دقيق يوفّر البطارية
            if (crit <= slot) {
                try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); }
                catch (Exception e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); }
            } else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            // Refresh the progress marker without waking a sleeping phone.
            // Existing prayer/dhikr wake-up alarms above stay unchanged.
            PendingIntent skyTick = PendingIntent.getBroadcast(c, 8,
                    new Intent(c, WidgetProvider.class).setAction(ACT),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.set(AlarmManager.RTC, (now / 300000L + 1) * 300000L, skyTick);
            Weather.ensure(c);
        } catch (Exception ignored) {}
    }
}

/** Day/night time progress, not astronomical sun/moon altitude or moon phase. */
final class SunMoonIndicator {
    static final class State {
        final boolean day;
        final float progress;
        State(boolean day, float progress) { this.day = day; this.progress = progress; }
    }

    static State state(long now, long[][] days) {
        long sunrise = days[1][1], sunset = days[1][4];
        if (now >= sunrise && now < sunset) return between(true, now, sunrise, sunset);
        // Night ends at sunrise, including the interval after Fajr before sunrise.
        return now < sunrise
                ? between(false, now, days[0][4], sunrise)
                : between(false, now, sunset, days[2][1]);
    }

    private static State between(boolean day, long now, long start, long end) {
        float p = end > start ? (float) ((double) (now - start) / (end - start)) : 0f;
        return new State(day, Math.max(0f, Math.min(1f, p)));
    }

    static String description(State s) {
        return (s.day ? "تقدّم النهار، شمس، " : "تقدّم الليل، هلال، ")
                + Math.round(s.progress * 100) + "٪، ليس موقعاً فلكياً";
    }

    // Deep half-circle dome, 100x58 logical units, circular (rx = ry): sunrise at the right
    // end, sunset at the left end (RTL, matching the prayer-chips order). Day: the sun
    // travels right to left from sunrise to sunset; night: a crescent from sunset to sunrise.
    static void drawArc(android.graphics.Canvas c, State s, int gold) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(gold); p.setStrokeWidth(2.5f); p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        android.graphics.RectF oval = new android.graphics.RectF(8f, 8f, 92f, 92f); // rx = ry = 42: true semicircle
        float sweep = 180f * s.progress, start = 360f - sweep;
        p.setAlpha(65); c.drawArc(oval, 180f, 180f, false, p);
        if (sweep > 0.5f) { p.setAlpha(210); c.drawArc(oval, start, sweep, false, p); }
        double a = Math.toRadians(start);
        float x = 50f + 42f * (float) Math.cos(a), y = 50f + 42f * (float) Math.sin(a);
        p.setStyle(android.graphics.Paint.Style.FILL);
        p.setAlpha(30); c.drawCircle(x, y, 8f, p);
        p.setAlpha(255);
        if (s.day) {
            c.drawCircle(x, y, 4.5f, p);
            p.setStrokeWidth(1.4f);
            for (int i = 0; i < 8; i++) {
                double r = i * Math.PI / 4;
                c.drawLine(x + (float) Math.cos(r) * 6.5f, y + (float) Math.sin(r) * 6.5f,
                        x + (float) Math.cos(r) * 9f, y + (float) Math.sin(r) * 9f, p);
            }
        } else {
            // Transparent crescent, so both original widget gradients show through.
            int save = c.saveLayer(x - 8, y - 8, x + 8, y + 8, null);
            c.drawCircle(x, y, 5.5f, p);
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
            c.drawCircle(x + 2.6f, y - 2f, 4.8f, p);
            p.setXfermode(null); c.restoreToCount(save);
        }
    }
}
