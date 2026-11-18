package im.marin.onyxtv;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CustomArtTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void resolvesExactFileAndExtensionlessNames() throws IOException {
        File base = temp.newFolder("files");
        File banners = new File(base, CustomArt.BANNERS_DIR);
        banners.mkdirs();
        File tv = new File(banners, "tv-spain.jpg");
        try (FileOutputStream out = new FileOutputStream(tv)) {
            out.write(new byte[] { 1, 2, 3 });
        }
        assertEquals(tv, CustomArt.resolveFile(base, CustomArt.BANNERS_DIR, "tv-spain.jpg"));
        assertEquals(tv, CustomArt.resolveFile(base, CustomArt.BANNERS_DIR, "tv-spain"));
        assertNull(CustomArt.resolveFile(base, CustomArt.BANNERS_DIR, "missing.jpg"));
    }

    @Test public void rejectsPathTraversalAndEmptyNames() throws IOException {
        File base = temp.newFolder("files");
        new File(base, CustomArt.BANNERS_DIR).mkdirs();
        assertNull(CustomArt.resolveFile(base, CustomArt.BANNERS_DIR, ""));
        assertNull(CustomArt.resolveFile(base, CustomArt.BANNERS_DIR, "../wallpaper.jpg"));
        assertNull(CustomArt.resolveFile(base, CustomArt.BANNERS_DIR, "sub/tv-spain.jpg"));
        assertNull(CustomArt.resolveFile(null, CustomArt.BANNERS_DIR, "tv-spain.jpg"));
    }

    @Test public void computesPowerOfTwoSampleSize() {
        assertEquals(1, CustomArt.sampleSize(640, 360, 640, 360));
        assertEquals(1, CustomArt.sampleSize(1024, 571, 640, 360));
        assertEquals(2, CustomArt.sampleSize(1280, 720, 640, 360));
        assertEquals(4, CustomArt.sampleSize(1024, 1024, 256, 256));
    }

    @Test public void mapAndListRoundTripEscapesSpecialCharacters() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("com.example.one", "App\tOne");
        map.put("com.example.two", "Line1\nLine2\\End");
        String encodedMap = CustomArt.encodeMap(map);
        Map<String, String> parsedMap = CustomArt.parseMap(encodedMap);
        assertEquals(map, parsedMap);

        List<String> list = Arrays.asList("scan:485:com.netflix.ninja", "hold:KEYCODE_F1:input:hw\\1\ntab\tval");
        String encodedList = CustomArt.encodeList(list);
        assertEquals(list, CustomArt.parseList(encodedList));
        assertTrue(CustomArt.parseList("").isEmpty());
    }

    @Test public void bannerTargetMatchesDocumented960x540Spec() {
        assertEquals(960, CustomArt.BANNER_TARGET_WIDTH);
        assertEquals(540, CustomArt.BANNER_TARGET_HEIGHT);
        assertEquals(256, CustomArt.ICON_TARGET_SIZE);
    }
}
