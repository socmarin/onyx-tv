package im.marin.onyxtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.media.tv.TvInputInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class TvInputTilesTest {

    private static TvInputTiles.RawInput raw(String id, String parentId, int type,
                                             boolean passthrough, boolean hidden,
                                             boolean systemApp, String label, String customLabel) {
        return new TvInputTiles.RawInput(id, parentId, type, passthrough, hidden,
                systemApp, label, customLabel);
    }

    @Test
    public void streamingBoxesWithoutHardwareInputsProduceNone() {
        List<TvInputTiles.RawInput> inputs = Arrays.asList(
                raw("com.amazon.franktvinput/.FrankTvInputService", null, TvInputInfo.TYPE_TUNER, false, false, true, "Live", ""),
                raw("com.rtve.androidtv/.LiveTV", null, TvInputInfo.TYPE_TUNER, false, false, false, "RTVE", ""),
                raw("tv.pluto/.TifService", null, TvInputInfo.TYPE_TUNER, false, false, false, "Pluto", ""));
        List<TvInputTiles.TileSpec> tiles = TvInputTiles.buildTiles(
                inputs, Collections.emptyList(), "", Collections.emptySet(), "TDT");
        assertTrue(tiles.isEmpty());
    }

    @Test
    public void discoversHdmiBeforeAvAndMergesCecDevices() {
        List<TvInputTiles.RawInput> inputs = Arrays.asList(
                raw("av/HW1", null, TvInputInfo.TYPE_COMPOSITE, true, false, true, "AV", ""),
                raw("hdmi/HW5", null, TvInputInfo.TYPE_HDMI, true, false, true, "HDMI 1", ""),
                raw("hdmi/HW6", null, TvInputInfo.TYPE_HDMI, true, false, true, "HDMI 2", ""),
                raw("hdmi/HW6/cec1", "hdmi/HW6", TvInputInfo.TYPE_HDMI, true, false, true, "Fire TV", ""));
        List<TvInputTiles.TileSpec> tiles = TvInputTiles.buildTiles(
                inputs, Collections.emptyList(), "", Collections.emptySet(), "TDT");

        assertEquals(3, tiles.size());
        assertEquals("hdmi/HW5", tiles.get(0).inputId);
        assertEquals("HDMI 1", tiles.get(0).label);
        assertEquals(TvInputTiles.KIND_HDMI, tiles.get(0).kind);

        assertEquals("hdmi/HW6/cec1", tiles.get(1).inputId);
        assertEquals("Fire TV · HDMI 2", tiles.get(1).label);

        assertEquals("av/HW1", tiles.get(2).inputId);
        assertEquals("AV", tiles.get(2).label);
        assertEquals(TvInputTiles.KIND_AV, tiles.get(2).kind);
    }

    @Test
    public void excludesDisabledHiddenAndAlreadyMappedInputs() {
        List<String> sources = Collections.singletonList("HW8=-");
        Set<String> mapped = new HashSet<>(Collections.singletonList("hdmi/HW6"));
        List<TvInputTiles.RawInput> inputs = Arrays.asList(
                raw("hdmi/HW5", null, TvInputInfo.TYPE_HDMI, true, false, true, "HDMI 1", ""),
                raw("hdmi/HW6", null, TvInputInfo.TYPE_HDMI, true, false, true, "HDMI 2", ""),
                raw("hdmi/HW7", null, TvInputInfo.TYPE_HDMI, true, true, true, "HDMI 3", ""),
                raw("hdmi/HW8", null, TvInputInfo.TYPE_HDMI, true, false, true, "HDMI 4", ""));

        List<TvInputTiles.TileSpec> tiles = TvInputTiles.buildTiles(
                inputs, sources, "", mapped, "TDT");
        assertEquals(1, tiles.size());
        assertEquals("hdmi/HW5", tiles.get(0).inputId);
    }

    @Test
    public void includesSystemTunerFirstInInputsPicker() {
        List<TvInputTiles.RawInput> inputs = Arrays.asList(
                raw("tuner/AnalogInputService/HW1", null, TvInputInfo.TYPE_TUNER, false, false, true, "ATV", ""),
                raw("tuner/TunerInputService/HW0", null, TvInputInfo.TYPE_TUNER, false, false, true, "DTV", ""),
                raw("hdmi/HW5", null, TvInputInfo.TYPE_HDMI, true, false, true, "HDMI 1", ""));

        List<TvInputTiles.TileSpec> tiles = TvInputTiles.buildTiles(
                inputs, Collections.emptyList(), "", Collections.emptySet(), "TDT");
        assertEquals(2, tiles.size());
        assertEquals("tuner/TunerInputService/HW0", tiles.get(0).inputId);
        assertEquals("TDT", tiles.get(0).label);
        assertEquals(TvInputTiles.KIND_TUNER, tiles.get(0).kind);
        assertEquals("hdmi/HW5", tiles.get(1).inputId);
    }

    @Test
    public void inputPackageRoundTripsSafely() {
        assertTrue(TvInputTiles.isInputPackage("input:hdmi/HW5"));
        assertFalse(TvInputTiles.isInputPackage("input:"));
        assertFalse(TvInputTiles.isInputPackage("com.netflix.ninja"));
        assertEquals("hdmi/HW5", TvInputTiles.inputIdFromPackage("input:hdmi/HW5"));
        assertNull(TvInputTiles.inputIdFromPackage("input:"));
    }

    @Test
    public void filtersToExplicitSelectionWhenProvided() {
        List<TvInputTiles.TileSpec> all = Arrays.asList(
                new TvInputTiles.TileSpec("hdmi/HW5", "HDMI 1", "HDMI 1", TvInputTiles.KIND_HDMI),
                new TvInputTiles.TileSpec("hdmi/HW6", "HDMI 2", "HDMI 2", TvInputTiles.KIND_HDMI),
                new TvInputTiles.TileSpec("av/HW1", "AV", "AV", TvInputTiles.KIND_AV));
        assertEquals(3, TvInputTiles.filterSelected(all, null).size());
        assertTrue(TvInputTiles.filterSelected(all, Collections.emptySet()).isEmpty());
        Set<String> pick = new HashSet<>(Arrays.asList("hdmi/HW6", "av/HW1"));
        List<TvInputTiles.TileSpec> filtered = TvInputTiles.filterSelected(all, pick);
        assertEquals(2, filtered.size());
        assertEquals("hdmi/HW6", filtered.get(0).inputId);
        assertEquals("av/HW1", filtered.get(1).inputId);
        assertFalse(TvInputTiles.hasTuner(filtered));
        assertTrue(TvInputTiles.hasTuner(Collections.singletonList(
                new TvInputTiles.TileSpec("tuner/HW0", "TDT", "TDT", TvInputTiles.KIND_TUNER))));
    }

    @Test
    public void ownedLiveTvPackageAppliesOnlyWhenPhysicalTunerExists() {
        List<TvInputTiles.TileSpec> hdmiOnly = Collections.singletonList(
                new TvInputTiles.TileSpec("hdmi/HW5", "HDMI 1", "HDMI 1", TvInputTiles.KIND_HDMI));
        List<TvInputTiles.TileSpec> withTuner = Arrays.asList(
                new TvInputTiles.TileSpec("tuner/HW0", "TDT", "TDT", TvInputTiles.KIND_TUNER),
                new TvInputTiles.TileSpec("hdmi/HW5", "HDMI 1", "HDMI 1", TvInputTiles.KIND_HDMI));

        assertNull(TvInputTiles.ownedLiveTvPackage(hdmiOnly, "com.vewd.core.tvapp"));
        assertNull(TvInputTiles.ownedLiveTvPackage(withTuner, ""));
        assertEquals("com.vewd.core.tvapp",
                TvInputTiles.ownedLiveTvPackage(withTuner, "com.vewd.core.tvapp"));
    }

    @Test
    public void filterVisibleRemovesUserHiddenInputsWithoutAffectingOwnedTuner() {
        List<TvInputTiles.TileSpec> all = Arrays.asList(
                new TvInputTiles.TileSpec("tuner/HW0", "TV en directo", "TV EN DIRECTO", TvInputTiles.KIND_TUNER),
                new TvInputTiles.TileSpec("hdmi/HW5", "HDMI 1", "HDMI 1", TvInputTiles.KIND_HDMI),
                new TvInputTiles.TileSpec("av/HW1", "Compuesto", "COMPUESTO", TvInputTiles.KIND_AV));

        List<TvInputTiles.TileSpec> visible = TvInputTiles.filterVisible(
                all, new HashSet<>(Collections.singletonList("av/HW1")));
        assertEquals(2, visible.size());
        assertEquals("tuner/HW0", visible.get(0).inputId);
        assertEquals("hdmi/HW5", visible.get(1).inputId);
        assertEquals(3, TvInputTiles.filterVisible(all, Collections.emptySet()).size());
    }
}
