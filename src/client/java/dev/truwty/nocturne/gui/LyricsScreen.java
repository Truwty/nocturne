package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.LyricsSettings;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.setting.ActionSetting;
import dev.truwty.nocturne.setting.BoolSetting;
import dev.truwty.nocturne.setting.ColorSetting;
import dev.truwty.nocturne.setting.ModeSetting;
import dev.truwty.nocturne.setting.NumberSetting;
import dev.truwty.nocturne.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Environment(EnvType.CLIENT)
public class LyricsScreen extends Screen {
   private static final int BW = 150;
   private static final int BH = 20;
   private static final int GAP = 10;
   private static final int ROW = 24;
   private final Screen parent;
   private final LyricsScreen.Page page;
   private final List<Runnable> refresh = new ArrayList<>();
   private int top;

   public LyricsScreen(Screen parent) {
      this(parent, LyricsScreen.Page.MAIN);
   }

   public LyricsScreen(Screen parent, LyricsScreen.Page page) {
      super(Component.literal(page.title));
      this.parent = parent;
      this.page = page;
   }

   private static LyricsSettings s() {
      return NocturneClient.SETTINGS;
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      this.refresh.clear();
      LyricsSettings s = s();

      List<Setting<?>> list = switch (this.page) {
         case MAIN -> List.of(s.preset, s.plainLyrics, s.debug, this.action("Copy debug"));
         case ANIMATION -> List.of(
            s.style, s.intensity, s.lyricFont, s.maxWords, s.linger, s.size, s.offset, s.beatPulse, s.titleCard, s.nextLine, s.smoothFont
         );
         case PLACEMENT -> List.of(
            s.follow,
            s.battle,
            s.distance,
            s.smartPlace,
            s.keepUp,
            s.faceYou,
            s.floor,
            s.seeThrough,
            s.orbit,
            s.orbitSpeed,
            s.cloudStyle,
            s.cloudDrift,
            s.cloudTint
         );
         case EFFECTS -> List.of(
            s.colorMode, s.color, s.color2, s.glow, s.chroma, s.sparkles, s.flashEffects, s.shakeEffects, s.beatZoom, s.readable, s.albumRain, s.topBar, s.cinematic, s.shaderMode
         );
         case PLAYER -> List.of(this.action("Detection"), this.action("Reconnect"));
      };
      int rows = (list.size() + 1) / 2 + (this.page == LyricsScreen.Page.MAIN ? 3 : 0);
      boolean status = this.page == LyricsScreen.Page.MAIN || this.page == LyricsScreen.Page.PLAYER;
      this.top = Math.max(status ? 46 : 32, Math.min(this.height / 6 + (status ? 10 : 0), this.height - 40 - rows * 24 - 24));
      int left = this.width / 2 - 150 - 5;
      int right = this.width / 2 + 5;
      int i = 0;
      if (this.page == LyricsScreen.Page.MAIN) {
         Button master = StudioButton.create(Component.empty(), b -> {
            s.setEnabled(!s.isEnabled());
            s.save();
         }).bounds(left, this.top, 150, 20).build();
         this.refresh.add(() -> master.setMessage(Component.literal("Lyrics: " + (s.isEnabled() ? "ON" : "OFF"))));
         this.addRenderableWidget(master);
         ActionSetting demo = this.action("Demo");
         Button demoBtn = StudioButton.create(Component.empty(), b -> demo.action.run()).bounds(right, this.top, 150, 20).build();
         this.refresh.add(() -> demoBtn.setMessage(Component.literal(demo.label.get())));
         this.addRenderableWidget(demoBtn);
         i = 2;
      }

      for (Setting<?> st : list) {
         int x = i % 2 == 0 ? left : right;
         int y = this.top + i / 2 * 24;
         this.addRenderableWidget(this.widget(st, x, y, 150));
         i++;
      }

      if (this.page == LyricsScreen.Page.MAIN) {
         if (i % 2 == 1) {
            i++;
         }

         int y = this.top + i / 2 * 24 + 8;
         this.addRenderableWidget(this.sub(LyricsScreen.Page.ANIMATION, "Animation...", left, y));
         this.addRenderableWidget(this.sub(LyricsScreen.Page.PLACEMENT, "Placement...", right, y));
         this.addRenderableWidget(this.sub(LyricsScreen.Page.EFFECTS, "Colours and effects...", left, y + 24));
         this.addRenderableWidget(this.sub(LyricsScreen.Page.PLAYER, "Desktop player...", right, y + 24));
      }

      this.addRenderableWidget(StudioButton.create(Component.literal("Style: Vanilla ⇄ Glass"), b -> {
         s.guiStyle.set(1);
         s.save();
         ClientAccess.setScreen(this.minecraft, new GlassLyricsScreen(this.rootParent()));
      }).bounds(left, this.height - 27, 150, 20).build());
      this.addRenderableWidget(
         StudioButton.create(Component.literal("Done"), b -> this.onClose()).bounds(right, this.height - 27, 150, 20).build()
      );

      for (Runnable r : this.refresh) {
         r.run();
      }
   }

