package im.marin.onyxtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Restores the Home key accessibility helper and starts Onyx after a cold boot when running under
 * {@code stock_home_redirect} (such as Fire OS), where Onyx cannot be set as Android's default HOME activity.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "FireHomeRedirect";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        Log.i(TAG, "BootReceiver action=" + action);
        GuardKeep.ensureHomeKey(context);
        RemotePowerGuard.enforceSettings(context);
        if (Config.load(context).stockHomeRedirect.isEmpty()) return;
        if (HomeKeyService.isHomeShownThisBoot(context)) return;
        try {
            Intent home = new Intent(context, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(home);
            Log.i(TAG, "BootReceiver started MainActivity");
        } catch (RuntimeException e) {
            Log.w(TAG, "BootReceiver failed to start MainActivity", e);
        }
    }
}
