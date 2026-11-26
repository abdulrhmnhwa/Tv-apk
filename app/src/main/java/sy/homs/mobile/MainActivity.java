package sy.homs.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import java.io.File;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class MainActivity extends Activity {
    /** وسيلة استقبال الاقتراحات (اختر واحدة): رابط نموذج Google Forms (يُفضَّل، بلا بريد ولا حساب للمستخدم) أو بريدك الإلكتروني */
    static final String FEEDBACK_FORM = "https://docs.google.com/forms/d/e/1FAIpQLScBsV-XxyjmC0A4sDF-ETBu5Zngn62MPRFDw59tKChjr_NO0A/viewform";
    static final String FEEDBACK_EMAIL = ""; // مثال: "name@gmail.com"

    private WebView w;
    private boolean loaded = false;
    private String pendingOpen = null;

    /** ألوان شريط الحالة والتنقل وخلفية الصفحة بحسب المظهر (ليلي/نهاري) */
    private void applyBars(boolean light) {
        int c = light ? 0xFFF1FBF5 : 0xFF04170F;
        getWindow().setStatusBarColor(c);
        getWindow().setNavigationBarColor(c);
        if (w != null) w.setBackgroundColor(c);
        if (Build.VERSION.SDK_INT >= 23) {
            android.view.View d = getWindow().getDecorView();
            int f = d.getSystemUiVisibility();
            f = light ? (f | android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) : (f & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            if (Build.VERSION.SDK_INT >= 26)
                f = light ? (f | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) : (f & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            d.setSystemUiVisibility(f);
        }
    }

    /** يفتح قسم الأذكار المطلوب (من الودجت أو من الإشعار) بعد جاهزية الصفحة */
    private void handleOpen(Intent it) {
        if (it == null) return;
        String k = it.getStringExtra("open");
        it.removeExtra("open");
        if (k == null || !k.matches("[a-z]{1,12}")) return;
        if (loaded) w.evaluateJavascript("window.openAdhkar&&window.openAdhkar('" + k + "')", null);
        else pendingOpen = k;
    }

    @Override
    protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        setIntent(it);
        handleOpen(it);
    }

    public class Bridge {
        @JavascriptInterface public String get() { return Prefs.cfg(MainActivity.this); }
        @JavascriptInterface public void save(String j) { Prefs.setCfg(MainActivity.this, j); Scheduler.schedule(MainActivity.this); WidgetProvider.refresh(MainActivity.this); RamadanWidget.refresh(MainActivity.this); LockNotif.refresh(MainActivity.this); }
        @JavascriptInterface public void theme(boolean light) { runOnUiThread(() -> applyBars(light)); }
        @JavascriptInterface public void test() { runOnUiThread(() -> Takbeer.play(getApplicationContext(), () -> {})); }
        @JavascriptInterface public void vib(int ms) { runOnUiThread(() -> buzz(false)); }
        @JavascriptInterface public void vibTest() { runOnUiThread(() -> buzz(true)); }
        @JavascriptInterface public void wx(int t, int code) { Weather.store(MainActivity.this, t, code); WidgetProvider.refresh(MainActivity.this); }
        @JavascriptInterface public void pickTone(String cur) { runOnUiThread(() -> pickToneUi(cur)); }
        @JavascriptInterface public void testTone(String u) { runOnUiThread(() -> testToneUi(u)); }
        /** هل مُنح التطبيق إذن «عدم الإزعاج» (لازم للصامت التلقائي) */
        /* ---------- بطاقات المشاركة ---------- */
        private File cardFile(String b64) throws Exception {
            File d = new File(getCacheDir(), "share");
            d.mkdirs();
            File[] old = d.listFiles();
            if (old != null) for (File o : old) o.delete();
            File f = new File(d, "card" + System.currentTimeMillis() + ".png");
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(android.util.Base64.decode(b64, android.util.Base64.DEFAULT));
            fo.close();
            return f;
        }
        @JavascriptInterface public String shareImg(String b64, String cap) {
            try {
                final File f = cardFile(b64);
                final Uri u = ShareProvider.uri(f.getName());
                final String text = cap;
                runOnUiThread(() -> {
                    try {
                        Intent it = new Intent(Intent.ACTION_SEND).setType("image/png");
                        it.putExtra(Intent.EXTRA_STREAM, u);
                        it.putExtra(Intent.EXTRA_TEXT, text);
                        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(it, "شارك البطاقة"));
                    } catch (Exception ig) {}
                });
                return "ok";
            } catch (Throwable e) { return "err"; }
        }
        /** حفظ في المعرض (أندرويد 10+ بلا أذونات)؛ "na" على الإصدارات الأقدم */
        @JavascriptInterface public String saveImg(String b64) {
            if (Build.VERSION.SDK_INT < 29) return "na";
            try {
                byte[] by = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "mawaqit_" + System.currentTimeMillis() + ".png");
                cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MawaqitSyria");
                Uri u = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (u == null) return "err";
                java.io.OutputStream os = getContentResolver().openOutputStream(u);
                os.write(by);
                os.close();
                return "ok";
            } catch (Throwable e) { return "err"; }
        }
        /* ---------- ضمان وصول الأذان ---------- */
        @JavascriptInterface public String relStatus() {
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                boolean n = Build.VERSION.SDK_INT < 24 || nm.areNotificationsEnabled();
                if (n && Build.VERSION.SDK_INT >= 26) {
                    android.app.NotificationChannel ch = nm.getNotificationChannel("adhan");
                    if (ch != null && ch.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE) n = false;
                }
                android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                boolean ex = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                boolean bt = Build.VERSION.SDK_INT < 23 || ((android.os.PowerManager) getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
                o.put("notif", n).put("exact", ex).put("batt", bt).put("sdk", Build.VERSION.SDK_INT)
                        .put("oem", Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT)).put("model", Build.MANUFACTURER + " " + Build.MODEL)
                        .put("lastTest", getSharedPreferences("rel", 0).getLong("test_ok", 0));
                return o.toString();
            } catch (Throwable e) { return "{}"; }
        }
        @JavascriptInterface public void relOpen(final String what) {
            runOnUiThread(() -> {
                String pk = getPackageName();
                Uri pu = Uri.parse("package:" + pk);
                Intent it = null;
                try {
                    if ("notif".equals(what)) {
                        if (Build.VERSION.SDK_INT >= 26) it = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pk);
                    } else if ("exact".equals(what)) {
                        if (Build.VERSION.SDK_INT >= 31) it = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pu);
                    } else if ("batt".equals(what)) {
                        if (Build.VERSION.SDK_INT >= 23) it = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pu);
                    } else if ("auto".equals(what)) {
                        String[][] c = {
                            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                            {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
                            {"com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"}};
                        for (String[] x : c) {
                            try { startActivity(new Intent().setClassName(x[0], x[1])); return; } catch (Throwable ignored) {}
                        }
                    }
                    if (it != null) { startActivity(it); return; }
                } catch (Throwable ignored) {}
                try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pu)); } catch (Throwable ignored) {}
            });
        }
        /** اختبار إشعار الأذان: sec=0 فوراً، وإلا بعد sec ثانية عبر منبّه (ينجح والتطبيق مغلق) */
        @JavascriptInterface public void relTest(int sec) {
            try {
                Intent i = new Intent(MainActivity.this, AdhanReceiver.class).setAction(Scheduler.ACT).putExtra("k", 99).putExtra("i", 0);
                if (sec <= 0) { sendBroadcast(i); return; }
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(MainActivity.this, 77, i,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                long at = System.currentTimeMillis() + sec * 1000L;
                try {
                    android.app.PendingIntent show = android.app.PendingIntent.getActivity(MainActivity.this, 78, new Intent(MainActivity.this, MainActivity.class), android.app.PendingIntent.FLAG_IMMUTABLE);
                    am.setAlarmClock(new android.app.AlarmManager.AlarmClockInfo(at, show), pi);
                } catch (Throwable e) { am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pi); }
            } catch (Throwable ignored) {}
        }
        @JavascriptInterface public boolean silentAccess() { return PrayerSilence.hasAccess(MainActivity.this); }
        @JavascriptInterface public void askSilentAccess() {
            runOnUiThread(() -> { try { startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); } catch (Exception ig) {} });
        }
        @JavascriptInterface public String appVer() { return UpdateCheck.currentName(MainActivity.this); }
        /** تحقق يدوي من التحديث (زر الإعدادات): النتيجة تصل إلى window.updRes */
        @JavascriptInterface public void checkUpdate() {
            new Thread(() -> {
                final String r = UpdateCheck.fetch(MainActivity.this);
                runOnUiThread(() -> { if (w != null) w.evaluateJavascript("window.updRes&&window.updRes(" + r + ")", null); });
            }).start();
        }
        /** إرسال اقتراح: نموذج Google Forms إن وُضع رابطه، وإلا رسالة بريد جاهزة (مجاني). يعيد "cfg" إن لم يُضبط أي منهما. */
        @JavascriptInterface public String sendFeedback() {
            if (!FEEDBACK_FORM.isEmpty()) {
                runOnUiThread(() -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_FORM))); } catch (Exception ig) {} });
                return "ok";
            }
            if (FEEDBACK_EMAIL.isEmpty()) return "cfg";
            final String body = "\n\n— — —\nالنسخة: " + UpdateCheck.currentName(MainActivity.this) + "\nأندرويد: " + Build.VERSION.RELEASE
                    + " · " + Build.MANUFACTURER + " " + Build.MODEL;
            runOnUiThread(() -> {
                try {
                    Intent it = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
                    it.putExtra(Intent.EXTRA_EMAIL, new String[]{FEEDBACK_EMAIL});
                    it.putExtra(Intent.EXTRA_SUBJECT, "اقتراح — مواقيت الصلاة سوريا");
                    it.putExtra(Intent.EXTRA_TEXT, body);
                    startActivity(it);
                } catch (Exception e) {
                    try {
                        Intent it = new Intent(Intent.ACTION_SEND).setType("text/plain");
                        it.putExtra(Intent.EXTRA_TEXT, "اقتراح لتطبيق مواقيت الصلاة سوريا:" + body);
                        startActivity(Intent.createChooser(it, "أرسل اقتراحك"));
                    } catch (Exception ig) {}
                }
            });
            return "ok";
        }
        @JavascriptInterface public void openUpdate(String u) {
            if (u == null || !u.startsWith("https://")) return;
            runOnUiThread(() -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ig) {} });
        }
        /** يفتح صفحة محددة من موقع تنزيل (تفسير/ترجمة) داخل التطبيق؛ يقتصر على tanzil.net */
        @JavascriptInterface public void openSite(String u) {
            final String url = (u != null && u.startsWith("https://tanzil.net/")) ? u : "https://tanzil.net/";
            runOnUiThread(() -> {
                trackWeb = false;
                openWebUi(url);
                if (web != null) {
                    web.loadUrl("about:blank");
                    web.postDelayed(() -> web.loadUrl(url), 200);
                }
            });
        }
        @JavascriptInterface public void openWeb(String u) { runOnUiThread(() -> { trackWeb = false; openWebUi(u); }); }
        @JavascriptInterface public void openOut(String u) { if (u == null || !u.startsWith("https://")) return; runOnUiThread(() -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ig) {} }); }
        /** فتح القرآن مع تتبّع الموضع: عند الإغلاق تُرسل الصفحة/الموضع للواجهة لإضافتها على الختمة */
        @JavascriptInterface public void openQuran(String u) { runOnUiThread(() -> { trackWeb = true; openWebUi(u); }); }
        @JavascriptInterface public void wx2(int code, int max, int min) { Weather.storeTomorrow(MainActivity.this, code, max, min); WidgetProvider.refresh(MainActivity.this); }
        @JavascriptInterface public void tsSet(int n, int t, int c) { if (FloatTasbih.prefs(MainActivity.this).getBoolean("dirty", false)) return; FloatTasbih.prefs(MainActivity.this).edit().putInt("n", n).putInt("t", t).putInt("c", c).apply(); }
        @JavascriptInterface public boolean floatState() { return FloatTasbih.running(MainActivity.this); }
        @JavascriptInterface public void floatOn(boolean on) { runOnUiThread(() -> floatToggle(on)); }
        @JavascriptInterface public void askNotif() { runOnUiThread(() -> requestNotif(true)); }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AnnounceService.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        w = new WebView(this);
        applyBars("light".equals(Prefs.obj(this).optString("th", "dark")));
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(false); // تلاوة الآيات داخل المصحف تبدأ من زر الاستماع في الأقسام
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                loaded = true;
                syncTs();
                if (pendingOpen != null) {
                    String k = pendingOpen;
                    pendingOpen = null;
                    v.evaluateJavascript("window.openAdhkar&&window.openAdhkar('" + k + "')", null);
                }
            }
        });
        w.addJavascriptInterface(new Bridge(), "App");
        root = new FrameLayout(this);
        root.addView(w, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        Scheduler.schedule(this);
        requestNotif(false);
        handleOpen(getIntent());
        w.loadUrl("file:///android_asset/index.html");
    }

    /** رجّة خفيفة مزدوجة مع عدة محاولات احتياطية، وفي وضع التجربة تظهر حالة الجهاز */
    @SuppressWarnings("deprecation")
    private void buzz(boolean diag) {
        Vibrator v = null;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) v = vm.getDefaultVibrator();
            }
            if (v == null) v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable ignored) {}
        boolean has = v != null && v.hasVibrator(), ok = false;
        long[] pat = {0, 70, 90, 70};
        if (has) {
            try {
                AudioAttributes aa = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1), aa);
                else v.vibrate(pat, -1, aa);
                ok = true;
            } catch (Throwable ignored) {}
            if (!ok) {
                try {
                    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1));
                    else v.vibrate(pat, -1);
                    ok = true;
                } catch (Throwable ignored) {}
            }
        }
        if (!ok) {
            try { w.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); }
            catch (Throwable ignored) {}
        }
        if (diag) {
            String mode = "عادي";
            try {
                int rm = ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).getRingerMode();
                mode = rm == AudioManager.RINGER_MODE_SILENT ? "صامت" : rm == AudioManager.RINGER_MODE_VIBRATE ? "اهتزاز" : "عادي";
            } catch (Throwable ignored) {}
            Toast.makeText(this, "الاهتزاز: " + (has ? (ok ? "تم الإرسال للمحرك" : "فشل الإرسال") : "غير مدعوم")
                    + " · وضع الصوت: " + mode, Toast.LENGTH_LONG).show();
        }
    }

    private FrameLayout root;
    private LinearLayout webBox;
    private boolean trackWeb, clearHist;
    private String gotoUrl = "";
    private int goTry;
    private TextView goBtn;
    private LinearLayout goRow;
    private final Runnable goHide = () -> { if (goRow != null) goRow.setVisibility(View.GONE); };
    private WebView web;

    /** يفتح موقع القرآن داخل التطبيق فوق الواجهة (بدون متصفح خارجي) */
    @SuppressWarnings("SetJavaScriptEnabled")
    private void openWebUi(String u) {
        if (u == null || !u.startsWith("https://")) u = "https://tanzil.net/";
        if (webBox == null) {
            webBox = new LinearLayout(this);
            webBox.setOrientation(LinearLayout.VERTICAL);
            webBox.setBackgroundColor(0xFF04170F);
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setBackgroundColor(0xFF04170F);
            int pad = (int) (10 * getResources().getDisplayMetrics().density);
            bar.setPadding(pad, pad / 2, pad, pad / 2);
            TextView close = new TextView(this);
            close.setText("✕");
            close.setTextSize(22);
            close.setTextColor(0xFFD9B85C);
            close.setPadding(pad, 0, pad, 0);
            close.setOnClickListener(v -> closeWeb());
            TextView t = new TextView(this);
            t.setText("القرآن الكريم");
            t.setTextSize(18);
            t.setTextColor(0xFFD9B85C);
            t.setGravity(Gravity.CENTER);
            bar.addView(close, new LinearLayout.LayoutParams(-2, -2));
            bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            View sp = new View(this);
            bar.addView(sp, new LinearLayout.LayoutParams(close.getPaddingLeft() * 2 + (int) (22 * getResources().getDisplayMetrics().density), 1));
            webBox.addView(bar, new LinearLayout.LayoutParams(-1, -2));
            goBtn = new TextView(this);
            goBtn.setText("📖 الذهاب إلى صفحة الختمة");
            goBtn.setTextSize(16);
            goBtn.setTextColor(0xFF04170F);
                        goBtn.setGravity(Gravity.CENTER);
            goBtn.setPadding(pad, pad, pad, pad);
            goBtn.setOnClickListener(v -> {
                if (web == null) return;
                String[] g = gotoUrl.split(" ");
                if (g.length == 0 || !g[0].startsWith("https://")) return;
                // تحميل جديد كامل بالرابط (الموقع يقرأ الموضع عند التحميل). الضغطة الثانية تجرّب صيغة سورة:آية
                final String tgt = g[goTry % g.length];
                goTry++;
                goBtn.setText(g.length > 1 ? "📖 لم تصل للصفحة؟ اضغط للمحاولة بطريقة أخرى" : "📖 الذهاب إلى صفحة الختمة");
                web.loadUrl("about:blank");
                web.postDelayed(() -> web.loadUrl(tgt), 200);
                goRow.removeCallbacks(goHide);
                goRow.postDelayed(goHide, 3000); // يختفي بعد 3 ثوانٍ من الضغط
            });
            goRow = new LinearLayout(this);
            goRow.setOrientation(LinearLayout.HORIZONTAL);
            goRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            goRow.setBackgroundColor(0xFFD9B85C);
            goRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView gx = new TextView(this);
            gx.setText("✕");
            gx.setTextSize(20);
            gx.setTextColor(0xFF04170F);
            gx.setPadding(pad * 2, pad, pad * 2, pad);
            gx.setOnClickListener(v -> { goRow.removeCallbacks(goHide); goRow.setVisibility(View.GONE); });
            goRow.addView(gx, new LinearLayout.LayoutParams(-2, -2));
            goRow.addView(goBtn, new LinearLayout.LayoutParams(0, -2, 1));
            webBox.addView(goRow, new LinearLayout.LayoutParams(-1, -2));
            web = new WebView(this);
            web.setBackgroundColor(0xFF04170F);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setAllowFileAccess(false);
            ws.setBuiltInZoomControls(true);
            ws.setDisplayZoomControls(false);
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView v, String url) {
                    if (clearHist && url != null && url.startsWith("http")) { clearHist = false; v.clearHistory(); }
                }
                @Override public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest rq) {
                    String sc = rq.getUrl().getScheme(); return sc == null || !sc.startsWith("http"); // الروابط داخل التطبيق
                }
                @Override public void onReceivedError(WebView v, android.webkit.WebResourceRequest rq, android.webkit.WebResourceError er) {
                    if (rq.isForMainFrame())
                        v.loadDataWithBaseURL(null, "<html dir='rtl'><body style='background:#04170f;color:#d9b85c;font:18px sans-serif;text-align:center;padding:3rem 1rem'>يتطلب اتصالاً بالإنترنت<br><br>تحقق من الاتصال ثم أعد فتح النافذة</body></html>", "text/html", "utf-8", null);
                }
            });
            webBox.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
            root.addView(webBox, new FrameLayout.LayoutParams(-1, -1));
        }
        webBox.setVisibility(View.VISIBLE);
        // مصحف واحد: يرجع دائماً لآخر صفحة كنت عليها. زر «الذهاب إلى صفحة الختمة» (للختمة فقط) ينقلك للصفحة التالية للمقروء
        gotoUrl = trackWeb ? u : "";
        goTry = 0;
        goBtn.setText("📖 الذهاب إلى صفحة الختمة");
        goRow.removeCallbacks(goHide);
        goRow.setVisibility(trackWeb ? View.VISIBLE : View.GONE);
        if (trackWeb) goRow.postDelayed(goHide, 7000); // يختفي لحاله بعد 7 ثوانٍ إن لم تضغط عليه
        clearHist = true;
        String cur = web.getUrl();
        if (cur == null || !cur.startsWith("http"))
            web.loadUrl(getSharedPreferences("quran", 0).getString("out", "https://tanzil.net/"));
    }

    private void closeWeb() {
        if (webBox == null) return;
        String cur = web != null ? web.getUrl() : null;
        if (cur != null && cur.startsWith("http")) getSharedPreferences("quran", 0).edit().putString("out", cur).apply();
        if (trackWeb && web != null) {
            trackWeb = false;
            w.evaluateJavascript("window.quranDone&&window.quranDone()", null);
        }
        webBox.setVisibility(View.GONE);
    }

    private void floatToggle(boolean on) {
        if (!on) { FloatTasbih.stop(this); return; }
        if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل «السماح بالظهور فوق التطبيقات» للتطبيق ثم عُد وفعّل المسبحة العائمة", Toast.LENGTH_LONG).show();
            try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
            return;
        }
        FloatTasbih.start(this);
    }

    /** يمرّر عدّ المسبحة العائمة إلى الواجهة عند الرجوع للتطبيق */
    private final Runnable poll = new Runnable() { @Override public void run() { syncTs(); w.postDelayed(this, 1000); } };

    private void syncTs() {
        if (!loaded) return;
        android.content.SharedPreferences p = FloatTasbih.prefs(this);
        if (!p.getBoolean("dirty", false)) return;
        p.edit().putBoolean("dirty", false).apply();
        w.evaluateJavascript("window.tsSync&&window.tsSync(" + p.getInt("n", 0) + "," + p.getInt("c", 0) + ")", null);
    }

    private static final int REQ_TONE = 77;
    private Ringtone testing;

    private void pickToneUi(String cur) {
        try {
            Intent it = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            it.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION | RingtoneManager.TYPE_ALARM);
            it.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "نغمة التنبيه");
            it.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            it.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            it.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            Uri existing = cur == null || cur.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    : cur.equals("silent") ? null : Uri.parse(cur);
            it.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing);
            startActivityForResult(it, REQ_TONE);
        } catch (Exception e) {
            Toast.makeText(this, "تعذّر فتح قائمة النغمات", Toast.LENGTH_SHORT).show();
        }
    }

    private void testToneUi(String u) {
        try {
            if (testing != null) testing.stop();
            Uri uri = u == null || u.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : u.equals("silent") ? null : Uri.parse(u);
            if (uri == null) return;
            testing = RingtoneManager.getRingtone(this, uri);
            if (testing != null) testing.play();
        } catch (Exception ignored) {}
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int rc, int res, Intent d) {
        super.onActivityResult(rc, res, d);
        if (rc != REQ_TONE || res != RESULT_OK || d == null) return;
        Uri u = d.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        String val, name;
        if (u == null) { val = "silent"; name = "صامت"; }
        else if (u.equals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))) { val = ""; name = "الافتراضية"; }
        else {
            val = u.toString();
            name = "نغمة مخصصة";
            try { Ringtone r = RingtoneManager.getRingtone(this, u); if (r != null) name = r.getTitle(this); } catch (Exception ignored) {}
        }
        w.evaluateJavascript("window.toneSet&&window.toneSet(" + JSONObject.quote(val) + "," + JSONObject.quote(name) + ")", null);
    }

    private void requestNotif(boolean say) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else if (say) {
            Toast.makeText(this, "الإشعارات مفعّلة", Toast.LENGTH_SHORT).show();
        }
    }

    /** إن كان الأذان يُشغَّل والتطبيق مفتوح: زر رفع/خفض الصوت يوقفه فوراً */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent e) {
        if ((keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
                && Takbeer.isPlaying()) {
            Takbeer.stopAll();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    public void onBackPressed() {
        if (webBox != null && webBox.getVisibility() == View.VISIBLE) {
            if (web.canGoBack()) web.goBack(); else closeWeb();
            return;
        }
        w.evaluateJavascript("window.goBack?window.goBack():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override protected void onResume() { super.onResume(); w.onResume(); syncTs(); w.removeCallbacks(poll); w.postDelayed(poll, 500); if (web != null) web.onResume(); Scheduler.schedule(this); WidgetProvider.refresh(this); LockNotif.refresh(this); UpdateCheck.maybe(this); PrayerSilence.check(this); }
    @Override protected void onPause() { w.removeCallbacks(poll); w.evaluateJavascript("window.selReset&&window.selReset()", null); w.onPause(); if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (web != null) web.destroy(); w.destroy(); super.onDestroy(); }
}
