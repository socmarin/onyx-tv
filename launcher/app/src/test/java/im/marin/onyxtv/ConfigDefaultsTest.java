package im.marin.onyxtv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigDefaultsTest {

    @Test
    public void parsesBackgroundColorAndRejectsMalformed() {
        assertEquals(0xFF0E0F12, Config.parseBgColor("#0E0F12", 0xFF000000));
        assertEquals(0xFF112233, Config.parseBgColor("#80112233", 0xFF000000));
        assertEquals(0xFF0E0F12, Config.parseBgColor("not-a-color", 0xFF0E0F12));
        assertEquals(0xFF0E0F12, Config.parseBgColor("", 0xFF0E0F12));
    }

    @Test
    public void parsesFocusScaleWithinValidBounds() {
        assertEquals(1.25f, Config.parseFocusScale("1.25", 1.1f), 0.0001f);
        assertEquals(1.0f, Config.parseFocusScale("1.0", 1.25f), 0.0001f);
        assertEquals(1.25f, Config.parseFocusScale("0.8", 1.25f), 0.0001f);
        assertEquals(1.25f, Config.parseFocusScale("3.5", 1.25f), 0.0001f);
        assertEquals(1.25f, Config.parseFocusScale("invalid", 1.25f), 0.0001f);
    }

    @Test
    public void resolvesFireOsAutoAndUserToggle() {
        assertEquals("com.amazon.tv.launcher", Config.resolveFireOsStockHome(true, null, ""));
        assertTrue(Config.resolveFireOsRestore(true, null, false));
        assertEquals("", Config.resolveFireOsStockHome(true, Boolean.FALSE, "com.amazon.tv.launcher"));
        assertFalse(Config.resolveFireOsRestore(true, Boolean.FALSE, true));
        assertEquals("", Config.resolveFireOsStockHome(false, Boolean.TRUE, ""));
        assertFalse(Config.resolveFireOsRestore(false, Boolean.TRUE, false));
    }

    @Test
    public void resolvesPowerOnHomeModeAndDisablesOnFireOs() {
        assertEquals(Config.POWER_ON_OFF, Config.resolvePowerOnHomeMode(true, Config.POWER_ON_ALWAYS, true, true));
        assertEquals(Config.POWER_ON_OFF, Config.resolvePowerOnHomeMode(false, null, false, false));
        assertEquals(Config.POWER_ON_LIVE_TV, Config.resolvePowerOnHomeMode(false, null, true, false));
        assertEquals(Config.POWER_ON_ALWAYS, Config.resolvePowerOnHomeMode(false, null, false, true));
        assertEquals(Config.POWER_ON_ALWAYS, Config.resolvePowerOnHomeMode(false, Config.POWER_ON_ALWAYS, false, false));
        assertEquals(Config.POWER_ON_OFF, Config.resolvePowerOnHomeMode(false, Config.POWER_ON_OFF, true, true));
    }

    @Test
    public void resolvesAutoLiveTvForXiaomiFireOsAndRespectsOverrides() {
        assertEquals("com.vewd.core.tvapp",
                Config.resolveAutoLiveTvPackage(false, "com.mediatek.wwtv.tvcenter", false, true, true));
        assertEquals("com.mediatek.wwtv.tvcenter",
                Config.resolveAutoLiveTvPackage(false, "com.mediatek.wwtv.tvcenter", true, true, true));
        assertEquals("custom.tv.pkg",
                Config.resolveAutoLiveTvPackage(true, "custom.tv.pkg", false, true, true));

        assertEquals("com.vewd.core.tis/.TerrestrialHbbTvInputService",
                Config.resolveAutoLiveTvInputId(false, "", "com.vewd.core.tvapp", true));
        assertEquals("custom/input",
                Config.resolveAutoLiveTvInputId(true, "custom/input", "com.vewd.core.tvapp", true));
        assertEquals("",
                Config.resolveAutoLiveTvInputId(false, "", "com.mediatek.wwtv.tvcenter", true));
    }

    @Test
    public void preservesOnTvChoicesForPlainConfigJsonWithoutThemeRevision() {
        assertFalse(Config.shouldClearOnTvChoices(null, "absent:absent", "absent"));
        assertFalse(Config.shouldClearOnTvChoices("absent:absent", "54@100:absent", "absent"));
        assertFalse(Config.shouldClearOnTvChoices("54@100:absent", "88@200:absent", "absent"));
        assertTrue(Config.shouldClearOnTvChoices(null, "54@100:rev1", "rev1"));
        assertTrue(Config.shouldClearOnTvChoices("absent:absent", "54@100:rev1", "rev1"));
        assertTrue(Config.shouldClearOnTvChoices("54@100:rev1", "absent:rev2", "rev2"));
        assertFalse(Config.shouldClearOnTvChoices("54@100:rev1", "54@100:rev1", "rev1"));
    }
}
