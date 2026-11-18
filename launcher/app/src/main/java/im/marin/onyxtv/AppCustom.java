package im.marin.onyxtv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-app custom labels, 1:1 icon ({@code files/app_icons/}), 16:9 banner ({@code files/app_banners/}),
 * and visibility overrides configured on the TV in Onyx settings.
 */
final class AppCustom {

    static final class AppInfoItem {
        final ResolveInfo resolveInfo;
        final String pkg;
        final String originalLabel;

        AppInfoItem(ResolveInfo resolveInfo, String pkg, String originalLabel) {
            this.resolveInfo = resolveInfo;
            this.pkg = pkg;
            this.originalLabel = originalLabel;
        }
    }

    private AppCustom() {
    }

    static boolean isHidden(Config cfg, String pkg) {
        return cfg != null && pkg != null && cfg.userHiddenApps != null && cfg.userHiddenApps.contains(pkg);
    }

    static void setHidden(SharedPreferences prefs, String pkg, boolean hide) {
        if (prefs == null || pkg == null || pkg.trim().isEmpty()) return;
        Set<String> raw = prefs.getStringSet(Config.APP_HIDDEN_KEY, Collections.emptySet());
        Set<String> updated = raw == null ? new HashSet<>() : new HashSet<>(raw);
        if (hide) updated.add(pkg);
        else updated.remove(pkg);
        SharedPreferences.Editor editor = prefs.edit();
        if (updated.isEmpty()) editor.remove(Config.APP_HIDDEN_KEY);
        else editor.putStringSet(Config.APP_HIDDEN_KEY, updated);
        editor.apply();
    }

    static void resetApp(SharedPreferences prefs, String pkg) {
        if (prefs == null || pkg == null || pkg.trim().isEmpty()) return;
        CustomArt.setMapEntry(prefs, Config.APP_LABELS_KEY, pkg, null);
        CustomArt.setMapEntry(prefs, Config.APP_ICONS_KEY, pkg, null);
        CustomArt.setMapEntry(prefs, Config.APP_BANNERS_KEY, pkg, null);
        setHidden(prefs, pkg, false);
    }

    static void clearAll(SharedPreferences.Editor editor) {
        if (editor == null) return;
        editor.remove(Config.APP_LABELS_KEY)
                .remove(Config.APP_ICONS_KEY)
                .remove(Config.APP_BANNERS_KEY)
                .remove(Config.APP_HIDDEN_KEY);
    }

    static boolean hasCustom(SharedPreferences prefs) {
        return prefs != null && (prefs.contains(Config.APP_LABELS_KEY)
                || prefs.contains(Config.APP_ICONS_KEY)
                || prefs.contains(Config.APP_BANNERS_KEY)
                || prefs.contains(Config.APP_HIDDEN_KEY));
    }

    static String safeSlug(String pkg) {
        return CustomArt.safeSlug(pkg, "app");
    }

    static String importIconImage(Context ctx, String pkg, Uri uri) {
        if (pkg == null) return null;
        return CustomArt.importIconImage(ctx, "app_icon_" + safeSlug(pkg) + ".png", uri);
    }

    static String importBannerImage(Context ctx, String pkg, Uri uri) {
        if (pkg == null) return null;
        return CustomArt.importBannerImage(ctx, CustomArt.APP_BANNERS_DIR, "app_banner_" + safeSlug(pkg) + ".png", uri);
    }

    static String formatArtName(String filename, String importedFallback) {
        if (filename == null || filename.trim().isEmpty()) return "";
        String lower = filename.trim().toLowerCase(Locale.ROOT);
        if ((lower.startsWith("app_icon_") || lower.startsWith("app_banner_"))
                && importedFallback != null && !importedFallback.isEmpty()) {
            return importedFallback;
        }
        return InputCustom.formatBannerFileName(filename, importedFallback);
    }

    /** Discovers all launchable TV + phone Android apps (except Onyx itself), including user-hidden ones. */
    static List<AppInfoItem> discoverApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        String self = ctx.getPackageName();
        Map<String, AppInfoItem> byPkg = new HashMap<>();
        Intent[] queries = {
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        };
        for (Intent q : queries) {
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (ri.activityInfo == null || !ri.activityInfo.exported) continue;
                String pkg = ri.activityInfo.packageName;
                if (self.equals(pkg) || byPkg.containsKey(pkg)) continue;
                String label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
                byPkg.put(pkg, new AppInfoItem(ri, pkg, label));
            }
        }
        List<AppInfoItem> list = new ArrayList<>(byPkg.values());
        Collator collator = Collator.getInstance();
        list.sort((a, b) -> collator.compare(a.originalLabel, b.originalLabel));
        return list;
    }

    static int customizedCount(SharedPreferences prefs, Config cfg, List<AppInfoItem> apps) {
        Map<String, String> labels = CustomArt.readMap(prefs, Config.APP_LABELS_KEY);
        Map<String, String> icons = CustomArt.readMap(prefs, Config.APP_ICONS_KEY);
        Map<String, String> banners = CustomArt.readMap(prefs, Config.APP_BANNERS_KEY);
        int count = 0;
        for (AppInfoItem item : apps) {
            if (labels.containsKey(item.pkg)
                    || icons.containsKey(item.pkg)
                    || banners.containsKey(item.pkg)
                    || isHidden(cfg, item.pkg)) {
                count++;
            }
        }
        return count;
    }
}
