package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.lyrics.*;
import dev.truwty.nocturne.performance.RenderPerformance;
import dev.truwty.nocturne.playback.Playback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class VisualizerHud {
  private static final float[] LEVELS = new float[64];
  private static long last;

  private VisualizerHud() {}

  public static void render(GuiGraphicsExtractor g) {
    var s = NocturneClient.SETTINGS;
    var mc = Minecraft.getInstance();
    var song = Playback.current();
    if (!s.isEnabled()
        || s.visualizer.get() == 0
        || Accessibility.hidden()
        || s.reducedMotion.on()
        || Accessibility.photosensitive()
        || mc.getWindow().isIconified()
        || !mc.isWindowActive()
        || mc.isPaused()
        || song == null
        || !song.playing()) return;
    var lyrics = LyricsService.INSTANCE.lyrics();
    long clock = (long) Math.max(0, song.positionMs() + s.offset.get() + Studio.songOffset(song));
    if (s.visualizer.get() == 1 && lyrics != null) {
      int index = lyrics.indexAt(clock);
      if (index >= 0 && clock < lyrics.lines.get(index).end()) return;
    }
    var frame = LyricsService.INSTANCE.system.audioFrame();
    if (frame == null) return;
    int width = Math.min(240, g.guiWidth() - 32), height = 68;
    var box =
        HudAvoidance.place(
            new HudLayout.Box((g.guiWidth() - width) / 2.0, g.guiHeight() * .73, width, height),
            g.guiWidth(),
            g.guiHeight());
    int x = (int) box.x(), y = (int) box.y();
    Draw.round(g, x, y, width, height, 8, Draw.alpha(Accessibility.background(), .86f));
    float[] levels =
        s.visualizerStyle.get() == 1
            ? frame.waveform()
            : s.visualizerStyle.get() == 2
                ? new float[] {frame.bass(), frame.mid(), frame.treble()}
                : frame.spectrum();
    long now = System.nanoTime();
    double dt = last == 0 ? .016 : Math.clamp((now - last) / 1e9, 0, .1);
    last = now;
    int count = Math.min(levels.length, RenderPerformance.scale() < .5 ? 16 : 64);
    double barWidth = (width - 20.0) / count;
    for (int i = 0; i < count; i++) {
      int index = i * levels.length / count;
      double response =
          s.visualizerStyle.get() == 2
              ? switch (index) {
                case 0 -> s.bassResponse.get();
                case 1 -> s.midResponse.get();
                default -> s.trebleResponse.get();
              }
              : index < levels.length / 3
                  ? s.bassResponse.get()
                  : index < levels.length * 2 / 3 ? s.midResponse.get() : s.trebleResponse.get();
      boolean waveform = s.visualizerStyle.get() == 1;
      float target =
          (float)
              Math.clamp(
                  (waveform ? levels[index] : Math.abs(levels[index])) * s.audioGain.f() * response,
                  waveform ? -1 : 0,
                  1);
      LEVELS[i] +=
          (target - LEVELS[i]) * (float) (1 - Math.exp(-dt * (target > LEVELS[i] ? 28 : 8)));
      int barHeight = Math.max(1, Math.round(LEVELS[i] * 32));
      int left = x + 10 + (int) (i * barWidth);
      int top = waveform ? y + 26 - Math.round(LEVELS[i] * 16) : y + 44 - barHeight;
      int bottom = waveform ? top + 2 : y + 44;
      g.fill(
          left,
          top,
          left + Math.max(1, (int) barWidth - 1),
          bottom,
          Draw.mix(s.color.argb(), s.color2.argb(), count == 1 ? 0 : i / (float) (count - 1)));
    }
    RenderPerformance.mesh(count * 4 + 4);
    String label =
        frame.bpm() > 0
            ? String.format(
                java.util.Locale.ROOT,
                "%.0f BPM · %.0f%% confidence",
                frame.bpm(),
                frame.confidence() * 100)
            : "Listening for a steady beat";
    Draw.text(g, mc.font, label, x + 10, y + 52, s.uiText.argb(), false);
  }
}
