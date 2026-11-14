package sy.homs.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** مسبحة عائمة دائرية فوق كل التطبيقات: الضغطة تزيد العدّ، والسحب ينقلها */
public class FloatTasbih extends Service {
    static final String ACT_STOP = "sy.homs.mobile.FLOAT_STOP";
    static final String CH = "float";
    static final int NID = 9001;

    private WindowManager wm;
    private TextView v;
    private WindowManager.LayoutParams lp;
    private SharedPreferences sp;
    private final SharedPreferences.OnSharedPreferenceChangeListener lis = (p, k) -> { if (v != null) v.post(this::draw); };

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("ts", 0); }

    static boolean running(Context c) { return prefs(c).getBoolean("float", false); }

    static void start(Context c) {
        Intent i = new Intent(c, FloatTasbih.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static void stop(Context c) { c.stopService(new Intent(c, FloatTasbih.class)); prefs(c).edit().putBoolean("float", false).apply(); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent it, int flags, int id) {
        if (it != null && ACT_STOP.equals(it.getAction())) { stopSelf(); return START_NOT_STICKY; }
        sp = prefs(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CH, "المسبحة العائمة", NotificationManager.IMPORTANCE_LOW));
        }
        Intent stop = new Intent(this, FloatTasbih.class).setAction(ACT_STOP);
        PendingIntent pi = PendingIntent.getService(this, 5, stop, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        nb.setSmallIcon(R.drawable.ic_stat_mosque).setContentTitle("المسبحة العائمة تعمل").setContentText("اضغط لإيقافها").setContentIntent(pi).setOngoing(true);
        Notification n = nb.build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NID, n, 1 << 30); // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else startForeground(NID, n);
        if (v == null) addView();
        sp.edit().putBoolean("float", true).apply();
        return START_STICKY;
    }

    private int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + .5f); }

    private void addView() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        v = new TextView(this);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(0xFFF4F1E8);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextSize(22);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xE60F5A3A);
        g.setStroke(dp(3), 0xFFD6B25E);
        v.setBackground(g);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        int sz = dp(72);
        lp = new WindowManager.LayoutParams(sz, sz, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = sp.getInt("fx", dp(16));
        lp.y = sp.getInt("fy", dp(240));
        v.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int ox, oy; boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); ox = lp.x; oy = lp.y; moved = false;
                        view.setAlpha(.7f);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                        if (moved || Math.abs(mx) > dp(8) || Math.abs(my) > dp(8)) {
                            moved = true;
                            lp.x = ox + (int) mx; lp.y = oy + (int) my;
                            try { wm.updateViewLayout(v, lp); } catch (Throwable ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        view.setAlpha(1f);
                        if (moved) sp.edit().putInt("fx", lp.x).putInt("fy", lp.y).apply();
                        else count();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        view.setAlpha(1f);
                        return true;
                }
                return false;
            }
        });
        draw();
        try { wm.addView(v, lp); } catch (Throwable t) { stopSelf(); return; }
        sp.registerOnSharedPreferenceChangeListener(lis);
    }

    private void draw() { if (v != null) v.setText(String.valueOf(sp.getInt("n", 0))); }

    /** +1، وعند بلوغ الهدف يرجع العدّاد إلى صفر وتزيد الدورات */
    private void count() {
        int n = sp.getInt("n", 0) + 1, t = sp.getInt("t", 0), c = sp.getInt("c", 0);
        boolean cycle = t > 0 && n >= t;
        if (cycle) { n = 0; c++; }
        sp.edit().putInt("n", n).putInt("c", c).putBoolean("dirty", true).apply();
        draw();
        buzz(cycle ? 60 : 12);
    }

    @SuppressWarnings("deprecation")
    private void buzz(int ms) {
        try {
            Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vb == null || !vb.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) vb.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else vb.vibrate(ms);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroy() {
        try { if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(lis); } catch (Throwable ignored) {}
        try { if (v != null) wm.removeView(v); } catch (Throwable ignored) {}
        v = null;
        prefs(this).edit().putBoolean("float", false).apply();
        super.onDestroy();
    }
}
