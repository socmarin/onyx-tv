package im.marin.onyxtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.provider.Settings;

import java.util.List;

/** The standard settings intent, or, when nothing usable answers it, the first vendor settings entry
 *  from settings_fallback_components that is installed (Xiaomi China, TCL / FFALCON, Fire OS), else the
 *  AOSP TV settings app's main screen where it is installed. */
final class SystemSettings {
    private SystemSettings() {}

    static Intent intent(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        Intent standard = new Intent(action);
        ResolveInfo resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY);
        // Fire OS resolves the standard actions to a CTS compliance stub that opens nothing;
        // treat it as missing so the vendor fallback runs.
        if (resolved != null && !resolved.activityInfo.name.contains("CTSDummy")) return standard;
        Intent vendor = vendorIntent(context);
        if (vendor != null) return vendor;
        // Some firmware ships the AOSP TV settings app with no filter for the standard actions (HY300 Pro+
        // projector, rk3566, Android 14): open its main screen directly. Not a vendor entry, so the Settings
        // menu's "Native settings" row stays as it was.
        Intent aosp = new Intent().setComponent(AOSP_TV_SETTINGS);
        try {
            pm.getPackageInfo(AOSP_TV_SETTINGS.getPackageName(), 0);
            return aosp;
        } catch (PackageManager.NameNotFoundException e) {
            return standard;
        }
    }

    private static final ComponentName AOSP_TV_SETTINGS =
            new ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings");

    /** The first vendor settings entry from settings_fallback_components that is installed, or null
     *  when none is. China-market firmware keeps pages the AOSP TV settings has no row for -
     *  Xiaomi's Account & Security, where the ADB switch lives - in the vendor's own settings app;
     *  MainActivity's Settings menu offers it as "Native settings" while one is installed. */
    static Intent vendorIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String value : context.getResources().getStringArray(R.array.settings_fallback_components)) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component == null) continue;
            // Explicit resolveActivity() always succeeds, so check the package exists instead.
            try {
                pm.getPackageInfo(component.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            return new Intent().setComponent(component);
        }
        return null;
    }

    /** The TV's App info page for pkg, or null where the firmware has none (CVTE's settings app, #1202). */
    static Intent appInfo(Context context, String pkg) {
        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        return context.getPackageManager().resolveActivity(info, PackageManager.MATCH_DEFAULT_ONLY) != null ? info : null;
    }

    /** Uninstall dialog for user-removable apps, or null for pure system apps or when no uninstaller resolves. */
    static Intent uninstallIntent(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        if (!canUninstall(pm, pkg)) return null;
        Intent uninstall = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null));
        ResolveInfo resolved = pm.resolveActivity(uninstall, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && !resolved.activityInfo.name.contains("CTSDummy") ? uninstall : null;
    }

    static boolean canUninstall(PackageManager pm, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            return isUninstallableFlags(pm.getApplicationInfo(pkg, 0).flags);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    static boolean isUninstallableFlags(int applicationFlags) {
        boolean system = (applicationFlags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean updatedSystem = (applicationFlags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        return !system || updatedSystem;
    }

    static final String FIRE_HOME_PKG = "com.amazon.tv.launcher";
    static final String VEWD_TV_PKG = "com.vewd.core.tvapp";
    static final String AMAZON_LIVETV_PKG = "com.amazon.tv.livetv";
    static final String AMAZON_LIVETV_YAC_ACTION = "com.amazon.tv.livetv.action.YAC_LAUNCH";
    static final String VEWD_TERRESTRIAL_INPUT_ID = "com.vewd.core.tis/.TerrestrialHbbTvInputService";

    static boolean isInstalled(PackageManager pm, String pkg) {
        if (pm == null || pkg == null || pkg.isEmpty()) return false;
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    /** True when Amazon's Fire TV launcher package is installed on this device. */
    static boolean isFireOs(Context context) {
        return context != null && isInstalled(context.getPackageManager(), FIRE_HOME_PKG);
    }

    /** True when Amazon's Live TV YAC activity is resolvable (Xiaomi Fire OS tuner flow). */
    static boolean hasAmazonYacLiveTv(Context context) {
        if (context == null) return false;
        Intent intent = new Intent(AMAZON_LIVETV_YAC_ACTION).setPackage(AMAZON_LIVETV_PKG);
        return context.getPackageManager().resolveActivity(intent, 0) != null;
    }

    /** True when the given TvInputInfo id is registered in the system TvInputManager. */
    static boolean hasTvInputId(Context context, String inputId) {
        if (context == null || inputId == null || inputId.isEmpty()) return false;
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return false;
        try {
            List<TvInputInfo> list = tim.getTvInputList();
            if (list == null) return false;
            for (TvInputInfo info : list) {
                if (info != null && inputId.equals(info.getId())) return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    /** True when the TV exposes a real system Home picker (hidden when stock_home_redirect or a stub is used). */
    static boolean hasHomePicker(Context context, String stockHomeRedirect) {
        if (stockHomeRedirect != null && !stockHomeRedirect.trim().isEmpty()) return false;
        if (isFireOs(context)) return false;
        ResolveInfo resolved = context.getPackageManager().resolveActivity(
                new Intent(Settings.ACTION_HOME_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
                && isRealHomePickerActivity(resolved.activityInfo.packageName, resolved.activityInfo.name);
    }

    static boolean isRealHomePickerActivity(String pkg, String activityName) {
        if (pkg == null || pkg.isEmpty() || activityName == null) return false;
        return !activityName.contains("CTSDummy");
    }

    /**
     * The TV's display or picture settings. On Xiaomi sets, the AOSP DISPLAY_SETTINGS action resolves to
     * an empty stub in DroidTvSettings; prefer Xiaomi's own PictureActivity where installed.
     */
    static Intent displayIntent(Context context) {
        ComponentName xiaomi = new ComponentName("com.android.tv.settings",
                "com.xiaomi.mitv.settings.picture.PictureActivity");
        if (context.getPackageManager().resolveActivity(new Intent().setComponent(xiaomi), 0) != null) {
            return new Intent().setComponent(xiaomi);
        }
        return intent(context, Settings.ACTION_DISPLAY_SETTINGS);
    }
}
