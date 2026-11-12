package sy.homs.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

public class Takbeer {
    /** إجراء إيقاف الصوت الجاري حالياً (null إن لم يكن هناك صوت) */
    private static volatile Runnable current = null;

    public static boolean isPlaying() { return current != null; }

    /** يوقف صوت الأذان/التكبير الجاري فوراً */
    public static void stopAll() {
        Runnable r = current;
        if (r != null) r.run();
    }

    /**
     * حارس الإيقاف: أثناء تشغيل الصوت يوقفه فوراً عند ضغط زر رفع/خفض الصوت أو زر القفل (تشغيل/إطفاء الشاشة).
     * - أزرار الصوت: عبر MediaSession تلتقط الأزرار حتى والشاشة مطفأة، مع استقبال تغيّر صوت المنبّه كاحتياط.
     * - زر القفل: عبر بثّ إطفاء/إضاءة الشاشة.
     */
    private static class Guard {
        final Context app;
        final Runnable stop;
        final long t0 = SystemClock.elapsedRealtime();
        BroadcastReceiver rcv;
        MediaSession ms;
        final Handler hd = new Handler(Looper.getMainLooper());
        int[] base = null;
        boolean scr = true;
        final Runnable poll = new Runnable() {
            @Override public void run() {
                if (current != stop) return; // انتهى التشغيل
                try {
                    int[] v = vols();
                    boolean s = interactive();
                    if (SystemClock.elapsedRealtime() - t0 < 1500) { base = v; scr = s; } // فترة سماح لأول لحظات التشغيل
                    else if (s != scr || !java.util.Arrays.equals(v, base)) { stop.run(); return; }
                } catch (Throwable ignored) {}
                hd.postDelayed(this, 120);
            }
        };

        int[] vols() {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            return new int[]{am.getStreamVolume(AudioManager.STREAM_ALARM), am.getStreamVolume(AudioManager.STREAM_MUSIC),
                    am.getStreamVolume(AudioManager.STREAM_RING), am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)};
        }

        boolean interactive() {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            return pm.isInteractive();
        }

        Guard(Context app, Runnable stop) { this.app = app; this.stop = stop; }

        void arm() {
            current = stop;
            rcv = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    // تجاهل أول ثانية ونصف حتى لا تُوقفه إضاءة الشاشة التلقائية عند وصول الإشعار
                    if (SystemClock.elapsedRealtime() - t0 < 1500) return;
                    String a = i.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(a) || Intent.ACTION_SCREEN_ON.equals(a)) stop.run();
                    else if ("android.media.VOLUME_CHANGED_ACTION".equals(a)
                            && i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == AudioManager.STREAM_ALARM) stop.run();
                }
            };
            // كل تسجيل بمعزل عن الآخر: فشل أحدهما (مثلاً في أندرويد 14) لا يلغي الثاني
            try {
                IntentFilter f = new IntentFilter();
                f.addAction(Intent.ACTION_SCREEN_OFF);
                f.addAction(Intent.ACTION_SCREEN_ON);
                app.registerReceiver(rcv, f);
            } catch (Throwable ignored) {}
            try {
                IntentFilter f = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
                if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(rcv, f, Context.RECEIVER_EXPORTED);
                else app.registerReceiver(rcv, f);
            } catch (Throwable ignored) {}
            // طبقة مراقبة مباشرة لا تعتمد على البثّ: تقارن حالة الشاشة ومستويات الصوت كل 120 مللي ثانية
            hd.postDelayed(poll, 120);
            try {
                ms = new MediaSession(app, "adhan_guard");
                ms.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
                ms.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                    @Override public void onAdjustVolume(int direction) { stop.run(); }
                });
                ms.setActive(true);
            } catch (Throwable e) { ms = null; }
        }

        void disarm() {
            if (current == stop) current = null;
            hd.removeCallbacks(poll);
            try { if (rcv != null) app.unregisterReceiver(rcv); } catch (Throwable ignored) {}
            rcv = null;
            try { if (ms != null) { ms.setActive(false); ms.release(); } } catch (Throwable ignored) {}
            ms = null;
        }
    }

    public static boolean has(Context c, String name) {
        try { c.getAssets().openFd(name).close(); return true; } catch (Exception e) { return false; }
    }

    /** يشغّل ملفاً صوتياً من assets (بتصنيف المنبّه). إن فشل ينتهي بصمت. */
    public static void playFile(final Context c, String name, final Runnable done) {
        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] fin = {false};
        final Runnable end = () -> { if (!fin[0]) { fin[0] = true; done.run(); } };
        final Guard[] g = {null};
        final Runnable fullEnd = () -> { if (g[0] != null) g[0].disarm(); end.run(); };
        h.postDelayed(end, 20000);
        try {
            AssetFileDescriptor afd = c.getAssets().openFd(name);
            final MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(m -> { m.release(); fullEnd.run(); });
            mp.setOnErrorListener((m, w, e) -> { m.release(); fullEnd.run(); return true; });
            mp.prepare();
            g[0] = new Guard(c.getApplicationContext(), () -> {
                try { mp.stop(); } catch (Throwable ignored) {}
                try { mp.release(); } catch (Throwable ignored) {}
                fullEnd.run();
            });
            mp.start();
            g[0].arm();
        } catch (Exception e) { fullEnd.run(); }
    }

    /** يشغّل assets/takbeer.mp3 إن وُجد، وإلا ينطق «الله أكبر» مرتين بمحرك النطق */
    public static void play(final Context c, final Runnable done) {
        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] fin = {false};
        final Runnable end = () -> { if (!fin[0]) { fin[0] = true; done.run(); } };
        final Guard[] g = {null};
        final Runnable fullEnd = () -> { if (g[0] != null) g[0].disarm(); end.run(); };
        h.postDelayed(end, 15000);
        final AudioAttributes at = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        try {
            AssetFileDescriptor afd = c.getAssets().openFd("takbeer.mp3");
            final MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(at);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(m -> { m.release(); fullEnd.run(); });
            mp.setOnErrorListener((m, w, e) -> { m.release(); fullEnd.run(); return true; });
            mp.prepare();
            g[0] = new Guard(c.getApplicationContext(), () -> {
                try { mp.stop(); } catch (Throwable ignored) {}
                try { mp.release(); } catch (Throwable ignored) {}
                fullEnd.run();
            });
            mp.start();
            g[0].arm();
            return;
        } catch (Exception ignored) {}
        final TextToSpeech[] t = new TextToSpeech[1];
        t[0] = new TextToSpeech(c, st -> {
            if (st != TextToSpeech.SUCCESS || t[0] == null) { fullEnd.run(); return; }
            if (t[0].setLanguage(new Locale("ar")) < TextToSpeech.LANG_AVAILABLE) { t[0].shutdown(); fullEnd.run(); return; }
            t[0].setAudioAttributes(at);
            t[0].setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { if ("b".equals(id)) { t[0].shutdown(); fullEnd.run(); } }
                @Override public void onError(String id) { t[0].shutdown(); fullEnd.run(); }
            });
            g[0] = new Guard(c.getApplicationContext(), () -> {
                try { t[0].stop(); } catch (Throwable ignored) {}
                try { t[0].shutdown(); } catch (Throwable ignored) {}
                fullEnd.run();
            });
            g[0].arm();
            t[0].speak("الله أكبر", TextToSpeech.QUEUE_FLUSH, null, "a");
            t[0].speak("الله أكبر", TextToSpeech.QUEUE_ADD, null, "b");
        });
    }
}
