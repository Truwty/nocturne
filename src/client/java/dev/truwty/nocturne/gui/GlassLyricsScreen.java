package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.LyricsSettings;
import dev.truwty.nocturne.setting.ActionSetting;
import dev.truwty.nocturne.setting.BoolSetting;
import dev.truwty.nocturne.setting.ColorSetting;
import dev.truwty.nocturne.setting.ModeSetting;
import dev.truwty.nocturne.setting.NumberSetting;
import dev.truwty.nocturne.setting.Setting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
public class GlassLyricsScreen extends Screen {
   private static final int BG = -536082412;
   private static final int PANEL_HI = -15200218;
   private static final int LINE = 583644912;
   private static final int TEXT = -988421;
   private static final int MUTED = -6319176;
   private static final int FAINT = -9608315;
   private static final int ACCENT = -4883457;
   private static final int ROW = 22;
   private static final int W = 300;
   private static final float SCALE = 0.8F;
   private final Screen parent;
   private float open;
   private float scroll;
   private float scrollT;
   private long lastNanos;
   private NumberSetting dragging;
   private int px;
   private int py;
   private int ph;
   private int listTop;
   private int listH;
   private final Map<Setting<?>, Float> knob = new HashMap<>();
   private float chipX;
   private float chipY;
   private float chipW;
   private float chipH = 14.0F;
   private float chipHover;

   public GlassLyricsScreen(Screen parent) {
      super(Component.literal("Nocturne"));
      this.parent = parent;
   }

   public boolean isPauseScreen() {
      return false;
   }

   private LyricsSettings s() {
      return NocturneClient.SETTINGS;
   }

   private List<Setting<?>> cachedRows;

   private List<Setting<?>> rows() {
      if (cachedRows != null) return cachedRows;
      List<Setting<?>> out = new ArrayList<>(this.s().settings);
      out.remove(this.s().guiStyle);
      out.remove(this.s().impact);
      cachedRows = List.copyOf(out);
      return cachedRows;
   }

