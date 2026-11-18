package im.marin.onyxtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-input custom labels and icon/banner overrides configured on the TV in Onyx settings.
 * Stored as serialized key-value maps in {@code launcher_settings} SharedPreferences and merged
 * into {@link Config#appLabels} and {@link Config#appBanners} under {@code input:<inputId>}.
 */
final class InputCustom {
    static final String PRESET_PREFIX = "preset:";
    static final String PRESET_TV = PRESET_PREFIX + "tv";
    static final String PRESET_CONSOLE = PRESET_PREFIX + "console";
    static final String PRESET_STREAMING = PRESET_PREFIX + "streaming";
    static final String PRESET_PC = PRESET_PREFIX + "pc";
    static final String PRESET_DISC = PRESET_PREFIX + "disc";
    static final String PRESET_AUDIO = PRESET_PREFIX + "audio";
    static final String PRESET_DECODER = PRESET_PREFIX + "decoder";
    static final String PRESET_HDMI = PRESET_PREFIX + "hdmi";
    static final String PRESET_AV = PRESET_PREFIX + "av";

    static final String[] QUICK_NAME_PRESETS = {
            "Xbox", "PlayStation", "Switch", "Fire TV", "Apple TV", "Chromecast", "PC", "TV Box"
    };

    static final class Preset {
        final String id;
        final int iconRes;
        final int labelRes;

        Preset(String id, int iconRes, int labelRes) {
            this.id = id;
            this.iconRes = iconRes;
            this.labelRes = labelRes;
        }
    }

    private static final List<Preset> PRESETS = Collections.unmodifiableList(Arrays.asList(
            new Preset(PRESET_TV, R.drawable.ic_livetv, R.string.input_preset_tv),
            new Preset(PRESET_CONSOLE, R.drawable.ic_input_console, R.string.input_preset_console),
            new Preset(PRESET_STREAMING, R.drawable.ic_input_streaming, R.string.input_preset_streaming),
            new Preset(PRESET_PC, R.drawable.ic_input_pc, R.string.input_preset_pc),
            new Preset(PRESET_DISC, R.drawable.ic_input_disc, R.string.input_preset_disc),
            new Preset(PRESET_AUDIO, R.drawable.ic_input_audio, R.string.input_preset_audio),
            new Preset(PRESET_DECODER, R.drawable.ic_input_decoder, R.string.input_preset_decoder),
            new Preset(PRESET_HDMI, R.drawable.ic_input, 0),
            new Preset(PRESET_AV, R.drawable.ic_av, 0)));

    private InputCustom() {
    }

    static List<Preset> presets() {
        return PRESETS;
    }

    static boolean isPreset(String value) {
        return value != null && value.startsWith(PRESET_PREFIX);
    }

    static int presetIconRes(String value) {
        if (!isPreset(value)) return 0;
        for (Preset p : PRESETS) {
            if (p.id.equals(value)) return p.iconRes;
        }
        return 0;
    }

    static String presetLabel(Context ctx, String value) {
        if (value == null || value.isEmpty()) return ctx.getString(R.string.input_default);
        if (PRESET_HDMI.equals(value)) return "HDMI";
        if (PRESET_AV.equals(value)) return "AV";
        for (Preset p : PRESETS) {
            if (p.id.equals(value)) return ctx.getString(p.labelRes);
        }
        return value;
    }

    static Map<String, String> readMap(SharedPreferences prefs, String key) {
        return CustomArt.readMap(prefs, key);
    }

    static Map<String, String> parseMap(String raw) {
        return CustomArt.parseMap(raw);
    }

    static String encodeMap(Map<String, String> map) {
        return CustomArt.encodeMap(map);
    }

    static void setEntry(SharedPreferences prefs, String key, String inputId, String value) {
        CustomArt.setMapEntry(prefs, key, inputId, value);
    }

    static void clearAll(SharedPreferences.Editor editor) {
        if (editor == null) return;
        editor.remove(Config.INPUT_LABELS_KEY)
                .remove(Config.INPUT_BANNERS_KEY)
                .remove(Config.INPUT_HIDDEN_KEY);
    }

    static boolean hasCustom(SharedPreferences prefs) {
        return prefs != null && (prefs.contains(Config.INPUT_LABELS_KEY)
                || prefs.contains(Config.INPUT_BANNERS_KEY)
                || prefs.contains(Config.INPUT_HIDDEN_KEY));
    }

    static boolean isHidden(Config cfg, String inputId) {
        return cfg != null && inputId != null && cfg.hiddenInputs != null && cfg.hiddenInputs.contains(inputId);
    }

    static void setHidden(SharedPreferences prefs, String inputId, boolean hide) {
        if (prefs == null || inputId == null || inputId.trim().isEmpty()) return;
        java.util.Set<String> raw = prefs.getStringSet(Config.INPUT_HIDDEN_KEY, Collections.emptySet());
        java.util.Set<String> updated = raw == null ? new java.util.HashSet<>() : new java.util.HashSet<>(raw);
        if (hide) updated.add(inputId);
        else updated.remove(inputId);
        SharedPreferences.Editor editor = prefs.edit();
        if (updated.isEmpty()) editor.remove(Config.INPUT_HIDDEN_KEY);
        else editor.putStringSet(Config.INPUT_HIDDEN_KEY, updated);
        editor.apply();
    }

    /** Returns user image files available inside {@code files/banners/}, sorted alphabetically. */
    static List<File> listBannerFiles(Context ctx) {
        return CustomArt.listImages(ctx, CustomArt.BANNERS_DIR);
    }

    /** Formats a banner filename (e.g. "firetv.jpg", "tv-spain.jpg") into a clean display label. */
    static String formatBannerFileName(String filename, String importedFallback) {
        return formatBannerFileName(null, filename, importedFallback);
    }

    static String formatBannerFileName(Context ctx, String filename, String importedFallback) {
        if (filename == null || filename.trim().isEmpty()) return "";
        String clean = filename.trim();
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) clean = clean.substring(slash + 1);
        int dot = clean.lastIndexOf('.');
        String stem = dot > 0 ? clean.substring(0, dot) : clean;
        String lower = stem.toLowerCase(Locale.ROOT);
        if (lower.startsWith("input_") && importedFallback != null && !importedFallback.isEmpty()) {
            return importedFallback;
        }
        if ("tv-spain".equals(lower) || "tdt-spain".equals(lower) || "tv_spain".equals(lower)) {
            return ctx != null ? ctx.getString(R.string.input_banner_tdt_spain) : "TDT (Spain)";
        }
        String single = knownDeviceToken(lower);
        if (single != null) return single;

        String[] parts = stem.split("[-_]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            String token = knownDeviceToken(part.toLowerCase(Locale.ROOT));
            if (token != null) {
                sb.append(token);
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.length() > 0 ? sb.toString() : stem;
    }

    private static String knownDeviceToken(String lower) {
        switch (lower) {
            case "firetv": return "Fire TV";
            case "appletv": return "Apple TV";
            case "googletv": return "Google TV";
            case "androidtv": return "Android TV";
            case "tvbox": return "TV Box";
            case "tv": return "TV";
            case "tdt": return "TDT";
            case "pc": return "PC";
            case "ps4": return "PS4";
            case "ps5": return "PS5";
            case "hdmi": return "HDMI";
            case "dvd": return "DVD";
            case "bluray": return "Blu-ray";
            case "xbox": return "Xbox";
            case "playstation": return "PlayStation";
            case "chromecast": return "Chromecast";
            case "shield": return "Shield";
            case "roku": return "Roku";
            default: return null;
        }
    }

    static String safeSlug(String inputId) {
        return CustomArt.safeSlug(inputId, "input");
    }

    /**
     * Imports a user-picked image from {@code uri} into {@code files/banners/input_<slug>.png}, normalizing
     * full-bleed 16:9 banners cleanly and centering square or transparent icons on Onyx's dark-glass tile.
     */
    static String importBannerImage(Context ctx, String inputId, Uri uri) {
        if (inputId == null) return null;
        return CustomArt.importBannerImage(ctx, CustomArt.BANNERS_DIR, "input_" + safeSlug(inputId) + ".png", uri);
    }
}
