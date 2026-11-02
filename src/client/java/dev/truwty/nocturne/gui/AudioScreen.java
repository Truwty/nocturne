package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.lyrics.LyricsService;
import dev.truwty.nocturne.setting.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class AudioScreen extends StudioPanel {
  public AudioScreen(Screen parent) {
    super(parent, "Music spectrum & beat effects");
  }

  @Override
  protected int contentHeight() {
    return 370;
  }

  @Override
  protected void build() {
    var s = NocturneClient.SETTINGS;
    int yy = body + 100;
    for (var mode : new ModeSetting[] {s.visualizer, s.visualizerStyle}) {
      button(
          x + 16,
          yy,
          w - 32,
          mode.name + ": " + mode.current(),
          SettingsCatalog.help(mode),
          () -> {
            mode.cycle(1);
            s.save();
            rebuildWidgets();
          });
      yy += 26;
    }
    for (var toggle : new BoolSetting[] {s.audioReactive, s.detectedBeat}) {
      button(
          x + 16,
          yy,
          w - 32,
          toggle.name + ": " + (toggle.on() ? "On" : "Off"),
          "Capture only the selected music application; unavailable capture produces no reaction",
          () -> {
            toggle.toggle();
            s.save();
            rebuildWidgets();
          });
      yy += 26;
    }
    for (var number :
        new NumberSetting[] {s.audioGain, s.bassResponse, s.midResponse, s.trebleResponse}) {
      final int row = yy;
      addRenderableWidget(
          new StudioSlider(
              x + 16,
              row,
              w - 32,
              20,
              net.minecraft.network.chat.Component.empty(),
              number.fraction()) {
            {
              updateMessage();
            }

            @Override
            protected void updateMessage() {
              setMessage(Draw.comp(number.name + ": " + number.display()));
            }

            @Override
            protected void applyValue() {
              number.setFraction(value);
              updateMessage();
            }

            @Override
            public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
              super.onRelease(event);
              s.save();
            }
          });
      yy += 26;
    }
  }

  @Override
  public void onClose() {
    NocturneClient.SETTINGS.save();
    super.onClose();
  }

  @Override
  protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
    int ink = NocturneClient.SETTINGS.uiText.argb();
    text(g, LyricsService.INSTANCE.system.audioStatus(), body, ink);
    text(g, "Windows process capture · Minecraft and other applications excluded", body + 20, ink);
    text(
        g, "Browser capture includes all audio tabs in that browser process tree.", body + 38, ink);
    text(g, "Spotify remote devices need the music playing on this computer.", body + 56, ink);
    var frame = LyricsService.INSTANCE.system.audioFrame();
    text(
        g,
        frame == null
            ? "Waiting for local playback and a supported unique player process"
            : String.format(
                java.util.Locale.ROOT,
                "Bass %.2f · Mid %.2f · Treble %.2f · %s",
                frame.bass(),
                frame.mid(),
                frame.treble(),
                frame.bpm() > 0 ? Math.round(frame.bpm()) + " BPM (estimated)" : "Tempo uncertain"),
        body + 76,
        ink);
  }
}
