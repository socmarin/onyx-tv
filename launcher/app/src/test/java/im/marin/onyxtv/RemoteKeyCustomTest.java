package im.marin.onyxtv;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteKeyCustomTest {

    @Test
    public void formatAndParseTapAndHoldShortcutsWithColons() {
        String tap = RemoteKeyCustom.formatEntry(false, "scan:485", "com.netflix.ninja");
        assertEquals("scan:485:com.netflix.ninja", tap);
        RemoteKeyCustom.Shortcut parsedTap = RemoteKeyCustom.parseEntry(tap);
        assertNotNull(parsedTap);
        assertFalse(parsedTap.hold);
        assertEquals("scan:485", parsedTap.keyToken);
        assertEquals("com.netflix.ninja", parsedTap.target);

        String holdInput = RemoteKeyCustom.formatEntry(true, "KEYCODE_PROG_RED",
                "input:com.droidlogic.tvinput/.Hdmi1Service/HW5");
        assertEquals("hold:KEYCODE_PROG_RED:input:com.droidlogic.tvinput/.Hdmi1Service/HW5", holdInput);
        RemoteKeyCustom.Shortcut parsedHold = RemoteKeyCustom.parseEntry(holdInput);
        assertNotNull(parsedHold);
        assertTrue(parsedHold.hold);
        assertEquals("KEYCODE_PROG_RED", parsedHold.keyToken);
        assertEquals("input:com.droidlogic.tvinput/.Hdmi1Service/HW5", parsedHold.target);

        assertNull(RemoteKeyCustom.parseEntry("front:KEYCODE_F1:com.foo"));
        assertNull(RemoteKeyCustom.parseEntry("malformed"));
    }

    @Test
    public void capturedKeyPrefersScancodeOverKeycode() {
        assertEquals("scan:490", RemoteKeyCustom.formatCapturedKeyToken(196, 490));
        assertEquals("", RemoteKeyCustom.formatCapturedKeyToken(0, 0));
        assertEquals("4062", RemoteKeyCustom.formatCapturedKeyToken(4062, 0));
        assertEquals("KEYCODE_PROG_RED", RemoteKeyCustom.formatCapturedKeyToken(
                KeyEvent.KEYCODE_PROG_RED, 0, c -> "KEYCODE_PROG_RED"));
    }

    @Test
    public void navigationAndVolumeKeysRemainReserved() {
        assertTrue(RemoteKeyCustom.isReservedNavigationKey(KeyEvent.KEYCODE_DPAD_UP));
        assertTrue(RemoteKeyCustom.isReservedNavigationKey(KeyEvent.KEYCODE_DPAD_CENTER));
        assertTrue(RemoteKeyCustom.isReservedNavigationKey(KeyEvent.KEYCODE_BACK));
        assertTrue(RemoteKeyCustom.isReservedNavigationKey(KeyEvent.KEYCODE_HOME));
        assertTrue(RemoteKeyCustom.isReservedNavigationKey(KeyEvent.KEYCODE_VOLUME_UP));
        assertFalse(RemoteKeyCustom.isReservedNavigationKey(KeyEvent.KEYCODE_PROG_RED));
        assertFalse(RemoteKeyCustom.isReservedNavigationKey(4062));
    }

    @Test
    public void prettyKeyNamesFormatCleanly() {
        assertEquals("Prog Red", RemoteKeyCustom.prettyKeyName(null, "KEYCODE_PROG_RED"));
        assertEquals("Scan 485", RemoteKeyCustom.prettyKeyName(null, "scan:485"));
        assertEquals("Amazon Music", RemoteKeyCustom.prettyKeyName(null, "window:details:Amazon Music"));
        assertEquals("com.netflix.ninja", RemoteKeyCustom.prettyKeyName(null, "window:com.netflix.ninja"));
    }

    @Test
    public void windowShortcutsParseFormatAndConvertToRedirectRule() {
        String entry = RemoteKeyCustom.formatEntry(false, "window:details:Amazon Music", "com.movistarplus.androidtv");
        assertEquals("window:details:Amazon Music=com.movistarplus.androidtv", entry);
        RemoteKeyCustom.Shortcut parsed = RemoteKeyCustom.parseEntry(entry);
        assertNotNull(parsed);
        assertFalse(parsed.hold);
        assertTrue(RemoteKeyCustom.isWindowToken(parsed.keyToken));
        assertEquals("window:details:Amazon Music", parsed.keyToken);
        assertEquals("com.movistarplus.androidtv", parsed.target);
        assertEquals("details:Amazon Music=com.movistarplus.androidtv", RemoteKeyCustom.toWindowRedirectRule(entry));
        assertNull(RemoteKeyCustom.toWindowRedirectRule("scan:485:com.netflix.ninja"));
    }
}
