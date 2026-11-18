package im.marin.onyxtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Discovers the TV's physical inputs (HDMI ports, AV/SCART, and hardware tuner when no Live TV tile
 * is already on Home) and renders virtual Home tiles that switch straight to each source (#inputs-on-home).
 */
final class TvInputTiles {
    static final String INPUT_PREFIX = "input:";

    static final int KIND_HDMI = 0;
    static final int KIND_AV = 1;
    static final int KIND_TUNER = 2;

    /** Snapshot of one TvInputInfo entry so discovery logic runs in pure JVM unit tests too. */
    static final class RawInput {
        final String id;
        final String parentId;
        final int type;
        final boolean passthrough;
        final boolean hidden;
        final boolean systemApp;
        final String label;
        final String customLabel;

        RawInput(String id, String parentId, int type, boolean passthrough, boolean hidden,
                 boolean systemApp, String label, String customLabel) {
            this.id = id;
            this.parentId = parentId;
            this.type = type;
            this.passthrough = passthrough;
            this.hidden = hidden;
            this.systemApp = systemApp;
            this.label = label;
            this.customLabel = customLabel;
        }
    }

    static final class TileSpec {
        final String inputId;
        final String pkg;
        final String label;
        final String badge;
        final int kind;

        TileSpec(String inputId, String label, String badge, int kind) {
            this.inputId = inputId;
            this.pkg = INPUT_PREFIX + inputId;
            this.label = label;
            this.badge = badge;
            this.kind = kind;
        }

        ComponentName component() {
            return new ComponentName(pkg, inputId);
        }
    }

    private TvInputTiles() {
    }

    static boolean isInputPackage(String pkg) {
        return pkg != null && pkg.startsWith(INPUT_PREFIX) && pkg.length() > INPUT_PREFIX.length();
    }

    static String inputIdFromPackage(String pkg) {
        return isInputPackage(pkg) ? pkg.substring(INPUT_PREFIX.length()) : null;
    }

    /** Enabled input tiles after applying user's explicit selection (if any). */
    static List<TileSpec> active(Context ctx, Config cfg) {
        if (cfg == null) return Collections.emptyList();
        android.content.SharedPreferences choices =
                ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        if (choices.contains(Config.INPUT_TILES_SELECTED_KEY)) {
            Set<String> raw = choices.getStringSet(Config.INPUT_TILES_SELECTED_KEY, Collections.emptySet());
            Set<String> selected = raw == null ? Collections.emptySet() : new HashSet<>(raw);
            return selected.isEmpty() ? Collections.emptyList() : filterSelected(discover(ctx, cfg), selected);
        }
        if (!cfg.inputTiles) return Collections.emptyList();
        return filterSelected(discover(ctx, cfg), cfg.inputTilesSelected);
    }

    static List<TileSpec> filterSelected(List<TileSpec> discovered, Set<String> selectedIds) {
        if (discovered == null || discovered.isEmpty()) return Collections.emptyList();
        if (selectedIds == null) return discovered;
        if (selectedIds.isEmpty()) return Collections.emptyList();
        List<TileSpec> filtered = new ArrayList<>(discovered.size());
        for (TileSpec spec : discovered) {
            if (selectedIds.contains(spec.inputId)) filtered.add(spec);
        }
        return filtered;
    }

    static boolean hasTuner(List<TileSpec> tiles) {
        if (tiles == null) return false;
        for (TileSpec spec : tiles) {
            if (spec.kind == KIND_TUNER) return true;
        }
        return false;
    }

    /**
     * Live TV package governed by the TDT entry in Entradas en el inicio, or null if this TV has
     * no physical tuner. Suppresses the OEM Live TV app on Home and in the app library even when
     * TDT is unchecked so it does not reappear or duplicate outside Entradas en el inicio.
     */
    static String ownedLiveTvPackage(Context ctx, Config cfg) {
        if (ctx == null || cfg == null || cfg.liveTvPackage.isEmpty()) return null;
        return ownedLiveTvPackage(discoverAll(ctx, cfg), cfg.liveTvPackage);
    }

    static String ownedLiveTvPackage(List<TileSpec> discovered, String liveTvPackage) {
        if (liveTvPackage == null || liveTvPackage.isEmpty()) return null;
        return hasTuner(discovered) ? liveTvPackage : null;
    }

    /** Visible discovered input tiles for the active TV (excluding user-hidden inputs). */
    static List<TileSpec> discover(Context ctx, Config cfg) {
        return filterVisible(discoverAll(ctx, cfg), cfg == null ? null : cfg.hiddenInputs);
    }

    static List<TileSpec> filterVisible(List<TileSpec> all, Set<String> hiddenInputs) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        if (hiddenInputs == null || hiddenInputs.isEmpty()) return all;
        List<TileSpec> visible = new ArrayList<>(all.size());
        for (TileSpec spec : all) {
            if (!hiddenInputs.contains(spec.inputId)) visible.add(spec);
        }
        return visible;
    }

    /** All discovered hardware inputs (including ones hidden by the user in Settings). */
    static List<TileSpec> discoverAll(Context ctx, Config cfg) {
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return Collections.emptyList();
        List<TvInputInfo> systemInputs;
        try {
            systemInputs = tim.getTvInputList();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
        if (systemInputs == null || systemInputs.isEmpty()) return Collections.emptyList();

        InputNames.PortStrings portStrings = InputNames.PortStrings.from(ctx);
        List<RawInput> raw = new ArrayList<>(systemInputs.size());
        for (TvInputInfo info : systemInputs) {
            if (info == null) continue;
            String portLabel = InputNames.localizePortLabel(info.loadLabel(ctx), info.getType(), portStrings);
            CharSequence cl = InputNames.tidy(info.loadCustomLabel(ctx));
            boolean isSystem = info.getServiceInfo() != null
                    && info.getServiceInfo().applicationInfo != null
                    && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            raw.add(new RawInput(
                    info.getId(),
                    info.getParentId(),
                    info.getType(),
                    info.isPassthroughInput(),
                    info.isHidden(ctx),
                    isSystem,
                    portLabel.isEmpty() ? info.getId() : portLabel,
                    cl == null ? "" : cl.toString()));
        }
        Set<String> alreadyMappedInputs = mappedInputIds(cfg);
        return buildTiles(raw, cfg.inputSources, cfg.liveTvInputId,
                alreadyMappedInputs, ctx.getString(R.string.live_tv));
    }

    static List<TileSpec> buildTiles(List<RawInput> inputs, List<String> inputSources,
                                     String configuredTunerId,
                                     Set<String> alreadyMappedInputs, String tunerFallbackLabel) {
        if (inputs == null || inputs.isEmpty()) return Collections.emptyList();

        List<RawInput> sorted = new ArrayList<>(inputs);
        sorted.sort((a, b) -> {
            boolean ha = a.type == TvInputInfo.TYPE_HDMI;
            boolean hb = b.type == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
        });

        Set<String> allIds = new HashSet<>();
        for (RawInput info : sorted) allIds.add(info.id);

        Map<String, List<RawInput>> cecDevices = new HashMap<>();
        for (RawInput info : sorted) {
            if (info.parentId == null || !allIds.contains(info.parentId)) continue;
            if (!info.passthrough || info.hidden) continue;
            cecDevices.computeIfAbsent(info.parentId, k -> new ArrayList<>()).add(info);
        }

        List<TileSpec> result = new ArrayList<>();
        String tunerId = null;
        boolean tunerHw = false;
        boolean tunerAnalog = false;

        for (RawInput info : sorted) {
            boolean matchesConfigured = configuredTunerId != null && !configuredTunerId.isEmpty()
                    && configuredTunerId.equals(info.id);
            boolean hwTuner = info.systemApp && info.id.matches(".*/HW\\d+$");
            if (info.type == TvInputInfo.TYPE_TUNER && (matchesConfigured || hwTuner)
                    && (configuredTunerId == null || configuredTunerId.isEmpty() || matchesConfigured)) {
                boolean analog = info.id.toLowerCase(Locale.ROOT).contains("analog");
                boolean better = tunerId == null
                        || (hwTuner && !tunerHw)
                        || (hwTuner == tunerHw && tunerAnalog && !analog)
                        || (hwTuner == tunerHw && analog == tunerAnalog && info.id.compareTo(tunerId) < 0);
                if (better) {
                    tunerId = info.id;
                    tunerHw = hwTuner;
                    tunerAnalog = analog;
                }
            }
            if (!info.passthrough || info.hidden) continue;
            if (isDisabledSource(inputSources, info.id)) continue;
            if (info.parentId != null && allIds.contains(info.parentId)) continue;

            boolean hdmi = info.type == TvInputInfo.TYPE_HDMI;
            String portLabel = (info.label == null || info.label.isEmpty()) ? info.id : info.label;
            if (hdmi && !portLabel.toUpperCase(Locale.ROOT).contains("HDMI")) {
                portLabel = "HDMI " + portLabel;
            }
            boolean named = info.customLabel != null && !info.customLabel.isEmpty()
                    && !info.customLabel.equals(portLabel);

            List<RawInput> devices = cecDevices.get(info.id);
            if (devices != null) {
                for (RawInput device : devices) {
                    if (alreadyMappedInputs.contains(device.id) || alreadyMappedInputs.contains(info.id)) continue;
                    String devName = device.customLabel != null && !device.customLabel.isEmpty()
                            ? device.customLabel
                            : (named ? info.customLabel : device.label);
                    String label = devName == null || devName.isEmpty() ? portLabel : devName + " · " + portLabel;
                    result.add(new TileSpec(device.id, label, portLabel, KIND_HDMI));
                }
                continue;
            }
            if (alreadyMappedInputs.contains(info.id)) continue;
            String label = named ? info.customLabel + " · " + portLabel : portLabel;
            result.add(new TileSpec(info.id, label, shortBadge(portLabel, hdmi), hdmi ? KIND_HDMI : KIND_AV));
        }

        if (tunerId != null && !alreadyMappedInputs.contains(tunerId)
                && !isDisabledSource(inputSources, tunerId)) {
            String label = tunerFallbackLabel != null && !tunerFallbackLabel.isEmpty()
                    ? tunerFallbackLabel : "TV";
            result.add(0, new TileSpec(tunerId, label, shortBadge(label, false), KIND_TUNER));
        }
        return result;
    }

    private static boolean isDisabledSource(List<String> inputSources, String id) {
        return Integer.valueOf(-1).equals(InputSources.mapped(inputSources, id));
    }

    private static Set<String> mappedInputIds(Config cfg) {
        Set<String> mapped = new HashSet<>();
        if (cfg == null || cfg.launchActions == null) return mapped;
        for (String action : cfg.launchActions.values()) {
            String id = LaunchActions.inputId(action);
            if (id != null) mapped.add(id);
        }
        return mapped;
    }

    static String shortBadge(String label, boolean hdmi) {
        String clean = label == null ? "" : label.trim();
        if (clean.isEmpty()) return hdmi ? "HDMI" : "AV";
        String upper = clean.toUpperCase(Locale.ROOT);
        if (upper.length() <= 16) return upper;
        return upper.substring(0, 15).trim() + "…";
    }

    /** Crisp 16:9 Onyx dark-glass banner for an auto-discovered TV input tile. */
    static Drawable renderBanner(Context ctx, TileSpec spec) {
        return renderBanner(ctx, spec, null, null);
    }

    static Drawable renderBanner(Context ctx, TileSpec spec, String customLabel, String presetRef) {
        int w = 320, h = 180;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                0xFF1D2430, 0xFF0E1218, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, w, h, bgPaint);

        int presetIcon = InputCustom.presetIconRes(presetRef);
        int iconRes = presetIcon != 0 ? presetIcon
                : (spec.kind == KIND_HDMI ? R.drawable.ic_input
                : (spec.kind == KIND_TUNER ? R.drawable.ic_livetv : R.drawable.ic_av));
        Drawable icon = ctx.getDrawable(iconRes);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(0xFFE6ECF5);
            int iconSize = 54;
            int left = (w - iconSize) / 2;
            int top = 38;
            icon.setBounds(left, top, left + iconSize, top + iconSize);
            icon.draw(canvas);
        }

        String badge = (customLabel != null && !customLabel.trim().isEmpty())
                ? shortBadge(customLabel, spec.kind == KIND_HDMI)
                : spec.badge;
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFDCE3ED);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setLetterSpacing(0.08f);
        float textSize = 24f;
        textPaint.setTextSize(textSize);
        float maxWidth = w - 28f;
        float measured = textPaint.measureText(badge);
        if (measured > maxWidth && measured > 0f) {
            textPaint.setTextSize(Math.max(15f, textSize * (maxWidth / measured)));
        }
        canvas.drawText(badge, w / 2f, 134f, textPaint);

        return new BitmapDrawable(ctx.getResources(), bmp);
    }
}
