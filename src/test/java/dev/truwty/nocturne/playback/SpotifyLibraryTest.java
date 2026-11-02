package dev.truwty.nocturne.playback;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class SpotifyLibraryTest {
  @Test
  void handlesSearchAndPlaylistResponseShapesWithoutTreatingLocalFilesAsPlayable() {
    var response =
        JsonParser.parseString(
                """
                {"tracks":{"items":[{"name":"Track","uri":"spotify:track:1234567890123456789012","artists":[{"name":"Artist"}]},
                  {"name":"Local","is_local":true,"uri":"spotify:local:artist:track"},null],"next":null}}
                """)
            .getAsJsonObject();
    var page = SpotifyPlayback.decodeLibrary(response, false, false, 5);
    assertEquals(1, page.items().size());
    assertEquals("Artist", page.items().getFirst().artist());
    assertFalse(page.more());
    for (String wrapper : new String[] {"item", "track"}) {
      var playlist =
          JsonParser.parseString(
                  "{\"items\":[{\""
                      + wrapper
                      + "\":{\"name\":\"Track\",\"uri\":\"spotify:track:1234567890123456789012\"}},{\""
                      + wrapper
                      + "\":null}],\"next\":\"page2\"}")
              .getAsJsonObject();
      var tracks = SpotifyPlayback.decodeLibrary(playlist, false, true, 6);
      assertEquals(1, tracks.items().size());
      assertTrue(tracks.more());
    }
  }

  @Test
  void acceptsOnlySpotifyPlaylistIdentifiers() {
    var response =
        JsonParser.parseString(
                """
                {"items":[{"name":"Mix","uri":"spotify:playlist:1234567890123456789012"},{"name":"Bad","uri":"https://example.org"}]}
                """)
            .getAsJsonObject();
    var page = SpotifyPlayback.decodeLibrary(response, true, false, 1);
    assertEquals(1, page.items().size());
    assertTrue(page.items().getFirst().playlist());
  }
}
