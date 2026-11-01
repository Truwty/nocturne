package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SystemMediaSourceTest {
    private JsonObject sample(boolean playing) {
        JsonObject json = new JsonObject();
        json.addProperty("title", "夜の歌");
        json.addProperty("artist", "Test artist");
        json.addProperty("album", "Test album");
        json.addProperty("app", "Spotify.exe");
        json.addProperty("playing", playing);
        json.addProperty("pos", 12000);
        json.addProperty("dur", 180000);
        json.addProperty("updated", 10000);
        json.addProperty("now", 12000);
        return json;
    }

    @Test void detectsSpotifyAndPreservesUnicodeWithoutCredentials() {
        NowPlaying result = SystemMediaSource.decodeSession(sample(true));
        assertEquals("Spotify app", result.source());
        assertEquals("夜の歌", result.title());
        assertEquals(14000, result.progressMs());
    }

    @Test void pausedTimelineDoesNotAdvance() {
        assertEquals(12000, SystemMediaSource.decodeSession(sample(false)).progressMs());
    }

    @Test void staleTimelineDoesNotHideLyricsByJumpingToTrackEnd() {
        JsonObject json = sample(true);
        json.addProperty("now", Long.MAX_VALUE);
        assertEquals(12000, SystemMediaSource.decodeSession(json).progressMs());
    }

    @Test void sparseTimelineUpdatesStillAdvanceAfterTenSeconds() {
        JsonObject json=sample(true); json.addProperty("now",40000);
        assertEquals(42000,SystemMediaSource.decodeSession(json).progressMs());
    }

    @Test void futureTimestampDoesNotRewindPlayback() {
        JsonObject json=sample(true); json.addProperty("updated",20000);
        assertEquals(12000,SystemMediaSource.decodeSession(json).progressMs());
    }

    @Test void reportsCapabilitiesPerPlayerAndPreservesDeviceIdentity() {
        var o=com.google.gson.JsonParser.parseString("{\"sessions\":[{\"id\":\"one\",\"playing\":true,\"seek\":true},{\"id\":\"two\",\"toggle\":true,\"issue\":\"\"}]}").getAsJsonObject();
        var players=SystemMediaSource.decodePlayers(o);assertTrue(players.getFirst().capabilities().contains("seek"));assertFalse(players.get(1).capabilities().contains("seek"));assertTrue(players.get(1).capabilities().contains("toggle"));
        var media=sample(true);media.addProperty("deviceId","headphones");assertEquals("Spotify.exe|headphones",SystemMediaSource.decodeSession(media).sessionId());
    }

    @Test void missingSessionClearsDetection() {
        assertNull(SystemMediaSource.decodeSession(new JsonObject()));
    }
}