   private Screen rootParent() {
      Screen p = this.parent;

      while (p instanceof LyricsScreen) {
         LyricsScreen ls = (LyricsScreen)p;
         p = ls.parent;
      }

      return p;
   }

   private ActionSetting action(String name) {
      for (Setting<?> st : s().settings) {
         if (st instanceof ActionSetting a && a.name.equals(name)) {
            return a;
         }
      }

      throw new IllegalArgumentException(name);
   }

   private Button sub(LyricsScreen.Page target, String label, int x, int y) {
      return StudioButton.create(Component.literal(label), b -> ClientAccess.setScreen(this.minecraft, new LyricsScreen(this, target)))
         .bounds(x, y, 150, 20)
         .build();
   }

   private void changed() {
      s().tickPreset();
      s().save();

      for (Runnable r : this.refresh) {
         r.run();
      }
   }

   private AbstractWidget widget(Setting<?> st, int x, int y, int w) {
      if (st instanceof NumberSetting n) {
         AbstractSliderButton slider = new StudioSlider(x, y, w, 20, Component.empty(), n.fraction()) {
            protected void updateMessage() {
               this.setMessage(Component.literal(n.name + ": " + n.display()));
            }

            protected void applyValue() {
               n.setFraction(this.value);
               LyricsScreen.s().tickPreset();
            }
         };
         slider.setMessage(Component.literal(n.name + ": " + n.display()));
         return slider;
      } else {
         Button b = StudioButton.create(Component.empty(), btn -> {
            if (st instanceof BoolSetting bs) {
               bs.toggle();
            } else if (st instanceof ModeSetting m) {
               m.cycle(1);
            } else if (st instanceof ColorSetting c) {
               c.set(nextColor(c.argb()));
            } else if (st instanceof ActionSetting a) {
               a.action.run();
            }

            this.changed();
         }).bounds(x, y, w, 20).build();
         this.refresh.add(() -> b.setMessage(label(st)));
         return b;
      }
   }

   private static int nextColor(int current) {
      int[] ps = ColorSetting.PRESETS;

      for (int i = 0; i < ps.length; i++) {
         if (ps[i] == current) {
            return ps[(i + 1) % ps.length];
         }
      }

      return ps[0];
   }

   private static Component label(Setting<?> st) {
      if (st instanceof BoolSetting b) {
         return Component.literal(st.name + ": " + (b.on() ? "ON" : "OFF"));
      } else if (st instanceof ModeSetting m) {
         return Component.literal(st.name + ": " + m.current());
      } else if (st instanceof ColorSetting c) {
         MutableComponent m = Component.literal(st.name + ": ");
         return m.append(Component.literal("■■■").withColor(c.argb() & 16777215));
      } else {
         return st instanceof ActionSetting a ? Component.literal(a.label.get()) : Component.literal(st.name);
      }
   }

   @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
      g.fill(0,0,width,height,0x99000000);
      int panelWidth=Math.min(width-8,344);
      Draw.round(g,(width-panelWidth)/2,6,panelWidth,height-12,9,Draw.alpha(s().uiBackground.argb(),s().uiOpacity.f()/100));
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
      for (Runnable r : this.refresh) {
         r.run();
      }

      super.extractRenderState(g, mx, my, delta);
      Draw.textCentered(g, this.font, this.page.title, this.width / 2.0F, 15.0F, -1, 1.0F, true);
      if (this.page == LyricsScreen.Page.MAIN || this.page == LyricsScreen.Page.PLAYER) {
         String status = Draw.trim(this.font, this.action("Status").label.get(), this.width - 40);
         Draw.textCentered(g, this.font, status, this.width / 2.0F, this.top - 13, -6250336, 1.0F, false);
      }

      if (this.page == LyricsScreen.Page.PLACEMENT) {
         String tip = switch (s().follow.get()) {
            case 3 -> "In the sky: lines float high in front of you with depth and glow";
            case 4 -> "In the clouds: words form out of cloud puffs at cloud height";
            case 5 -> "Auto: battle when fighting, sky or clouds outside, calm indoors";
            case 6 -> "Around you, moving along: scattered spots that travel and turn with you";
            default -> "Orbit: press F5 twice to look at yourself";
         };
         Draw.textCentered(g, this.font, tip, this.width / 2.0F, this.height - 42, -8355712, 1.0F, false);
      }
   }

   public void onClose() {
      s().save();
      ClientAccess.setScreen(this.minecraft, this.parent);
   }

   @Environment(EnvType.CLIENT)
   public enum Page {
      MAIN("Nocturne"),
      ANIMATION("Animation"),
      PLACEMENT("Placement"),
      EFFECTS("Colours and effects"),
      PLAYER("Desktop player");

      final String title;

      Page(String title) {
         this.title = title;
      }
   }
}
