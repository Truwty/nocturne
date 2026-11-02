package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.Fx;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

public final class Draw {
   private static final Map<Long, Identifier> SHAPES = new HashMap<>();
   private static int shapeCount;
   public static final FontDescription SMOOTH = new Resource(Identifier.fromNamespaceAndPath("nocturne", "smooth"));
   public static final FontDescription SMOOTH_BOLD = new Resource(Identifier.fromNamespaceAndPath("nocturne", "smooth_bold"));
   public static final FontDescription ELEGANT = new Resource(Identifier.fromNamespaceAndPath("nocturne", "elegant"));
   public static final FontDescription IMPACT = new Resource(Identifier.fromNamespaceAndPath("nocturne", "impact"));
   public static final String[] LYRIC_FONTS = new String[]{"Smooth", "Elegant", "Impact", "Pixel", "Unicode"};
   private static final Map<Item, ItemStack> ICONS = new HashMap<>();

   private Draw() {
   }

   public static int alpha(int c, float a) {
      int al = Math.round((c >>> 24 & 0xFF) * Mth.clamp(a, 0.0F, 1.0F));
      return al << 24 | c & 16777215;
   }

   public static int withAlpha(int c, int a) {
      return Mth.clamp(a, 0, 255) << 24 | c & 16777215;
   }

   public static int mix(int a, int b, float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      int aa = a >>> 24 & 0xFF;
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int ba = b >>> 24 & 0xFF;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
   }

   public static int hsv(float h, float s, float v) {
      return 0xFF000000 | Mth.hsvToRgb((h % 1.0F + 1.0F) % 1.0F, Mth.clamp(s, 0.0F, 1.0F), Mth.clamp(v, 0.0F, 1.0F)) & 16777215;
   }

   public static int brighten(int c, float amount) {
      return mix(c, c & 0xFF000000 | 16777215, amount);
   }

   public static boolean visible(int c) {
      return c >>> 24 > 3;
   }