   public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
      g.fillGradient(0, 0, this.width, this.height, Draw.alpha(-1341848823, this.open), Draw.alpha(-804649964, this.open));
   }

   public void extractRenderState(GuiGraphicsExtractor g, int realMx, int realMy, float delta) {
      super.extractRenderState(g, realMx, realMy, delta);
      int width = Math.round(this.width / 0.8F);
      int height = Math.round(this.height / 0.8F);
      int mx = Math.round(realMx / 0.8F);
      int my = Math.round(realMy / 0.8F);
      Matrix3x2fStack pose = g.pose();
      pose.pushMatrix();
      pose.scale(0.8F, 0.8F);
      long now = System.nanoTime();
      float dt = this.lastNanos == 0L ? 0.016666668F : Math.min(0.05F, (float)(now - this.lastNanos) / 1.0E9F);
      this.lastNanos = now;
      Anim.tick(dt);
      this.open = this.s().reducedMotion.on() ? 1.0F : Anim.to(this.open, 1.0F, 9.0F);
      this.scroll = this.s().reducedMotion.on() ? this.scrollT : Anim.to(this.scroll, this.scrollT, 16.0F);
      List<Setting<?>> list = this.rows();
      this.ph = Math.min(height - 56, 60 + list.size() * rowHeight() + 12);
      this.px = (width - 300) / 2;
      this.py = (height - this.ph) / 2 - 8 + Math.round((1.0F - Anim.easeOutCubic(this.open)) * 14.0F);
      Draw.shadow(g, this.px, this.py, 300, this.ph, 12, this.open);
      Draw.roundF(g, this.px, this.py, 300.0F, this.ph, 12.0F, Draw.alpha(theme(-536082412), this.open));
      Draw.roundOutlineF(g, this.px, this.py, 300.0F, this.ph, 12.0F, 1.0F, Draw.alpha(583644912, this.open));
      Draw.circle(g, this.px + 18, this.py + 18, 5.0F, Draw.alpha(theme(-4883457), this.open));
      Draw.circle(g, this.px + 16.6F, this.py + 16.6F, 1.6F, Draw.alpha(-1, this.open * 0.8F));
      Draw.text(g, this.font, "Nocturne", this.px + 30, this.py + 11, Draw.alpha(theme(-988421), this.open), 1.5F, true);
      Draw.text(g, this.font, "Your music, in your world", this.px + 30, this.py + 27, Draw.alpha(theme(-6319176), this.open), false);
      this.toggle(g, this.px + 300 - 46, this.py + 13, this.s().isEnabled(), this.knobFor(null, this.s().isEnabled()), this.open);
      Draw.rect(g, this.px + 12, this.py + 46, 276, 1, Draw.alpha(583644912, this.open));
      this.listTop = this.py + 52;
      this.listH = this.ph - 60;
      float maxScroll = Math.max(0, list.size() * rowHeight() - this.listH);
      this.scrollT = Math.max(0.0F, Math.min(maxScroll, this.scrollT));
      g.enableScissor(this.px, this.listTop, this.px + 300, this.listTop + this.listH);

      for (int i = 0; i < list.size(); i++) {
         Setting<?> st = list.get(i);
         float y = this.listTop + i * rowHeight() - this.scroll;
         if (!(y < this.listTop - rowHeight()) && !(y > this.listTop + this.listH)) {
            float in = Anim.easeOutCubic(Anim.clamp01(this.open * 1.6F - i * 0.02F));
            this.row(g, st, this.px + 14 + (1.0F - in) * 10.0F, y, 272, mx, my, this.open * in);
         }
      }

      g.disableScissor();
      if (maxScroll > 0.0F) {
         float bar = (float)(this.listH * this.listH) / (list.size() * rowHeight());
         float by = this.listTop + (this.listH - bar) * (this.scroll / maxScroll);
         Draw.roundF(g, this.px + 300 - 6, by, 2.0F, bar, 1.0F, Draw.alpha(-9608315, this.open * 0.6F));
      }

      String chip = "Style: Glass   ⇄   Vanilla";
      this.chipW = Draw.width(this.font, chip) + 22;
      this.chipX = (width - this.chipW) / 2.0F;
      this.chipY = this.py + this.ph + 6;
      boolean ch = mx >= this.chipX && mx < this.chipX + this.chipW && my >= this.chipY && my < this.chipY + this.chipH;
      this.chipHover = Anim.to(this.chipHover, ch ? 1.0F : 0.0F, 14.0F);
      Draw.roundF(g, this.chipX, this.chipY, this.chipW, this.chipH, 7.0F, Draw.alpha(Draw.mix(theme(-15200218), theme(-4883457), this.chipHover * 0.85F), this.open));
      Draw.roundOutlineF(g, this.chipX, this.chipY, this.chipW, this.chipH, 7.0F, 1.0F, Draw.alpha(583644912, this.open));
      Draw.textCentered(
         g, this.font, chip, width / 2.0F, this.chipY + 3.0F, Draw.alpha(Draw.mix(theme(-988421), -15595489, this.chipHover), this.open), 1.0F, false
      );
      Draw.textCentered(
         g, this.font, NocturneClient.OPEN_KEY.getTranslatedKeyMessage().getString() + " opens this · Esc closes", width / 2.0F, this.chipY + this.chipH + 5.0F, Draw.alpha(-9608315, this.open), 1.0F, false
      );
      pose.popMatrix();
   }

   private int rowHeight() { return this.s().uiSpacing.i(); }

   private int theme(int original) {
      return switch (original) {
         case -536082412 -> Draw.withAlpha(this.s().uiBackground.argb(), Math.round(255 * this.s().uiOpacity.f() / 100));
         case -15200218 -> Draw.mix(this.s().uiBackground.argb(), this.s().uiAccent.argb(), 0.12F);
         case -4883457 -> this.s().uiAccent.argb();
         case -988421 -> this.s().uiText.argb();
         case -6319176 -> Draw.mix(this.s().uiText.argb(), this.s().uiBackground.argb(), 0.28F);
         default -> original;
      };
   }

   private float knobFor(Setting<?> st, boolean on) {
      float k = this.knob.getOrDefault(st, on ? 1.0F : 0.0F);
      k = Anim.to(k, on ? 1.0F : 0.0F, 16.0F);
      this.knob.put(st, k);
      return k;
   }

   private void row(GuiGraphicsExtractor g, Setting<?> st, float x, float y, int w, int mx, int my, float a) {
      boolean hover = mx >= x && mx < x + w && my >= y && my < y + 22.0F && my >= this.listTop && my < this.listTop + this.listH;
      if (hover) {
         Draw.roundF(g, x - 4.0F, y + 1.0F, w + 8, 20.0F, 6.0F, Draw.alpha(theme(-15200218), a));
      }

      float ty = y + 7.0F;
      Draw.text(g, this.font, st.name, x + 2.0F, ty, Draw.alpha(theme(-988421), a), false);
      float right = x + w - 2.0F;
      if (st instanceof BoolSetting b) {
         this.toggle(g, right - 26.0F, y + 5.0F, b.on(), this.knobFor(st, b.on()), a);
      } else if (st instanceof ModeSetting m) {
         String v = m.current();
         float vw = Draw.width(this.font, v) + 22;
         Draw.roundF(g, right - vw, y + 4.0F, vw, 14.0F, 7.0F, Draw.alpha(theme(-15200218), a));
         Draw.textRight(g, this.font, "‹ " + v + " ›", right - 6.0F, ty, Draw.alpha(hover ? theme(-988421) : theme(-6319176), a), false);
      } else if (st instanceof NumberSetting n) {
         float sw = 100.0F;
         float sx = right - sw - 40.0F;
         float f = n.fraction();
         Draw.roundF(g, sx, y + 11.0F - 1.5F, sw, 3.0F, 1.5F, Draw.alpha(822083583, a));
         Draw.roundF(g, sx, y + 11.0F - 1.5F, sw * f, 3.0F, 1.5F, Draw.alpha(theme(-4883457), a));
         Draw.circle(g, sx + sw * f, y + 11.0F, this.dragging == n ? 5.0F : 4.0F, Draw.alpha(-1, a));
         Draw.textRight(g, this.font, n.display(), right, ty, Draw.alpha(theme(-6319176), a), false);
      } else if (st instanceof ColorSetting c) {
         int[] ps = ColorSetting.PRESETS;

         for (int i = 0; i < ps.length; i++) {
            float cx = right - 5.0F - (ps.length - 1 - i) * 13;
            float cy = y + 11.0F;
            if (ps[i] == c.argb()) {
               Draw.ringF(g, cx, cy, 6.0F, Draw.alpha(-1, a), 1.2F);
            }

            Draw.circle(g, cx, cy, 4.2F, Draw.alpha(ps[i], a));
         }
      } else if (st instanceof ActionSetting act) {
         String label = act.label.get();
         boolean status = st.name.equals("Status");
         if (status) {
            Draw.textRight(
               g,
               this.font,
               Draw.trim(this.font, label, w - Draw.width(this.font, st.name) - 20),
               right,
               ty,
               Draw.alpha(theme(-6319176), a),
               false
            );
         } else {
            float bw = Draw.width(this.font, label) + 18;
            Draw.roundF(g, right - bw, y + 3.0F, bw, 16.0F, 7.0F, Draw.alpha(hover ? theme(-4883457) : theme(-15200218), a));
            Draw.textRight(g, this.font, label, right - 9.0F, ty, Draw.alpha(hover ? -15595489 : theme(-988421), a), false);
         }
      }
   }

   private void toggle(GuiGraphicsExtractor g, float x, float y, boolean on, float k, float a) {
      Draw.roundF(g, x, y, 26.0F, 12.0F, 6.0F, Draw.alpha(Draw.mix(1090519039, theme(-4883457), k), a));
      Draw.circle(g, x + 6.0F + k * 14.0F, y + 6.0F, 4.4F, Draw.alpha(-1, a));
   }

   private double mouseX() {
      return this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
   }

   private double mouseY() {
      return this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
   }

   public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
      double mx = this.mouseX() / 0.8F;
      double my = this.mouseY() / 0.8F;
      boolean right = e.button() == 1;
      if (mx >= this.chipX && mx < this.chipX + this.chipW && my >= this.chipY && my < this.chipY + this.chipH) {
         this.s().guiStyle.set(0);
         this.s().save();
         this.minecraft.setScreen(new LyricsScreen(this.parent));
         return true;
      }

      if (mx >= this.px + 300 - 50 && mx < this.px + 300 - 14 && my >= this.py + 8 && my < this.py + 32) {
         this.s().setEnabled(!this.s().isEnabled());
         this.s().save();
         return true;
      }

      if (!(my < this.listTop) && !(my >= this.listTop + this.listH) && !(mx < this.px) && !(mx >= this.px + 300)) {
         List<Setting<?>> list = this.rows();
         int i = (int)Math.floor((my - this.listTop + this.scroll) / (double)rowHeight());
         if (i >= 0 && i < list.size()) {
            Setting<?> st = list.get(i);
            float x = this.px + 14;
            float w = 272.0F;
            float rightX = x + w - 2.0F;
            if (st instanceof BoolSetting b) {
               b.toggle();
            } else if (st instanceof ModeSetting m) {
               m.cycle(right ? -1 : 1);
            } else if (st instanceof NumberSetting n) {
               this.dragging = n;
               this.setSlider(n, mx, rightX);
            } else if (st instanceof ColorSetting c) {
               int[] ps = ColorSetting.PRESETS;

               for (int k = 0; k < ps.length; k++) {
                  float cx = rightX - 5.0F - (ps.length - 1 - k) * 13;
                  if (Math.abs(mx - cx) < 6.5) {
                     c.set(ps[k]);
                  }
               }
            } else if (st instanceof ActionSetting act && !st.name.equals("Status")) {
               act.action.run();
            }

            this.s().tickPreset();
            this.s().save();
            return true;
         } else {
            return true;
         }
      } else {
         return super.mouseClicked(e, doubleClick);
      }
   }

   private void setSlider(NumberSetting n, double mx, float rightX) {
      float sw = 100.0F;
      float sx = rightX - sw - 40.0F;
      n.setFraction((mx - sx) / sw);
   }

   public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
      if (this.dragging != null) {
         this.setSlider(this.dragging, this.mouseX() / 0.8F, this.px + 14 + 272 - 2);
         return true;
      } else {
         return super.mouseDragged(e, dx, dy);
      }
   }

   public boolean mouseReleased(MouseButtonEvent e) {
      if (this.dragging != null) {
         this.dragging = null;
         this.s().save();
      }

      return super.mouseReleased(e);
   }

   public boolean mouseScrolled(double x, double y, double sx, double sy) {
      this.scrollT -= (float)sy * rowHeight() * 2.0F;
      return true;
   }

   public void onClose() {
      this.s().save();
      this.minecraft.setScreen(this.parent);
   }
}
