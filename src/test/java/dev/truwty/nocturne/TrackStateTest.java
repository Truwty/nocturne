package dev.truwty.nocturne;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackStateTest {
    @Test void formatsMinutesWithoutWrappingAtOneHour() {
        assertEquals("0:00", TrackState.time(-10));
        assertEquals("3:09", TrackState.time(189.9));
        assertEquals("60:00", TrackState.time(3600));
    }

    @Test void idleStateHasNoActivePlaybackControls() {
        var state = TrackState.idle("Offline");
        assertFalse(state.connected());
        assertFalse(state.canToggle());
        assertEquals(0, state.currentPosition());
    }

    @Test void partialBridgeResponseResetsPreviousTrackAndCapabilities() {
        var state = MediaBridge.decode(JsonParser.parseString("{\"connected\":true,\"title\":\"Nothing playing\"}").getAsJsonObject());
        assertTrue(state.connected());
        assertFalse(state.canNext());
        assertEquals("", state.art());
        assertTrue(state.sources().isEmpty());
    }

    @Test void progressNeverExceedsTrackLength() {
        var state = MediaBridge.decode(JsonParser.parseString("{\"position\":200,\"duration\":100,\"playing\":true}").getAsJsonObject());
        assertEquals(100, state.currentPosition());
    }

    @Test void sourceNamesAreRecognizable() {
        assertEquals("Spotify", TrackState.friendlySource("Spotify.exe"));
        assertEquals("Cider", TrackState.friendlySource("sh.cider.app"));
        assertEquals("Desktop audio", TrackState.friendlySource(""));
    }
}
