package sy.homs.dash;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    static MainActivity cur;
    private WebView w;
    private View black;
    private boolean sleeping;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

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
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) { push(); }
        });

        FrameLayout fl = new FrameLayout(this);
        fl.setBackgroundColor(0xFF04170F);
        fl.addView(w, new FrameLayout.LayoutParams(-1, -1));
        black = new View(this);
        black.setBackgroundColor(Color.BLACK);
        black.setVisibility(View.GONE);
        fl.addView(black, new FrameLayout.LayoutParams(-1, -1));
        setContentView(fl);

        cur = this;
        Scheduler.schedule(this);
        w.loadUrl("file:///android_asset/index.html");
    }

    private void push() {
        w.evaluateJavascript("window.setAdhan&&window.setAdhan(" + Prefs.on(this) + ")", null);
    }

    private void toggleAdhan() {
        boolean n = !Prefs.on(this);
        Prefs.set(this, n);
        Scheduler.schedule(this);
        push();
        Toast.makeText(this, n ? "تكبير الأذان: مفعّل" : "تكبير الأذان: متوقف", Toast.LENGTH_SHORT).show();
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
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    sleep();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    toggleAdhan();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    Toast.makeText(this, "تجربة صوت التكبير", Toast.LENGTH_SHORT).show();
                    Takbeer.play(getApplicationContext(), () -> {});
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onNewIntent(android.content.Intent i) {
        super.onNewIntent(i);
        wake();
    }

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

    @Override protected void onResume() { super.onResume(); w.onResume(); w.resumeTimers(); Scheduler.schedule(this); push(); }
    @Override protected void onPause() { w.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (cur == this) cur = null; w.destroy(); super.onDestroy(); }
}