   public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
      if (w > 0 && h > 0 && visible(c)) {
         g.fill(x, y, x + w, y + h, c);
      }
   }

   public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int c) {
      rect(g, Math.round(x), Math.round(y), Math.round(x + w) - Math.round(x), Math.round(y + h) - Math.round(y), c);
   }

   public static float guiScale() {
      return Minecraft.getInstance().getWindow().getGuiScale();
   }

   private static Identifier shape(int d, int thick) {
      long key = (long)d << 20 | thick;
      Identifier id = SHAPES.get(key);
      if (id != null) {
         return id;
      }

      NativeImage img = new NativeImage(d, d, true);
      float r = d / 2.0F;
      float c = d / 2.0F;

      for (int y = 0; y < d; y++) {
         for (int x = 0; x < d; x++) {
            int hit = 0;

            for (int sy = 0; sy < 4; sy++) {
               for (int sx = 0; sx < 4; sx++) {
                  float px = x + (sx + 0.5F) / 4.0F - c;
                  float py = y + (sy + 0.5F) / 4.0F - c;
                  float dist = (float)Math.sqrt(px * px + py * py);
                  if (dist <= r && (thick == 0 || dist >= r - thick)) {
                     hit++;
                  }
               }
            }

            int a = Math.round(hit / 16.0F * 255.0F);
            img.setPixel(x, y, a << 24 | 16777215);
         }
      }

      id = Identifier.fromNamespaceAndPath("nocturne", "shape/" + d + "_" + thick);
      if (++shapeCount > 600) {
         for (Identifier old : SHAPES.values()) {
            Minecraft.getInstance().getTextureManager().release(old);
         }

         SHAPES.clear();
         shapeCount = 1;
      }

      Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "nocturne shape", img));
      SHAPES.put(key, id);
      return id;
   }

   private static void blitPx(GuiGraphicsExtractor g, Identifier tex, int x, int y, int u, int v, int w, int h, int size, int c) {
      g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, size, size, c);
   }

   private static void inPixels(GuiGraphicsExtractor g, Runnable body) {
      float gs = guiScale();
      Matrix3x2fStack p = g.pose();
      p.pushMatrix();
      p.scale(1.0F / gs, 1.0F / gs);
      body.run();
      p.popMatrix();
   }

   public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
      round(g, (float)x, (float)y, (float)w, (float)h, r, c);
   }

   public static void round(GuiGraphicsExtractor g, float x, float y, float w, float h, int r, int c) {
      roundF(g, x, y, w, h, r, c);
   }

   public static void roundF(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int c) {
      if (!(w <= 0.0F) && !(h <= 0.0F) && visible(c)) {
         float gs = guiScale();
         int X0 = Math.round(x * gs);
         int Y0 = Math.round(y * gs);
         int X1 = Math.round((x + w) * gs);
         int Y1 = Math.round((y + h) * gs);
         int W = X1 - X0;
         int H = Y1 - Y0;
         int R = Math.min(Math.round(r * gs), Math.min(W, H) / 2);
         inPixels(g, () -> {
            if (R <= 0) {
               g.fill(X0, Y0, X1, Y1, c);
            } else {
               Identifier tex = shape(R * 2, 0);
               int D = R * 2;
               blitPx(g, tex, X0, Y0, 0, 0, R, R, D, c);
               blitPx(g, tex, X1 - R, Y0, R, 0, R, R, D, c);
               blitPx(g, tex, X0, Y1 - R, 0, R, R, R, D, c);
               blitPx(g, tex, X1 - R, Y1 - R, R, R, R, R, D, c);
               if (W > D) {
                  g.fill(X0 + R, Y0, X1 - R, Y0 + R, c);
                  g.fill(X0 + R, Y1 - R, X1 - R, Y1, c);
               }

               if (H > D) {
                  g.fill(X0, Y0 + R, X1, Y1 - R, c);
               }
            }
         });
      }
   }

   public static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
      roundOutlineF(g, x, y, w, h, r, 1.0F, c);
   }

   public static void roundOutlineF(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float thick, int c) {
      if (!(w <= 1.0F) && !(h <= 1.0F) && visible(c)) {
         float gs = guiScale();
         int X0 = Math.round(x * gs);
         int Y0 = Math.round(y * gs);
         int X1 = Math.round((x + w) * gs);
         int Y1 = Math.round((y + h) * gs);
         int W = X1 - X0;
         int H = Y1 - Y0;
         int T = Math.max(1, Math.round(thick * gs));
         int R = Math.max(T, Math.min(Math.round(r * gs), Math.min(W, H) / 2));
         inPixels(g, () -> {
            int D = R * 2;
            Identifier tex = shape(D, T);
            blitPx(g, tex, X0, Y0, 0, 0, R, R, D, c);
            blitPx(g, tex, X1 - R, Y0, R, 0, R, R, D, c);
            blitPx(g, tex, X0, Y1 - R, 0, R, R, R, D, c);
            blitPx(g, tex, X1 - R, Y1 - R, R, R, R, R, D, c);
            g.fill(X0 + R, Y0, X1 - R, Y0 + T, c);
            g.fill(X0 + R, Y1 - T, X1 - R, Y1, c);
            g.fill(X0, Y0 + R, X0 + T, Y1 - R, c);
            g.fill(X1 - T, Y0 + R, X1, Y1 - R, c);
         });
      }
   }

   public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int c) {
      if (!(radius <= 0.25F) && visible(c)) {
         float gs = guiScale();
         int D = Math.max(1, Math.round(radius * 2.0F * gs));
         if (D > 512) {
            roundF(g, cx - radius, cy - radius, radius * 2.0F, radius * 2.0F, radius, c);
         } else {
            int X = Math.round(cx * gs - D / 2.0F);
            int Y = Math.round(cy * gs - D / 2.0F);
            inPixels(g, () -> blitPx(g, shape(D, 0), X, Y, 0, 0, D, D, D, c));
         }
      }
   }

   public static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, int c, int thick) {
      ringF(g, cx, cy, r, c, thick);
   }

   public static void ringF(GuiGraphicsExtractor g, float cx, float cy, float r, int c, float thick) {
      if (!(r < 1.0F) && visible(c)) {
         float gs = guiScale();
         int T = Math.max(1, Math.round(thick * gs));
         int D = Math.round((r + thick / 2.0F) * 2.0F * gs);
         if (D <= 512) {
            int X = Math.round(cx * gs - D / 2.0F);
            int Y = Math.round(cy * gs - D / 2.0F);
            inPixels(g, () -> blitPx(g, shape(D, T), X, Y, 0, 0, D, D, D, c));
         } else {
            int n = Math.max(32, Math.round((float)((Math.PI * 2) * r * gs) / Math.max(1.0F, T * 0.5F)));
            inPixels(g, () -> {
               Identifier dot = shape(T, 0);

               for (int i = 0; i < n; i++) {
                  double a = i * Math.PI * 2.0 / n;
                  int X = Math.round((float)((cx + Math.cos(a) * r) * gs - T / 2.0F));
                  int Yx = Math.round((float)((cy + Math.sin(a) * r) * gs - T / 2.0F));
                  blitPx(g, dot, X, Yx, 0, 0, T, T, T, c);
               }
            });
         }
      }
   }

   public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float strength) {
      for (int i = 8; i >= 1; i--) {
         float a = strength * 0.085F * (1.0F - i / 9.0F);
         roundF(g, x - i, y - i + 3, w + i * 2, h + i * 2, r + i, alpha(-16777216, a));
      }
   }

   public static void hGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int left, int right) {
      if (w > 0 && h > 0) {
         Matrix3x2fStack p = g.pose();
         p.pushMatrix();
         p.translate(x, y + h);
         p.rotate((float) (-Math.PI / 2));
         g.fillGradient(0, 0, h, w, left, right);
         p.popMatrix();
      }
   }

   public static void vGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
      if (w > 0 && h > 0) {
         g.fillGradient(x, y, x + w, y + h, top, bottom);
      }
   }

   public static MutableComponent comp(String s) {
      return comp(s, false);
   }

   public static MutableComponent comp(String s, boolean bold) {
      MutableComponent c = Component.literal(s);
      if (dev.truwty.nocturne.NocturneClient.SETTINGS != null && dev.truwty.nocturne.NocturneClient.SETTINGS.uiFont.get() != 0) {
         return lyric(s, bold, dev.truwty.nocturne.NocturneClient.SETTINGS.uiFont.get());
      }
      if (Fx.smoothFont()) {
         return c.withStyle(st -> st.withFont(bold ? SMOOTH_BOLD : SMOOTH));
      } else {
         return bold ? c.withStyle(st -> st.withBold(true)) : c;
      }
   }

   public static MutableComponent lyric(String s, boolean big, int font) {
      MutableComponent c = Component.literal(s);

      return switch (font) {
         case 1 -> c.withStyle(st -> st.withFont(ELEGANT));
         case 2 -> c.withStyle(st -> st.withFont(IMPACT));
         case 4 -> c.withStyle(st -> st.withFont(new Resource(Identifier.fromNamespaceAndPath("minecraft", "uniform"))));
         case 3 -> big ? c.withStyle(st -> st.withBold(true)) : c;
         default -> c.withStyle(st -> st.withFont(big ? SMOOTH_BOLD : SMOOTH));
      };
   }

   public static FormattedCharSequence seq(String s) {
      return dev.truwty.nocturne.lyrics.LyricText.visual(comp(s));
   }

   public static int width(Font f, String s) {
      return f.width(seq(s));
   }

   public static int width(Font f, String s, boolean bold) {
      return f.width(comp(s, bold));
   }

   public static int width(Font f, FormattedText t) {
      return f.width(t);
   }

   public static int width(Font f, FormattedCharSequence t) {
      return f.width(t);
   }

   public static void text(GuiGraphicsExtractor g, Font f, String s, float x, float y, int c, boolean shadow) {
      if (visible(c) && !s.isEmpty()) {
         int ix = Math.round(x);
         int iy = Math.round(y);
         if (!Fx.smoothFont() || !(Math.abs(x - ix) > 0.01F) && !(Math.abs(y - iy) > 0.01F)) {
            g.text(f, dev.truwty.nocturne.lyrics.LyricText.visual(comp(s)), ix, iy, c, shadow);
         } else {
            Matrix3x2fStack p = g.pose();
            p.pushMatrix();
            p.translate(x, y);
            g.text(f, dev.truwty.nocturne.lyrics.LyricText.visual(comp(s)), 0, 0, c, shadow);
            p.popMatrix();
         }
      }
   }

   public static void text(GuiGraphicsExtractor g, Font f, String s, float x, float y, int c, float scale, boolean shadow) {
      if (visible(c) && !s.isEmpty()) {
         if (Math.abs(scale - 1.0F) < 0.01F) {
            text(g, f, s, x, y, c, shadow);
         } else {
            Matrix3x2fStack p = g.pose();
            p.pushMatrix();
            p.translate(x, y);
            p.scale(scale, scale);
            g.text(f, dev.truwty.nocturne.lyrics.LyricText.visual(comp(s)), 0, 0, c, shadow);
            p.popMatrix();
         }
      }
   }

   public static void textCentered(GuiGraphicsExtractor g, Font f, String s, float cx, float y, int c, float scale, boolean shadow) {
      text(g, f, s, cx - width(f, s) * scale / 2.0F, y, c, scale, shadow);
   }

   public static void textRight(GuiGraphicsExtractor g, Font f, String s, float right, float y, int c, boolean shadow) {
      text(g, f, s, right - width(f, s), y, c, shadow);
   }

   public static String trim(Font f, String s, int maxWidth) {
      if (width(f, s) <= maxWidth) {
         return s;
      }

      String dots = "...";
      int dw = width(f, dots);
      StringBuilder b = new StringBuilder();

      for (char ch : s.toCharArray()) {
         if (width(f, b.toString() + ch) + dw > maxWidth) {
            break;
         }

         b.append(ch);
      }

      return b + dots;
   }

   public static List<String> wrap(Font f, String s, int maxWidth) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : s.split(" ")) {
         String tryLine = line.isEmpty() ? word : line + " " + word;
         if (width(f, tryLine) > maxWidth && !line.isEmpty()) {
            lines.add(line.toString());
            line = new StringBuilder(word);
         } else {
            line = new StringBuilder(tryLine);
         }
      }

      if (!line.isEmpty()) {
         lines.add(line.toString());
      }

      return lines;
   }

   public static ItemStack icon(Item item) {
      return Minecraft.getInstance().level == null ? ItemStack.EMPTY : ICONS.computeIfAbsent(item, ItemStack::new);
   }

   public static void item(GuiGraphicsExtractor g, ItemStack stack, float x, float y, float scale) {
      if (!stack.isEmpty()) {
         Matrix3x2fStack p = g.pose();
         p.pushMatrix();
         p.translate(x, y);
         p.scale(scale, scale);
         g.item(stack, 0, 0);
         p.popMatrix();
      }
   }

   public static boolean inside(double mx, double my, float x, float y, float w, float h) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
   }
}
