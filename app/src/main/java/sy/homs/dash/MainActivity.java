package sy.homs.dash;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {
    private static final int GOLD = 0xFFD6B25E, GREEN = 0xFF0F4D36;

    static class Item { String label, pkg; Drawable icon; Intent intent; }

    private WebView w;
    private LinearLayout row;
    private TextView status;
    static MainActivity cur;
    private View black;
    private boolean sleeping;
    private PackageManager pm;
    private int itemW, iconS, firstApp = 5;
    private final BroadcastReceiver pkgR = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { load(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        pm = getPackageManager();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = dm.widthPixels, sh = dm.heightPixels;
        itemW = sw / 9;
        iconS = (int) (itemW * 0.56f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF04170F);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        w = new WebView(this);
        w.setBackgroundColor(0xFF04170F);
        w.setFocusable(false);
        w.setFocusableInTouchMode(false);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        w.setVerticalScrollBarEnabled(false);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        w.setWebViewClient(new WebViewClient());
        root.addView(w, new LinearLayout.LayoutParams(-1, 0, 58f));

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setClipChildren(false);
        rail.setPadding(sw / 50, sh / 70, sw / 50, 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText("التطبيقات");
        t1.setTextColor(GOLD);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemW * 0.2f);
        TextView t2 = new TextView(this);
        t2.setText("   اضغط مطولاً على تطبيق لإدارته، أو على 🔔 لتجربة الصوت");
        t2.setTextColor(0xFFA9C2B4);
        t2.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemW * 0.12f);
        status = new TextView(this);
        status.setTextColor(GOLD);
        status.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemW * 0.12f);
        status.setPadding(itemW / 2, 0, 0, 0);
        head.addView(t1);
        head.addView(t2);
        head.addView(status);
        rail.addView(head);

        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        sv.addView(row, new ViewGroup.LayoutParams(-2, -1));
        rail.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(rail, new LinearLayout.LayoutParams(-1, 0, 42f));

        FrameLayout fl = new FrameLayout(this);
        fl.addView(root, new FrameLayout.LayoutParams(-1, -1));
        black = new View(this);
        black.setBackgroundColor(Color.BLACK);
        black.setVisibility(View.GONE);
        fl.addView(black, new FrameLayout.LayoutParams(-1, -1));
        setContentView(fl);
        cur = this;
        w.loadUrl("file:///android_asset/index.html");
        Scheduler.schedule(this);
        load();
    }

    private void load() {
        new Thread(() -> {
            Map<String, Item> m = new HashMap<>();
            String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
            for (String c : cats) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(c);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    String p = ri.activityInfo.packageName;
                    if (p.equals(getPackageName()) || m.containsKey(p)) continue;
                    Item it = new Item();
                    it.pkg = p;
                    it.label = String.valueOf(ri.loadLabel(pm));
                    it.icon = ri.loadIcon(pm);
                    it.intent = new Intent(Intent.ACTION_MAIN).addCategory(c)
                            .setClassName(p, ri.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    m.put(p, it);
                }
            }
            List<Item> l = new ArrayList<>(m.values());
            Collections.sort(l, (a, c) -> a.label.compareToIgnoreCase(c.label));
            runOnUiThread(() -> render(l));
        }).start();
    }

    private void render(List<Item> l) {
        row.removeAllViews();
        row.addView(cell("الإعدادات", null, "⚙️", () -> go(new Intent(Settings.ACTION_SETTINGS)), null));
        row.addView(cell("الشبكة", null, "📶", () -> go(new Intent(Settings.ACTION_WIFI_SETTINGS)), null));
        row.addView(cell("المصادر", null, "🔌", this::inputs, this::pickSource));
        row.addView(cell("إطفاء الشاشة", null, "🌙", this::sleep, null));
        final View[] ad = new View[1];
        ad[0] = cell("الأذان", null, Prefs.on(this) ? "🔔" : "🔕", () -> {
            boolean n = !Prefs.on(this);
            Prefs.set(this, n);
            ((TextView) ((ViewGroup) ad[0]).getChildAt(0)).setText(n ? "🔔" : "🔕");
            Scheduler.schedule(this);
            Toast.makeText(this, n ? "تكبير الأذان مفعّل" : "تكبير الأذان متوقف", Toast.LENGTH_SHORT).show();
        }, () -> Takbeer.play(getApplicationContext(), () -> {}));
        row.addView(ad[0]);
        for (final Item i : l) {
            row.addView(cell(i.label, i.icon, null, () -> go(i.intent),
                    () -> go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + i.pkg)))));
        }
        focusFirst();
    }

    private void focusFirst() {
        int n = row.getChildCount();
        if (n > 0) row.getChildAt(Math.min(firstApp, n - 1)).requestFocus();
    }

    private View cell(String label, Drawable ic, String emoji, final Runnable run, final Runnable lng) {
        int mg = (int) (itemW * 0.05f), pd = (int) (itemW * 0.07f);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(pd, pd, pd, pd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(itemW, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(mg, mg, mg, mg);
        c.setLayoutParams(lp);
        final GradientDrawable norm = box(0x22FFFFFF, 0), foc = box(GREEN, 4);
        c.setBackground(norm);

        View icon;
        if (ic != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(ic);
            icon = iv;
        } else {
            TextView tv = new TextView(this);
            tv.setText(emoji);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, iconS * 0.7f);
            icon = tv;
        }
        c.addView(icon, new LinearLayout.LayoutParams(iconS, iconS));

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemW * 0.13f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = (int) (itemW * 0.04f);
        c.addView(t, tl);

        c.setOnFocusChangeListener((v, f) -> {
            v.setBackground(f ? foc : norm);
            v.animate().scaleX(f ? 1.1f : 1f).scaleY(f ? 1.1f : 1f).setDuration(110).start();
            v.setElevation(f ? 8f : 0f);
        });
        c.setOnClickListener(v -> run.run());
        if (lng != null) c.setOnLongClickListener(v -> { lng.run(); return true; });
        return c;
    }

    private GradientDrawable box(int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(itemW * 0.12f);
        if (stroke > 0) g.setStroke(stroke, GOLD);
        return g;
    }

    private void go(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show(); }
    }

    private void inputs() {
        String s = getSharedPreferences("p", 0).getString("src", null);
        if (s == null) { pickSource(); return; }
        String[] x = s.split("/");
        go(new Intent().setClassName(x[0], x[1]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** يبحث عن شاشات النظام المحتملة للمصادر لتجربتها وحفظ الصحيحة */
    private void pickSource() {
        Toast.makeText(this, "جارِ البحث عن شاشة المصادر…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final TreeMap<String, String> m = new TreeMap<>();
            java.util.regex.Pattern k = java.util.regex.Pattern.compile("source|input|signal|hdmi|livetv|tvapp|tvui|tvsetting|atv|dtv|channel|hisi|wisdom|launcher");
            try {
                for (android.content.pm.PackageInfo p : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
                    if (p.activities == null || p.packageName.equals(getPackageName())) continue;
                    for (android.content.pm.ActivityInfo a : p.activities) {
                        String id = a.packageName + "/" + a.name;
                        if (a.exported && a.enabled && k.matcher(id.toLowerCase()).find())
                            m.put(id, a.name.substring(a.name.lastIndexOf('.') + 1) + "\n" + a.packageName);
                    }
                }
            } catch (Throwable ignored) {}
            final List<String> ids = new ArrayList<>(m.keySet()), labels = new ArrayList<>(m.values());
            runOnUiThread(() -> showPicker(ids, labels));
        }).start();
    }

    private void showPicker(final List<String> ids, List<String> labels) {
        if (ids.isEmpty()) { Toast.makeText(this, "لم أجد شاشات مناسبة", Toast.LENGTH_LONG).show(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(itemW / 4, itemW / 4, itemW / 4, itemW / 4);
        TextView hd = new TextView(this);
        hd.setText("ضغطة = تجربة الشاشة   ·   ضغطة مطولة = حفظها كزر المصادر");
        hd.setTextColor(GOLD);
        hd.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemW * 0.13f);
        box.addView(hd);
        ListView lv = new ListView(this);
        lv.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int p, View c, ViewGroup g) {
                TextView t = (TextView) super.getView(p, c, g);
                t.setTextColor(Color.WHITE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, itemW * 0.11f);
                return t;
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();
        lv.setOnItemClickListener((a, v, p, id) -> {
            String[] x = ids.get(p).split("/");
            go(new Intent().setClassName(x[0], x[1]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        lv.setOnItemLongClickListener((a, v, p, id) -> {
            getSharedPreferences("p", 0).edit().putString("src", ids.get(p)).apply();
            Toast.makeText(this, "تم حفظ: " + ids.get(p), Toast.LENGTH_LONG).show();
            d.dismiss();
            return true;
        });
        d.getWindow().setBackgroundDrawable(new ColorDrawable(0xFF0B3B2B));
        d.show();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        d.getWindow().setLayout((int) (dm.widthPixels * 0.75f), (int) (dm.heightPixels * 0.85f));
    }

    private void sleep() {
        sleeping = true;
        black.setVisibility(View.VISIBLE);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.01f;
        getWindow().setAttributes(lp);
    }

    void wake() {
        if (!sleeping) return;
        sleeping = false;
        black.setVisibility(View.GONE);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = -1f;
        getWindow().setAttributes(lp);
        focusFirst();
    }

    static void wakeNow() {
        final MainActivity a = cur;
        if (a != null) a.runOnUiThread(a::wake);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (sleeping) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) wake();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void updateStatus() {
        android.content.SharedPreferences p = getSharedPreferences("p", 0);
        long nx = p.getLong("next", 0), ls = p.getLong("last", 0);
        status.setText(Prefs.on(this) && nx > 0
                ? "التالي: " + p.getString("nn", "") + " " + Scheduler.fmt(nx) + (ls > 0 ? " · آخر تنبيه: " + Scheduler.fmt(ls) : " · لم يُسجَّل تنبيه بعد")
                : "التكبير متوقف");
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (sleeping) wake();
        focusFirst();
    }

    @Override public void onBackPressed() { /* الشاشة الرئيسية لا تُغلق */ }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgR, f);
    }

    @Override protected void onStop() { try { unregisterReceiver(pkgR); } catch (Exception ignored) {} super.onStop(); }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override protected void onResume() { super.onResume(); w.onResume(); w.resumeTimers(); Scheduler.schedule(this); updateStatus(); }
    @Override protected void onPause() { w.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (cur == this) cur = null; w.destroy(); super.onDestroy(); }
}
