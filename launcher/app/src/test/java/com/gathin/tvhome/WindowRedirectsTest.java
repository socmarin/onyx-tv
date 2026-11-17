package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WindowRedirectsTest {
    @Test public void emptyInputProducesEmptyRules() {
        WindowRedirects empty = WindowRedirects.parse(Collections.emptyList());
        assertTrue(empty.isEmpty());
        assertNull(empty.matchWindow("com.example.music", "com.example.MainActivity"));
        assertNull(empty.matchDetailsText("Example Music"));
    }

    @Test public void matchesActivityBeforePackageAndNormalizesShortClassNames() {
        WindowRedirects rules = WindowRedirects.parse(Arrays.asList(
                "com.example.app=com.example.fallback",
                "com.example.app/.SpecificActivity=library",
                "com.example.other=com.example.target"
        ));
        assertFalse(rules.isEmpty());
        assertTrue(rules.hasWindowRules());
        assertFalse(rules.hasDetailsRules());
        assertEquals("library", rules.matchWindow("com.example.app", "com.example.app.SpecificActivity"));
        assertEquals("com.example.fallback", rules.matchWindow("com.example.app", "com.example.app.OtherActivity"));
        assertEquals("com.example.target", rules.matchWindow("com.example.other", ""));
        assertNull(rules.matchWindow("com.example.unknown", "MainActivity"));
    }

    @Test public void matchesDetailsCaseInsensitivelyAndSkipsMalformedEntries() {
        WindowRedirects rules = WindowRedirects.parse(Arrays.asList(
                null,
                "missing_equals",
                "=missing_key",
                "com.example.empty=",
                "details:  ",
                "details:Example Music=com.example.player"
        ));
        assertFalse(rules.hasWindowRules());
        assertTrue(rules.hasDetailsRules());
        assertEquals("com.example.player", rules.matchDetailsText("Install example music now"));
        assertNull(rules.matchDetailsText("Install something else"));
    }
}
