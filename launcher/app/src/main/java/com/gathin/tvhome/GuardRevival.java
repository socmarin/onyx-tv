package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

/**
 * Starts this launcher's process after the firmware has killed it.
 *
 * A notification listener keeps its approval across a force stop, and the system binds it again
 * once the process is gone. That rebind is what brings Home back on TCL and Xiaomi. No notification
 * is read. Enable with {@code cmd notification allow_listener com.gathin.tvhome/.GuardRevival}.
 */
public class GuardRevival extends NotificationListenerService {
    private BroadcastReceiver unlocked;
    private ContentObserver settingsObserver;

    @Override
    public void onListenerConnected() {
        UserManager users = getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) {
            if (unlocked != null) return;
            unlocked = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    stopWaiting();
                    onUnlockedConnected();
                }
            };
            registerReceiver(unlocked, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            if (users.isUserUnlocked()) {
                stopWaiting();
                onUnlockedConnected();
            }
            return;
        }
        onUnlockedConnected();
    }

    private void onUnlockedConnected() {
        GuardKeep.ensureHomeKey(this);
        boolean watchPowerSetting = RemotePowerGuard.isEnabled(this);
        if (watchPowerSetting) RemotePowerGuard.enforceSettings(this);
        if (settingsObserver == null) {
            settingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    GuardKeep.ensureHomeKey(GuardRevival.this);
                    if (RemotePowerGuard.enforceSettings(GuardRevival.this)) {
                        RemotePowerGuard.scheduleSync(GuardRevival.this, 2500L, "dc-setting-reset");
                    }
                }
            };
            try {
                getContentResolver().registerContentObserver(
                        Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                        false, settingsObserver);
                getContentResolver().registerContentObserver(
                        Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
                        false, settingsObserver);
                if (watchPowerSetting) {
                    getContentResolver().registerContentObserver(
                            Settings.Global.getUriFor("show_dc_settings"),
                            false, settingsObserver);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void stopWaiting() {
        if (unlocked == null) return;
        try {
            unregisterReceiver(unlocked);
        } catch (RuntimeException ignored) {
        }
        unlocked = null;
    }

    @Override
    public void onDestroy() {
        stopWaiting();
        if (settingsObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(settingsObserver);
            } catch (RuntimeException ignored) {
            }
            settingsObserver = null;
        }
        super.onDestroy();
    }
}
