package im.marin.onyxtv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AppCustomTest {

    @Test
    public void safeSlugIsFilenameSafeAndDistinctPerPackage() {
        String s1 = AppCustom.safeSlug("com.google.android.youtube.tv");
        String s2 = AppCustom.safeSlug("com.netflix.ninja");
        assertTrue(s1.matches("[a-z0-9_]+"));
        assertTrue(s2.matches("[a-z0-9_]+"));
        assertNotEquals(s1, s2);
    }

    @Test
    public void formatArtNameRecognizesImportedAppArtAndNamedFiles() {
        assertEquals("Custom image", AppCustom.formatArtName("app_icon_netflix_abc123.png", "Custom image"));
        assertEquals("Custom image", AppCustom.formatArtName("app_banner_youtube_def456.png", "Custom image"));
        assertEquals("Xbox", AppCustom.formatArtName("xbox.png", "Custom image"));
    }
}
