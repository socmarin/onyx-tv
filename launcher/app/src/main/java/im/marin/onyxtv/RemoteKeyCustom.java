package im.marin.onyxtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * User-defined remote key shortcuts configured on the TV in Onyx settings.
 * Stored as an ordered list of {@code app_keys} entries in {@code launcher_settings}
 * and appended to {@link Config#appKeys} so user mappings take precedence cleanly.
 */
final class RemoteKeyCustom {

    interface KeyCaptureHandler {
        boolean onKeyEvent(KeyEvent event);
    }

    interface WindowCaptureHandler {
        boolean onWindowOpened(String pkg, String cls, String detailsTitle);
    }

    static final String WINDOW_TOKEN_PREFIX = "window:";

    /** Active while Onyx is waiting for a remote press in Settings; intercepted in Accessibility filters first. */
    static volatile KeyCaptureHandler activeCapture;
    static volatile WindowCaptureHandler activeWindowCapture;

    static final class Shortcut {
        final String raw;
        final boolean hold;
        final String keyToken;
        final String target;

        Shortcut(String raw, boolean hold, String keyToken, String target) {
            this.raw = raw;
            this.hold = hold;
            this.keyToken = keyToken;
            this.target = target;
        }
    }

    private RemoteKeyCustom() {
    }

    static List<String> readEntries(SharedPreferences prefs) {
        return CustomArt.readList(prefs, Config.CUSTOM_APP_KEYS_KEY);
    }

    static List<Shortcut> readShortcuts(SharedPreferences prefs) {
        List<String> entries = readEntries(prefs);
        List<Shortcut> out = new ArrayList<>(entries.size());
        for (String entry : entries) {
            Shortcut parsed = parseEntry(entry);
            if (parsed != null) out.add(parsed);
        }
        return out;
    }

    static boolean isWindowToken(String keyToken) {
        return keyToken != null && keyToken.startsWith(WINDOW_TOKEN_PREFIX);
    }

    /** Returns the raw {@link WindowRedirects} rule (e.g. {@code com.netflix.ninja=target}) or null. */
    static String toWindowRedirectRule(String rawEntry) {
        if (rawEntry == null) return null;
        String trimmed = rawEntry.trim();
        if (!trimmed.startsWith(WINDOW_TOKEN_PREFIX)) return null;
        String rule = trimmed.substring(WINDOW_TOKEN_PREFIX.length()).trim();
        int eq = rule.indexOf('=');
        return eq > 0 && eq < rule.length() - 1 ? rule : null;
    }

    static Shortcut parseEntry(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;
        if (text.startsWith(WINDOW_TOKEN_PREFIX)) {
            String rest = text.substring(WINDOW_TOKEN_PREFIX.length()).trim();
            int eq = rest.indexOf('=');
            if (eq <= 0 || eq >= rest.length() - 1) return null;
            String ruleKey = rest.substring(0, eq).trim();
            String target = rest.substring(eq + 1).trim();
            if (ruleKey.isEmpty() || target.isEmpty()) return null;
            return new Shortcut(text, false, WINDOW_TOKEN_PREFIX + ruleKey, target);
        }
        boolean hold = false;
        if (text.startsWith("hold:")) {
            hold = true;
            text = text.substring("hold:".length()).trim();
        } else if (text.startsWith("front:")) {
            return null;
        }
        if (text.startsWith("scan:")) {
            String[] parts = text.split(":", 3);
            if (parts.length != 3) return null;
            String scan = parts[1].trim();
            String target = parts[2].trim();
            if (scan.isEmpty() || target.isEmpty()) return null;
            return new Shortcut(raw.trim(), hold, "scan:" + scan, target);
        }
        int colon = text.indexOf(':');
        if (colon <= 0 || colon >= text.length() - 1) return null;
        String keyToken = text.substring(0, colon).trim();
        String target = text.substring(colon + 1).trim();
        if (keyToken.isEmpty() || target.isEmpty()) return null;
        return new Shortcut(raw.trim(), hold, keyToken, target);
    }

    static String formatEntry(boolean hold, String keyToken, String target) {
        if (keyToken == null || target == null) return "";
        String cleanKey = keyToken.trim();
        String cleanTarget = target.trim();
        if (cleanKey.isEmpty() || cleanTarget.isEmpty()) return "";
        if (isWindowToken(cleanKey)) return cleanKey + "=" + cleanTarget;
        return (hold ? "hold:" : "") + cleanKey + ":" + cleanTarget;
    }

    /** Formats a captured physical press, preferring scancode when available to disambiguate OEM buttons. */
    static String formatCapturedKeyToken(int keyCode, int scanCode) {
        return formatCapturedKeyToken(keyCode, scanCode, KeyEvent::keyCodeToString);
    }

    static String formatCapturedKeyToken(int keyCode, int scanCode, IntFunction<String> keyNames) {
        if (scanCode > 0) return "scan:" + scanCode;
        if (keyCode <= 0) return "";
        if (keyCode >= 1000) return String.valueOf(keyCode);
        try {
            String name = keyNames != null ? keyNames.apply(keyCode) : "";
            return name == null || name.isEmpty() ? String.valueOf(keyCode) : name;
        } catch (RuntimeException ignored) {
            return String.valueOf(keyCode);
        }
    }

    /** Keys essential for navigating Android TV / volume / power, never bound to shortcuts. */
    static boolean isReservedNavigationKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return true;
            default:
                return false;
        }
    }

    static void upsertShortcut(SharedPreferences prefs, boolean hold, String keyToken, String target) {
        String entry = formatEntry(hold, keyToken, target);
        if (prefs == null || entry.isEmpty()) return;
        List<String> updated = new ArrayList<>();
        for (String existing : readEntries(prefs)) {
            Shortcut s = parseEntry(existing);
            if (s != null && s.hold == hold && s.keyToken.equals(keyToken)) continue;
            updated.add(existing);
        }
        updated.add(entry);
        CustomArt.writeList(prefs, Config.CUSTOM_APP_KEYS_KEY, updated);
    }

    static void removeShortcutAt(SharedPreferences prefs, int index) {
        if (prefs == null) return;
        List<String> current = new ArrayList<>(readEntries(prefs));
        if (index < 0 || index >= current.size()) return;
        current.remove(index);
        CustomArt.writeList(prefs, Config.CUSTOM_APP_KEYS_KEY, current);
    }

    static void clearAll(SharedPreferences.Editor editor) {
        if (editor != null) editor.remove(Config.CUSTOM_APP_KEYS_KEY);
    }

    static boolean hasCustom(SharedPreferences prefs) {
        return prefs != null && prefs.contains(Config.CUSTOM_APP_KEYS_KEY);
    }

    static String prettyKeyName(Context ctx, String keyToken) {
        if (keyToken == null) return "";
        if (isWindowToken(keyToken)) {
            String rule = keyToken.substring(WINDOW_TOKEN_PREFIX.length()).trim();
            String name;
            if (rule.regionMatches(true, 0, "details:", 0, "details:".length())) {
                name = rule.substring("details:".length()).trim();
            } else if (rule.contains("AppsGridLauncherActivity")) {
                name = ctx != null ? ctx.getString(R.string.app_library) : "Apps";
            } else {
                name = resolveAppLabel(ctx, rule);
            }
            return ctx != null ? ctx.getString(R.string.remote_key_scan_label, name) : name;
        }
        if (keyToken.startsWith("scan:")) {
            String code = keyToken.substring("scan:".length());
            return ctx != null ? ctx.getString(R.string.remote_key_scan_label, code) : "Scan " + code;
        }
        String clean = keyToken;
        if (clean.startsWith("KEYCODE_")) clean = clean.substring("KEYCODE_".length());
        clean = clean.replace('_', ' ').toLowerCase(Locale.ROOT).trim();
        if (clean.isEmpty()) return keyToken;
        StringBuilder sb = new StringBuilder(clean.length());
        boolean capNext = true;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == ' ') {
                sb.append(' ');
                capNext = true;
            } else if (capNext) {
                sb.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String resolveAppLabel(Context ctx, String pkg) {
        if (ctx != null && pkg != null && !pkg.isEmpty()) {
            try {
                PackageManager pm = ctx.getPackageManager();
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                if (label != null && label.length() > 0) return label.toString();
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            }
        }
        return pkg == null ? "" : pkg;
    }

    static boolean isAccessibilityServiceEnabled(Context ctx) {
        if (ctx == null) return false;
        try {
            int enabled = Settings.Secure.getInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled != 1) return false;
            String services = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(services)) return false;
            ComponentName target = new ComponentName(ctx, HomeKeyService.class);
            String full = target.flattenToString();
            String shortName = target.flattenToShortString();
            for (String s : services.split(":")) {
                if (full.equalsIgnoreCase(s) || shortName.equalsIgnoreCase(s)) return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }
}
