package im.marin.onyxtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class LauncherLocaleTest {
    @Test
    public void plainTagsBecomeLocales() {
        assertEquals("vi", LauncherLocale.parse("vi").getLanguage());
        assertEquals("BR", LauncherLocale.parse("pt-BR").getCountry());
        assertEquals("fil", LauncherLocale.parse(" fil ").getLanguage());
    }

    @Test
    public void tagsWithScriptsAndExtensionsBecomeLocales() {
        assertEquals("Latn", LauncherLocale.parse("sr-Latn").getScript());
        assertEquals("ar", LauncherLocale.parse("ar-EG-u-nu-latn").getLanguage());
        assertEquals("latn", LauncherLocale.parse("ar-EG-u-nu-latn").getUnicodeLocaleType("nu"));
    }

    @Test
    public void emptyOrMalformedTagsFollowTheTv() {
        assertNull(LauncherLocale.parse(""));
        assertNull(LauncherLocale.parse(null));
        assertNull(LauncherLocale.parse("Vietnamese"));
        assertNull(LauncherLocale.parse("vi_VN"));
        assertNull(LauncherLocale.parse("../vi"));
        assertNull(LauncherLocale.parse("vi-"));
        assertNull(LauncherLocale.parse("vi--VN"));
    }

    @Test
    public void supportedLanguagesAreUniqueAndValid() {
        List<LauncherLocale.LanguageOption> options = LauncherLocale.supportedLanguages();
        assertEquals(33, options.size());
        Set<String> seen = new HashSet<>();
        for (LauncherLocale.LanguageOption option : options) {
            assertTrue("duplicate tag: " + option.tag, seen.add(option.tag));
            assertNotNull("invalid tag: " + option.tag, LauncherLocale.parse(option.tag));
            assertFalse("empty label: " + option.tag, option.label.trim().isEmpty());
        }
        assertTrue(seen.contains("ca"));
        assertTrue(seen.contains("eu"));
        assertTrue(seen.contains("gl"));
    }

    @Test
    public void nativeLabelReturnsAutonymsAndNullWhenEmpty() {
        assertNull(LauncherLocale.nativeLabel(null));
        assertNull(LauncherLocale.nativeLabel(""));
        assertNull(LauncherLocale.nativeLabel("   "));
        assertEquals("Català", LauncherLocale.nativeLabel("ca"));
        assertEquals("Euskara", LauncherLocale.nativeLabel("eu"));
        assertEquals("Galego", LauncherLocale.nativeLabel("gl"));
        assertEquals("Español", LauncherLocale.nativeLabel("ES"));
    }
}
