package im.marin.onyxtv;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
}
