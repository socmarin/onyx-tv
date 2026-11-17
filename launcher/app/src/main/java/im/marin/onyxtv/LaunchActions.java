package im.marin.onyxtv;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import java.util.Map;
import java.util.function.BiFunction;

/** Selects a configured, package-scoped action only when it resolves; otherwise keeps the normal launch. */
final class LaunchActions {
    /** A launch_actions value "view:<uri>" opens that URI in the package instead of sending an action. */
    static final String VIEW_PREFIX = "view:";

    private LaunchActions() {}

    static <T> T select(String pkg, Map<String, String> actions, T fallback,
                        BiFunction<String, String, T> resolver) {
        String action = actions.get(pkg);
        if (action == null) return fallback;
        T custom = resolver.apply(pkg, action);
        return custom != null ? custom : fallback;
    }

    private static final String INPUT_PREFIX = "input:";

    /** The TV input id from an "input:<id>" launch_actions value, or null when the value isn't one, including
     *  an empty id ("input:" alone), which falls back to a normal launch rather than an empty input switch. */
    static String inputId(String action) {
        if (action == null || !action.startsWith(INPUT_PREFIX)) return null;
        String id = action.substring(INPUT_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }

    /** The URI of a "view:<uri>" value, or null for a plain action. */
    static String viewUri(String value) {
        if (value == null || !value.startsWith(VIEW_PREFIX)) return null;
        String uri = value.substring(VIEW_PREFIX.length()).trim();
        return uri.isEmpty() ? null : uri;
    }

    /** The trimmed "pkg/cls" value when an entry names an explicit component, or null otherwise. */
    static String componentString(String value) {
        if (value == null || viewUri(value) != null || inputId(value) != null) return null;
        String trimmed = value.trim();
        int slash = trimmed.indexOf('/');
        if (slash <= 0 || slash >= trimmed.length() - 1 || trimmed.indexOf(':') >= 0) return null;
        return trimmed;
    }

    /** The intent for a launch_actions value: ACTION_VIEW on the URI, "pkg/cls", "targetPkg:action", or the action on {@code pkg}. */
    static Intent intent(String pkg, String value) {
        String uri = viewUri(value);
        if (uri != null) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg);
        }
        String component = componentString(value);
        if (component != null) {
            ComponentName target = ComponentName.unflattenFromString(component);
            if (target != null) return new Intent().setComponent(target);
        }
        if (value != null) {
            int colon = value.indexOf(':');
            if (colon > 0 && colon < value.length() - 1) {
                return new Intent(value.substring(colon + 1).trim())
                        .setPackage(value.substring(0, colon).trim());
            }
        }
        return new Intent(value).setPackage(pkg);
    }
}
