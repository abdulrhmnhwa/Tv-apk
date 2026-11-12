package sy.homs.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

public class MainActivity extends Activity {
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
        @JavascriptInterface public void save(String j) { Prefs.setCfg(MainActivity.this, j); Scheduler.schedule(MainActivity.this); WidgetProvider.refresh(MainActivity.this); LockNotif.refresh(MainActivity.this); }
        @JavascriptInterface public void theme(boolean light) { runOnUiThread(() -> applyBars(light)); }
        @JavascriptInterface public void test() { runOnUiThread(() -> Takbeer.play(getApplicationContext(), () -> {})); }
        @JavascriptInterface public void vib(int ms) { runOnUiThread(() -> buzz(false)); }
        @JavascriptInterface public void vibTest() { runOnUiThread(() -> buzz(true)); }
        @JavascriptInterface public void wx(int t, int code) { Weather.store(MainActivity.this, t, code); WidgetProvider.refresh(MainActivity.this); }
        @JavascriptInterface public void pickTone(String cur) { runOnUiThread(() -> pickToneUi(cur)); }
        @JavascriptInterface public void testTone(String u) { runOnUiThread(() -> testToneUi(u)); }
        @JavascriptInterface public void askNotif() { runOnUiThread(() -> requestNotif(true)); }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                loaded = true;
                if (pendingOpen != null) {
                    String k = pendingOpen;
                    pendingOpen = null;
                    v.evaluateJavascript("window.openAdhkar&&window.openAdhkar('" + k + "')", null);
                }
            }
        });
        w.addJavascriptInterface(new Bridge(), "App");
        setContentView(w);

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
        w.evaluateJavascript("window.goBack?window.goBack():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override protected void onResume() { super.onResume(); w.onResume(); Scheduler.schedule(this); WidgetProvider.refresh(this); LockNotif.refresh(this); }
    @Override protected void onPause() { w.onPause(); super.onPause(); }
    @Override protected void onDestroy() { w.destroy(); super.onDestroy(); }
}
