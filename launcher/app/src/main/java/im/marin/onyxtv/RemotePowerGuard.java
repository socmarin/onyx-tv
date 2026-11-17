package im.marin.onyxtv;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Optionally keeps a Fire TV BLE remote's Power button mapped exclusively to {@code BT_KEYPRESS}
 * across reboots, Bluetooth reconnects and Fire OS Device Control resets when {@code fire_remote_bt_power_mac}
 * is configured. Combining IR + BT on Power causes some remotes to stop emitting {@code BT_KEYPRESS}.
 */
final class RemotePowerGuard {
    private static final String TAG = "FireHomeRedirect";
    private static final String SHOW_DC_SETTINGS = "show_dc_settings";
    private static final String KEYMAP_ACTION = "com.amazon.device.bluetoothkeymap.action.KEYMAP_SYNC";
    private static final ComponentName KEYMAP_SERVICE = new ComponentName(
            "com.amazon.device.bluetoothkeymaplib",
            "com.amazon.device.bluetoothkeymaplib.service.KeyMapService");
    private static final String POWER_ACTIONS_JSON =
            "[{\"CommandType\":\"BT\",\"BTCommand\":\"BT_KEYPRESS\"}]";
    private static final String FULL_SYNC_JSON =
            "{\"TableUpdate\":{"
                    + "\"FireTV\":{\"ID\":\"a9c013fe-6d4b-46bf-8c09-e17f5ced8896\","
                    + "\"Power\":" + POWER_ACTIONS_JSON + "},"
                    + "\"TV\":{\"ID\":\"a9c013fe-6d4b-46bf-8c09-e17f5ced8897\","
                    + "\"Power\":" + POWER_ACTIONS_JSON + "}}}";
    private static final String STATE_SWITCH_JSON =
            "{\"StateChanged\":{\"NewStateID\":\"a9c013fe-6d4b-46bf-8c09-e17f5ced8896\",\"ToggleBitRegister\":0}}";
    private static final long MIN_SYNC_INTERVAL_MS = 5000L;
    private static final long STATE_SWITCH_DELAY_MS = 8000L;
    private static final long BOOT_FALLBACK_DELAY_MS = 90000L;
    private static final String PREFS_BOOT = "boot";
    private static final String KEY_POWER_SYNC_BOOT = "power_sync_boot";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingSync;
    private static Runnable pendingStateSwitch;
    private static long lastSyncAt;
    private static Boolean supported;

    private RemotePowerGuard() {
    }

    static boolean isSupported(Context context) {
        if (Boolean.TRUE.equals(supported)) return true;
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(KEYMAP_ACTION).setComponent(KEYMAP_SERVICE);
        if (pm.resolveService(query, 0) != null) {
            supported = Boolean.TRUE;
            return true;
        }
        return false;
    }

    static boolean isEnabled(Context context) {
        return !configuredMac(context).isEmpty() && isSupported(context);
    }

    static String configuredMac(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return "";
        return Config.load(context).fireRemoteBtPowerMac;
    }

    /**
     * Resets {@code show_dc_settings} to 0 so Fire OS Device Control skips its IR-only FULL_SYNC
     * overwrite. Returns true if the setting had reverted to non-zero.
     */
    static boolean enforceSettings(Context context) {
        if (!isEnabled(context)) return false;
        ContentResolver cr = context.getContentResolver();
        try {
            int current = Settings.Global.getInt(cr, SHOW_DC_SETTINGS, 0);
            if (current == 0) return false;
            boolean ok = Settings.Global.putInt(cr, SHOW_DC_SETTINGS, 0);
            Log.i(TAG, "Reset show_dc_settings=0 (was " + current + ", ok=" + ok + ")");
            return ok;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to reset show_dc_settings", e);
            return false;
        }
    }

    /** Schedules a debounced BT_KEYPRESS sync on the main looper. */
    static void scheduleSync(Context context, long delayMs, String reason) {
        if (!isEnabled(context)) return;
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (pendingSync != null) MAIN.removeCallbacks(pendingSync);
            if (pendingStateSwitch != null) {
                MAIN.removeCallbacks(pendingStateSwitch);
                pendingStateSwitch = null;
            }
            pendingSync = () -> {
                pendingSync = null;
                syncRemotePower(app, reason);
            };
            MAIN.postDelayed(pendingSync, Math.max(0L, delayMs));
        });
    }

    /** Schedules one boot fallback sync if this boot has not synced yet. */
    static void scheduleBootFallback(Context context) {
        if (!isEnabled(context) || isSyncedThisBoot(context)) return;
        Context app = context.getApplicationContext();
        scheduleSync(app, BOOT_FALLBACK_DELAY_MS, "boot-fallback");
    }

    static void syncRemotePower(Context context, String reason) {
        String remoteAddress = configuredMac(context);
        if (remoteAddress.isEmpty() || !isSupported(context)) return;
        enforceSettings(context);
        long now = SystemClock.elapsedRealtime();
        if (lastSyncAt != 0 && now - lastSyncAt < MIN_SYNC_INTERVAL_MS) return;
        lastSyncAt = now;
        Context app = context.getApplicationContext();
        try {
            Intent fullSync = new Intent(KEYMAP_ACTION)
                    .setComponent(KEYMAP_SERVICE)
                    .putExtra("address", remoteAddress)
                    .putExtra("action", "full_sync")
                    .putExtra("json", FULL_SYNC_JSON);
            app.startService(fullSync);
            if (pendingStateSwitch != null) MAIN.removeCallbacks(pendingStateSwitch);
            pendingStateSwitch = () -> {
                pendingStateSwitch = null;
                try {
                    Intent stateSwitch = new Intent(KEYMAP_ACTION)
                            .setComponent(KEYMAP_SERVICE)
                            .putExtra("address", remoteAddress)
                            .putExtra("action", "state_switch")
                            .putExtra("json", STATE_SWITCH_JSON);
                    app.startService(stateSwitch);
                } catch (RuntimeException ignored) {
                }
            };
            MAIN.postDelayed(pendingStateSwitch, STATE_SWITCH_DELAY_MS);
            markSyncedThisBoot(app);
            Log.i(TAG, "Synced BT_KEYPRESS Power keymap (" + reason + ")");
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to sync BT_KEYPRESS Power keymap (" + reason + ")", e);
        }
    }

    private static boolean isSyncedThisBoot(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return false;
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot < 0) return false;
        return context.getSharedPreferences(PREFS_BOOT, Context.MODE_PRIVATE)
                .getInt(KEY_POWER_SYNC_BOOT, -1) == boot;
    }

    private static void markSyncedThisBoot(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return;
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot < 0) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_BOOT, Context.MODE_PRIVATE);
        if (prefs.getInt(KEY_POWER_SYNC_BOOT, -1) != boot) {
            prefs.edit().putInt(KEY_POWER_SYNC_BOOT, boot).apply();
        }
    }
}
