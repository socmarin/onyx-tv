package im.marin.onyxtv;

import android.media.tv.TvInputInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InputNamesTest {
    @Test public void philipsTypeBecomesAnEmoji() {
        assertEquals("🎮 Xbox", InputNames.tidy("Xbox&GAME_CONSOLE"));
        assertEquals("🎮 Switch", InputNames.tidy("Switch &GAME_CONSOLE"));
        assertEquals("💻 Ordinateur", InputNames.tidy("Ordinateur&COMPUTER"));
    }

    @Test public void anUnknownTypeLeavesTheNameAlone() {
        assertEquals("Box", InputNames.tidy("Box&SOMETHING_NEW"));
    }

    @Test public void aTypeWithNoNameIsJustItsEmoji() {
        assertEquals("🎮", InputNames.tidy("&GAME_CONSOLE"));
    }

    @Test public void theTextNullAndBlanksAreNoName() {
        assertNull(InputNames.tidy("null"));
        assertNull(InputNames.tidy("  "));
        assertNull(InputNames.tidy(null));
    }

    @Test public void otherNamesAreKept() {
        assertEquals("MacBook Pro", InputNames.tidy("MacBook Pro"));
        assertEquals("Tom & Jerry", InputNames.tidy("Tom & Jerry"));
        assertEquals("AT&T", InputNames.tidy("AT&T"));
        assertEquals("R&D", InputNames.tidy("R&D"));
    }

    @Test public void genericHardwarePortsAreLocalized() {
        InputNames.PortStrings es = new InputNames.PortStrings(
                "Compuesto", "Componentes", "Euroconector", "Otro");

        assertEquals("Compuesto", InputNames.localizePortLabel("Composite", TvInputInfo.TYPE_COMPOSITE, es));
        assertEquals("Compuesto 1", InputNames.localizePortLabel("Composite 1", TvInputInfo.TYPE_COMPOSITE, es));
        assertEquals("Compuesto", InputNames.localizePortLabel("Composite Video", TvInputInfo.TYPE_COMPOSITE, es));
        assertEquals("Componentes", InputNames.localizePortLabel("Component", TvInputInfo.TYPE_COMPONENT, es));
        assertEquals("Euroconector", InputNames.localizePortLabel("SCART", TvInputInfo.TYPE_SCART, es));
        assertEquals("Otro", InputNames.localizePortLabel("Other", TvInputInfo.TYPE_OTHER, es));
        assertEquals("AV", InputNames.localizePortLabel("AV", TvInputInfo.TYPE_COMPOSITE, es));
        assertEquals("HDMI 2", InputNames.localizePortLabel("2", TvInputInfo.TYPE_HDMI, es));
        assertEquals("HDMI 1", InputNames.localizePortLabel("HDMI 1", TvInputInfo.TYPE_HDMI, es));
        assertEquals("Compuesto", InputNames.localizePortLabel("", TvInputInfo.TYPE_COMPOSITE, es));
    }
}
