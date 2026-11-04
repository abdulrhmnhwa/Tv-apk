package sy.homs.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView w;

    class Bridge {
        @JavascriptInterface public String get() { return Prefs.cfg(MainActivity.this); }
        @JavascriptInterface public void save(String j) { Prefs.setCfg(MainActivity.this, j); Scheduler.schedule(MainActivity.this); WidgetProvider.refresh(MainActivity.this); }
        @JavascriptInterface public void test() { runOnUiThread(() -> Takbeer.play(getApplicationContext(), () -> {})); }
        @JavascriptInterface public void vib(int ms) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(Math.max(5, Math.min(300, ms)));
        }
        @JavascriptInterface public void askNotif() { runOnUiThread(() -> requestNotif(true)); }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0xFF04170F);
        getWindow().setNavigationBarColor(0xFF04170F);

        w = new WebView(this);
        w.setBackgroundColor(0xFF04170F);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setSupportZoom(false);
        w.setWebViewClient(new WebViewClient());
        w.addJavascriptInterface(new Bridge(), "App");
        setContentView(w);

        Scheduler.schedule(this);
        requestNotif(false);
        w.loadUrl("file:///android_asset/index.html");
    }

    private void requestNotif(boolean say) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else if (say) {
            Toast.makeText(this, "الإشعارات مفعّلة", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        w.evaluateJavascript("window.goBack?window.goBack():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    @Override protected void onResume() { super.onResume(); w.onResume(); Scheduler.schedule(this); WidgetProvider.refresh(this); }
    @Override protected void onPause() { w.onPause(); super.onPause(); }
    @Override protected void onDestroy() { w.destroy(); super.onDestroy(); }
}
