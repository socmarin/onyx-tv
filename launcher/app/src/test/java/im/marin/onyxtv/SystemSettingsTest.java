package im.marin.onyxtv;

import android.content.pm.ApplicationInfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemSettingsTest {
    @Test public void userAndUpdatedSystemAppsAreUninstallable() {
        assertTrue(SystemSettings.isUninstallableFlags(0));
        assertFalse(SystemSettings.isUninstallableFlags(ApplicationInfo.FLAG_SYSTEM));
        assertTrue(SystemSettings.isUninstallableFlags(
                ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP));
    }

    @Test public void ctsDummyAndEmptyNamesDoNotCountAsHomePicker() {
        assertFalse(SystemSettings.isRealHomePickerActivity(null, "com.example.Picker"));
        assertFalse(SystemSettings.isRealHomePickerActivity("", "com.example.Picker"));
        assertFalse(SystemSettings.isRealHomePickerActivity("com.amazon.tv.launcher", null));
        assertFalse(SystemSettings.isRealHomePickerActivity(
                "com.amazon.tv.settings", "com.amazon.tv.settings.CTSDummyActivity"));
        assertTrue(SystemSettings.isRealHomePickerActivity(
                "com.android.permissioncontroller", "com.android.permissioncontroller.role.ui.DefaultAppActivity"));
    }
}
