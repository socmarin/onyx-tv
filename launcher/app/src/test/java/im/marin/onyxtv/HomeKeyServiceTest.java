package im.marin.onyxtv;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeKeyServiceTest {
    private static final long SOON = 45_000L;
    private static final String MY_PKG = "im.marin.onyxtv";
    private static final String FIRE_HOME_PKG = "com.amazon.tv.launcher";
    private static final String FIRE_HOME_CLS = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static final Set<String> FIRE_HOME_CLASSES = Collections.singleton(FIRE_HOME_CLS);

    @Test public void aTapIsAShortPress() {
        assertTrue(HomeKeyService.isShortPress(1_000, 1_120, 400));
    }

    @Test public void holdingPastTheTimeoutIsALongPress() {
        assertFalse(HomeKeyService.isShortPress(1_000, 1_400, 400));
        assertFalse(HomeKeyService.isShortPress(1_000, 3_000, 400));
    }

    @Test public void firstBindOfANewBootProbes() {
        assertTrue(HomeKeyService.firstBindAfterBoot(12, 11, SOON));
        assertTrue(HomeKeyService.firstBindAfterBoot(1, -1, SOON));
    }

    @Test public void aRebindInTheSameBootDoesNot() {
        assertFalse(HomeKeyService.firstBindAfterBoot(12, 12, SOON));
    }

    @Test public void aBindLongAfterBootDoesNot() {
        assertFalse(HomeKeyService.firstBindAfterBoot(12, 11, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void noBootCountMeansNoProbe() {
        assertFalse(HomeKeyService.firstBindAfterBoot(-1, -1, SOON));
    }

    private static final String TV = "com.mediatek.wwtv.tvcenter";

    @Test public void liveTvBeforeAnyKeyGoesHome() {
        assertTrue(HomeKeyService.powerOnRedirect(TV, TV, SOON, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void otherAppsAndInputsAreLeftAlone() {
        assertFalse(HomeKeyService.powerOnRedirect(TV, "com.netflix.ninja", SOON, HomeKeyService.BOOT_WINDOW_MS));
        assertFalse(HomeKeyService.powerOnRedirect(TV, null, SOON, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void afterAKeyOrTheWindowLiveTvStays() {
        assertFalse(HomeKeyService.powerOnRedirect(TV, TV, SOON, 0));
        assertFalse(HomeKeyService.powerOnRedirect(TV, TV, HomeKeyService.BOOT_WINDOW_MS, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void offMeansNothing() {
        assertFalse(HomeKeyService.powerOnRedirect("", TV, SOON, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void alwaysGoesHomeAfterScreenOnUntilAKey() {
        assertTrue(HomeKeyService.stillArmed(SOON, SOON + HomeKeyService.SCREEN_ON_WINDOW_MS));
        assertFalse(HomeKeyService.stillArmed(SOON, 0));
    }

    @Test public void wakeOnFireHomeRedirectsWhenPreviouslyInOnyx() {
        assertEquals(
                HomeKeyService.WakeAction.REDIRECT,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, FIRE_HOME_PKG,
                        MY_PKG, MY_PKG + ".MainActivity", FIRE_HOME_CLASSES));
    }

    @Test public void wakeOnFireHomeRedirectsWhenPreviouslyInHomeActivity() {
        assertEquals(
                HomeKeyService.WakeAction.REDIRECT,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, FIRE_HOME_PKG,
                        FIRE_HOME_PKG, FIRE_HOME_CLS, FIRE_HOME_CLASSES));
    }

    @Test public void wakeOnFireSettingsProbesInsteadOfRedirecting() {
        assertEquals(
                HomeKeyService.WakeAction.PROBE,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, FIRE_HOME_PKG,
                        FIRE_HOME_PKG, "com.amazon.tv.launcher.settings.SettingsActivity", FIRE_HOME_CLASSES));
    }

    @Test public void wakeOnFireLauncherWithUnknownHistoryProbesFirst() {
        assertEquals(
                HomeKeyService.WakeAction.PROBE,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, FIRE_HOME_PKG,
                        "", "", FIRE_HOME_CLASSES));
    }

    @Test public void wakeLeavesNormalAppsAndLiveTvAlone() {
        assertEquals(
                HomeKeyService.WakeAction.IGNORE,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, "com.example.videoapp",
                        MY_PKG, MY_PKG + ".MainActivity", FIRE_HOME_CLASSES));
        assertEquals(
                HomeKeyService.WakeAction.IGNORE,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, "com.amazon.tv.livetv",
                        "com.amazon.tv.livetv", "com.amazon.tv.livetv.LiveTvActivity", FIRE_HOME_CLASSES));
    }

    @Test public void wakeLeavesFocusedOnyxAndDisabledRedirectAlone() {
        assertEquals(
                HomeKeyService.WakeAction.IGNORE,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, true, FIRE_HOME_PKG,
                        MY_PKG, MY_PKG + ".MainActivity", FIRE_HOME_CLASSES));
        assertEquals(
                HomeKeyService.WakeAction.IGNORE,
                HomeKeyService.classifyWakeWindow(
                        "", MY_PKG, false, FIRE_HOME_PKG,
                        MY_PKG, MY_PKG + ".MainActivity", Collections.emptySet()));
    }

    @Test public void wakeRetriesWhileWindowIsSettling() {
        assertEquals(
                HomeKeyService.WakeAction.RETRY,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, "",
                        MY_PKG, MY_PKG + ".MainActivity", FIRE_HOME_CLASSES));
        assertEquals(
                HomeKeyService.WakeAction.RETRY,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, "com.android.systemui",
                        MY_PKG, MY_PKG + ".MainActivity", FIRE_HOME_CLASSES));
        assertEquals(
                HomeKeyService.WakeAction.RETRY,
                HomeKeyService.classifyWakeWindow(
                        FIRE_HOME_PKG, MY_PKG, false, MY_PKG,
                        MY_PKG, MY_PKG + ".MainActivity", FIRE_HOME_CLASSES));
    }

    @Test public void stockHomeClassMatchesExplicitAndHomeActivityNames() {
        assertTrue(HomeKeyService.isStockHomeClass(FIRE_HOME_CLASSES, FIRE_HOME_CLS));
        assertTrue(HomeKeyService.isStockHomeClass(Collections.emptySet(), "com.example.HomeActivity"));
        assertFalse(HomeKeyService.isStockHomeClass(FIRE_HOME_CLASSES, "com.amazon.tv.launcher.settings.SettingsActivity"));
    }

    @Test public void bluetoothNameSyncsOnlyWhenDifferentAndOutsideCooldown() {
        assertTrue(HomeKeyService.shouldSyncBluetoothName("Living Room TV", "Living Room", 10_000L, -1L));
        assertFalse(HomeKeyService.shouldSyncBluetoothName("Living Room TV", "Living Room TV", 10_000L, -1L));
        assertFalse(HomeKeyService.shouldSyncBluetoothName("", "Living Room", 10_000L, -1L));
        assertFalse(HomeKeyService.shouldSyncBluetoothName(null, "Living Room", 10_000L, -1L));
        assertFalse(HomeKeyService.shouldSyncBluetoothName(
                "Living Room TV", "Living Room", 12_000L, 10_000L));
        assertTrue(HomeKeyService.shouldSyncBluetoothName(
                "Living Room TV", "Living Room", 10_000L + HomeKeyService.BT_NAME_SYNC_COOLDOWN_MS, 10_000L));
    }
}
