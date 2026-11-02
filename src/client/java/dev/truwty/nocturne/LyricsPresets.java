package dev.truwty.nocturne;

import dev.truwty.nocturne.setting.ModeSetting;

public final class LyricsPresets {
   public static final String[] NAMES = new String[]{
      "Custom", "Music edit", "Chill", "Hype", "Minimal", "Neon", "Retro", "Bouncy", "Dreamy", "Sky", "Battle", "Album"
   };
   private static final LyricsPresets.P[] PRESETS = new LyricsPresets.P[]{
      null,
      new LyricsPresets.P("Edit", "Normal", "Smooth", "Gradient", -9444394, -3561729, true, true, true, true, true, true),
      new LyricsPresets.P("Rise", "Chill", "Elegant", "Gradient", -7359233, -3561729, true, false, false, false, false, true),
      new LyricsPresets.P("Edit", "Hype", "Impact", "Rainbow", -42396, -12470, true, true, true, true, true, true),
      new LyricsPresets.P("Karaoke", "Chill", "Smooth", "Single", -921608, -921608, false, false, false, false, false, false),
      new LyricsPresets.P("Glitch", "Normal", "Smooth", "Gradient", -4883457, -9444394, true, true, true, true, false, false),
      new LyricsPresets.P("Typewriter", "Normal", "Pixel", "Single", -12470, -19620, true, false, false, false, false, true),
      new LyricsPresets.P("Drop", "Normal", "Impact", "Gradient", -34150, -19620, true, false, true, true, true, false),
      new LyricsPresets.P("Zoom", "Chill", "Elegant", "Gradient", -3561729, -7359233, true, false, true, false, false, true),
      new LyricsPresets.P("Rise", "Chill", "Elegant", "Gradient", -921608, -3561729, true, false, true, false, false, false),
      new LyricsPresets.P("Edit", "Hype", "Impact", "Gradient", -9444394, -921608, true, true, true, true, true, false),
      new LyricsPresets.P("Edit", "Normal", "Smooth", "Album", -921608, -3561729, true, true, true, true, true, true)
   };

   private LyricsPresets() {
   }

   private static void mode(ModeSetting m, String option) {
      int i = m.options.indexOf(option);
      if (i >= 0) {
         m.set(i);
      }
   }

   public static void apply(LyricsSettings s, int index) {
      if (index > 0 && index < PRESETS.length) {
         LyricsPresets.P p = PRESETS[index];
         mode(s.style, p.style());
         mode(s.intensity, p.intensity());
         mode(s.lyricFont, p.font());
         mode(s.colorMode, p.colorMode());
         s.color.set(p.color());
         s.color2.set(p.color2());
         s.glow.set(p.glow());
         s.chroma.set(p.chroma());
         s.sparkles.set(p.sparkles());
         s.impact.set(p.impact());
         if (!p.impact()) { s.flashEffects.set(false); s.shakeEffects.set(false); }
         s.beatZoom.set(p.beatZoom());
         s.cinematic.set(p.cinematic());
      }
   }

   private record P(
      String style,
      String intensity,
      String font,
      String colorMode,
      int color,
      int color2,
      boolean glow,
      boolean chroma,
      boolean sparkles,
      boolean impact,
      boolean beatZoom,
      boolean cinematic
   ) {
   }
}
