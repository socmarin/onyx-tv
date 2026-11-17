package im.marin.onyxtv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses and matches {@code window_redirects} entries from {@link Config}.
 *
 * Supported formats:
 * <ul>
 *   <li>{@code package=target} — redirects whenever {@code package} opens a window.</li>
 *   <li>{@code package/className=target} or {@code package/.ShortClass=target} — redirects that specific activity.</li>
 *   <li>{@code details:text=target} — matches Fire OS Appstore details/update dialogs whose text contains {@code text}.</li>
 * </ul>
 */
final class WindowRedirects {
    static final WindowRedirects EMPTY = new WindowRedirects(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    private static final String DETAILS_PREFIX = "details:";

    private final Map<String, String> packageTargets;
    private final Map<String, String> activityTargets;
    private final Map<String, String> detailsTargets;

    private WindowRedirects(
            Map<String, String> packageTargets,
            Map<String, String> activityTargets,
            Map<String, String> detailsTargets) {
        this.packageTargets = packageTargets;
        this.activityTargets = activityTargets;
        this.detailsTargets = detailsTargets;
    }

    static WindowRedirects parse(List<String> entries) {
        if (entries == null || entries.isEmpty()) return EMPTY;
        Map<String, String> packages = new LinkedHashMap<>();
        Map<String, String> activities = new LinkedHashMap<>();
        Map<String, String> details = new LinkedHashMap<>();
        for (String raw : entries) {
            if (raw == null) continue;
            int eq = raw.indexOf('=');
            if (eq <= 0 || eq == raw.length() - 1) continue;
            String key = raw.substring(0, eq).trim();
            String target = raw.substring(eq + 1).trim();
            if (key.isEmpty() || target.isEmpty()) continue;
            if (key.regionMatches(true, 0, DETAILS_PREFIX, 0, DETAILS_PREFIX.length())) {
                String needle = key.substring(DETAILS_PREFIX.length()).trim().toLowerCase(Locale.ROOT);
                if (!needle.isEmpty()) details.put(needle, target);
                continue;
            }
            int slash = key.indexOf('/');
            if (slash > 0 && slash < key.length() - 1) {
                String pkg = key.substring(0, slash).trim();
                String cls = key.substring(slash + 1).trim();
                if (!pkg.isEmpty() && !cls.isEmpty()) {
                    String fullCls = cls.startsWith(".") ? pkg + cls : cls;
                    activities.put(pkg + "/" + fullCls, target);
                }
            } else if (slash < 0) {
                packages.put(key, target);
            }
        }
        if (packages.isEmpty() && activities.isEmpty() && details.isEmpty()) return EMPTY;
        return new WindowRedirects(
                Collections.unmodifiableMap(packages),
                Collections.unmodifiableMap(activities),
                Collections.unmodifiableMap(details));
    }

    boolean isEmpty() {
        return packageTargets.isEmpty() && activityTargets.isEmpty() && detailsTargets.isEmpty();
    }

    boolean hasWindowRules() {
        return !packageTargets.isEmpty() || !activityTargets.isEmpty();
    }

    boolean hasDetailsRules() {
        return !detailsTargets.isEmpty();
    }

    /** Exact activity rule first, then package-level rule, or {@code null} when nothing matches. */
    String matchWindow(String pkg, String cls) {
        if (pkg == null || pkg.isEmpty()) return null;
        if (cls != null && !cls.isEmpty() && !activityTargets.isEmpty()) {
            String byActivity = activityTargets.get(pkg + "/" + cls);
            if (byActivity != null) return byActivity;
        }
        return packageTargets.get(pkg);
    }

    /** First {@code details:} rule whose needle appears in {@code text} (case-insensitive), or {@code null}. */
    String matchDetailsText(String text) {
        if (text == null || text.isEmpty() || detailsTargets.isEmpty()) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : detailsTargets.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    Map<String, String> detailsRules() {
        return detailsTargets;
    }
}
