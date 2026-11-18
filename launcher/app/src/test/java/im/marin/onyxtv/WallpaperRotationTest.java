package im.marin.onyxtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

public class WallpaperRotationTest {

    private static File touch(File dir, String name, int bytes) throws IOException {
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[bytes]);
        }
        return f;
    }

    @Test
    public void imagesKeepsPicturesOnlySortedByName() throws IOException {
        File dir = Files.createTempDirectory("walls").toFile();
        touch(dir, "b.JPG", 10);
        touch(dir, "a.png", 10);
        touch(dir, "notes.txt", 10);
        touch(dir, "empty.jpg", 0);
        new File(dir, "folder.jpg").mkdir();
        List<File> images = WallpaperRotation.images(dir);
        assertEquals(Arrays.asList("a.png", "b.JPG"), names(images));
        assertTrue(WallpaperRotation.images(null).isEmpty());
        assertTrue(WallpaperRotation.images(new File(dir, "missing")).isEmpty());
    }

    @Test
    public void pickAvoidsThePreviousPictureWhenThereIsAnother() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        Random random = new Random(1);
        for (int i = 0; i < 20; i++) {
            assertNotEquals("a.jpg", WallpaperRotation.pick(images, "a.jpg", random).getName());
        }
        assertEquals("a.jpg", WallpaperRotation.pick(Collections.singletonList(new File("a.jpg")), "a.jpg", random).getName());
        assertNull(WallpaperRotation.pick(Collections.emptyList(), "", random));
    }

    @Test
    public void sequentialAdvancesInSortedOrderAndWraps() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"));
        assertEquals("b.jpg", WallpaperRotation.next(images, "a.jpg").getName());
        assertEquals("c.jpg", WallpaperRotation.next(images, "b.jpg").getName());
        assertEquals("a.jpg", WallpaperRotation.next(images, "c.jpg").getName());
        // A first run, or an image removed from the collection, starts at the first picture.
        assertEquals("a.jpg", WallpaperRotation.next(images, "").getName());
        assertEquals("a.jpg", WallpaperRotation.next(images, "gone.jpg").getName());
        assertEquals("only.jpg", WallpaperRotation.next(
                Collections.singletonList(new File("only.jpg")), "only.jpg").getName());
        assertNull(WallpaperRotation.next(Collections.emptyList(), ""));
    }

    @Test
    public void pickUsesTheRequestedOrder() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"));
        Random random = new Random(1);
        assertEquals("b.jpg", WallpaperRotation.pick(images, "a.jpg", WallpaperRotation.ORDER_SEQUENTIAL, random).getName());
        assertEquals("a.jpg", WallpaperRotation.pick(images, "c.jpg", WallpaperRotation.ORDER_SEQUENTIAL, random).getName());
        for (int i = 0; i < 20; i++) {
            assertNotEquals("a.jpg", WallpaperRotation.pick(
                    images, "a.jpg", WallpaperRotation.ORDER_SHUFFLE, random).getName());
        }
        // Unknown order keeps the historical shuffle rather than failing or blanking.
        assertNotEquals("a.jpg", WallpaperRotation.pick(images, "a.jpg", "random", random).getName());
    }

    @Test
    public void findsTheFirstPictureWhenThePreviousOneIsGone() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        assertEquals("a.jpg", WallpaperRotation.next(images, "deleted.jpg").getName());
    }

    @Test
    public void findMatchesByName() {
        List<File> images = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        assertEquals("b.jpg", WallpaperRotation.find(images, "b.jpg").getName());
        assertNull(WallpaperRotation.find(images, "c.jpg"));
    }

    @Test
    public void anOnTvPickReplacesTheRotation() {
        List<File> collection = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"));
        File single = new File("wallpaper.jpg");
        // No choice, or a picture that has gone: the theme's rotation decides.
        assertNull(WallpaperRotation.chosen(collection, single, null));
        assertNull(WallpaperRotation.chosen(collection, single, "gone.jpg"));
        assertNull(WallpaperRotation.chosen(collection, null, "wallpaper.jpg"));
        // None: no wallpaper at all.
        assertTrue(WallpaperRotation.chosen(collection, single, WallpaperRotation.CHOICE_NONE).isEmpty());
        assertTrue(WallpaperRotation.chosen(Collections.emptyList(), null, WallpaperRotation.CHOICE_NONE).isEmpty());
        // A picked picture is the only one shown, so the rotation can't move past it.
        assertEquals(Collections.singletonList("b.jpg"), names(WallpaperRotation.chosen(collection, single, "b.jpg")));
        assertEquals(Collections.singletonList("wallpaper.jpg"),
                names(WallpaperRotation.chosen(collection, single, "wallpaper.jpg")));
        // Picking again simply replaces the earlier pick.
        assertEquals(Collections.singletonList("c.jpg"), names(WallpaperRotation.chosen(collection, single, "c.jpg")));
    }

    @Test
    public void homeRebuildsWhenTheSettingsRevisionMoved() {
        assertFalse(Config.settingsChanged(0, 0));
        assertFalse(Config.settingsChanged(3, 3));
        assertTrue(Config.settingsChanged(0, 1));
        assertTrue(Config.settingsChanged(4, 0));   // settings cleared
    }

    @Test
    public void dueOnlyWithRotationOnAndTheIntervalPassed() {
        long minute = 60_000L;
        assertFalse(WallpaperRotation.due(0, 10 * minute, 0));             // rotation off
        assertTrue(WallpaperRotation.due(0, 10 * minute, 5 * minute));      // never changed yet
        assertFalse(WallpaperRotation.due(8 * minute, 10 * minute, 5 * minute));
        assertTrue(WallpaperRotation.due(5 * minute, 10 * minute, 5 * minute));
        assertTrue(WallpaperRotation.due(20 * minute, 10 * minute, 5 * minute));   // clock went back
    }

    @Test
    public void userWallpapersTakePrecedenceOverBuiltInsWithSameName() {
        List<File> user = Arrays.asList(new File("/user/christmas.jpg"), new File("/user/custom.jpg"));
        List<File> builtin = Arrays.asList(new File("/builtin/christmas.jpg"), new File("/builtin/autumn.jpg"));
        List<File> merged = Config.mergeWallpapers(user, builtin);
        assertEquals(Arrays.asList("christmas.jpg", "custom.jpg", "autumn.jpg"), names(merged));
        assertEquals("/user/christmas.jpg", merged.get(0).getPath());
    }

    @Test
    public void builtInWallpapersResolveLocalizedLabels() {
        assertEquals(R.string.wallpaper_christmas_trees, Config.wallpaperLabelRes("christmas_trees.jpg"));
        assertEquals(R.string.wallpaper_christmas_nativity, Config.wallpaperLabelRes("christmas_nativity.jpg"));
        assertEquals(R.string.wallpaper_christmas_tio_de_nadal, Config.wallpaperLabelRes("christmas_tio_de_nadal.jpg"));
        assertEquals(R.string.wallpaper_christmas_olentzero, Config.wallpaperLabelRes("christmas_olentzero.jpg"));
        assertEquals(R.string.wallpaper_halloween_darkness, Config.wallpaperLabelRes("halloween_darkness.jpg"));
        assertEquals(R.string.wallpaper_love_hearts, Config.wallpaperLabelRes("love_hearts.jpg"));
        assertEquals(R.string.wallpaper_carnival_mask, Config.wallpaperLabelRes("carnival_mask.jpg"));
        assertEquals(R.string.wallpaper_women_female_symbol, Config.wallpaperLabelRes("women_female_symbol.jpg"));
        assertEquals(R.string.wallpaper_women_mother, Config.wallpaperLabelRes("women_mother.jpg"));
        assertEquals(R.string.wallpaper_women_pregnant, Config.wallpaperLabelRes("women_pregnant.jpg"));
        assertEquals(R.string.wallpaper_women_breastfeeding, Config.wallpaperLabelRes("women_breastfeeding.jpg"));
        assertEquals(R.string.wallpaper_religion_catholic_procession,
                Config.wallpaperLabelRes("religion_catholic_procession.jpg"));
        assertEquals(R.string.wallpaper_children_balloons, Config.wallpaperLabelRes("children_balloons.jpg"));
        assertEquals(R.string.wallpaper_men_father, Config.wallpaperLabelRes("men_father.jpg"));
        assertEquals(R.string.wallpaper_christmas_merry, Config.wallpaperLabelRes("christmas_merry.jpg"));
        assertEquals(R.string.wallpaper_celebrations_birthday, Config.wallpaperLabelRes("celebrations_birthday.jpg"));
        assertEquals(R.string.wallpaper_diversity_agender, Config.wallpaperLabelRes("diversity_agender.jpg"));
        assertEquals(R.string.wallpaper_diversity_rainbow, Config.wallpaperLabelRes("diversity_rainbow.jpg"));
        assertEquals(R.string.wallpaper_diversity_intersex, Config.wallpaperLabelRes("diversity_intersex.jpg"));
        assertEquals(R.string.wallpaper_diversity_pansexual, Config.wallpaperLabelRes("diversity_pansexual.jpg"));
        assertEquals(R.string.wallpaper_diversity_trans, Config.wallpaperLabelRes("diversity_trans.jpg"));
        assertEquals(0, Config.wallpaperLabelRes("valentines_day_hearts.jpg"));
        assertEquals(0, Config.wallpaperLabelRes("custom.jpg"));
    }

    @Test
    public void localizedWallpapersResolveLanguageAndFallbackCleanly() {
        Set<String> available = new HashSet<>(Arrays.asList(
                "en.jpg", "es.jpg", "es-419.jpg", "pt.jpg", "pt-PT.jpg",
                "sr-Latn.jpg", "iw.jpg", "in.jpg", "zh-CN.jpg", "zh-TW.jpg"
        ));
        assertEquals("es.jpg", Config.resolveLocalizedWallpaperFile("es", Locale.US, available));
        assertEquals("es-419.jpg", Config.resolveLocalizedWallpaperFile("es-419", Locale.US, available));
        assertEquals("pt-PT.jpg", Config.resolveLocalizedWallpaperFile("pt-PT", Locale.US, available));
        assertEquals("sr-Latn.jpg", Config.resolveLocalizedWallpaperFile("sr-Latn", Locale.US, available));
        assertEquals("iw.jpg", Config.resolveLocalizedWallpaperFile("", Locale.forLanguageTag("he-IL"), available));
        assertEquals("in.jpg", Config.resolveLocalizedWallpaperFile("", Locale.forLanguageTag("id-ID"), available));
        assertEquals("zh-TW.jpg", Config.resolveLocalizedWallpaperFile("", Locale.forLanguageTag("zh-HK"), available));
        assertEquals("zh-CN.jpg", Config.resolveLocalizedWallpaperFile("", Locale.SIMPLIFIED_CHINESE, available));
        assertEquals("es.jpg", Config.resolveLocalizedWallpaperFile("", Locale.forLanguageTag("es-ES"), available));
        assertEquals("en.jpg", Config.resolveLocalizedWallpaperFile("xx", Locale.forLanguageTag("zz"), available));
        assertNull(Config.resolveLocalizedWallpaperFile("es", Locale.US, Collections.emptySet()));
    }

    private static List<String> names(List<File> files) {
        String[] out = new String[files.size()];
        for (int i = 0; i < out.length; i++) out[i] = files.get(i).getName();
        return Arrays.asList(out);
    }
}
