package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.playback.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class MusicLibraryScreen extends StudioPanel {
  private String query = "", playlist = "";
  private int offset;
  private SpotifyPlayback.LibraryItem selected;
  private SpotifyPlayback.LibraryPage shown;

  public MusicLibraryScreen(Screen parent) {
    super(parent, "Music library · Spotify");
  }

  private SpotifyPlayback spotify() {
    return Playback.spotify();
  }

  private void load() {
    selected = null;
    spotify().browseLibrary(query, playlist, offset);
    rebuildWidgets();
  }

  @Override
  public void tick() {
    if (shown != spotify().library()) rebuildWidgets();
  }

  @Override
  protected void build() {
    shown = spotify().library();
    var search = field(x + 16, body, w - 140, "Search Spotify tracks", query);
    search.setMaxLength(200);
    search.setResponder(value -> query = value);
    button(
        x + w - 120,
        body,
        104,
        "Search",
        "Find tracks through the optional Spotify account integration",
        () -> {
          playlist = "";
          offset = 0;
          load();
        });
    button(
        x + 16,
        body + 26,
        (w - 36) / 2,
        "My playlists",
        "Available playlists depend on Spotify developer access",
        () -> {
          query = "";
          playlist = "";
          offset = 0;
          load();
        });
    button(
        x + 20 + (w - 36) / 2,
        body + 26,
        (w - 36) / 2,
        "Account & permissions",
        "Optional integration; desktop detection stays available",
        () -> ClientAccess.setScreen(minecraft, new PlaybackScreen(this, 2)));
    for (int i = 0; i < shown.items().size(); i++) {
      var item = shown.items().get(i);
      button(
          x + 16,
          body + 74 + i * 25,
          w - 32,
          (item.equals(selected) ? "› " : "") + item.title() + " — " + item.artist(),
          item.playlist() ? "Open this playlist" : "Select this track, then Play or Queue",
          () -> {
            if (item.playlist()) {
              playlist = item.uri().substring("spotify:playlist:".length());
              offset = 0;
              load();
            } else {
              selected = item;
              rebuildWidgets();
            }
          });
    }
    int bw = (w - 44) / 4;
    button(
                x + 16,
                body + 207,
                bw,
                "Previous",
                "Previous page",
                () -> {
                  offset = Math.max(0, offset - 5);
                  load();
                })
            .active =
        offset > 0;
    button(
                x + 20 + bw,
                body + 207,
                bw,
                "Next",
                "Next page",
                () -> {
                  offset += 5;
                  load();
                })
            .active =
        shown.more() && offset < 1000;
    button(
                x + 24 + bw * 2,
                body + 207,
                bw,
                "Play",
                "Requires Spotify Premium and an active playback device",
                () -> spotify().playTrack(selected.uri()))
            .active =
        selected != null;
    button(
                x + 28 + bw * 3,
                body + 207,
                bw,
                "Queue",
                "Add selected track to the active Spotify queue",
                () -> spotify().addQueue(selected.uri()))
            .active =
        selected != null;
    button(
                x + 16,
                body + 234,
                w - 32,
                "Open in Spotify",
                "View the selected track on Spotify",
                () ->
                    ClientAccess.openUri(
                        java.net.URI.create(
                            "https://open.spotify.com/"
                                + selected.uri().substring(8).replace(':', '/'))))
            .active =
        selected != null;
    notice = shown.message();
  }

  @Override
  protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
    text(
        g,
        "Spotify account access is optional · track search and private playlist browsing",
        body + 54,
        NocturneClient.SETTINGS.uiText.argb());
    text(g, spotify().status(), body + 264, NocturneClient.SETTINGS.uiText.argb());
  }

  @Override
  protected int contentHeight() {
    return 285;
  }
}
