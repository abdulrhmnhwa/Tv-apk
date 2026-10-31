package sy.homs.dash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Intent a = new Intent(c, MainActivity.class);
        a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(a); } catch (Exception ignored) {}
    }
}
