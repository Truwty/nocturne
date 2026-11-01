package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.performance.RenderPerformance;
import dev.truwty.nocturne.gui.Draw;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class HudLyrics {
   private static final int TEXT = 0;
   private static final int PUFF = 1;
   private static final int QUAD = 2;
   private static final int PUFF_TEX = 64;
   private static final List<HudLyrics.Cmd> POOL = new ArrayList<>();
   private static final List<HudLyrics.Cmd> FRAME = new ArrayList<>();
   private static int used;
   private static final Matrix4f INV_BASE = new Matrix4f();
   private static final Matrix4f FULL = new Matrix4f();
   private static final Vector4f V = new Vector4f();
   private static final float[] P = new float[12];
   private static Vec3 cam = Vec3.ZERO;
   private static Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
   private static boolean ready;
   private static float groupDepth;
   private static float groupAlpha = 1.0F;
   private static Identifier puffTex;
   private static float fade = 1.0F;
   private static int why;

   private HudLyrics() {
   }

   public static void reset() {
      used = 0;
      ready = false;
   }

   public static boolean begin(Vec3 camPos, Matrix4fc base) {
      reset();
      if (camPos == null) {
         return false;
      }

      INV_BASE.set(base);
      if (Math.abs(INV_BASE.determinant()) < 1.0E-12F) {
         INV_BASE.identity();
      } else {
         INV_BASE.invert();
      }

      cam = camPos;
      ready = true;
      return true;
   }

   public static boolean recording() {
      return ready;
   }

   public static void group(float depthSq, float alpha) {
      groupDepth = depthSq;
      groupAlpha = alpha;
   }

   private static HudLyrics.Cmd next(int kind, Matrix4fc pose, int color) {
      HudLyrics.Cmd c;
      if (used < POOL.size()) {
         c = POOL.get(used);
      } else {
         c = new HudLyrics.Cmd();
         POOL.add(c);
      }

      used++;RenderPerformance.projected();
      c.kind = kind;
      c.order = used;
      c.pose.set(pose);
      c.depth = groupDepth;
      c.color = Draw.alpha(color, groupAlpha);
      return c;
   }

   public static void text(Matrix4fc pose, float x, float y, FormattedCharSequence s, boolean shadow, int color) {
      if (ready && Draw.visible(Draw.alpha(color, groupAlpha))) {
         RenderPerformance.text();
         HudLyrics.Cmd c = next(0, pose, color);
         c.x = x;
         c.y = y;
         c.text = s;
         c.shadow = shadow;
         c.width = Minecraft.getInstance().font.width(s);
      }
   }

   public static void puff(Matrix4fc pose, float cx, float cy, float r, int color) {
      if (ready && Draw.visible(Draw.alpha(color, groupAlpha))) {
         HudLyrics.Cmd c = next(1, pose, color);
         c.x = cx;
         c.y = cy;
         c.x1 = r;
      }
   }

   public static void quad(Matrix4fc pose, float x0, float y0, float x1, float y1, int color) {
      if (ready && Draw.visible(Draw.alpha(color, groupAlpha))) {
         HudLyrics.Cmd c = next(2, pose, color);
         c.x = x0;
         c.y = y0;
         c.x1 = x1;
         c.y1 = y1;
      }
   }

   public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
      long start=RenderPerformance.time();try{renderContents(g,delta);}finally{RenderPerformance.addTime(start);}
   }
   private static void renderContents(GuiGraphicsExtractor g, DeltaTracker delta) {
      if (ready && used != 0) {
         Minecraft mc = Minecraft.getInstance();
         Font font = mc.font;
         int gw = g.guiWidth();
         int gh = g.guiHeight();
         Camera camera = mc.gameRenderer.getMainCamera();
         forward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(camera.rotation());
         FRAME.clear();

         for (int i = 0; i < used; i++) {
            FRAME.add(POOL.get(i));
         }

         FRAME.sort(Comparator.<HudLyrics.Cmd>comparingDouble(cx -> -cx.depth).thenComparingInt(cx -> cx.order));
         int drawn = 0;
         int steep = 0;
         int off = 0;
         Matrix3x2fStack pose = g.pose();

         try {
            for (HudLyrics.Cmd c : FRAME) {
               FULL.set(INV_BASE).mul(c.pose);
               switch (c.kind) {
                  case 0:
                     float cx = c.x + c.width / 2.0F;
                     float cy = c.y + 4.0F;
                     Matrix3x2f affx = affine(mc, cx, cy, Math.max(1.0F, c.width / 2.0F), 4.5F, gw, gh);
                     if (affx == null) {
                        if (why == 2) {
                           steep++;
                        } else {
                           off++;
                        }
                     } else {
                        drawn++;
                        if (fade < 0.99F) {
                           steep++;
                        }

                        pose.pushMatrix();
                        pose.mul(affx);
                        pose.translate(c.x, c.y);
                        g.text(font, c.text, 0, 0, Draw.alpha(c.color, fade), c.shadow);
                        pose.popMatrix();
                     }
                     break;
                  case 1:
                     float r = c.x1;
                     Matrix3x2f aff = affine(mc, c.x, c.y, Math.max(0.5F, r), Math.max(0.5F, r), gw, gh);
                     if (aff == null) {
                        if (why == 2) {
                           steep++;
                        } else {
                           off++;
                        }
                     } else {
                        drawn++;
                        if (fade < 0.99F) {
                           steep++;
                        }

                        pose.pushMatrix();
                        pose.mul(aff);
                        pose.translate(c.x - r, c.y - r);
                        pose.scale(r * 2.0F / 64.0F, r * 2.0F / 64.0F);
                        g.blit(RenderPipelines.GUI_TEXTURED, puffTexture(), 0, 0, 0.0F, 0.0F, 64, 64, 64, 64, Draw.alpha(c.color, fade));
                        pose.popMatrix();
                     }
                     break;
                  default:
                     float w = c.x1 - c.x;
                     float h = c.y1 - c.y;
                     if (!(w <= 0.0F) && !(h <= 0.0F)) {
                        Matrix3x2f affRect = affine(mc, (c.x + c.x1) / 2.0F, (c.y + c.y1) / 2.0F, Math.max(1.0F, w / 2.0F), Math.max(0.5F, h / 2.0F), gw, gh);
                        if (affRect == null) {
                           if (why == 2) {
                              steep++;
                           } else {
                              off++;
                           }
                        } else {
                           drawn++;
                           if (fade < 0.99F) {
                              steep++;
                           }

                           pose.pushMatrix();
                           pose.mul(affRect);
                           pose.translate(c.x, c.y);
                           pose.scale(w, h);
                           g.fill(0, 0, 1, 1, Draw.alpha(c.color, fade));
                           pose.popMatrix();
                        }
                     }
               }
            }
         } catch (Throwable t) {
            NocturneClient.LOG.debug("HUD lyrics failed", t);
            LyricsDebug.note("HUD drawing failed: " + t);
         }

         LyricsDebug.hudRecorded = used;
         LyricsDebug.hudDrawn = drawn;
         LyricsDebug.hudSteep = steep;
         LyricsDebug.hudOff = off;
         used = 0;
         ready = false;
      } else {
         LyricsDebug.hudOff = 0;
         LyricsDebug.hudSteep = 0;
         LyricsDebug.hudDrawn = 0;
         LyricsDebug.hudRecorded = 0;
         used = 0;
      }
   }

   private static boolean project(Minecraft mc, float x, float y, int i, int gw, int gh) {
      V.set(x, y, 0.0F, 1.0F);
      FULL.transform(V);
      float rx = V.x / V.w;
      float ry = V.y / V.w;
      float rz = V.z / V.w;
      if (rx * forward.x + ry * forward.y + rz * forward.z < 0.05F) {
         return false;
      }

      Vec3 ndc = mc.gameRenderer.projectPointToScreen(new Vec3(cam.x + rx, cam.y + ry, cam.z + rz));
      P[i] = (float)(ndc.x * 0.5 + 0.5) * gw;
      P[i + 1] = (float)(0.5 - ndc.y * 0.5) * gh;
      return true;
   }

   private static Matrix3x2f affine(Minecraft mc, float cx, float cy, float hx, float hy, int gw, int gh) {
      fade = 1.0F;
      why = 1;
      float e = 0.5F;
      if (project(mc, cx, cy, 0, gw, gh)
         && project(mc, cx + 0.5F, cy, 2, gw, gh)
         && project(mc, cx - 0.5F, cy, 4, gw, gh)
         && project(mc, cx, cy + 0.5F, 6, gw, gh)
         && project(mc, cx, cy - 0.5F, 8, gw, gh)) {
         float ax = (P[2] - P[4]) / 1.0F;
         float ay = (P[3] - P[5]) / 1.0F;
         float bx = (P[6] - P[8]) / 1.0F;
         float by = (P[7] - P[9]) / 1.0F;
         if (ax * by - ay * bx <= 0.0F) {
            return null;
         }

         float size = (float)(Math.sqrt(ax * ax + ay * ay) * hx + Math.sqrt(bx * bx + by * by) * hy);
         if (size > gw * 2) {
            return null;
         }

         if (!(P[0] < -size - gw) && !(P[0] > gw * 2 + size) && !(P[1] < -size - gh) && !(P[1] > gh * 2 + size)) {
            float worst = 0.0F;

            for (int k = 0; k < 4; k++) {
               float dx = (k & 1) == 0 ? -hx : hx;
               float dy = (k & 2) == 0 ? -hy : hy;
               if (!project(mc, cx + dx, cy + dy, 10, gw, gh)) {
                  return null;
               }

               float px = P[0] + ax * dx + bx * dy;
               float py = P[1] + ay * dx + by * dy;
               float err = (float)Math.sqrt((P[10] - px) * (P[10] - px) + (P[11] - py) * (P[11] - py));
               worst = Math.max(worst, err / Math.max(2.0F, size));
            }

            if (worst > 0.55F) {
               why = 2;
               return null;
            } else {
               fade = 1.0F - Math.max(0.0F, Math.min(1.0F, (worst - 0.2F) / 0.35F));
               return new Matrix3x2f(ax, ay, bx, by, P[0] - ax * cx - bx * cy, P[1] - ay * cx - by * cy);
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Identifier puffTexture() {
      if (puffTex != null) {
         return puffTex;
      }

      NativeImage img = new NativeImage(64, 64, true);
      float c = 32.0F;

      for (int y = 0; y < 64; y++) {
         for (int x = 0; x < 64; x++) {
            float dx = (x + 0.5F - c) / c;
            float dy = (y + 0.5F - c) / c;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            float a = d >= 1.0F ? 0.0F : (d < 0.5F ? 1.0F - 0.9F * d : 1.1F * (1.0F - d));
            img.setPixel(x, y, Math.round(a * 255.0F) << 24 | 16777215);
         }
      }

      puffTex = Identifier.fromNamespaceAndPath("nocturne", "hud_puff");
      Minecraft.getInstance().getTextureManager().register(puffTex, new DynamicTexture(() -> "nocturne puff", img));
      return puffTex;
   }

   @Environment(EnvType.CLIENT)
   private static final class Cmd {
      int kind;
      int order;
      final Matrix4f pose = new Matrix4f();
      float x;
      float y;
      float x1;
      float y1;
      float depth;
      FormattedCharSequence text;
      int width;
      int color;
      boolean shadow;
   }
}
