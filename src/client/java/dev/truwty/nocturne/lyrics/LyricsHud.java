package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.LyricsSettings;
import dev.truwty.nocturne.gui.Anim;
import dev.truwty.nocturne.gui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Environment(EnvType.CLIENT)
public final class LyricsHud {
   private static float bars;
   private static float barIn;
   private static float hlX;
   private static float hlW;
   private static float hlA;
   private static float scrollX;
   private static float titleT = 99.0F;
   private static long lastNanos;
   private static String barKey = "";
   private static int barLine = -1;

   private LyricsHud() {
   }

   public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
      long now = System.nanoTime();
      float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (float)(now - lastNanos) / 1.0E9F);
      lastNanos = now;
      LyricsSettings mod = NocturneClient.SETTINGS;
      Minecraft mc = Minecraft.getInstance();
      if (mod != null && mc.player != null) {
         boolean want = mod.isEnabled() && mod.cinematic.on() && LyricsRenderer.active();
         bars = bars + ((want ? 1.0F : 0.0F) - bars) * (1.0F - (float)Math.exp(-dt * 3.5F));
         if (bars < 0.003F) {
            bars = 0.0F;
         }

         int sw = g.guiWidth();
         int sh = g.guiHeight();
         if (bars > 0.0F) {
            float e = Anim.easeInOutCubic(bars);
            int h = Math.round(Math.min(sh * 0.075F, 26.0F) * e);
            int black = Draw.alpha(-16777216, 0.92F);
            g.fill(0, 0, sw, h, black);
            g.fill(0, sh - h, sw, sh, black);
            Draw.vGradient(g, 0, h, sw, 14, Draw.alpha(-16777216, 0.35F * e), 0);
            Draw.vGradient(g, 0, sh - h - 14, sw, 14, 0, Draw.alpha(-16777216, 0.35F * e));
            int vw = Math.round(sw * 0.12F);
            Draw.hGradient(g, 0, h, vw, sh - 2 * h, Draw.alpha(-16777216, 0.28F * e), 0);
            Draw.hGradient(g, sw - vw, h, vw, sh - 2 * h, 0, Draw.alpha(-16777216, 0.28F * e));
            NowPlaying np = LyricsService.INSTANCE.now();
            if (np != null && h >= 12) {
               Font f = mc.font;
               int col = mod.color.argb();
               String info = "♪ " + np.title() + "  ·  " + np.artist();
               info = Draw.trim(f, info, sw - 120);
               long pos = Math.min(np.durationMs(), LyricsRenderer.clockMs());
               String times = np.durationMs() > 0L ? " " + mmss(pos) + " / " + mmss(np.durationMs()) : "";
               int tw = Draw.width(f, info) + Draw.width(f, times);
               float ty = (h - 8) / 2.0F;
               Draw.text(g, f, info, (sw - tw) / 2.0F, ty, Draw.alpha(-1, 0.85F * e), false);
               Draw.text(g, f, times, (sw - tw) / 2.0F + Draw.width(f, info), ty, Draw.alpha(-1, 0.45F * e), false);
               if (np.durationMs() > 0L) {
                  float p = Anim.clamp01((float)pos / (float)np.durationMs());
                  g.fill(0, h - 1, sw, h, Draw.alpha(-1, 0.08F * e));
                  Draw.hGradient(g, 0, h - 1, Math.round(sw * p), 1, Draw.alpha(mod.color2.argb(), e), Draw.alpha(col, e));
               }
            }
         }

         karaokeBar(g, mc, mod, dt, sw, bars > 0.0F ? Math.round(Math.min(sh * 0.075F, 26.0F) * Anim.easeInOutCubic(bars)) : 0);
         extras(g,mc,mod,sw,sh);
         float flash = LyricsRenderer.flash();
         if (flash > 0.01F && mod.isEnabled()) {
            g.fill(0, 0, sw, sh, Draw.alpha(-1, Math.min(0.12F, flash * 0.07F)));
         }

         if (mod.debug.on()) {
            debugPanel(g, mc.font, sw, sh);
         }
      }
   }

   private static void debugPanel(GuiGraphicsExtractor g, Font f, int sw, int sh) {
      List<LyricsDebug.Row> rows = LyricsDebug.rows();
      int w = Math.min(sw - 12, 320);
      int labelW = 92;
      int textW = w - labelW - 18;
      List<Object[]> out = new ArrayList<>();

      for (LyricsDebug.Row r : rows) {
         List<String> parts = Draw.wrap(f, r.value(), textW);

         for (int i = 0; i < parts.size(); i++) {
            out.add(new Object[]{i == 0 ? r : null, parts.get(i), r.level()});
         }
      }

      int lineH = 10;
      int h = 18 + out.size() * lineH + 14;
      float x0 = 6.0F;
      float y0 = Math.min(34, Math.max(4, sh - h - 4));
      Draw.roundF(g, x0, y0, w, h, 6.0F, -536147948);
      Draw.text(g, f, "Nocturne debug", x0 + 8.0F, y0 + 6.0F, -3561729, false);
      float y = y0 + 19.0F;

      for (Object[] o : out) {
         int lvl = (Integer)o[2];

         int col = switch (lvl) {
            case 0 -> -10755190;
            case 1 -> -12470;
            case 2 -> -42396;
            default -> -7699049;
         };
         if (o[0] instanceof LyricsDebug.Row r) {
            Draw.circle(g, x0 + 11.0F, y + 3.5F, 2.5F, col);
            Draw.text(g, f, r.label(), x0 + 18.0F, y, -4672570, false);
         }

         boolean head = o[0] instanceof LyricsDebug.Row r0 && r0.label().equals("Likely cause");
         Draw.text(g, f, (String)o[1], x0 + 18.0F + labelW - 8.0F, y, head ? col : -921608, false);
         y += lineH;
      }

      Draw.text(g, f, "Copy all of it: K -> Copy debug info", x0 + 8.0F, y + 3.0F, -9475459, false);
   }

   private static void karaokeBar(GuiGraphicsExtractor g, Minecraft mc, LyricsSettings mod, float dt, int sw, int top) {
      NowPlaying np = LyricsService.INSTANCE.now();
      Lyrics ly = LyricsService.INSTANCE.lyrics();
      if (np != null && !np.key().equals(barKey)) {
         barKey = np.key();
         titleT = 0.0F;
         barLine = -1;
      }

      titleT += dt;
      boolean want = mod.isEnabled() && mod.topBar.on() && np != null && (ly != null || titleT < 2.6F);
      barIn = barIn + ((want ? 1.0F : 0.0F) - barIn) * (1.0F - (float)Math.exp(-dt * 7.0F));
      if (!(barIn < 0.004F) && np != null) {
         Font f = mc.font;
         float a = Anim.easeOutCubic(barIn);
         int accent = LyricsRenderer.accent();
         int w = Math.min(sw - 70, 300);
         int h = 16;
         float x0 = (sw - w) / 2.0F;
         float y0 = top + 5 - (1.0F - a) * 8.0F;
         Draw.roundF(g, x0, y0, w, h, 8.0F, Draw.alpha(-535688164, a));
         Draw.roundOutlineF(g, x0, y0, w, h, 8.0F, 1.0F, Draw.alpha(accent, 0.35F * a));
         long pos = LyricsRenderer.clockMs();
         Draw.textRight(g, f, mmss(pos), x0 - 6.0F, y0 + 4.0F, Draw.alpha(-1, 0.8F * a), true);
         float spin = (float)(System.nanoTime() / 1.0E9 * (np.playing() ? 3 : 0));
         Draw.circle(g, x0 + 9.0F, y0 + h / 2.0F, 4.5F, Draw.alpha(accent, a));
         Draw.circle(g, x0 + 9.0F + (float)Math.cos(spin) * 2.2F, y0 + h / 2.0F + (float)Math.sin(spin) * 2.2F, 1.1F, Draw.alpha(-1, 0.9F * a));
         float beat = LyricsRenderer.beatNow();
         double now = System.nanoTime() / 1.0E9;

         for (int i = 0; i < 4; i++) {
            float v = np.playing()
               ? 0.25F + 0.45F * beat * (i % 2 == 0 ? 1.0F : 0.7F) + 0.3F * (0.5F + 0.5F * (float)Math.sin(now * (5.0 + i * 1.7) + i))
               : 0.2F;
            float bh = 2.0F + 8.0F * Anim.clamp01(v);
            Draw.roundF(g, x0 + w - 22.0F + i * 4, y0 + h / 2.0F - bh / 2.0F, 2.0F, bh, 1.0F, Draw.alpha(Draw.mix(accent, -1, 0.3F), a));
         }

         int tx0 = Math.round(x0 + 18.0F);
         int tx1 = Math.round(x0 + w - 26.0F);
         float ty = y0 + 4.0F;
         g.enableScissor(tx0, Math.round(y0), tx1, Math.round(y0 + h));
         int idx = ly == null ? -1 : ly.indexAt(pos);
         if (!(titleT < 2.6F) && idx >= 0) {
            Lyrics.Line line = ly.lines.get(idx);
            List<Lyrics.Word> words = line.words();
            int n = words.size();
            float[] wx = new float[n];
            float[] ww = new float[n];
            float space = line.explicitSpacing()?0:Draw.width(f, " ");
            float x = 0.0F;

            for (int i : LyricText.order(words,java.util.stream.IntStream.range(0,n).boxed().toList())) {
               wx[i] = x;
               ww[i] = Draw.width(f, words.get(i).text());
               x += ww[i] + space;
            }

            float total = x;
            int ci = -1;

            for (int i = 0; i < n; i++) {
               if (words.get(i).start() <= pos) {
                  ci = i;
               }
            }

            float slide = 0.0F;
            if (ci >= 0 && ci + 1 < n) {
               long s0 = words.get(ci).start();
               long s1 = words.get(ci + 1).start();
               float t = Anim.clamp01((float)(pos - s0) / (float)Math.max(1L, s1 - s0));
               slide = Anim.easeInOutCubic(Anim.clamp01((t - 0.72F) / 0.28F));
            }

            float bx = ci < 0 ? 0.0F : (ci + 1 < n ? wx[ci] + (wx[ci + 1] - wx[ci]) * slide : wx[ci]);
            float bw = ci < 0 ? ww[0] : (ci + 1 < n ? ww[ci] + (ww[ci + 1] - ww[ci]) * slide : ww[ci]);
            boolean jump = idx != barLine;
            barLine = idx;
            float k = jump ? 1.0F : 1.0F - (float)Math.exp(-dt * 22.0F);
            hlX = hlX + (bx - hlX) * k;
            hlW = hlW + (bw - hlW) * k;
            hlA = hlA + ((ci >= 0 ? 1.0F : 0.0F) - hlA) * (1.0F - (float)Math.exp(-dt * 10.0F));
            float area = tx1 - tx0 - 4;
            float want2 = Math.max(0.0F, Math.min(total - area * 0.6F, hlX + hlW / 2.0F - area * 0.4F));
            scrollX = jump ? want2 : scrollX + (want2 - scrollX) * (1.0F - (float)Math.exp(-dt * 8.0F));
            float ox = tx0 + 2 - scrollX;
            if (hlA > 0.01F) {
               Draw.roundF(g, ox + hlX - 3.0F, y0 + 2.5F, hlW + 6.0F, h - 5, 5.0F, Draw.alpha(accent, 0.92F * a * hlA));
            }

            int cur = slide >= 0.5F && ci + 1 < n ? ci + 1 : ci;

            for (int i = 0; i < n; i++) {
               int c = i == cur ? -15594978 : (i < cur ? -218103809 : 1946157055);
               Draw.text(g, f, words.get(i).text(), ox + wx[i], ty, Draw.alpha(c, a), false);
               if(i==ci) { float progress=words.get(i).progress(pos); boolean rtl=!new java.text.Bidi(words.get(i).text(),java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight();
                  int length=Math.round(ww[i]*progress);int left=Math.round(ox+wx[i]);
                  g.fill(rtl?left+Math.round(ww[i])-length:left,Math.round(ty+9),rtl?left+Math.round(ww[i]):left+length,Math.round(ty+10),accent);
               }
            }

            if (idx + 1 < ly.lines.size()) {
               Draw.text(g, f, "·  " + ly.lines.get(idx + 1).text(), ox + total + 2.0F, ty, Draw.alpha(1308622847, a), false);
            }
         } else {
            String t = "✔ ♪ " + np.title() + "  ·  " + np.artist();
            float in = Anim.easeOutCubic(Math.min(titleT, 2.6F) / 0.4F);
            Draw.text(g, f, t, tx0 + 2 + (1.0F - in) * 10.0F, ty, Draw.alpha(-1, 0.9F * a * in), false);
            Draw.text(g, f, "✔", tx0 + 2 + (1.0F - in) * 10.0F, ty, Draw.alpha(-14756000, a * in), false);
         }

         g.disableScissor();
      }
   }

   private static void extras(GuiGraphicsExtractor g,Minecraft mc,LyricsSettings mod,int sw,int sh) {
      Lyrics lyrics=LyricsService.INSTANCE.lyrics();if(!mod.isEnabled()||lyrics==null)return;
      long clock=LyricsRenderer.clockMs();int index=lyrics.indexAt(clock);int y=sh-52;
      if(index>=0 && index<lyrics.lines.size() && clock<lyrics.lines.get(index).end()) {
         var line=lyrics.lines.get(index);
         if(mod.translation.on()&&!line.translation().isBlank()) {center(g,mc.font,line.translation(),sw,y,mod.uiText.argb());y+=12;}
         if(mod.romanization.on()&&!line.romanization().isBlank())center(g,mc.font,line.romanization(),sw,y,Draw.alpha(mod.uiText.argb(),.7f));
      }
      long next=lyrics.nextVocal(clock);
      if(mod.vocalCountdown.on()&&next>clock+999)center(g,mc.font,"Vocals in "+mmss(next-clock),sw,sh-72,mod.uiAccent.argb());
      if(mod.lyricConfidence.on())center(g,mc.font,lyrics.confidence(),sw,sh-19,Draw.alpha(mod.uiText.argb(),.55f));
   }
   private static void center(GuiGraphicsExtractor g,Font font,String value,int sw,int y,int color) {
      var text=LyricText.visual(Draw.lyric(value,false,NocturneClient.SETTINGS.lyricFont.get()));
      int max=sw-24;g.enableScissor(12,y,sw-12,y+11);g.text(font,text,Math.max(12,(sw-font.width(text))/2),y,color,true);g.disableScissor();
   }
   private static String mmss(long ms) {
      long s = ms / 1000L;
      return s / 60L + ":" + (s % 60L < 10L ? "0" : "") + s % 60L;
   }
}
