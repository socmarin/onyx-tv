package im.marin.onyxtv;

import android.content.Context;
import android.media.tv.TvInputInfo;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Philips (TP Vision) saves an input's name from its Sources menu as "Name&TYPE", the type picking the icon there
 * ("Xbox&GAME_CONSOLE", "Ordinateur&COMPUTER"), and an unnamed port as the text "null". Shown as is, the Inputs
 * menu read "Xbox&GAME_CONSOLE · HDMI 1" and "null · HDMI 4". This gives "🎮 Xbox" and no name; the TV's own
 * setting is left as it is.
 * Also localizes generic English hardware port labels reported by firmwares (e.g. "Composite", "Component",
 * "SCART", "Other") into the active Onyx language.
 */
final class InputNames {
    private static final Pattern COMPOSITE_LABEL =
            Pattern.compile("^composite(?:\\s+(?:video|av))?(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPONENT_LABEL =
            Pattern.compile("^component(?:\\s+video)?(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCART_LABEL =
            Pattern.compile("^scart(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTHER_LABEL =
            Pattern.compile("^(?:other|external)(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_VIDEO_LABEL =
            Pattern.compile("^video(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);

    static final class PortStrings {
        final String composite;
        final String component;
        final String scart;
        final String other;

        PortStrings(String composite, String component, String scart, String other) {
            this.composite = composite;
            this.component = component;
            this.scart = scart;
            this.other = other;
        }

        static PortStrings from(Context ctx) {
            return new PortStrings(
                    ctx.getString(R.string.input_composite),
                    ctx.getString(R.string.input_component),
                    ctx.getString(R.string.input_scart),
                    ctx.getString(R.string.input_other));
        }
    }

    private InputNames() {
    }

    /** Translates generic English port names (Composite, Component, SCART, Other) into the user's locale. */
    static String localizePortLabel(CharSequence rawLabel, int type, PortStrings strings) {
        String clean = rawLabel == null ? "" : rawLabel.toString().trim();
        if (clean.isEmpty()) return defaultLabelForType(type, strings);
        if (strings != null) {
            String mapped = matchGenericPort(clean, type, strings);
            if (mapped != null) return mapped;
        }
        if (type == TvInputInfo.TYPE_HDMI && !clean.toUpperCase(Locale.ROOT).contains("HDMI")) {
            return "HDMI " + clean;
        }
        return clean;
    }

    private static String matchGenericPort(String label, int type, PortStrings strings) {
        Matcher composite = COMPOSITE_LABEL.matcher(label);
        if (composite.matches()) return withIndex(strings.composite, composite.group(1));
        if (type == TvInputInfo.TYPE_COMPOSITE) {
            Matcher video = GENERIC_VIDEO_LABEL.matcher(label);
            if (video.matches()) return withIndex(strings.composite, video.group(1));
        }
        Matcher component = COMPONENT_LABEL.matcher(label);
        if (component.matches()) return withIndex(strings.component, component.group(1));
        Matcher scart = SCART_LABEL.matcher(label);
        if (scart.matches()) return withIndex(strings.scart, scart.group(1));
        if (type == TvInputInfo.TYPE_OTHER) {
            Matcher other = OTHER_LABEL.matcher(label);
            if (other.matches()) return withIndex(strings.other, other.group(1));
        }
        return null;
    }

    private static String withIndex(String base, String index) {
        if (base == null || base.isEmpty()) return index == null ? "" : index;
        return index == null || index.isEmpty() ? base : base + " " + index;
    }

    private static String defaultLabelForType(int type, PortStrings strings) {
        switch (type) {
            case TvInputInfo.TYPE_HDMI: return "HDMI";
            case TvInputInfo.TYPE_COMPOSITE: return strings != null ? strings.composite : "Composite";
            case TvInputInfo.TYPE_COMPONENT: return strings != null ? strings.component : "Component";
            case TvInputInfo.TYPE_SCART: return strings != null ? strings.scart : "SCART";
            case TvInputInfo.TYPE_SVIDEO: return "S-Video";
            case TvInputInfo.TYPE_VGA: return "VGA";
            case TvInputInfo.TYPE_DVI: return "DVI";
            case TvInputInfo.TYPE_DISPLAY_PORT: return "DisplayPort";
            case TvInputInfo.TYPE_OTHER: return strings != null ? strings.other : "";
            default: return "";
        }
    }

    static String tidy(CharSequence raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty() || s.equals("null")) return null;
        int amp = s.lastIndexOf('&');
        // Only a type in Philips' form (three or more capitals, digits or underscores) is taken off, so "Tom & Jerry" stays.
        if (amp < 0 || !s.substring(amp + 1).matches("[A-Z][A-Z0-9_]{2,}")) return s;
        String name = s.substring(0, amp).trim();
        String emoji = emoji(s.substring(amp + 1));
        if (name.isEmpty()) return emoji;
        return emoji == null ? name : emoji + " " + name;
    }

    private static String emoji(String type) {
        String t = type.toUpperCase(Locale.ROOT);
        if (t.contains("GAME")) return "🎮";                                                   // 🎮
        if (t.contains("COMPUTER") || t.equals("PC")) return "💻";                             // 💻
        if (t.contains("BLU") || t.contains("DVD") || t.contains("DISC")) return "📀";         // 📀
        if (t.contains("BOX") || t.contains("STB") || t.contains("SAT") || t.contains("CABLE")) return "📡"; // 📡
        if (t.contains("AUDIO") || t.contains("SOUND") || t.contains("AMP")) return "🔊";      // 🔊
        if (t.contains("CAM")) return "📷";                                                    // 📷
        return null;
    }
}
