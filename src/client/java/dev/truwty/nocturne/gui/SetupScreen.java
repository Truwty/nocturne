package dev.truwty.nocturne.gui;

import com.google.gson.JsonObject;
import dev.truwty.nocturne.*;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.lyrics.*;
import dev.truwty.nocturne.playback.Playback;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Checks use the active services; the demo is explicitly separate from live playback. */
public final class SetupScreen extends StudioPanel {
  private int step;

  public SetupScreen(Screen parent) {
    super(parent, "Welcome to Nocturne");
  }

  public static boolean completed() {
    var saved = Studio.library().preset("onboarding", "version1");
    return saved != null
        && saved.has("complete")
        && saved.get("complete").isJsonPrimitive()
        && saved.getAsJsonPrimitive("complete").isBoolean()
        && saved.get("complete").getAsBoolean();
  }

  @Override
  protected void build() {
    switch (step) {
      case 0 -> {
        button(
            x + 16,
            body + 90,
            w - 32,
            "Check desktop players again",
            "Start a song in your desktop music player, then retry",
            () -> {
              LyricsService.INSTANCE.system.stop();
              LyricsService.INSTANCE.system.start();
            });
        button(
            x + 16,
            body + 116,
            w - 32,
            "Player priorities & optional account integration",
            "Automatic desktop detection is the default",
            () -> ClientAccess.setScreen(minecraft, new PlaybackScreen(this)));
      }
      case 1 -> {
        button(
            x + 16,
            body + 90,
            w - 32,
            "Retry lyric lookup",
            "Search online again for the currently detected song",
            () -> LyricsService.INSTANCE.refetch());
        button(
            x + 16,
            body + 116,
            w - 32,
            "Search or import lyrics",
            "Choose a better match or import your own LRC",
            () -> ClientAccess.setScreen(minecraft, new StudioScreen(this, 1)));
      }
      default -> {
        button(
            x + 16,
            body + 90,
            w - 32,
            "Open HUD placement",
            "Place lyrics inside safe screen margins",
            () -> ClientAccess.setScreen(minecraft, new HudEditorScreen(this)));
        button(
            x + 16,
            body + 116,
            w - 32,
            "Open world placement",
            "Use a world before editing the 3D placement",
            () -> ClientAccess.setScreen(minecraft, new PlacementScreen(this)));
        button(
            x + 16,
            body + 142,
            w - 32,
            LyricsService.INSTANCE.demoPlaying()
                ? "Stop visual demo"
                : "Start clearly labelled visual demo",
            "A demo checks visibility independently of music detection",
            () -> {
              if (LyricsService.INSTANCE.demoPlaying()) LyricsService.INSTANCE.stopDemo();
              else LyricsService.INSTANCE.startDemo();
              rebuildWidgets();
            });
      }
    }
  }

  @Override
  protected void footer() {
    button(
        x + 12,
        y + h - 29,
        80,
        step == 0 ? "Later" : "Previous",
        "Setup can be reopened from Studio",
        () -> {
          if (step == 0) ClientAccess.setScreen(minecraft, new StudioScreen(parent));
          else {
            step--;
            rebuildWidgets();
          }
        });
    button(
        x + w - 132,
        y + h - 29,
        120,
        step < 2 ? "Next check" : "Finish setup",
        "Live checks are informative; missing lyrics do not prevent setup",
        () -> {
          if (step < 2) {
            step++;
            rebuildWidgets();
            return;
          }
          var saved = new JsonObject();
          saved.addProperty("complete", true);
          Studio.library().preset("onboarding", "version1", saved);
          if (LyricsService.INSTANCE.demoPlaying()) LyricsService.INSTANCE.stopDemo();
          ClientAccess.setScreen(minecraft, new StudioScreen(parent));
        });
  }

  @Override
  protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
    var song = Playback.current();
    var service = LyricsService.INSTANCE;
    int color = NocturneClient.SETTINGS.uiText.argb();
    text(
        g,
        "Step "
            + (step + 1)
            + " / 3 · "
            + new String[] {"Desktop player", "Lyrics", "Visibility & placement"}[step],
        body,
        NocturneClient.SETTINGS.uiAccent.argb());
    text(
        g,
        song == null
            ? "Waiting for a song. Open a music app and press play."
            : (service.demoPlaying() ? "Demo preview: " : "Detected: ")
                + song.artist()
                + " — "
                + song.title(),
        body + 22,
        color);
    text(
        g,
        "Desktop apps detected: " + service.system.players().size() + " · no account required",
        body + 40,
        color);
    text(
        g,
        service.demoPlaying()
            ? "Demo lyrics only · play a real song to check lookup"
            : service.lyrics() == null || service.lyrics().lines.isEmpty()
                ? "Lyrics: waiting or unavailable for this song"
                : "Lyrics: " + service.lyrics().lines.size() + " lines available",
        body + 58,
        color);
    text(g, "Visibility: " + LyricsRecovery.current().detail(), body + 194, color);
    text(g, "N opens Studio. You can revisit every check from Help & setup.", body + 220, color);
  }
}
