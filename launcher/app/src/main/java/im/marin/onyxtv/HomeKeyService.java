package im.marin.onyxtv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * Also maps a dedicated Apps button (KEYCODE_APP_4, and Fire OS's Apps grid window) to Onyx's App
 * library when {@code app_library} is enabled, and supports configurable {@code window_redirects}.
 *
 * With stock_home_redirect set, it also sends the stock launcher's home screen back here—both on window
 * changes and when waking from standby ({@code ACTION_SCREEN_ON})—for firmware that turns that launcher
 * on again or cannot disable it at all (such as Fire OS 7).
 *
 * With power_on_home set, a Live TV window (live_tv_package) that the firmware opens by itself at power-on is sent
 * back here too, until the first key press after boot or after the screen turns on (Changhong / Homwee boards).
 * With power_on_home_always set, Home comes up at every power-on whatever is in front, for TVs that resume their
 * last source: a Philips back on an HDMI input sends no window event until the input has a picture.
 *
 * With home_long_press_home set, it takes every Home press, so one held past the long-press timeout opens this launcher
 * instead of Android's ALL_APPS action, which nothing answers once the stock launcher is disabled (Philips TPM191E).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services im.marin.onyxtv/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private boolean consumeVolumeKeys;
    /** app_library: routes the dedicated Apps button (KEYCODE_APP_4 / Fire OS Apps grid) to Onyx's App library. */
    private boolean appLibrary;
    /** app_keys, the same maps InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();

    /** Waits for the user to unlock when Android binds this service at boot; null once the config is read. */
    private BroadcastReceiver unlockReceiver;
    /** The stock launcher's HOME activities (stock_home_redirect), disabled ones included. */
    private final Set<String> stockHome = new HashSet<>();
    /** stock_home_redirect, the stock launcher's package. */
    private String stockHomePackage = "";
    /** window_redirects: configurable OS-intercepted window -> target redirects. */
    private WindowRedirects windowRedirects = WindowRedirects.EMPTY;
    /** power_on_home: the Live TV package to send back to Home, empty when off; armed until this uptime or a key. */
    private String powerOnLiveTv = "";
    private long powerOnUntil;
    /** home_long_press_home: this service takes every Home press, held ones included. */
    private boolean homeLongPressHome;
    /** app_keys "front:" entries, and the package of the activity in front, from window changes while there are any. */
    private Map<Integer, String> frontAppKeys = Collections.emptyMap();
    private Map<Integer, String> frontAppScancodes = Collections.emptyMap();
    private String frontPackage = "";
    /** power_on_home_always: Home at every power-on, not only over Live TV. */
    private boolean powerOnAlways;
    private long loadedSettingsRevision = -1L;
    /** power_on_home / stock_home_redirect: handles ACTION_SCREEN_ON; null unless at least one is active. */
    private BroadcastReceiver screenOnReceiver;
    /** Stops key filtering while a game controller is connected; null unless gamepad_passthrough and key_filter, or before the unlock. */
    private GamepadPassthrough gamepads;
    /** Local opt-in adapter, created only when redirecting Amazon's protected Home. */
    private FireHomeRedirect fireHome;
    /** Re-applies BT_KEYPRESS Power keymap when the Fire TV remote HID profile connects. */
    private BroadcastReceiver remoteHidReceiver;
    /** Keeps BluetoothAdapter's advertised name synced with Settings.Secure.bluetooth_name across Fire OS resets. */
    private BroadcastReceiver btNameReceiver;
    private long lastBtNameSyncAt = -1L;
    /** Screen-off receiver while guard_off_in_standby is set; null otherwise. */
    private BroadcastReceiver standbyExit;
    /** disableSelf is how this service leaves for standby, not the user turning it off. */
    private boolean steppingOut;

    static final String VENEZIA_PKG = "com.amazon.venezia";
    static final String VENEZIA_APPS_GRID = "com.amazon.venezia.grid.AppsGridLauncherActivity";
    private static final String VENEZIA_APP_LAUNCH = "com.amazon.venezia.pdi.AppLaunchActivity";
    private static final String VENEZIA_APP_DETAILS = "com.amazon.venezia.details.AppDetailsActivity";
    private static final String VENEZIA_UPDATE_DIALOG = "com.amazon.venezia.appupdates.ManualUpdateDialogActivity";
    private static final long BOOT_FALLBACK_MS = 2500L;
    private static final long REMOTE_REDIRECT_COOLDOWN_MS = 1200L;
    private static final long EXPLICIT_LAUNCH_GRACE_MS = 4000L;
    static final long BT_NAME_SYNC_COOLDOWN_MS = 5000L;
    private static final long[] DETAILS_RETRY_DELAYS_MS = { 250L, 650L, 1200L, 2000L };
    private static final long[] SCREEN_ON_CHECK_DELAYS_MS = { 0L, 150L, 250L, 400L, 600L, 1000L };

    /** The bound helper, so the opt-in broadcast can arm it without waiting for a new connection. */
    private static HomeKeyService running;
    private static volatile String explicitLaunchPkg = "";
    private static volatile long explicitLaunchUntil = 0L;
    private Runnable bootFallback;
    private boolean userLeftHomeAtBoot;
    private Runnable detailsCheck;
    private Runnable captureDetailsCheck;
    private boolean watchingAppDetails;
    private final Map<String, Long> lastWindowRedirects = new HashMap<>();
    private Runnable screenOnStockHomeCheck;
    private String lastActivityPackage = "";
    private String lastActivityClass = "";

    enum WakeAction {
        IGNORE,
        RETRY,
        PROBE,
        REDIRECT
    }

    static boolean isRunning() {
        return running != null;
    }

    /** Marks a user-initiated app launch from Onyx so window redirects don't hijack intentional opens. */
    static void noteExplicitAppLaunch(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        explicitLaunchPkg = pkg;
        explicitLaunchUntil = SystemClock.uptimeMillis() + EXPLICIT_LAUNCH_GRACE_MS;
    }

    static void setWindowCapture(RemoteKeyCustom.WindowCaptureHandler captureHandler) {
        RemoteKeyCustom.activeWindowCapture = captureHandler;
        HomeKeyService service = running;
        if (service == null) return;
        if (captureHandler == null) service.clearCaptureDetailsCheck();
        else service.ensureWindowEventsSubscribed();
    }

    private void ensureWindowEventsSubscribed() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        if ((info.eventTypes & AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) == 0) {
            info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            setServiceInfo(info);
        }
    }

    static void markHomeShownThisBoot(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return;
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot < 0) return;
        SharedPreferences prefs = context.getSharedPreferences("boot", MODE_PRIVATE);
        if (prefs.getInt("home_boot", -1) != boot) {
            prefs.edit().putInt("home_boot", boot).apply();
        }
    }

    static boolean isHomeShownThisBoot(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return false;
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot < 0) return false;
        return context.getSharedPreferences("boot", MODE_PRIVATE).getInt("home_boot", -1) == boot;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // The service is directBootAware: Android 11 skips a service that is not while the user is still
        // locked at boot, writes accessibility_enabled=0 and, on TCL G03 firmware, never binds it after the
        // unlock. Config.load reads credential-protected storage, so wait for the unlock before reading it.
        UserManager users = getSystemService(UserManager.class);
        if (users.isUserUnlocked()) {
            loadConfig();
            return;
        }
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopWaitingForUnlock();
                loadConfig();
            }
        };
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        // The unlock may have happened between the check and the registration.
        if (users.isUserUnlocked()) {
            stopWaitingForUnlock();
            loadConfig();
        }
    }

    private void stopWaitingForUnlock() {
        if (unlockReceiver == null) return;
        unregisterReceiver(unlockReceiver);
        unlockReceiver = null;
    }

    /** Reloads config and key/window/power-on listeners when a running service exists. */
    static void reloadIfRunning() {
        HomeKeyService service = running;
        if (service != null) service.loadConfig();
    }

    /** The opt-in broadcast: arm this binding now, or drop the receiver if the opt-in was turned off. */
    static void applyStandbyOptIn() {
        HomeKeyService service = running;
        if (service == null) return;
        if (GuardKeep.offInStandby(service)) service.armStandbyExit();
        else service.disarmStandbyExit();
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        RemoteKeys.release(this);
        stopWaitingForUnlock();
        clearBootFallback();
        clearDetailsCheck();
        clearCaptureDetailsCheck();
        clearScreenOnStockHomeCheck();
        if (fireHome != null) {
            fireHome.destroy();
            fireHome = null;
        }
        disarmRemotePowerGuard();
        disarmBluetoothNameSync();
        if (screenOnReceiver != null) {
            try {
                unregisterReceiver(screenOnReceiver);
            } catch (RuntimeException ignored) {
            }
            screenOnReceiver = null;
        }
        if (standbyExit != null) {
            try {
                unregisterReceiver(standbyExit);
            } catch (RuntimeException ignored) {
            }
            standbyExit = null;
        }
        if (gamepads != null) gamepads.stop();
        if (consumeVolumeKeys) VolumeKeys.stop();
        super.onDestroy();
    }

    /** Some TCL boards never wake from the remote while this helper stays bound with the screen off. */
    private void armStandbyExit() {
        if (standbyExit != null || !GuardKeep.offInStandby(this)) return;
        standbyExit = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) return;
                leaveForStandby();
            }
        };
        registerReceiver(standbyExit, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        PowerManager power = getSystemService(PowerManager.class);
        if (power != null && !power.isInteractive()) leaveForStandby();
    }

    private void disarmStandbyExit() {
        if (standbyExit == null) return;
        try {
            unregisterReceiver(standbyExit);
        } catch (RuntimeException ignored) {
        }
        standbyExit = null;
    }

    private void leaveForStandby() {
        steppingOut = true;
        GuardKeep.stepOut(this);
    }

    private void armRemotePowerGuard() {
        if (!RemotePowerGuard.isEnabled(this)) {
            disarmRemotePowerGuard();
            return;
        }
        RemotePowerGuard.enforceSettings(this);
        RemotePowerGuard.scheduleBootFallback(this);
        if (remoteHidReceiver != null) return;
        remoteHidReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!"android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED".equals(intent.getAction())) {
                    return;
                }
                int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                if (state == 2) {
                    RemotePowerGuard.enforceSettings(context);
                    syncBluetoothName();
                    RemotePowerGuard.scheduleSync(context, 3000L, "hid-connected");
                }
            }
        };
        registerReceiver(remoteHidReceiver,
                new IntentFilter("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED"));
    }

    static boolean shouldSyncBluetoothName(String target, String current, long nowMs, long lastAttemptMs) {
        if (target == null || target.isEmpty() || target.equals(current)) return false;
        return lastAttemptMs < 0L || nowMs - lastAttemptMs >= BT_NAME_SYNC_COOLDOWN_MS;
    }

    private boolean canSyncBluetoothName() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
                && checkSelfPermission("android.permission.BLUETOOTH") == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission("android.permission.BLUETOOTH_ADMIN") == PackageManager.PERMISSION_GRANTED;
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void syncBluetoothName() {
        if (!canSyncBluetoothName()) return;
        try {
            String target = Settings.Secure.getString(getContentResolver(), "bluetooth_name");
            if (target == null || target.trim().isEmpty()) {
                target = Settings.Global.getString(getContentResolver(), "device_name");
            }
            if (target != null) target = target.trim();
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return;
            long now = SystemClock.elapsedRealtime();
            if (shouldSyncBluetoothName(target, adapter.getName(), now, lastBtNameSyncAt)) {
                lastBtNameSyncAt = now;
                adapter.setName(target);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void armBluetoothNameSync() {
        if (!canSyncBluetoothName()) {
            disarmBluetoothNameSync();
            return;
        }
        syncBluetoothName();
        if (btNameReceiver != null) return;
        btNameReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED.equals(action)) {
                    syncBluetoothName();
                } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                        && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                                == BluetoothAdapter.STATE_ON) {
                    syncBluetoothName();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(btNameReceiver, filter);
    }

    private void disarmBluetoothNameSync() {
        if (btNameReceiver == null) return;
        try {
            unregisterReceiver(btNameReceiver);
        } catch (RuntimeException ignored) {
        }
        btNameReceiver = null;
    }

    private void disarmRemotePowerGuard() {
        if (remoteHidReceiver == null) return;
        try {
            unregisterReceiver(remoteHidReceiver);
        } catch (RuntimeException ignored) {
        }
        remoteHidReceiver = null;
    }

    private void loadConfig() {
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        loadedSettingsRevision = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getLong(Config.SETTINGS_REVISION_KEY, 0L);
        Config config = Config.load(this);
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop. Here, not
        // in onServiceConnected: the note is in credential-protected storage too, readable only after the unlock.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        consumeVolumeKeys = config.consumeVolumeKeys;
        appLibrary = config.appLibrary;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        homeLongPressHome = config.homeLongPressHome;
        frontAppKeys = RemoteKeys.parseFrontAppKeys(config.appKeys);
        frontAppScancodes = RemoteKeys.parseFrontAppScancodes(config.appKeys);
        boolean watchFront = !frontAppKeys.isEmpty() || !frontAppScancodes.isEmpty();
        windowRedirects = WindowRedirects.parse(config.windowRedirects);
        stockHome.clear();
        stockHomePackage = config.stockHomeRedirect;
        if (!config.stockHomeRedirect.isEmpty()) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(config.stockHomeRedirect);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                stockHome.add(ri.activityInfo.name);
            }
        }
        if ("com.amazon.tv.launcher".equals(config.stockHomeRedirect)) {
            stockHome.add("com.amazon.tv.launcher.ui.HomeActivity_vNext");
            if (fireHome == null) {
                fireHome = new FireHomeRedirect(this, config.backgroundColor);
            }
            armRemotePowerGuard();
        } else {
            if (fireHome != null) {
                fireHome.destroy();
                fireHome = null;
            }
            if (RemotePowerGuard.isEnabled(this)) {
                armRemotePowerGuard();
            } else {
                disarmRemotePowerGuard();
            }
        }
        armBluetoothNameSync();
        // power_on_home, off by default. Not with boot_to_live_tv, or with Live TV picked as the Open on start app:
        // those open Live TV on purpose at boot.
        String openOnStart = config.openOnStartChoice
                ? getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getString(OpenOnStart.KEY, "") : "";
        powerOnLiveTv = config.powerOnHome && !config.bootToLiveTv && !config.liveTvPackage.equals(openOnStart)
                ? config.liveTvPackage : "";
        powerOnAlways = config.powerOnHomeAlways && !config.bootToLiveTv && openOnStart.isEmpty();
        clearScreenOnStockHomeCheck();
        boolean armPowerOn = !powerOnLiveTv.isEmpty() || powerOnAlways;
        if (armPowerOn || !stockHome.isEmpty()) {
            armScreenOn(armPowerOn);
        } else if (screenOnReceiver != null) {
            try {
                unregisterReceiver(screenOnReceiver);
            } catch (RuntimeException ignored) {
            }
            screenOnReceiver = null;
            powerOnUntil = 0;
        }
        // home_key_service.xml subscribes to no events, so window changes are requested only when a redirect is active.
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null && (!stockHome.isEmpty() || !powerOnLiveTv.isEmpty() || watchFront
                || !windowRedirects.isEmpty() || RemoteKeyCustom.activeWindowCapture != null)) {
            info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            setServiceInfo(info);
        }
        if (!stockHome.isEmpty() || !powerOnLiveTv.isEmpty()) probeAtBoot();
        armStandbyExit();
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
        // gamepad_passthrough, off by default. Only with key_filter on: off, the service never filters keys.
        if (config.gamepadPassthrough && config.keyFilter && gamepads == null) {
            gamepads = new GamepadPassthrough(this);
            gamepads.start();
        }
    }

    /** A tap's target: the "front:" entry while the button's own app is already in front, else the usual one. */
    private String inFront(Map<Integer, String> keys, Map<Integer, String> front, int code) {
        String target = keys.get(code);
        String alt = front.get(code);
        return alt != null && RemoteKeys.inFront(target, frontPackage) ? alt : target;
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        RemoteKeyCustom.KeyCaptureHandler capture = RemoteKeyCustom.activeCapture;
        if (capture != null && capture.onKeyEvent(event)) return true;
        if (unlockReceiver == null) {
            long rev = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                    .getLong(Config.SETTINGS_REVISION_KEY, 0L);
            if (rev != loadedSettingsRevision) loadConfig();
        }
        // power_on_home: the first key after power-on hands the TV to the user (power and wake keys don't count).
        if (powerOnUntil != 0 && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() != KeyEvent.KEYCODE_POWER && event.getKeyCode() != KeyEvent.KEYCODE_WAKEUP) {
            powerOnUntil = 0;
        }
        if (consumeVolumeKeys && VolumeKeys.handle(this, event)) return true;
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, inFront(appScancodes, frontAppScancodes, event.getScanCode()),
                holdAppScancodes.get(event.getScanCode()))) {
            clearScreenOnStockHomeCheck();
            return true;
        }
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                clearScreenOnStockHomeCheck();
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : SystemSettings.intent(this, Settings.ACTION_SETTINGS);
                try {
                    startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException unavailable) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        if (RemoteKeys.handle(this, event, inFront(appKeys, frontAppKeys, event.getKeyCode()),
                holdAppKeys.get(event.getKeyCode()))) {
            clearScreenOnStockHomeCheck();
            return true;
        }
        // home_long_press_home: Android never sees the Home key, so a held press cannot turn into ALL_APPS.
        if (homeLongPressHome && event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openHome();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && !stockHome.isEmpty()) {
            redirectHomeKey(event);
            return false;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openHome();
        return true;
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        RemoteKeys.release(this);
        if (repairRecord && !steppingOut) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (unlockReceiver == null) {
            long rev = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                    .getLong(Config.SETTINGS_REVISION_KEY, 0L);
            if (rev != loadedSettingsRevision) loadConfig();
        }
        CharSequence cls = event.getClassName();
        CharSequence pkg = event.getPackageName();
        String pkgStr = pkg == null ? "" : pkg.toString();
        String clsStr = cls == null ? "" : cls.toString();
        RemoteKeyCustom.WindowCaptureHandler windowCapture = RemoteKeyCustom.activeWindowCapture;
        if (windowCapture != null) {
            if (pkgStr.isEmpty() || getPackageName().equals(pkgStr)
                    || "android".equals(pkgStr) || "com.android.systemui".equals(pkgStr)
                    || VENEZIA_APP_LAUNCH.equals(clsStr)) {
                return;
            }
            if (VENEZIA_PKG.equals(pkgStr) && (VENEZIA_APP_DETAILS.equals(clsStr) || VENEZIA_UPDATE_DIALOG.equals(clsStr))) {
                captureVeneziaDetails(event, pkgStr, clsStr, windowCapture, 0);
                return;
            }
            if (isActivityWindow(pkgStr, clsStr)) {
                windowCapture.onWindowOpened(pkgStr, clsStr, null);
            }
            return;
        }
        boolean activityWindow = !ProbeActivity.class.getName().equals(clsStr) && isActivityWindow(pkgStr, clsStr);
        if (activityWindow) {
            lastActivityPackage = pkgStr;
            lastActivityClass = clsStr;
            // app_keys "front:" entries: only activities count, so a toast or the volume bar doesn't change the app in front.
            if (!frontAppKeys.isEmpty() || !frontAppScancodes.isEmpty()) {
                frontPackage = pkgStr;
            }
        }
        // power_on_home: the firmware opened Live TV by itself at power-on, before anyone pressed a key.
        if (powerOnRedirect(powerOnLiveTv, pkg, SystemClock.elapsedRealtime(), powerOnUntil)) {
            openHome();
            return;
        }
        boolean skipExplicit = !pkgStr.isEmpty() && pkgStr.equals(explicitLaunchPkg)
                && SystemClock.uptimeMillis() < explicitLaunchUntil;
        String configuredRedirect = skipExplicit ? null : windowRedirects.matchWindow(pkgStr, clsStr);
        if (configuredRedirect != null) {
            launchWindowRedirect(configuredRedirect);
            return;
        }
        if (appLibrary && VENEZIA_PKG.equals(pkgStr) && VENEZIA_APPS_GRID.equals(clsStr)) {
            launchWindowRedirect("library");
            return;
        }
        if (fireHome != null) {
            android.util.Log.i("FireHomeRedirect", "window event pkg=" + pkgStr + " cls=" + clsStr + " eventTime=" + event.getEventTime());
            if (getPackageName().equals(pkgStr)) {
                clearBootFallback();
                clearDetailsCheck();
                if (!ProbeActivity.class.getName().equals(clsStr)) {
                    clearScreenOnStockHomeCheck();
                }
            } else if (VENEZIA_PKG.equals(pkgStr)) {
                if (windowRedirects.hasDetailsRules()
                        && (VENEZIA_APP_DETAILS.equals(clsStr) || VENEZIA_UPDATE_DIALOG.equals(clsStr))) {
                    clearScreenOnStockHomeCheck();
                    fireHome.cancel();
                    clearBootFallback();
                    scheduleDetailsCheck(event);
                    return;
                }
                if (activityWindow) {
                    clearScreenOnStockHomeCheck();
                    fireHome.cancel();
                    clearDetailsCheck();
                }
            } else if ("com.amazon.tv.launcher".equals(pkgStr)) {
                clearScreenOnStockHomeCheck();
                clearDetailsCheck();
                if (clsStr.contains("Settings")) {
                    fireHome.cancel();
                } else if (isStockHomeClass(stockHome, clsStr)) {
                    if (isScreenInteractive()) {
                        openHome();
                    }
                }
            } else if (!pkgStr.isEmpty()
                    && !"android".equals(pkgStr) && !"com.android.systemui".equals(pkgStr)
                    && activityWindow) {
                clearScreenOnStockHomeCheck();
                fireHome.cancel();
                clearDetailsCheck();
                userLeftHomeAtBoot = true;
                clearBootFallback();
            }
            return;
        }
        if (!stockHome.isEmpty() && stockHome.contains(clsStr)) {
            clearScreenOnStockHomeCheck();
            if (isScreenInteractive()) openHome();
        } else if (activityWindow && !pkgStr.isEmpty()
                && !"android".equals(pkgStr) && !"com.android.systemui".equals(pkgStr)) {
            clearScreenOnStockHomeCheck();
        }
    }

    /** How long power_on_home stays armed after the screen turns on without a reboot. */
    static final long SCREEN_ON_WINDOW_MS = 60 * 1000L;

    /** power_on_home / stock_home_redirect: handles boot window (when power_on_home is on) and every screen-on. */
    private void armScreenOn(boolean armPowerOn) {
        if (armPowerOn) {
            if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) {
                powerOnUntil = BOOT_WINDOW_MS;
                if (powerOnAlways) handler.postDelayed(this::homeAtPowerOn, POWER_ON_ALWAYS_DELAY_MS);
            }
        } else {
            powerOnUntil = 0;
        }
        if (screenOnReceiver != null) return;
        screenOnReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Intent.ACTION_SCREEN_ON.equals(intent.getAction())) return;
                if (!powerOnLiveTv.isEmpty() || powerOnAlways) {
                    powerOnUntil = SystemClock.elapsedRealtime() + SCREEN_ON_WINDOW_MS;
                    if (powerOnAlways) handler.postDelayed(HomeKeyService.this::homeAtPowerOn, POWER_ON_ALWAYS_DELAY_MS);
                }
                if (!stockHome.isEmpty()) {
                    scheduleScreenOnStockHomeCheck();
                }
                syncBluetoothName();
            }
        };
        registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    /** power_on_home_always: after the firmware has brought its last source back (Philips: within 0.5 s). */
    static final long POWER_ON_ALWAYS_DELAY_MS = 800L;

    /** power_on_home_always: Home, unless a key was pressed since the screen came on (the user has the TV). */
    private void homeAtPowerOn() {
        if (stillArmed(SystemClock.elapsedRealtime(), powerOnUntil)) openHome();
    }

    static boolean stillArmed(long now, long armedUntil) {
        return now < armedUntil;
    }

    /** The settings menu offers Restart only while this helper is bound: it opens the system power menu. */
    static boolean canOpenPowerMenu() {
        return running != null;
    }

    /** The system power menu (Restart, and Power off where the firmware has it); the TV asks before it acts. */
    static void openPowerMenu() {
        HomeKeyService service = running;
        if (service != null) service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    static boolean powerOnRedirect(String liveTv, CharSequence pkg, long now, long armedUntil) {
        return !liveTv.isEmpty() && pkg != null && liveTv.contentEquals(pkg) && now < armedUntil;
    }

    /** How long after boot the first bind still counts as "at boot" for probeAtBoot. */
    static final long BOOT_WINDOW_MS = 3 * 60 * 1000L;

    /** stock_home_redirect at boot: the stock home is often on screen before Android binds this service, so no
     *  window event for it arrives (#1116). Once per boot, open ProbeActivity: when it closes, whatever was in
     *  front comes back and sends a window event, and onAccessibilityEvent covers it only if it is the stock home.
     *  An HDMI input or an app that the TV resumed at boot is left alone. */
    private void probeAtBoot() {
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        SharedPreferences prefs = getSharedPreferences("boot", MODE_PRIVATE);
        if (!firstBindAfterBoot(boot, prefs.getInt("probe_boot", -1), SystemClock.elapsedRealtime())) return;
        prefs.edit().putInt("probe_boot", boot).apply();
        launchProbeActivity();
        if (fireHome != null && !isHomeShownThisBoot(this)) {
            clearBootFallback();
            bootFallback = () -> {
                bootFallback = null;
                if (!isHomeShownThisBoot(this) && !userLeftHomeAtBoot) {
                    openHome();
                }
            };
            handler.postDelayed(bootFallback, BOOT_FALLBACK_MS);
        }
    }

    private void launchProbeActivity() {
        try {
            startActivity(new Intent(this, ProbeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    private void scheduleScreenOnStockHomeCheck() {
        clearScreenOnStockHomeCheck();
        scheduleScreenOnStockHomeStep(0);
    }

    private void scheduleScreenOnStockHomeStep(final int index) {
        if (stockHome.isEmpty() || index >= SCREEN_ON_CHECK_DELAYS_MS.length) return;
        screenOnStockHomeCheck = () -> {
            screenOnStockHomeCheck = null;
            if (stockHome.isEmpty() || !isScreenInteractive()) return;
            String activePkg = getActiveWindowPackage();
            WakeAction action = classifyWakeWindow(
                    stockHomePackage,
                    getPackageName(),
                    MainActivity.isWindowFocused(),
                    activePkg,
                    lastActivityPackage,
                    lastActivityClass,
                    stockHome);
            if (action == WakeAction.RETRY) {
                if (index + 1 < SCREEN_ON_CHECK_DELAYS_MS.length) {
                    scheduleScreenOnStockHomeStep(index + 1);
                } else if (fireHome != null) {
                    android.util.Log.i("FireHomeRedirect",
                            "screenOn wake check exhausted activePkg=" + activePkg
                                    + " lastPkg=" + lastActivityPackage + " lastCls=" + lastActivityClass);
                }
                return;
            }
            if (fireHome != null) {
                android.util.Log.i("FireHomeRedirect",
                        "screenOn wake check attempt=" + index + " activePkg=" + activePkg
                                + " lastPkg=" + lastActivityPackage + " lastCls=" + lastActivityClass
                                + " action=" + action);
            }
            if (action == WakeAction.REDIRECT) {
                openHome();
            } else if (action == WakeAction.PROBE) {
                launchProbeActivity();
            }
        };
        long delay = SCREEN_ON_CHECK_DELAYS_MS[index];
        if (delay == 0L) {
            handler.post(screenOnStockHomeCheck);
        } else {
            handler.postDelayed(screenOnStockHomeCheck, delay);
        }
    }

    private void clearScreenOnStockHomeCheck() {
        if (screenOnStockHomeCheck == null) return;
        handler.removeCallbacks(screenOnStockHomeCheck);
        screenOnStockHomeCheck = null;
    }

    private boolean isScreenInteractive() {
        PowerManager power = getSystemService(PowerManager.class);
        return power == null || power.isInteractive();
    }

    private String getActiveWindowPackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        try {
            CharSequence pkg = root.getPackageName();
            return pkg == null ? "" : pkg.toString();
        } finally {
            root.recycle();
        }
    }

    /** Decides how to handle the foreground window on ACTION_SCREEN_ON without disturbing normal apps or HDMI. */
    static WakeAction classifyWakeWindow(
            String stockHomePackage,
            String myPackage,
            boolean homeWindowFocused,
            String activePackage,
            String lastActivityPackage,
            String lastActivityClass,
            Set<String> stockHomeClasses) {
        if (stockHomePackage == null || stockHomePackage.isEmpty() || homeWindowFocused) {
            return WakeAction.IGNORE;
        }
        if (activePackage == null || activePackage.isEmpty()
                || "android".equals(activePackage) || "com.android.systemui".equals(activePackage)) {
            return WakeAction.RETRY;
        }
        if (myPackage != null && myPackage.equals(activePackage)) {
            return WakeAction.RETRY;
        }
        if (!stockHomePackage.equals(activePackage)) {
            return WakeAction.IGNORE;
        }
        if (stockHomePackage.equals(lastActivityPackage)) {
            return isStockHomeClass(stockHomeClasses, lastActivityClass)
                    ? WakeAction.REDIRECT : WakeAction.PROBE;
        }
        if (lastActivityPackage == null || lastActivityPackage.isEmpty()) {
            return WakeAction.PROBE;
        }
        return WakeAction.REDIRECT;
    }

    static boolean isStockHomeClass(Set<String> stockHomeClasses, String className) {
        if (className == null || className.isEmpty()) return false;
        if (stockHomeClasses != null && stockHomeClasses.contains(className)) return true;
        return className.contains("HomeActivity");
    }

    private void clearBootFallback() {
        if (bootFallback == null) return;
        handler.removeCallbacks(bootFallback);
        bootFallback = null;
    }

    private void launchWindowRedirect(String target) {
        clearBootFallback();
        clearDetailsCheck();
        clearCaptureDetailsCheck();
        clearScreenOnStockHomeCheck();
        if (fireHome != null) fireHome.cancel();
        long now = SystemClock.uptimeMillis();
        Long last = lastWindowRedirects.get(target);
        if (last != null && now - last < REMOTE_REDIRECT_COOLDOWN_MS) return;
        lastWindowRedirects.put(target, now);
        android.util.Log.i("FireHomeRedirect", "redirecting window -> " + target);
        RemoteKeys.launch(this, target);
    }

    private void scheduleDetailsCheck(AccessibilityEvent event) {
        clearDetailsCheck();
        String match = matchEventDetails(event);
        if (match == null) match = matchWindowDetails();
        if (match != null) {
            launchWindowRedirect(match);
            return;
        }
        watchingAppDetails = true;
        scheduleDetailsStep(0);
    }

    private void scheduleDetailsStep(final int index) {
        if (!watchingAppDetails || index >= DETAILS_RETRY_DELAYS_MS.length) {
            watchingAppDetails = false;
            return;
        }
        detailsCheck = () -> {
            detailsCheck = null;
            if (!watchingAppDetails) return;
            String match = matchWindowDetails();
            if (match != null) {
                launchWindowRedirect(match);
                return;
            }
            scheduleDetailsStep(index + 1);
        };
        handler.postDelayed(detailsCheck, DETAILS_RETRY_DELAYS_MS[index]);
    }

    private void clearCaptureDetailsCheck() {
        if (captureDetailsCheck != null) {
            handler.removeCallbacks(captureDetailsCheck);
            captureDetailsCheck = null;
        }
    }

    private void captureVeneziaDetails(AccessibilityEvent event, String pkgStr, String clsStr,
                                       RemoteKeyCustom.WindowCaptureHandler windowCapture, int step) {
        clearCaptureDetailsCheck();
        String title = extractVeneziaDetailsTitle(event);
        if (title == null) title = findFirstDetailsTitle();
        if (title != null || step >= 2) {
            windowCapture.onWindowOpened(pkgStr, clsStr, title != null ? title : "Appstore");
            return;
        }
        captureDetailsCheck = () -> {
            captureDetailsCheck = null;
            if (RemoteKeyCustom.activeWindowCapture == windowCapture) {
                captureVeneziaDetails(null, pkgStr, clsStr, windowCapture, step + 1);
            }
        };
        handler.postDelayed(captureDetailsCheck, 280L);
    }

    private String extractVeneziaDetailsTitle(AccessibilityEvent event) {
        if (event == null) return null;
        for (CharSequence part : event.getText()) {
            String clean = cleanDetailsCandidate(part);
            if (clean != null) return clean;
        }
        return null;
    }

    private String findFirstDetailsTitle() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            return walkDetailsTitle(root);
        } finally {
            root.recycle();
        }
    }

    private static String walkDetailsTitle(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String clean = cleanDetailsCandidate(node.getText());
        if (clean != null) return clean;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                String found = walkDetailsTitle(child);
                if (found != null) return found;
            } finally {
                child.recycle();
            }
        }
        return null;
    }

    private static String cleanDetailsCandidate(CharSequence raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.length() < 2 || s.length() > 48) return null;
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        if ("appstore".equals(lower) || "amazon appstore".equals(lower)
                || "get".equals(lower) || "obtener".equals(lower)
                || "download".equals(lower) || "descargar".equals(lower)
                || "install".equals(lower) || "instalar".equals(lower)
                || "update".equals(lower) || "actualizar".equals(lower)
                || "open".equals(lower) || "abrir".equals(lower)
                || "cancel".equals(lower) || "cancelar".equals(lower)) {
            return null;
        }
        return s;
    }

    private void clearDetailsCheck() {
        watchingAppDetails = false;
        if (detailsCheck != null) {
            handler.removeCallbacks(detailsCheck);
            detailsCheck = null;
        }
    }

    private String matchEventDetails(AccessibilityEvent event) {
        if (event == null || !windowRedirects.hasDetailsRules()) return null;
        for (CharSequence part : event.getText()) {
            if (part == null) continue;
            String match = windowRedirects.matchDetailsText(part.toString());
            if (match != null) return match;
        }
        return null;
    }

    private String matchWindowDetails() {
        if (!windowRedirects.hasDetailsRules()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            CharSequence pkg = root.getPackageName();
            if (pkg != null && !VENEZIA_PKG.equals(pkg.toString())) return null;
            for (Map.Entry<String, String> rule : windowRedirects.detailsRules().entrySet()) {
                List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(rule.getKey());
                if (matches != null && !matches.isEmpty()) {
                    for (AccessibilityNodeInfo node : matches) {
                        if (node != null) node.recycle();
                    }
                    return rule.getValue();
                }
            }
            return null;
        } finally {
            root.recycle();
        }
    }

    static boolean firstBindAfterBoot(int boot, int lastBoot, long uptimeMs) {
        return boot >= 0 && boot != lastBoot && uptimeMs < BOOT_WINDOW_MS;
    }

    /** Lets the system see the key, so a long press still does what the firmware gives it. */
    private static final long HOME_KEY_DELAY_MS = 250;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean isActivityWindow(String pkg, String cls) {
        if (pkg.isEmpty() || cls.isEmpty()) return false;
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            return true;
        } catch (PackageManager.NameNotFoundException notAnActivity) {
            return false;
        }
    }

    /** stock_home_redirect on the Home key: when the stock home is already on screen (it came up at boot, before
     *  Android bound this service), Home brings it up again with no window change, so the redirect above never
     *  fires and the TV stays on the stock home until another app opens (#1116). A short press opens this
     *  launcher shortly after the system has handled the key; a long press (Google TV's quick settings) is left
     *  alone. Held time, not repeats: key repeats are made after accessibility filtering, so none arrive here. */
    private void redirectHomeKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()
                && isShortPress(event.getDownTime(), event.getEventTime(), ViewConfiguration.getLongPressTimeout())) {
            final long keyUpTime = event.getEventTime();
            handler.postDelayed(() -> {
                if (fireHome != null && FireHomeRedirect.wasShownAfter(keyUpTime)) return;
                openHome();
            }, HOME_KEY_DELAY_MS);
        }
    }

    static boolean isShortPress(long downTime, long upTime, long longPressTimeout) {
        return upTime - downTime < longPressTimeout;
    }

    private void openHome() {
        clearScreenOnStockHomeCheck();
        if (fireHome != null) {
            fireHome.request();
            return;
        }
        Intent open = stockHomeStays() ? new Intent(this, MainActivity.class)
                : new Intent(Intent.ACTION_MAIN).setPackage(getPackageName());
        startActivity(open.addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    /** stock_home_redirect while Android's Home is still the stock launcher (launcherx on Google TV outranks this
     *  one). A Home intent would then put this launcher in the home task and make it the home process, and from
     *  Android 10 the stock launcher, no longer exempt from the limits on background activity starts, can't open
     *  apps for the remote's YouTube and Netflix buttons (#1274). openHome starts it like an app instead:
     *  CATEGORY_HOME without ACTION_MAIN is not a Home intent, but still sends MainActivity back to the first tile.
     *  A MainActivity already in the home task, as the install scripts' first start leaves it, is reused as it is
     *  until that task goes (a restart or a reinstall). With this launcher or Android's chooser as Home, openHome
     *  keeps the Home intent. */
    private boolean stockHomeStays() {
        if (stockHome.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && stockHomePackage.equals(home.activityInfo.packageName);
    }

    @Override
    public void onInterrupt() {
    }
}
