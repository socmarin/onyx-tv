package im.marin.onyxtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class InputCustomTest {

    @Test
    public void jsonMapRoundTripsAndIgnoresMalformedOrEmptyEntries() {
        Map<String, String> source = new HashMap<>();
        source.put("com.droidlogic.tvinput/.Hdmi2InputService/HW6", "Xbox");
        source.put("com.vewd.core.tis/.TerrestrialHbbTvInputService", "TDT");
        source.put("empty", "   ");

        String encoded = InputCustom.encodeMap(source);
        Map<String, String> parsed = InputCustom.parseMap(encoded);
        assertEquals(2, parsed.size());
        assertEquals("Xbox", parsed.get("com.droidlogic.tvinput/.Hdmi2InputService/HW6"));
        assertEquals("TDT", parsed.get("com.vewd.core.tis/.TerrestrialHbbTvInputService"));

        assertTrue(InputCustom.parseMap("malformed_without_tab").isEmpty());
        assertTrue(InputCustom.parseMap("").isEmpty());
        assertEquals("", InputCustom.encodeMap(null));
    }

    @Test
    public void presetRecognitionMatchesRegisteredIcons() {
        assertTrue(InputCustom.isPreset(InputCustom.PRESET_CONSOLE));
        assertTrue(InputCustom.isPreset(InputCustom.PRESET_TV));
        assertFalse(InputCustom.isPreset("xbox.jpg"));
        assertFalse(InputCustom.isPreset(null));

        assertEquals(R.drawable.ic_input_console, InputCustom.presetIconRes(InputCustom.PRESET_CONSOLE));
        assertEquals(R.drawable.ic_livetv, InputCustom.presetIconRes(InputCustom.PRESET_TV));
        assertEquals(0, InputCustom.presetIconRes("preset:unknown"));
        assertEquals(0, InputCustom.presetIconRes("xbox.jpg"));
    }

    @Test
    public void safeSlugIsFilenameSafeAndDistinctPerInput() {
        String s1 = InputCustom.safeSlug("com.droidlogic.tvinput/.services.Hdmi1InputService/HW5");
        String s2 = InputCustom.safeSlug("com.droidlogic.tvinput/.services.Hdmi2InputService/HW6");
        assertTrue(s1.matches("[a-z0-9_]+"));
        assertTrue(s2.matches("[a-z0-9_]+"));
        assertNotEquals(s1, s2);
    }

    @Test
    public void bannerFileNamesFormatCleanlyForDisplay() {
        assertEquals("Fire TV", InputCustom.formatBannerFileName("firetv.jpg", "Custom image"));
        assertEquals("TDT (Spain)", InputCustom.formatBannerFileName("tv-spain.jpg", "Custom image"));
        assertEquals("Xbox", InputCustom.formatBannerFileName("xbox.jpg", "Custom image"));
        assertEquals("PS5", InputCustom.formatBannerFileName("ps5.png", "Custom image"));
        assertEquals("Custom image", InputCustom.formatBannerFileName("input_hdmi_123.png", "Custom image"));
    }
}
