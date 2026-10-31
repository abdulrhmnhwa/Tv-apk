package sy.homs.dash;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

public class Takbeer {
    /** يشغّل assets/takbeer.mp3 إن وُجد، وإلا ينطق «الله أكبر» مرتين بمحرك النطق */
    public static void play(final Context c, final Runnable done) {
        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] fin = {false};
        final Runnable end = () -> { if (!fin[0]) { fin[0] = true; done.run(); } };
        h.postDelayed(end, 15000);
        try {
            AssetFileDescriptor afd = c.getAssets().openFd("takbeer.mp3");
            final MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setOnCompletionListener(m -> { m.release(); end.run(); });
            mp.setOnErrorListener((m, w, e) -> { m.release(); end.run(); return true; });
            mp.prepare();
            mp.start();
            return;
        } catch (Exception ignored) {}
        final TextToSpeech[] t = new TextToSpeech[1];
        t[0] = new TextToSpeech(c, st -> {
            if (st != TextToSpeech.SUCCESS || t[0] == null) { end.run(); return; }
            if (t[0].setLanguage(new Locale("ar")) < TextToSpeech.LANG_AVAILABLE) { t[0].shutdown(); end.run(); return; }
            t[0].setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { if ("b".equals(id)) { t[0].shutdown(); end.run(); } }
                @Override public void onError(String id) { t[0].shutdown(); end.run(); }
            });
            t[0].speak("الله أكبر", TextToSpeech.QUEUE_FLUSH, null, "a");
            t[0].speak("الله أكبر", TextToSpeech.QUEUE_ADD, null, "b");
        });
    }
}
