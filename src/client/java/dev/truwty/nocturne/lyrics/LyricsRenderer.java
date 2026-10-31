package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.Studio;
import dev.truwty.nocturne.FrameMotion;
import dev.truwty.nocturne.LyricsSettings;
import dev.truwty.nocturne.gui.Anim;
import dev.truwty.nocturne.gui.Draw;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionfc;

@Environment(EnvType.CLIENT)
public final class LyricsRenderer {
   private static final int LIGHT = 15728880;
   private static final int MAX_ROW = 200;
   private static final int ROW_H = 13;
   private static final int SPACE = 4;
   private static final float BIG = 1.25F;
   private static final float PX = 0.02F;
   private static final int WHITE = -1;
   private static final int RED = -50325;
   private static final int CYAN = -12850945;
   private static final int SHADOW = -16053740;
   private static final int MAX_TRAIL = 6;
   private static final List<LyricsRenderer.Spark> SPARKS = new ArrayList<>();
   private static final List<LyricsRenderer.Mist> MIST = new ArrayList<>();
   private static final List<LyricsRenderer.BattleWord> BATTLE = new ArrayList<>();
   private static int combat;
   private static LivingEntity target;
   private static int cloudTint = -1;
   private static int skyTint = -7359233;
   private static float cloudGlow;
   private static float brightScene;
   private static float beat;
   private static double cloudY = 192.33;
   private static boolean outdoors = true;
   private static boolean calm;
   private static int fontIdx;
   private static int lastBattleHit = -1;
   private static int lastBattleLine = -1;
   private static int skySide = 1;
   private static float readNow;
   private static float readK;
   private static boolean hud;
   private static boolean seeThroughNow;
   private static int frameNo;
   private static Vec3 camNow = Vec3.ZERO;
   private static float frameDt = 0.016F;
   private static float mistTimer;
   private static final List<LyricsRenderer.Shown> TRAIL = new ArrayList<>();
   private static final Random RNG = new Random();
   private static LyricsRenderer.Shown current;
   private static LyricsRenderer.Place follow;
   private static LyricsRenderer.Place title;
   private static Vec3 followRel;
   private static float driftYaw;
   private static float runK;
   private static Vec3 lastCam;
   private static Lyrics lastLyrics;
   private static Lyrics chunkSource;
   private static Lyrics chunked;
   private static int chunkMax = -1;
   private static int lastSlot = -1;
   private static int lastSlot2 = -1;
   private static final float[][] SLOTS = new float[][]{
      {-30.0F, 0.75F, 4.2F, 0.0F},
      {28.0F, 0.25F, 3.8F, 0.0F},
      {-12.0F, 0.0F, 5.5F, 1.0F},
      {18.0F, 1.05F, 4.6F, 0.0F},
      {-34.0F, -0.35F, 3.6F, 0.0F},
      {16.0F, 0.0F, 6.2F, 1.0F},
      {2.0F, 1.35F, 4.8F, 0.0F},
      {34.0F, -0.25F, 4.0F, 0.0F},
      {-22.0F, 0.0F, 7.0F, 1.0F}
   };
   private static String songKey = "";
   private static float songAge = 99.0F;
   private static float gapAge;
   private static boolean culled;
   private static float editorX, editorY, editorYaw, editorPitch, editorRoll, audioEnvelope;
   private static int particleBudget() {
      LyricsSettings mod = NocturneClient.SETTINGS;
      return mod.reducedMotion.on() ? 0 : Math.min(mod.particleLimit.i(), mod.quality.get() == 0 ? 20 : mod.quality.get() == 1 ? 90 : 180);
   }
   private static float impact;
   private static float flash;
   private static float playingFade = 1.0F;
   private static float time;
   private static double clock = -1.0;
   private static long lastNanos;
   private static int lastHit = -1;
   private static boolean active;
   private static float orbitBlend;
   private static float orbitAngle;
   private static float phaseSeed;
   private static LyricsRenderer.Place titleOrbit;
   private static final String GLITCH = "#%&@$*!?<>/\\=+";

   private LyricsRenderer() {
   }

   public static boolean active() {
      return active;
   }

   public static float flash() {
      LyricsSettings mod = NocturneClient.SETTINGS;
      return mod != null && mod.flashEffects.on() && !mod.reducedMotion.on() ? flash * mod.intensityFactor() : 0.0F;
   }

   public static long clockMs() {
      return (long)Math.max(0.0, clock);
   }

   public static float beatNow() {
      return beat;
   }

   public static boolean fighting() {
      LyricsSettings mod = NocturneClient.SETTINGS;
      return mod != null && mod.battle.on() && combat > 0;
   }

   public static int accent() {
      LyricsSettings mod = NocturneClient.SETTINGS;
      int album = LyricsService.INSTANCE.albumColor();
      if (album != 0 && mod != null && mod.colorMode.get() == 3) {
         return album;
      } else {
         return mod == null ? -4883457 : mod.color.argb();
      }
   }

   public static void tick(Minecraft mc) {
      LocalPlayer p = mc.player;
      if (p == null) {
         combat = 0;
         target = null;
      } else {
         if (combat > 0) {
            combat--;
         }

         if (p.hurtTime > 0) {
            combat = 70;
            LivingEntity by = p.getLastHurtByMob();
            if (by != null && by != p) {
               target = by;
            }
         }

         LivingEntity hit = p.getLastHurtMob();
         if (hit != null && hit != p && p.tickCount - p.getLastHurtMobTimestamp() < 4) {
            combat = 70;
            target = hit;
         }

         if (p.swinging && mc.crosshairPickEntity instanceof LivingEntity le && le != p && le.isAlive()) {
            combat = Math.max(combat, 50);
            target = le;
         }

         if (target != null && (!target.isAlive() || target.distanceTo(p) > 24.0F)) {
            target = null;
         }

         if (combat == 0) {
            target = null;
         }
      }
   }

   public static float fovKick() {
      LyricsSettings mod = NocturneClient.SETTINGS;
      return mod != null && mod.isEnabled() && mod.beatZoom.on() && !mod.reducedMotion.on() ? 1.0F - impact * 0.035F * mod.intensityFactor() : 1.0F;
   }

   public static void render(LevelRenderContext ctx) {
      long nowN = System.nanoTime();
      float dt = lastNanos == 0L ? 0.016F : Math.min(0.1F, (float)(nowN - lastNanos) / 1.0E9F);
      lastNanos = nowN;
      if (NocturneClient.SETTINGS == null || !NocturneClient.SETTINGS.reducedMotion.on()) time += dt;
      culled = false;
      impact = impact * (float)Math.exp(-dt * 7.0F);
      flash = flash * (float)Math.exp(-dt * 10.0F);
      active = false;
      hud = false;
      HudLyrics.reset();
      LyricsDebug.frame();
      LyricsSettings mod = NocturneClient.SETTINGS;
      Minecraft mc = Minecraft.getInstance();
      if (mod != null && mod.isEnabled() && mc.player != null) {
         float rate = 5 / mod.transition.f();
         editorX = FrameMotion.approach(editorX, mod.editorX.f(), rate, dt);
         editorY = FrameMotion.approach(editorY, mod.editorY.f(), rate, dt);
         editorYaw = FrameMotion.angle(editorYaw, (float)Math.toRadians(mod.editorYaw.get()), rate, dt);
         editorPitch = FrameMotion.angle(editorPitch, (float)Math.toRadians(mod.editorPitch.get()), rate, dt);
         editorRoll = FrameMotion.angle(editorRoll, (float)Math.toRadians(mod.editorRoll.get()), rate, dt);
         float targetAudio = mod.audioReactive.on() && !mod.reducedMotion.on() ? LyricsService.INSTANCE.system.peak() * mod.audioGain.f() : 0;
         audioEnvelope = FrameMotion.approach(audioEnvelope, Math.clamp(targetAudio, 0, 1), targetAudio > audioEnvelope ? 20 : 5, dt);
         while (SPARKS.size() + MIST.size() > particleBudget()) {
            if (!MIST.isEmpty()) MIST.removeLast(); else SPARKS.removeLast();
         }

         NowPlaying np = LyricsService.INSTANCE.now();
         if (np == null) {
            reset();
            songKey = "";
         } else {
            Lyrics ly = chunk(LyricsService.INSTANCE.lyrics(), mod.maxWords.i());
            Font font = mc.font;
            Vec3 cam = ctx.levelState().cameraRenderState.pos;
            fontIdx = mod.lyricFont.get();
            frameDt = dt;
            environment(mc, cam);
            camNow = cam;
            frameNo++;
            seeThroughNow = mod.seeThrough.on();
            hud = ShaderCompat.hudMode(mod) && HudLyrics.begin(cam, ctx.poseStack().last().pose());
            float readTarget = mod.readable.on() ? Anim.clamp01((brightScene - 0.35F) / 0.4F) : 0.0F;
            readK = readK + (readTarget - readK) * (1.0F - (float)Math.exp(-dt * 1.5F));
            readNow = mod.readable.on() ? readK : -1.0F;
            int modeNow = placement(mod, mc, cam);
            float I = mod.intensityFactor() * (calm ? 0.6F : 1.0F);
            boolean world = modeNow != 2;
            boolean turn = mod.faceYou.on();
            float linger = world ? mod.linger.f() : 0.0F;
            boolean battleNow = mod.battle.on() && combat > 0;
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 look = mc.player.getViewVector(partial);
            double d = mod.distance.get();
            Vec3 flat = new Vec3(look.x, look.y * 0.35, look.z).normalize();
            Vec3 desired = cam.add(flat.scale(d)).add(0.0, mod.smartPlace.on() ? 0.15 + d * 0.13 : 0.15, 0.0);
            if (follow == null) {
               follow = new LyricsRenderer.Place(desired);
            }

            if (followRel == null) {
               followRel = desired.subtract(cam);
            }

            followRel = followRel.lerp(desired.subtract(cam), 1.0 - Math.exp(-dt * 5.0));
            follow.at = cam.add(followRel);
            float yawNow = (float)Math.atan2(look.x, look.z);
            float dyaw = yawNow - driftYaw;

            while (dyaw > Math.PI) {
               dyaw -= (float) (Math.PI * 2);
            }

            while (dyaw < -Math.PI) {
               dyaw += (float) (Math.PI * 2);
            }

            driftYaw = driftYaw + dyaw * (1.0F - (float)Math.exp(-dt * 2.5F));
            follow.ready = false;
            if (!np.key().equals(songKey)) {
               songKey = np.key();
               songAge = 0.0F;
               reset();
               clock = -1.0;
               title = new LyricsRenderer.Place(desired);
            }

            if (mod.cloudDrift.on() && !mod.reducedMotion.on()) {
               Vec3 wind = new Vec3(dt * 0.6, 0.0, 0.0);
               if (current != null && current.place.kind == 2) {
                  current.place.at = current.place.at.add(wind);
               }

               for (LyricsRenderer.Shown s : TRAIL) {
                  if (s.place.kind == 2 && (current == null || s.place != current.place)) {
                     s.place.at = s.place.at.add(wind);
                  }
               }
            }

            if (ly != lastLyrics) {
               lastLyrics = ly;
               current = null;
            }

            songAge += dt;
            double reported = np.positionMs() + mod.offset.get() + Studio.songOffset(np);
            if (!(clock < 0.0) && !(Math.abs(reported - clock) > 1200.0)) {
               if (np.playing()) {
                  clock += dt * 1000.0;
               }

               clock = clock + (reported - clock) * (1.0 - Math.exp(-dt * 2.5));
            } else {
               clock = reported;
            }

            long pos = (long)clock;
            playingFade = playingFade + ((np.playing() ? 1.0F : 0.35F) - playingFade) * (1.0F - (float)Math.exp(-dt * 6.0F));
            beat = mod.reducedMotion.on() ? 0 : mod.audioReactive.on() ? audioEnvelope : mod.beatPulse.on() && ly != null && np.playing() ? ly.beat(pos) : 0.0F;
            int idx = ly == null ? -1 : ly.indexAt(pos);
            LyricsDebug.hud = hud;
            LyricsDebug.pos = pos;
            LyricsDebug.line = idx;
            LyricsDebug.lines = ly == null ? 0 : ly.lines.size();
            LyricsDebug.placement = mod.follow.options.get(modeNow)
               + (mod.follow.get() == 5 ? " (Auto)" : "")
               + (orbitBlend > 0.5F ? " · orbiting you (F5)" : "");
            Vec3 previousAnchor = current == null ? null : current.place.pos();
            int curIdx = current == null ? -1 : current.index;
            if (ly != null && idx != curIdx) {
               if (current != null && !current.dismissed) {
                  finish(current, linger);
               }

               if (idx >= 0) {
                  LyricsRenderer.Place place = newPlace(modeNow, mc, cam, look, desired, mod);
                  if (previousAnchor != null && !mod.reducedMotion.on()) place.smoothed = previousAnchor;
                  current = layout(font, ly.lines.get(idx), idx, place);
                  if (world) {
                     settle(mc.level, cam, current, mod);
                     makeRoom(current, mod);
                  }
               } else {
                  current = null;
               }

               lastHit = -1;
            }

            carry(cam, dt, mod);
            follow.update(cam, dt, true);
            if (title != null) {
               title.update(cam, dt, turn);
            }

            if (current != null) {
               if (!world) {
                  current.place = follow;
               }

               if (current.place != follow) {
                  current.place.update(cam, dt, turn);
               }

               current.age += dt;
            }

            for (LyricsRenderer.Shown s : TRAIL) {
               if (!world) {
                  s.place = follow;
               }

               if (s.place != follow && s.place != (current == null ? null : current.place)) {
                  s.place.update(cam, dt, turn);
               }

               s.ended += dt;
               s.age += dt;
            }

            boolean orbitNow = mod.orbit.on() && !mod.reducedMotion.on() && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
            orbitBlend = orbitBlend + ((orbitNow ? 1.0F : 0.0F) - orbitBlend) * (1.0F - (float)Math.exp(-dt * 4.5));
            if (orbitBlend < 0.002F) {
               orbitBlend = 0.0F;
            }

            if (orbitBlend > 0.0F) {
               orbitAngle = orbitAngle + dt * 0.85F * mod.orbitSpeed.f() / 100.0F;
               Vec3 eye = mc.player.getEyePosition(partial);
               if (current != null) {
                  orbit(current, eye, cam, dt, true);
               }

               for (LyricsRenderer.Shown s : TRAIL) {
                  orbit(s, eye, cam, dt, false);
               }

               LyricsRenderer.Place home = world && title != null ? title : follow;
               if (titleOrbit == null) {
                  titleOrbit = new LyricsRenderer.Place(home.pos());
               }

               titleOrbit.at = home.pos().lerp(eye.add(0.0, 0.95 + Math.sin(time * 1.6) * 0.05, 0.0), Anim.easeInOutCubic(orbitBlend));
               titleOrbit.size = 0.55F + (1.0F - orbitBlend) * 0.45F;
               titleOrbit.ready = false;
               titleOrbit.update(cam, dt, true);
            }

            PoseStack ps = ctx.poseStack();
            SubmitNodeCollector out = ctx.submitNodeCollector();
            float scale = 0.02F * mod.size.f() / 100.0F;
            DisplayMode mode = mod.seeThrough.on() ? DisplayMode.SEE_THROUGH : DisplayMode.NORMAL;
            active = np.playing() && ly != null;
            if (mod.titleCard.on() && songAge < 5.5F && title != null) {
               titleCard(ps, out, font, cam, np, mod, orbitBlend > 0.0F ? titleOrbit : (world ? title : follow), scale, mode);
            }

            if (ly != null) {
               if (battleNow) {
                  for (LyricsRenderer.Shown s : TRAIL) {
                     s.ended = Math.max(s.ended, linger);
                  }
               }

               Iterator<LyricsRenderer.Shown> it = TRAIL.iterator();

               while (it.hasNext()) {
                  LyricsRenderer.Shown s = it.next();
                  float gone = s.ended - linger;
                  if (gone > 0.55F + s.words.size() * 0.025F) {
                     it.remove();
                  } else if (gone < 0.0F) {
                     float fade = 0.9F - 0.35F * Anim.clamp01(s.ended / Math.max(0.1F, linger));
                     drawLine(ps, out, font, cam, s, 2305843009213693951L, false, fade, mod, scale, I, mode);
                  } else if (at(s).kind == 2) {
                     drawCloudLine(ps, out, cam, s, 2305843009213693951L, false, 1.0F, Anim.clamp01(gone / 0.55F), mod, scale, mode);
                  } else {
                     drawLeaving(ps, out, font, cam, s, gone, mod, scale, I, mode);
                  }
               }

               if (current != null) {
                  Lyrics.Line line = ly.lines.get(current.index);
                  long lastWordEnd = line.words().isEmpty() ? line.end() : line.words().get(line.words().size() - 1).end();
                  long nextStart = current.index + 1 < ly.lines.size() ? ly.lines.get(current.index + 1).start() : Long.MAX_VALUE;
                  boolean gap = (pos > line.end() || pos > lastWordEnd + 2600L) && nextStart - pos > 1800L;
                  if (gap && !current.dismissed) {
                     finish(current, linger);
                     LyricsRenderer.Shown notes = new LyricsRenderer.Shown(
                        current.index, current.words, current.height, newPlace(modeNow, mc, cam, look, desired, mod)
                     );
                     if (world) {
                        settle(mc.level, cam, notes, mod);
                        makeRoom(notes, mod);
                     }

                     notes.dismissed = true;
                     current = notes;
                     gapAge = 0.0F;
                  }

                  if (current.dismissed) {
                     gapAge += dt;
                     float out01 = nextStart == Long.MAX_VALUE ? 1.0F : Anim.clamp01((float)(nextStart - pos) / 900.0F);
                     notes(ps, out, font, cam, at(current), mod, scale, Anim.clamp01(gapAge / 0.5F) * out01, mode);
                  } else if (battleNow) {
                     battleHits(mc, cam, current, pos, mod, I, partial);
                  } else {
                     drawLine(ps, out, font, cam, current, pos, true, 1.0F, mod, scale, I, mode);
                     if (mod.nextLine.on() && (modeNow == 1 || modeNow == 2) && current.index + 1 < ly.lines.size()) {
                        String next = Draw.trim(font, ly.lines.get(current.index + 1).text(), 260);
                        float in = Anim.easeOutCubic(current.age / 0.5F);
                        ps.pushPose();
                        face(ps, cam, at(current), 0.0F, scale);
                        ps.translate(0.0F, current.height / 2.0F + 9.0F + (1.0F - in) * 6.0F, 0.0F);
                        ps.scale(0.72F, 0.72F, 1.0F);
                        text(ps, out, -Draw.width(font, next) / 2.0F, -4.0F, Draw.seq(next), false, mode, Draw.alpha(-1, 0.4F * in * playingFade));
                        ps.popPose();
                     }
                  }
               } else if (!ly.lines.isEmpty() && ly.lines.get(0).start() > pos && ly.lines.get(0).start() - pos < 3600L) {
                  countdown(
                     ps,
                     out,
                     font,
                     cam,
                     orbitBlend > 0.0F && titleOrbit != null ? titleOrbit : (world && title != null ? title : follow),
                     mod,
                     scale,
                     (float)(ly.lines.get(0).start() - pos) / 1000.0F,
                     mode
                  );
               }
            }

            battle(ps, out, cam, dt, mod, scale, I, mode);
            mist(ps, out, cam, dt, scale);
            sparks(ps, out, font, cam, dt, scale, mode);
         }
      }
   }

   private static Lyrics chunk(Lyrics ly, int max) {
      if (ly == null) {
         return null;
      }

      if (ly != chunkSource || max != chunkMax) {
         chunkSource = ly;
         chunkMax = max;
         chunked = ly.chunked(max);
      }

      return chunked;
   }

   private static LyricsRenderer.Place scatterPlace(ClientLevel level, Vec3 cam, Vec3 look, LyricsSettings mod) {
      int tries = 0;

      int slot;
      do {
         slot = RNG.nextInt(SLOTS.length);
         tries++;
      } while (tries < 30 && (slot == lastSlot || slot == lastSlot2 || !mod.floor.on() && SLOTS[slot][3] > 0.0F));

      lastSlot2 = lastSlot;
      lastSlot = slot;
      float[] sl = SLOTS[slot];
      double a = Math.toRadians(sl[0] + (RNG.nextFloat() - 0.5F) * 10.0F);
      double dist = sl[2] * mod.distance.get() / 3.5 + (RNG.nextFloat() - 0.5F) * 0.6;
      Vec3 h = new Vec3(look.x, 0.0, look.z);
      h = h.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : h.normalize();
      Vec3 dir = new Vec3(h.x * Math.cos(a) + h.z * Math.sin(a), 0.0, -h.x * Math.sin(a) + h.z * Math.cos(a));
      double x = cam.x + dir.x * dist;
      double z = cam.z + dir.z * dist;
      if (sl[3] > 0.0F && level != null) {
         double ground = groundY(level, x, cam.y, z);
         if (!Double.isNaN(ground)) {
            LyricsRenderer.Place p = new LyricsRenderer.Place(new Vec3(x, ground + 0.03, z));
            p.floor = true;
            p.size = 1.35F;
            return p;
         }
      }

      double y = cam.y + Math.max(-0.6, Math.min(0.6, look.y)) * dist * 0.8 + sl[1] + (RNG.nextFloat() - 0.5F) * 0.3;
      LyricsRenderer.Place p = new LyricsRenderer.Place(new Vec3(x, y, z));
      p.roll = (RNG.nextFloat() - 0.5F) * 14.0F;
      p.size = 0.95F + RNG.nextFloat() * 0.25F;
      return p;
   }

   private static double groundY(ClientLevel level, double x, double fromY, double z) {
      MutableBlockPos bp = new MutableBlockPos();
      int top = (int)Math.floor(fromY);

      for (int y = top; y > top - 6; y--) {
         bp.set((int)Math.floor(x), y, (int)Math.floor(z));
         VoxelShape shape = level.getBlockState(bp).getCollisionShape(level, bp);
         if (!shape.isEmpty()) {
            double h = y + shape.max(Axis.Y);
            return h > fromY - 0.2 ? Double.NaN : h;
         }
      }

      return Double.NaN;
   }

   private static void reset() {
      SPARKS.clear();
      MIST.clear();
      BATTLE.clear();
      TRAIL.clear();
      current = null;
      titleOrbit = null;
   }

   private static boolean solid(ClientLevel level, MutableBlockPos bp, double x, double y, double z) {
      int by = (int)Math.floor(y);
      bp.set((int)Math.floor(x), by, (int)Math.floor(z));
      VoxelShape shape = level.getBlockState(bp).getCollisionShape(level, bp);
      return !shape.isEmpty() && y - by < shape.max(Axis.Y);
   }

   private static boolean blocked(ClientLevel level, MutableBlockPos bp, Vec3 at, Vec3 side, double half, double halfH) {
      for (int i = -2; i <= (NocturneClient.SETTINGS.quality.get() == 2 ? 2 : 1); i++) {
         double k = half * i / 2.0;

         for (int j = -1; j <= 1; j++) {
            if (solid(level, bp, at.x + side.x * k, at.y + halfH * j, at.z + side.z * k)) {
               return true;
            }
         }
      }

      return false;
   }

   private static void settle(ClientLevel level, Vec3 cam, LyricsRenderer.Shown s, LyricsSettings mod) {
      LyricsRenderer.Place p = s.place;
      if (level != null && mod.smartPlace.on() && p != follow && !p.floor && !p.drift && p.at != null && p.kind == 0) {
         MutableBlockPos bp = new MutableBlockPos();
         Vec3 to = p.at.subtract(cam);
         double len = to.length();
         if (!(len < 0.8)) {
            Vec3 dir = to.scale(1.0 / len);

            for (double t = 0.5; t < len; t += 0.2) {
               Vec3 q = cam.add(dir.scale(t));
               if (solid(level, bp, q.x, q.y, q.z)) {
                  p.at = cam.add(dir.scale(Math.max(1.3, t - 0.45)));
                  break;
               }
            }

            float sc = 0.02F * mod.size.f() / 100.0F * p.size;
            double half = s.halfWidth() * sc;
            double halfH = s.height / 2.0F * sc;
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            Vec3 start = p.at;

            for (int i = 0; i < 6 && blocked(level, bp, p.at, side, half, halfH); i++) {
               p.at = p.at.add(0.0, 0.25, 0.0);
            }

            if (blocked(level, bp, p.at, side, half, halfH)) {
               p.at = start;

               for (int i = 0; i < 8 && blocked(level, bp, p.at, side, half, halfH); i++) {
                  Vec3 closer = p.at.subtract(dir.scale(0.3));
                  if (closer.distanceTo(cam) < 1.3) {
                     break;
                  }

                  p.at = closer;
               }
            }
         }
      }
   }

   private static LyricsRenderer.Place at(LyricsRenderer.Shown s) {
      return orbitBlend > 0.0F && s.orbit != null ? s.orbit : s.place;
   }

   private static void orbit(LyricsRenderer.Shown s, Vec3 eye, Vec3 cam, float dt, boolean live) {
      float a = orbitAngle + s.phase;
      float r = live ? 1.55F : 1.85F;
      double up = live ? 0.05 : (s.index % 3 - 1) * 0.42 + 0.1;
      up += Math.sin(time * 1.3 + s.phase) * 0.06;
      Vec3 target = eye.add(Math.cos(a) * r, up, Math.sin(a) * r);
      if (s.orbit == null) {
         s.orbit = new LyricsRenderer.Place(s.place.pos());
      }

      float e = Anim.easeInOutCubic(orbitBlend);
      Vec3 home = s.place.pos();
      s.orbit.at = home.lerp(target, e);
      s.orbit.size = s.place.size + (0.42F - s.place.size) * e;
      s.orbit.ready = false;
      s.orbit.update(cam, dt, true);
   }

   private static void finish(LyricsRenderer.Shown s, float linger) {
      s.ended = 0.0F;
      TRAIL.add(s);
      int lingering = 0;

      for (int i = TRAIL.size() - 1; i >= 0; i--) {
         LyricsRenderer.Shown t = TRAIL.get(i);
         if (++lingering > 6) {
            t.ended = Math.max(t.ended, linger);
         }
      }
   }

   private static void makeRoom(LyricsRenderer.Shown fresh, LyricsSettings mod) {
      float scale = 0.02F * mod.size.f() / 100.0F * fresh.place.size;
      float lift = (fresh.height / 2.0F + 10.0F) * scale;
      if (!fresh.place.floor) {
         double near = 1.6 * Math.max(1.0F, fresh.place.size);

         for (LyricsRenderer.Shown s : TRAIL) {
            if (s.place != fresh.place && s.place.at != null && !s.place.floor && s.place.pos().distanceTo(fresh.place.at) < near) {
               s.place.liftTarget = s.place.liftTarget + (lift + s.height / 2.0F * scale);
            }
         }

         if (title != null && title.pos().distanceTo(fresh.place.at) < 1.6 && songAge < 5.5F) {
            title.liftTarget += lift;
         }
      }
   }

   private static void environment(Minecraft mc, Vec3 cam) {
      ClientLevel level = mc.level;
      if (level != null) {
         try {
            Float ch = (Float)level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, cam);
            if (ch != null) {
               cloudY = ch.floatValue();
            }

            Integer cc = (Integer)level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_COLOR, cam);
            Integer sun = (Integer)level.environmentAttributes().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, cam);
            Integer sky = (Integer)level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, cam);
            float lum = cc == null ? 1.0F : (0.3F * (cc >> 16 & 0xFF) + 0.59F * (cc >> 8 & 0xFF) + 0.11F * (cc & 0xFF)) / 255.0F;
            float day = Anim.clamp01((lum - 0.3F) / 0.55F);
            int base = Draw.mix(-4667137, -1, day);
            float sunA = sun == null ? 0.0F : (sun >>> 24 & 0xFF) / 255.0F;
            int sunCol = sun == null ? -24888 : Draw.mix(0xFF000000 | sun, -24888, 0.55F);
            cloudTint = Draw.mix(base, sunCol, sunA * 0.85F);
            cloudGlow = Math.max(1.0F - day, sunA * 0.6F);
            if (sky != null) {
               skyTint = 0xFF000000 | sky;
            }
         } catch (Throwable var12) {
         }

         BlockPos bp = BlockPos.containing(cam);
         LyricsDebug.raining = level.isRaining();
         outdoors = level.getBrightness(LightLayer.SKY, bp) >= 12 || level.canSeeSky(bp);
         float daylight = 1.0F - level.getSkyDarken() / 11.0F;
         brightScene = (outdoors ? 1.0F : 0.35F) * daylight;
      }
   }

   private static int placement(LyricsSettings mod, Minecraft mc, Vec3 cam) {
      int f = mod.follow.get();
      calm = false;
      if (f != 5) {
         return f;
      } else if (!outdoors) {
         calm = true;
         return 1;
      } else {
         return !(cam.y > cloudY - 28.0) && !(mc.player.getXRot() < -32.0F) ? 3 : 4;
      }
   }

   private static LyricsRenderer.Place newPlace(int mode, Minecraft mc, Vec3 cam, Vec3 look, Vec3 desired, LyricsSettings mod) {
      return switch (mode) {
         case 0 -> scatterPlace(mc.level, cam, look, mod);
         default -> new LyricsRenderer.Place(desired);
         case 2 -> follow;
         case 3 -> skyPlace(cam, look, mod);
         case 4 -> cloudPlace(mc, cam, look);
         case 6 -> {
            LyricsRenderer.Place p = scatterPlace(null, cam, look, mod);
            p.drift = true;
            p.rel = p.at.subtract(cam);
            p.off = p.rel;
            p.baseYaw = driftYaw;
            yield p;
         }
      };
   }

   private static Vec3 horizontal(Vec3 look) {
      Vec3 h = new Vec3(look.x, 0.0, look.z);
      return h.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : h.normalize();
   }

   private static void carry(Vec3 cam, float dt, LyricsSettings mod) {
      Vec3 delta = lastCam == null ? null : cam.subtract(lastCam);
      lastCam = cam;
      float want = 0.0F;
      if (delta != null && mod.keepUp.on()) {
         double len = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
         double speed = Math.sqrt(delta.x * delta.x + delta.z * delta.z) / Math.max(0.001, dt);
         if (len > 4.0) {
            delta = null;
         } else {
            want = Anim.clamp01((float)((speed - 4.6) / 1.0));
         }
      }

      runK = runK + (want - runK) * (1.0F - (float)Math.exp(-dt * 4.0F));
      Set<LyricsRenderer.Place> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      List<LyricsRenderer.Place> places = new ArrayList<>();
      if (current != null) {
         places.add(current.place);
      }

      for (LyricsRenderer.Shown s : TRAIL) {
         places.add(s.place);
      }

      if (title != null) {
         places.add(title);
      }

      float k = 1.0F - (float)Math.exp(-dt * 7.0F);

      for (LyricsRenderer.Place p : places) {
         if (p != null && p != follow && seen.add(p) && p.at != null) {
            if (p.drift) {
               Vec3 target = turnY(p.rel, driftYaw - p.baseYaw);
               p.off = p.off.lerp(target, k);
               p.at = cam.add(p.off);
            } else if (delta != null && runK > 0.002F && !p.floor && p.kind != 2) {
               p.at = p.at.add(delta.x * runK, delta.y * runK, delta.z * runK);
            }
         }
      }
   }

   private static Vec3 turnY(Vec3 v, double a) {
      return new Vec3(v.x * Math.cos(a) + v.z * Math.sin(a), v.y, -v.x * Math.sin(a) + v.z * Math.cos(a));
   }

   private static Vec3 turn(Vec3 h, double a) {
      return new Vec3(h.x * Math.cos(a) + h.z * Math.sin(a), 0.0, -h.x * Math.sin(a) + h.z * Math.cos(a));
   }

   private static LyricsRenderer.Place skyPlace(Vec3 cam, Vec3 look, LyricsSettings mod) {
      skySide = -skySide;
      Vec3 dir = turn(horizontal(look), Math.toRadians(skySide * (4.0F + RNG.nextFloat() * 8.0F)));
      double dist = 15.0 + (mod.distance.get() - 3.5) * 2.0;
      double pitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, look.y))));
      double elev = Math.toRadians(Math.max(8.0, Math.min(50.0, pitch + 13.0 + RNG.nextFloat() * 6.0F)));
      LyricsRenderer.Place p = new LyricsRenderer.Place(cam.add(dir.scale(Math.cos(elev) * dist)).add(0.0, Math.sin(elev) * dist, 0.0));
      p.kind = 1;
      p.size = (float)(dist / 3.5 * 1.05);
      p.roll = (RNG.nextFloat() - 0.5F) * 6.0F;
      return p;
   }

   private static LyricsRenderer.Place cloudPlace(Minecraft mc, Vec3 cam, Vec3 look) {
      Vec3 dir = turn(horizontal(look), Math.toRadians((RNG.nextFloat() - 0.5F) * 40.0F));
      double mid = cloudY + 2.0;
      double maxDist = Math.max(40.0, Math.min(150.0, mc.options.getEffectiveRenderDistance() * 16 * 0.55));
      double pitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, look.y))));
      LyricsRenderer.Place p;
      if (Math.abs(cam.y - mid) < 10.0) {
         double dist = 20.0;
         p = new LyricsRenderer.Place(cam.add(dir.scale(dist)).add(0.0, 1.5 + RNG.nextFloat() * 2.0F, 0.0));
         p.size = (float)(dist / 3.5 * 1.1);
      } else if (cam.y < mid) {
         double rise = mid - cam.y;
         double a = Math.toRadians(Math.max(28.0, Math.min(62.0, pitch + 22.0)));
         double total = Math.min(rise / Math.sin(a), maxDist);
         p = new LyricsRenderer.Place(cam.add(dir.scale(Math.cos(a) * total)).add(0.0, Math.sin(a) * total, 0.0));
         p.size = (float)(total / 3.5 * 1.15);
      } else {
         double top = cloudY + 4.1;
         double drop = cam.y - top;
         double a = Math.toRadians(32.0);
         double want = drop / Math.sin(a);
         double total = Math.min(want, maxDist);
         double y = total < want ? cam.y - Math.sin(a) * total : top;
         p = new LyricsRenderer.Place(cam.add(dir.scale(Math.cos(a) * total)).subtract(0.0, cam.y - y, 0.0));
         p.floor = true;
         p.size = (float)(total / 3.5 * 1.15);
      }

      p.kind = 2;
      p.roll = (RNG.nextFloat() - 0.5F) * 5.0F;
      return p;
   }

   private static void puff(PoseStack ps, SubmitNodeCollector out, float cx, float cy, float r, int color, float alpha, float z) {
      if (!culled && !(alpha <= 0.004F) && !(r <= 0.05F)) {
         if (hud) {
            HudLyrics.puff(ps.last().pose(), cx, cy, r, Draw.alpha(color, alpha));
         } else {
            int inner = Draw.alpha(color, alpha);
            int mid = Draw.alpha(color, alpha * 0.55F);
            int outer = color & 16777215;
            int seg = NocturneClient.SETTINGS.quality.get() == 0 ? 8 : NocturneClient.SETTINGS.quality.get() == 1 ? 12 : 16;
            ps.pushPose();
            ps.translate(0.0F, 0.0F, z);
            out.submitCustomGeometry(ps, RenderTypes.debugFilledBox(), (pose, buf) -> {
               for (int i = 0; i < seg; i++) {
                  double a0 = i * Math.PI * 2.0 / (double)seg;
                  double a1 = (i + 1) * Math.PI * 2.0 / (double)seg;
                  float c0 = (float)Math.cos(a0);
                  float s0 = (float)Math.sin(a0);
                  float c1 = (float)Math.cos(a1);
                  float s1 = (float)Math.sin(a1);
                  float hx0 = cx + c0 * r * 0.5F;
                  float hy0 = cy + s0 * r * 0.5F;
                  float hx1 = cx + c1 * r * 0.5F;
                  float hy1 = cy + s1 * r * 0.5F;
                  float x0 = cx + c0 * r;
                  float y0 = cy + s0 * r;
                  float x1 = cx + c1 * r;
                  float y1 = cy + s1 * r;

                  for (int side = 0; side < 2; side++) {
                     boolean f = side == 0;
                     buf.addVertex(pose, cx, cy, 0.0F).setColor(inner);
                     buf.addVertex(pose, f ? hx0 : hx1, f ? hy0 : hy1, 0.0F).setColor(mid);
                     buf.addVertex(pose, f ? hx1 : hx0, f ? hy1 : hy0, 0.0F).setColor(mid);
                     buf.addVertex(pose, f ? hx1 : hx0, f ? hy1 : hy0, 0.0F).setColor(mid);
                     buf.addVertex(pose, f ? hx0 : hx1, f ? hy0 : hy1, 0.0F).setColor(mid);
                     buf.addVertex(pose, f ? x0 : x1, f ? y0 : y1, 0.0F).setColor(outer);
                     buf.addVertex(pose, f ? x1 : x0, f ? y1 : y0, 0.0F).setColor(outer);
                     buf.addVertex(pose, f ? hx1 : hx0, f ? hy1 : hy0, 0.0F).setColor(mid);
                  }
               }
            });
            ps.popPose();
         }
      }
   }

   private static void cloudSlab(PoseStack ps, SubmitNodeCollector out, float hw, float hh, int tint, float a, float seed, float breakup) {
      int cols = Math.max(3, Math.round(hw / 7.0F));

      for (int i = 0; i <= cols; i++) {
         for (int row = 0; row < 2; row++) {
            float t = (float)i / cols;
            float x = -hw + t * 2.0F * hw + (hash(seed + i * 3.1F + row) - 0.5F) * 6.0F + (float)Math.sin(time * 0.7F + i + row) * 1.2F;
            float y = (row == 0 ? -hh * 0.35F : hh * 0.35F)
               + (hash(seed * 2.0F + i * 1.7F + row * 5) - 0.5F) * 4.0F
               + (float)Math.cos(time * 0.6F + i * 1.3F) * 0.8F;
            float edge = 1.0F - Math.abs(t * 2.0F - 1.0F);
            float r = hh * (0.75F + 0.35F * hash(seed + i * 7.7F + row)) * (0.65F + 0.35F * edge) + breakup * 10.0F;
            x *= 1.0F + breakup * 0.6F;
            y -= breakup * 6.0F * hash(i + row * 3.3F);
            puff(ps, out, x, y, r, tint, a * 0.42F * (1.0F - breakup), -0.8F);
         }
      }
   }

   private static void drawCloudLine(
      PoseStack ps,
      SubmitNodeCollector out,
      Vec3 cam,
      LyricsRenderer.Shown s,
      long pos,
      boolean live,
      float fade,
      float breakup,
      LyricsSettings mod,
      float scale,
      DisplayMode mode
   ) {
      Font font = Minecraft.getInstance().font;
      LyricsRenderer.Place pl = at(s);
      int style = mod.cloudStyle.get();
      int n = s.words.size();
      float saveRead = readNow;
      readNow = -1.0F;
      ps.pushPose();
      face(ps, cam, pl, 0.0F, scale);
      if (live && beat > 0.01F) {
         float b = 1.0F + 0.04F * beat;
         ps.scale(b, b, 1.0F);
      }

      float vis = playingFade * fade;
      boolean tintOn = mod.cloudTint.on();
      if (style == 2) {
         float form = live ? Anim.easeOutCubic(s.age / 0.8F) : 1.0F;
         cloudSlab(
            ps, out, s.halfWidth() + 10.0F, s.height / 2.0F + 7.0F, tintOn ? cloudTint : Draw.mix(-1, colorFor(mod, 0, 1), 0.25F), vis * form, s.phase, breakup
         );
      }

      for (LyricsRenderer.Placed w : s.words) {
         long tw = pos - w.word().start();
         if (tw >= 0L) {
            float dur = (float)Math.max(1L, w.word().end() - w.word().start());
            boolean singing = live && (float)tw < dur;
            int tint = tintOn ? cloudTint : colorFor(mod, w.index(), n);
            float seed = w.index() * 7.13F + s.phase;
            float form = Anim.easeOutCubic(Anim.clamp01((float)tw / 650.0F));
            float a = vis * Anim.clamp01((float)tw / 250.0F);
            float sc = w.sc() * (0.85F + 0.15F * form);
            float lift = -(1.0F - form) * 3.0F - breakup * 6.0F;
            float glow = mod.glow.on() ? Math.min(1.0F, cloudGlow * 0.8F + (singing ? 0.4F : 0.0F)) : 0.0F;
            if (style == 2) {
               int rim = Draw.mix(tint, -7504, 0.4F);
               word(
                  ps,
                  out,
                  w.comp(),
                  w.width(),
                  w.cx(),
                  w.cy() + lift,
                  sc,
                  0.0F,
                  Draw.mix(skyTint, -16777216, 0.15F),
                  a * form * (1.0F - breakup),
                  Math.max(0.7F, glow),
                  0.0F,
                  rim,
                  mode
               );
            } else {
               Component comp = w.comp();
               int width = w.width();
               float cx = w.cx();
               if (style == 1) {
                  float wr = Anim.clamp01((float)tw / Math.max(260.0F, Math.min(dur, w.text().length() * 70.0F)));
                  if (breakup > 0.0F || !live) {
                     wr = 1.0F;
                  }

                  int chars = Math.max(1, (int)Math.ceil(w.text().length() * wr));
                  comp = Draw.lyric(w.text().substring(0, Math.min(chars, w.text().length())), w.big(), fontIdx);
                  width = font.width(comp);
                  float left = w.cx() - w.width() * w.sc() / 2.0F;
                  cx = left + width * w.sc() / 2.0F;
                  if (live && wr < 1.0F) {
                     float pen = left + width * w.sc();
                     puff(ps, out, pen, w.cy(), 2.2F, -1, 0.6F * a, 0.2F);
                     mistTimer = mistTimer + frameDt;

                     while (mistTimer > 0.03F && SPARKS.size() + MIST.size() < particleBudget()) {
                        mistTimer -= 0.03F;
                        LyricsRenderer.Mist m = new LyricsRenderer.Mist();
                        m.place = pl;
                        m.x = pen;
                        m.y = w.cy() + (RNG.nextFloat() - 0.5F) * 3.0F;
                        m.vx = (RNG.nextFloat() - 0.5F) * 4.0F;
                        m.vy = (RNG.nextFloat() - 0.5F) * 2.0F;
                        m.r = 2.2F;
                        m.grow = 5.0F;
                        m.life = 2.6F;
                        m.alpha = 0.35F;
                        m.color = tint;
                        MIST.add(m);
                     }
                  }
               }

               int puffs = style == 1 ? 2 : 4;

               for (int k = 0; k < puffs; k++) {
                  float hx = (hash(seed + k * 1.9F) - 0.5F) * width * sc;
                  float hy = (hash(seed * 1.3F + k * 2.7F) - 0.5F) * 6.0F;
                  float r = (4.0F + hash(seed + k * 5.1F) * 4.0F) * w.sc() * (0.6F + 0.4F * form) + breakup * 8.0F;
                  hx *= 1.0F + breakup * 1.5F;
                  hy -= breakup * 5.0F * (0.5F + hash(seed + k));
                  puff(ps, out, cx + hx, w.cy() + hy + (float)Math.sin(time * 0.9F + seed + k) * 0.6F, r, tint, 0.16F * a * (1.0F - breakup), -0.6F);
               }

               float spread = (1.0F - form) * 4.0F + breakup * 9.0F;

               for (int k = 0; k < 8; k++) {
                  double ang = k * Math.PI / 4.0 + seed;
                  float ox = (float)Math.cos(ang) * (1.1F + spread);
                  float oy = (float)Math.sin(ang) * (1.1F + spread) * 0.7F;
                  word(
                     ps,
                     out,
                     comp,
                     width,
                     cx + ox,
                     w.cy() + oy + lift,
                     sc * (1.03F + spread * 0.02F),
                     0.0F,
                     tint,
                     a * 0.12F * (1.0F - breakup * 0.6F),
                     0.0F,
                     0.0F,
                     tint,
                     mode
                  );
               }

               float core = a * (1.0F - breakup) * (1.0F - breakup);
               word(ps, out, comp, width, cx, w.cy() + lift, sc, 0.0F, Draw.mix(tint, -1, 0.3F), core, glow, 0.0F, tint, mode);
            }
         }
      }

      ps.popPose();
      readNow = saveRead;
   }

   private static void mist(PoseStack ps, SubmitNodeCollector out, Vec3 cam, float dt, float scale) {
      Iterator<LyricsRenderer.Mist> it = MIST.iterator();

      while (it.hasNext()) {
         LyricsRenderer.Mist m = it.next();
         m.age += dt;
         if (m.age >= m.life) {
            it.remove();
         } else {
            m.x = m.x + m.vx * dt;
            m.y = m.y + m.vy * dt;
            float k = m.age / m.life;
            float a = m.alpha * (1.0F - k) * (1.0F - k) * playingFade * Anim.clamp01(m.age / 0.15F);
            ps.pushPose();
            face(ps, cam, m.place, 0.0F, scale);
            puff(ps, out, m.x, m.y, m.r + m.grow * k, m.color, a, -0.5F);
            ps.popPose();
         }
      }
   }

   private static void battleHits(Minecraft mc, Vec3 cam, LyricsRenderer.Shown s, long pos, LyricsSettings mod, float I, float partial) {
      if (s.index != lastBattleLine) {
         lastBattleLine = s.index;
         lastBattleHit = -1;
      }

      int n = s.words.size();

      for (LyricsRenderer.Placed w : s.words) {
         long tw = pos - w.word().start();
         if (tw >= 0L && w.index() > lastBattleHit) {
            lastBattleHit = w.index();
            if (tw <= 600L) {
               spawnBattle(mc, cam, w, colorFor(mod, w.index(), n), mod, I, partial);
            }
         }
      }
   }

   private static void spawnBattle(Minecraft mc, Vec3 cam, LyricsRenderer.Placed w, int color, LyricsSettings mod, float I, float partial) {
      Vec3 look = mc.player.getViewVector(partial);
      Vec3 anchor = target != null ? target.getEyePosition(partial).add(0.0, -0.25, 0.0) : cam.add(look.scale(4.5));
      Vec3 toA = anchor.subtract(cam);
      double dist = toA.length();
      if (dist < 1.5) {
         anchor = cam.add(look.scale(3.0));
         toA = anchor.subtract(cam);
         dist = 3.0;
      }

      Vec3 fwd = toA.normalize();
      Vec3 right = fwd.cross(new Vec3(0.0, 1.0, 0.0));
      right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
      Vec3 up = right.cross(fwd).normalize();
      int side = w.index() % 2 == 0 ? 1 : -1;
      if (RNG.nextFloat() < 0.25F) {
         side = -side;
      }

      double k = Math.max(0.8, dist / 4.5);
      Vec3 at = anchor.add(right.scale(side * (0.95 + RNG.nextDouble() * 1.25) * k))
         .add(up.scale((-0.35 + RNG.nextDouble() * 1.55) * k))
         .subtract(fwd.scale(0.8));
      double cone = Math.cos(Math.toRadians(10.0));

      for (int i = 0; i < 8 && at.subtract(cam).normalize().dot(look) > cone; i++) {
         at = at.add(right.scale(side * 0.45 * k));
      }

      List<AABB> boxes = new ArrayList<>();
      if (mc.level != null) {
         for (AbstractClientPlayer pl : mc.level.players()) {
            if (pl != mc.player) {
               boxes.add(pl.getBoundingBox().inflate(0.35));
            }
         }
      }

      if (target != null) {
         boxes.add(target.getBoundingBox().inflate(0.35));
      }

      for (int i = 0; i < 6; i++) {
         boolean inside = false;

         for (AABB b : boxes) {
            if (b.contains(at)) {
               inside = true;
            }
         }

         if (!inside) {
            break;
         }

         at = at.add(right.scale(side * 0.6));
      }

      LyricsRenderer.BattleWord bw = new LyricsRenderer.BattleWord();
      bw.place = new LyricsRenderer.Place(at);
      bw.place.size = (float)(at.distanceTo(cam) / 3.5 * (w.big() ? 1.9 : 1.3));
      bw.comp = Draw.lyric(w.text(), true, fontIdx == 3 ? 3 : (fontIdx == 1 ? 1 : 2));
      bw.width = mc.font.width(bw.comp);
      bw.life = w.big() ? 1.7F : 1.25F;
      bw.roll = side * (6.0F + RNG.nextFloat() * 10.0F);
      bw.color = color;
      bw.big = w.big();
      BATTLE.add(bw);

      while (BATTLE.size() > 9) {
         BATTLE.remove(0);
      }

      boolean hype = mod.intensity.get() == 2;
      if (w.big() || hype) {
         impact = Math.max(impact, w.big() ? 1.0F : 0.45F);
      }

      if (w.big()) {
         flash = Math.max(flash, 0.8F);
      }

      if (mod.sparkles.on()) {
         burst(bw.place, 0.0F, 0.0F, bw.width, color, w.big() ? Math.round(10.0F * Math.max(0.6F, I)) : (hype ? 4 : 2), I);
      }
   }

   private static void battle(PoseStack ps, SubmitNodeCollector out, Vec3 cam, float dt, LyricsSettings mod, float scale, float I, DisplayMode mode) {
      Iterator<LyricsRenderer.BattleWord> it = BATTLE.iterator();

      while (it.hasNext()) {
         LyricsRenderer.BattleWord b = it.next();
         b.age += dt;
         float leave = b.age - b.life;
         if (leave > 0.32F) {
            it.remove();
         } else {
            b.place.ready = false;
            b.place.update(cam, dt, true);
            float e = Anim.clamp01(b.age / 0.24F);
            float sc = 2.6F + -1.5999999F * Anim.easeOutBack(e);
            float l = leave > 0.0F ? leave / 0.32F : 0.0F;
            sc *= (1.0F - 0.35F * l) * (1.0F + 0.08F * beat * Math.min(1.4F, I));
            float alpha = Anim.clamp01(b.age / 0.06F) * (1.0F - l) * playingFade;
            float dy = leave > 0.0F ? -l * l * 14.0F : (1.0F - Anim.easeOutCubic(e)) * -6.0F;
            float chroma = mod.chroma.on() ? (1.0F - e) * 3.2F * I + l * 3.0F : 0.0F;
            float glow = mod.glow.on() ? (b.big ? 1.0F : 0.7F) * (1.0F - 0.4F * Anim.clamp01(b.age / b.life)) : 0.0F;
            int c = Draw.mix(-1, b.color, Anim.easeOutCubic(b.age / 0.3F));
            ps.pushPose();
            face(ps, cam, b.place, 0.0F, scale);
            if (mod.shakeEffects.on() && !mod.reducedMotion.on() && impact > 0.02F) {
               float k = impact * I * 1.4F;
               ps.translate((float)Math.sin(time * 71.0F + b.roll) * k, (float)Math.cos(time * 53.0F) * k * 0.7F, 0.0F);
            }

            word(ps, out, b.comp, b.width, 0.0F, dy, sc, b.roll * (0.6F + 0.4F * (1.0F - e)), c, alpha, glow, chroma, b.color, mode);
            ps.popPose();
         }
      }
   }

   private static void burst(LyricsRenderer.Place place, float x, float y, int width, int col, int count, float I) {
      for (int i = 0; i < count && SPARKS.size() + MIST.size() < particleBudget(); i++) {
         LyricsRenderer.Spark sp = new LyricsRenderer.Spark();
         sp.place = place;
         sp.x = x + (RNG.nextFloat() - 0.5F) * width;
         sp.y = y + (RNG.nextFloat() - 0.5F) * 6.0F;
         double a = RNG.nextDouble() * Math.PI * 2.0;
         float sp0 = (25.0F + RNG.nextFloat() * 45.0F) * Math.max(0.7F, I);
         sp.vx = (float)Math.cos(a) * sp0;
         sp.vy = (float)Math.sin(a) * sp0 - 25.0F;
         sp.life = 0.5F + RNG.nextFloat() * 0.5F;
         sp.size = 0.45F + RNG.nextFloat() * 0.5F;
         sp.spin = (RNG.nextFloat() - 0.5F) * 400.0F;
         sp.color = RNG.nextFloat() < 0.35F ? -1 : col;
         sp.glyph = RNG.nextFloat() < 0.6F ? "✦" : "•";
         SPARKS.add(sp);
      }
   }

   private static LyricsRenderer.Shown layout(Font font, Lyrics.Line line, int index, LyricsRenderer.Place place) {
      float bigMul = place.kind == 1 ? 1.55F : 1.25F;
      float smallMul = place.kind == 1 ? 0.82F : 1.0F;
      float rowH = place.kind == 1 ? 14.95F : 13.0F;
      List<Lyrics.Word> words = line.words();
      int n = words.size();
      boolean[] big = new boolean[n];
      int bigCount = 0;

      for (int i = 0; i < n && bigCount < 2; i++) {
         Lyrics.Word w = words.get(i);
         String t = w.text().replaceAll("[^\\p{L}\\p{N}]", "");
         boolean caps = t.length() >= 2 && t.equals(t.toUpperCase()) && !t.equals(t.toLowerCase());
         boolean held = w.end() - w.start() >= 750L && t.length() >= 3;
         boolean last = i == n - 1 && n >= 3 && t.length() >= 4;
         if (w.text().contains("!") || caps || held || last) {
            big[i] = true;
            bigCount++;
         }
      }

      List<List<Integer>> rows = new ArrayList<>();
      List<Integer> row = new ArrayList<>();
      int rowW = 0;
      int[] widths = new int[n];
      Component[] comps = new Component[n];

      for (int i = 0; i < n; i++) {
         comps[i] = Draw.lyric(words.get(i).text(), big[i], fontIdx);
         widths[i] = Math.round(font.width(comps[i]) * (big[i] ? bigMul : smallMul));
         int add = (row.isEmpty() ? 0 : 4) + widths[i];
         if (!row.isEmpty() && rowW + add > 200) {
            rows.add(row);
            row = new ArrayList<>();
            rowW = 0;
            add = widths[i];
         }

         row.add(i);
         rowW += add;
      }

      if (!row.isEmpty()) {
         rows.add(row);
      }

      List<LyricsRenderer.Placed> placed = new ArrayList<>();
      float top = -(rows.size() - 1) * rowH / 2.0F;

      for (int r = 0; r < rows.size(); r++) {
         List<Integer> rw = rows.get(r);
         int total = -4;

         for (int i : rw) {
            total += widths[i] + 4;
         }

         float x = -total / 2.0F;

         for (int i : rw) {
            placed.add(
               new LyricsRenderer.Placed(
                  words.get(i),
                  comps[i],
                  words.get(i).text(),
                  x + widths[i] / 2.0F,
                  top + r * rowH,
                  font.width(comps[i]),
                  i,
                  big[i],
                  big[i] ? bigMul : smallMul
               )
            );
            x += widths[i] + 4;
         }
      }

      return new LyricsRenderer.Shown(index, placed, rows.size() * rowH, place);
   }

   private static int colorFor(LyricsSettings mod, int i, int n) {
      if (mod.highContrast.on()) return -1;
      return switch (mod.colorMode.get()) {
         case 0 -> mod.color.argb();
         default -> Draw.mix(mod.color.argb(), mod.color2.argb(), n <= 1 ? 0.0F : (float)i / (n - 1));
         case 2 -> Draw.hsv((time * 0.12F + i * 0.08F) % 1.0F, 0.5F, 1.0F);
         case 3 -> {
            int album = LyricsService.INSTANCE.albumColor();
            yield album == 0
               ? Draw.mix(mod.color.argb(), mod.color2.argb(), n <= 1 ? 0.0F : (float)i / (n - 1))
               : Draw.mix(album, Draw.mix(album, -1, 0.55F), n <= 1 ? 0.0F : (float)i / (n - 1));
         }
      };
   }

   private static void drawLine(
      PoseStack ps,
      SubmitNodeCollector out,
      Font font,
      Vec3 cam,
      LyricsRenderer.Shown s,
      long pos,
      boolean live,
      float fade,
      LyricsSettings mod,
      float scale,
      float I,
      DisplayMode mode
   ) {
      if (at(s).kind == 2) {
         drawCloudLine(ps, out, cam, s, pos, live, fade, 0.0F, mod, scale, mode);
      } else {
         int style = mod.reducedMotion.on() ? 1 : mod.style.get();
         int n = s.words.size();
         boolean sky = at(s).kind == 1;
         ps.pushPose();
         face(ps, cam, at(s), 0.0F, scale);
         if (live && beat > 0.01F) {
            float b = 1.0F + 0.06F * beat * Math.min(1.4F, I);
            ps.scale(b, b, 1.0F);
         }

         if (live && mod.shakeEffects.on() && !mod.reducedMotion.on() && impact > 0.02F) {
            float k = impact * I * 1.8F;
            ps.translate((float)Math.sin(time * 71.0F) * k, (float)Math.cos(time * 53.0F) * k * 0.7F, 0.0F);
         }

         float vis = playingFade * fade;

         for (LyricsRenderer.Placed w : s.words) {
            long tw = pos - w.word().start();
            float dur = (float)Math.max(1L, w.word().end() - w.word().start());
            float p = Anim.clamp01((float)tw / dur);
            if (live && tw >= 0L && w.index() > lastHit) {
               lastHit = w.index();
               if (tw < 350L) {
                  onHit(s, w, mod, I, colorFor(mod, w.index(), n));
               }
            }

            int col = colorFor(mod, w.index(), n);
            float bigScale = w.sc();
            boolean singing = live && tw >= 0L && p < 1.0F;
            ps.pushPose();
            if (sky) {
               ps.translate(0.0F, 0.0F, (w.big() ? 9.0F : -4.0F - w.index() % 3 * 4.0F) * (1.0F - orbitBlend));
            }

            switch (style) {
               case 1: {
                  float in = Anim.clamp01((s.age - w.index() * 0.035F) / 0.35F);
                  float dy = (1.0F - Anim.easeOutBack(in)) * 7.0F;
                  int c = tw < 0L ? Draw.alpha(-1, 0.55F) : (singing ? Draw.mix(Draw.alpha(-1, 0.85F), col, Anim.easeOutCubic(p * 1.5F)) : col);
                  float pop = singing ? 1.0F + 0.2F * (float)Math.sin(p * Math.PI) * Math.min(1.3F, I) : 1.0F;
                  word(
                     ps,
                     out,
                     w.comp(),
                     w.width(),
                     w.cx(),
                     w.cy() + dy - (pop - 1.0F) * 6.0F,
                     bigScale * pop,
                     0.0F,
                     c,
                     in * vis,
                     mod.glow.on() && singing ? 1.0F : 0.0F,
                     0.0F,
                     col,
                     mode
                  );
                  break;
               }
               case 2: {
                  if (tw < 0L) {
                     break;
                  }

                  float typeDur = Math.min(dur, w.text().length() * 55.0F);
                  int chars = Math.max(1, (int)Math.ceil(w.text().length() * Anim.clamp01((float)tw / typeDur)));
                  String part = w.text().substring(0, Math.min(chars, w.text().length()));
                  Component pc = Draw.lyric(part, w.big(), fontIdx);
                  int pw = font.width(pc);
                  float left = w.cx() - w.width() * bigScale / 2.0F;
                  boolean typing = chars < w.text().length();
                  int c = singing ? col : Draw.mix(-1, col, w.big() ? 1.0F : 0.3F);
                  float chroma = mod.chroma.on() && typing ? 0.9F * I : 0.0F;
                  word(ps, out, pc, pw, left + pw * bigScale / 2.0F, w.cy(), bigScale, 0.0F, c, vis, mod.glow.on() && singing ? 0.8F : 0.0F, chroma, col, mode);
                  if (singing && ((int)(time * 4.0F) % 2 == 0 || typing)) {
                     ps.pushPose();
                     ps.translate(left + pw * bigScale + 1.0F, w.cy() - 4.0F, 0.1F);
                     text(ps, out, 0.0F, 0.0F, Draw.seq("▏"), false, mode, Draw.alpha(col, vis));
                     ps.popPose();
                  }
                  break;
               }
               case 3: {
                  float in = Anim.clamp01((s.age - w.index() * 0.04F) / 0.4F);
                  float wave = tw >= 0L ? (float)Math.sin(time * 5.0F + w.index() * 0.7F) * 2.2F * I : 0.0F;
                  float jump = singing ? -((float)Math.sin(p * Math.PI)) * 6.0F * I : 0.0F;
                  int c = tw < 0L ? Draw.alpha(-1, 0.4F) : col;
                  word(
                     ps,
                     out,
                     w.comp(),
                     w.width(),
                     w.cx(),
                     w.cy() + wave + jump + (1.0F - Anim.easeOutCubic(in)) * 5.0F,
                     bigScale * (singing ? 1.1F : 1.0F),
                     singing ? (float)Math.sin(p * Math.PI * 2.0) * 6.0F : 0.0F,
                     c,
                     in * vis,
                     mod.glow.on() && singing ? 1.0F : 0.0F,
                     0.0F,
                     col,
                     mode
                  );
                  break;
               }
               case 4:
                  if (tw >= 0L) {
                     float e = Anim.clamp01((float)tw / 460.0F);
                     float dyx = -(1.0F - bounce(e)) * 20.0F * Math.max(0.7F, I);
                     float squash = e > 0.35F && e < 0.6F ? 1.0F - 0.12F * (float)Math.sin((e - 0.35F) / 0.25F * Math.PI) : 1.0F;
                     float scx = bigScale * (singing ? 1.0F + 0.05F * (float)Math.sin(p * Math.PI) * I : 1.0F);
                     int target = !singing && !w.big() ? Draw.mix(-1, col, 0.35F) : col;
                     float glowx = mod.glow.on() ? (singing ? 0.9F : 0.0F) * (live ? 1.0F : 0.6F) : 0.0F;
                     ps.pushPose();
                     ps.translate(w.cx(), w.cy() + dyx, 0.0F);
                     ps.scale(1.0F / squash, squash, 1.0F);
                     ps.translate(-w.cx(), -w.cy(), 0.0F);
                     word(ps, out, w.comp(), w.width(), w.cx(), w.cy(), scx, 0.0F, target, Anim.clamp01((float)tw / 90.0F) * vis, glowx, 0.0F, col, mode);
                     ps.popPose();
                  }
                  break;
               case 5: {
                  if (tw < 0L) {
                     break;
                  }

                  float e = Anim.clamp01((float)tw / 300.0F);
                  String txt = w.text();
                  Component c0 = w.comp();
                  int width = w.width();
                  if (e < 1.0F) {
                     int shown = (int)Math.floor(txt.length() * e);
                     StringBuilder b = new StringBuilder(txt.substring(0, shown));

                     for (int k = shown; k < txt.length(); k++) {
                        char ch = txt.charAt(k);
                        b.append(
                           Character.isWhitespace(ch)
                              ? ch
                              : "#%&@$*!?<>/\\=+".charAt(Math.floorMod((int)(time * 30.0F) * 7 + k * 13 + w.index() * 31, "#%&@$*!?<>/\\=+".length()))
                        );
                     }

                     c0 = Draw.lyric(b.toString(), w.big(), fontIdx);
                     width = font.width(c0);
                  }

                  float flick = hash(w.index() * 7.1F + (float)Math.floor(time * 18.0F)) < (singing ? 0.12F : 0.03F) * I ? 1.0F : 0.0F;
                  float jx = ((1.0F - e) * 3.0F + flick * 2.0F) * I * (hash(w.index() + time * 40.0F) - 0.5F) * 2.0F;
                  float jy = (1.0F - e) * 1.5F * I * (hash(w.index() * 3.3F + time * 37.0F) - 0.5F) * 2.0F;
                  float chroma = mod.chroma.on() ? ((1.0F - e) * 2.8F + flick * 1.6F) * I : 0.0F;
                  int c = !singing && !w.big() ? Draw.mix(-1, col, 0.4F) : col;
                  float glow = mod.glow.on() && singing ? 0.8F : 0.0F;
                  word(ps, out, c0, width, w.cx() + jx, w.cy() + jy, bigScale, 0.0F, c, Anim.clamp01((float)tw / 50.0F) * vis, glow, chroma, col, mode);
                  break;
               }
               case 6: {
                  float in = Anim.clamp01((s.age - w.index() * 0.06F) / 0.7F);
                  float dy = (1.0F - Anim.easeOutCubic(in)) * 9.0F;
                  float lit = tw < 0L ? 0.0F : Anim.easeOutCubic(Anim.clamp01((float)tw / 260.0F));
                  int c = Draw.mix(Draw.alpha(-1, 0.5F), !singing && !w.big() ? Draw.mix(-1, col, 0.55F) : col, lit);
                  float sc = bigScale * (1.0F + (singing ? 0.04F * (float)Math.sin(p * Math.PI) : 0.0F));
                  float glow = mod.glow.on() && singing ? 0.6F : 0.0F;
                  word(ps, out, w.comp(), w.width(), w.cx(), w.cy() + dy, sc, 0.0F, c, in * vis, glow, 0.0F, col, mode);
                  break;
               }
               case 7:
                  if (tw >= 0L) {
                     float e = Anim.clamp01((float)tw / 320.0F);
                     float k = 1.0F - (float)Math.pow(2.0, -10.0F * e);
                     float from = (w.big() ? 3.6F : 2.8F) * Math.max(0.8F, Math.min(1.3F, I));
                     float sc = (from + (1.0F - from) * k) * bigScale * (singing ? 1.06F : 1.0F);
                     float alpha = Anim.clamp01((float)tw / 180.0F) * vis;
                     int c = !singing && !w.big() ? Draw.mix(-1, col, 0.3F) : col;
                     float glow = mod.glow.on() ? (singing ? 1.0F : (1.0F - k) * 0.8F) * (live ? 1.0F : 0.6F) : 0.0F;
                     float chroma = mod.chroma.on() ? (1.0F - k) * 2.2F * I : 0.0F;
                     word(ps, out, w.comp(), w.width(), w.cx(), w.cy(), sc, 0.0F, c, alpha, glow, chroma, col, mode);
                  }
                  break;
               default:
                  if (tw >= 0L) {
                     float e = Anim.clamp01((float)tw / (w.big() ? 340.0F : 240.0F));
                     float back = Anim.easeOutBack(e);
                     float cubic = Anim.easeOutCubic(e);
                     float from = w.big() ? 2.4F : 1.75F;
                     float scx = (from + (1.0F - from) * back) * bigScale;
                     if (singing) {
                        scx *= 1.0F + 0.06F * (float)Math.sin(p * Math.PI) * I;
                     }

                     float alpha = Anim.clamp01((float)tw / 70.0F) * vis;
                     float dyx = (1.0F - cubic) * -8.0F * I;
                     float rot = (1.0F - cubic) * (w.index() % 2 == 0 ? 1 : -1) * 13.0F * I;
                     float chroma = mod.chroma.on() ? (1.0F - e) * 3.5F * I + (w.big() && singing ? 0.5F * I : 0.0F) : 0.0F;
                     int target = !singing && !w.big() ? Draw.mix(-1, col, 0.3F) : col;
                     int cx = Draw.mix(-1, target, Anim.easeOutCubic((float)tw / 220.0F));
                     float glowx = mod.glow.on() ? (singing ? 1.0F : (w.big() ? 0.55F : 0.0F)) * (live ? 1.0F : 0.6F) : 0.0F;
                     word(ps, out, w.comp(), w.width(), w.cx(), w.cy() + dyx, scx, rot, cx, alpha, glowx, chroma, col, mode);
                  }
            }

            ps.popPose();
         }

         ps.popPose();
      }
   }

   private static void onHit(LyricsRenderer.Shown s, LyricsRenderer.Placed w, LyricsSettings mod, float I, int col) {
      boolean hype = mod.intensity.get() == 2;
      if (w.big()) {
         impact = Math.max(impact, 1.0F);
         flash = Math.max(flash, 1.0F);
      } else if (hype || mod.style.get() == 0) {
         impact = Math.max(impact, hype ? 0.4F : 0.18F);
      }

      if (mod.sparkles.on()) {
         int count = w.big() ? Math.round(9.0F * Math.max(0.6F, I)) : (hype ? 3 : 0);

         for (int i = 0; i < count && SPARKS.size() + MIST.size() < particleBudget(); i++) {
            LyricsRenderer.Spark sp = new LyricsRenderer.Spark();
            sp.place = at(s);
            sp.x = w.cx() + (RNG.nextFloat() - 0.5F) * w.width();
            sp.y = w.cy() + (RNG.nextFloat() - 0.5F) * 6.0F;
            double a = RNG.nextDouble() * Math.PI * 2.0;
            float sp0 = (25.0F + RNG.nextFloat() * 45.0F) * Math.max(0.7F, I);
            sp.vx = (float)Math.cos(a) * sp0;
            sp.vy = (float)Math.sin(a) * sp0 - 25.0F;
            sp.life = 0.5F + RNG.nextFloat() * 0.5F;
            sp.size = 0.45F + RNG.nextFloat() * 0.5F;
            sp.spin = (RNG.nextFloat() - 0.5F) * 400.0F;
            sp.color = RNG.nextFloat() < 0.35F ? -1 : col;
            sp.glyph = RNG.nextFloat() < 0.6F ? "✦" : "•";
            SPARKS.add(sp);
         }
      }
   }

   private static void drawLeaving(
      PoseStack ps, SubmitNodeCollector out, Font font, Vec3 cam, LyricsRenderer.Shown s, float age, LyricsSettings mod, float scale, float I, DisplayMode mode
   ) {
      int n = s.words.size();
      float startFade = mod.follow.get() != 2 ? 0.55F : 0.9F;
      ps.pushPose();
      face(ps, cam, at(s), 0.0F, scale);

      for (LyricsRenderer.Placed w : s.words) {
         float t = Anim.clamp01((age - w.index() * 0.025F) / 0.45F);
         float te = t * t;
         float dy = -te * (12 + w.index() % 3 * 4) * (0.6F + 0.4F * I);
         float dx = w.cx() * te * 0.35F;
         float rot = (w.index() % 2 == 0 ? 1 : -1) * te * 18.0F * I;
         float sc = (1.0F - 0.3F * te) * w.sc();
         float alpha = (1.0F - t) * startFade * playingFade;
         int col = colorFor(mod, w.index(), n);
         int c = w.big() ? col : Draw.mix(-1, col, 0.3F);
         float chroma = mod.chroma.on() ? te * 3.5F * I : 0.0F;
         word(ps, out, w.comp(), w.width(), w.cx() + dx, w.cy() + dy, sc, rot, c, alpha, 0.0F, chroma, col, mode);
      }

      ps.popPose();
   }

   private static void titleCard(
      PoseStack ps, SubmitNodeCollector out, Font font, Vec3 cam, NowPlaying np, LyricsSettings mod, LyricsRenderer.Place place, float scale, DisplayMode mode
   ) {
      float t = songAge;
      float fade = Anim.clamp01((5.5F - t) / 0.7F);
      int col = mod.color.argb();
      int col2 = mod.colorMode.get() == 0 ? col : mod.color2.argb();
      String title = Draw.trim(font, np.title(), 180);
      float track = (1.0F - Anim.easeOutCubic(t / 1.1F)) * 5.0F;
      int[] cps = title.codePoints().toArray();
      float total = font.width(Draw.lyric(title, true, fontIdx)) + track * Math.max(0, cps.length - 1);
      ps.pushPose();
      face(ps, cam, place, 0.9F, scale);
      ps.scale(1.6F, 1.6F, 1.0F);
      float x = -total / 2.0F;

      for (int i = 0; i < cps.length; i++) {
         String ch = new String(Character.toChars(cps[i]));
         float a = Anim.clamp01((t - i * 0.03F) / 0.3F);
         float dy = (1.0F - Anim.easeOutBack(a)) * 6.0F;
         int c = Draw.mix(-1, Draw.mix(col, col2, cps.length <= 1 ? 0.0F : (float)i / (cps.length - 1)), 0.25F);
         Component cc = Draw.lyric(ch, true, fontIdx);
         text(ps, out, x, -4.0F + dy, cc.getVisualOrderText(), true, mode, Draw.alpha(c, a * fade));
         x += font.width(cc) + track;
      }

      ps.popPose();
      float bar = Anim.easeOutCubic((t - 0.25F) / 0.8F);
      String artist = Draw.trim(font, np.artist().toUpperCase(), 200);
      float aw = Draw.width(font, artist) + 24;
      ps.pushPose();
      face(ps, cam, place, 0.62F, scale);
      quad(ps, out, -aw / 2.0F * bar, -0.6F, aw / 2.0F * bar, 0.6F, Draw.alpha(col, 0.9F * fade));
      float aa = Anim.clamp01((t - 0.45F) / 0.4F) * fade;
      text(ps, out, -Draw.width(font, artist) / 2.0F, 4.0F + (1.0F - Anim.easeOutCubic(aa)) * 4.0F, Draw.seq(artist), false, mode, Draw.alpha(col, aa));
      ps.popPose();
   }

   private static void notes(
      PoseStack ps, SubmitNodeCollector out, Font font, Vec3 cam, LyricsRenderer.Place at, LyricsSettings mod, float scale, float a, DisplayMode mode
   ) {
      if (!(a <= 0.01F)) {
         String[] glyphs = new String[]{"♪", "♫", "♪"};
         ps.pushPose();
         face(ps, cam, at, 0.0F, scale * 1.4F);

         for (int i = 0; i < glyphs.length; i++) {
            float dy = (float)Math.sin(time * 4.0F + i * 1.1F) * 3.0F;
            float pulse = 0.6F + 0.4F * (float)Math.sin(time * 4.0F + i * 1.1F + 1.0F);
            int c = colorFor(mod, i, glyphs.length);
            float x = (i - 1) * 14 - Draw.width(font, glyphs[i]) / 2.0F;
            text(ps, out, x, -4.0F + dy, Draw.seq(glyphs[i]), true, mode, Draw.alpha(c, a * pulse * playingFade));
         }

         ps.popPose();
      }
   }

   private static void countdown(
      PoseStack ps, SubmitNodeCollector out, Font font, Vec3 cam, LyricsRenderer.Place at, LyricsSettings mod, float scale, float secondsLeft, DisplayMode mode
   ) {
      ps.pushPose();
      face(ps, cam, at, 0.0F, scale * 1.5F);

      for (int i = 0; i < 3; i++) {
         float s = Anim.easeOutBack(Anim.clamp01(secondsLeft - i));
         if (!(s <= 0.01F)) {
            ps.pushPose();
            ps.translate((i - 1) * 9, 0.0F, 0.0F);
            ps.scale(s, s, 1.0F);
            text(ps, out, -Draw.width(font, "•") / 2.0F, -4.0F, Draw.seq("•"), true, mode, Draw.alpha(colorFor(mod, i, 3), Math.min(1.0F, s)));
            ps.popPose();
         }
      }

      ps.popPose();
   }

   private static void sparks(PoseStack ps, SubmitNodeCollector out, Font font, Vec3 cam, float dt, float scale, DisplayMode mode) {
      Iterator<LyricsRenderer.Spark> it = SPARKS.iterator();

      while (it.hasNext()) {
         LyricsRenderer.Spark s = it.next();
         s.age += dt;
         if (s.age >= s.life) {
            it.remove();
         } else {
            s.x = s.x + s.vx * dt;
            s.y = s.y + s.vy * dt;
            s.vy += 70.0F * dt;
            s.vx = s.vx * (float)Math.exp(-dt * 2.0F);
            float k = s.age / s.life;
            ps.pushPose();
            face(ps, cam, s.place, 0.0F, scale);
            ps.translate(s.x, s.y, 0.3F);
            rot(ps, com.mojang.math.Axis.ZP.rotationDegrees(s.spin * s.age));
            float sz = s.size * (1.0F - k * 0.5F);
            ps.scale(sz, sz, 1.0F);
            text(ps, out, -Draw.width(font, s.glyph) / 2.0F, -4.0F, Draw.seq(s.glyph), false, mode, Draw.alpha(s.color, (1.0F - k * k) * playingFade));
            ps.popPose();
         }
      }
   }

   private static float bounce(float t) {
      float n = 7.5625F;
      float d = 2.75F;
      if (t < 1.0F / d) {
         return n * t * t;
      } else if (t < 2.0F / d) {
         float var5;
         return n * (var5 = t - 1.5F / d) * var5 + 0.75F;
      } else {
         float var3;
         float var4;
         return t < 2.5F / d ? n * (var3 = t - 2.25F / d) * var3 + 0.9375F : n * (var4 = t - 2.625F / d) * var4 + 0.984375F;
      }
   }

   private static float hash(float x) {
      float s = (float)Math.sin(x * 12.9898F) * 43758.547F;
      return s - (float)Math.floor(s);
   }

   private static void face(PoseStack ps, Vec3 cam, LyricsRenderer.Place place, float up, float scale) {
      Vec3 at = place.pos();
      LyricsSettings settings = NocturneClient.SETTINGS;
      Vec3 relative = at.subtract(cam);
      culled = relative.lengthSqr() > settings.renderDistance.get() * settings.renderDistance.get();
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && relative.lengthSqr() > 4 && relative.normalize().dot(client.player.getViewVector(1)) < -0.2) culled = true;
      if (hud) {
         HudLyrics.group((float)at.distanceToSqr(cam), visibility(place, at.add(0.0, up, 0.0)));
      }

      ps.translate(at.x - cam.x, at.y + up - cam.y, at.z - cam.z);
      rot(ps, com.mojang.math.Axis.YP.rotation(place.yaw + editorYaw));
      ps.translate(editorX, editorY, 0);
      rot(ps, com.mojang.math.Axis.XP.rotation(-place.pitch + editorPitch));
      if (place.roll != 0.0F) {
         rot(ps, com.mojang.math.Axis.ZP.rotationDegrees(place.roll));
      }

      rot(ps, com.mojang.math.Axis.ZP.rotation(editorRoll));
      float s = scale * place.size;
      ps.scale(s, -s, s);
   }

   private static float visibility(LyricsRenderer.Place place, Vec3 at) {
      if (seeThroughNow) {
         return 1.0F;
      }

      if (place.visFrame == frameNo) {
         return place.hudVis;
      }

      boolean first = place.visFrame == 0;
      place.visFrame = frameNo;
      Minecraft mc = Minecraft.getInstance();
      float target = 1.0F;
      if (mc.level != null && mc.player != null && at.distanceToSqr(camNow) > 0.25) {
         Vec3 to = at.add(0.0, place.floor ? 0.12 : 0.0, 0.0);
         HitResult hit = mc.level.clip(new ClipContext(camNow, to, Block.COLLIDER, Fluid.NONE, mc.player));
         if (hit.getType() == Type.BLOCK && hit.getLocation().distanceTo(camNow) < camNow.distanceTo(to) - 0.3) {
            target = 0.0F;
         }

         if (target == 0.0F) {
            LyricsDebug.behindWall();
         }
      }

      place.hudVis = first ? target : place.hudVis + (target - place.hudVis) * (1.0F - (float)Math.exp(-frameDt * 10.0F));
      return place.hudVis;
   }

   private static void word(
      PoseStack ps,
      SubmitNodeCollector out,
      Component c,
      int width,
      float cx,
      float cy,
      float sc,
      float rotDeg,
      int color,
      float alpha,
      float glow,
      float chroma,
      int glowColor,
      DisplayMode mode
   ) {
      if (!(alpha <= 0.01F)) {
         LyricsSettings settings = NocturneClient.SETTINGS;
         if (settings.reducedMotion.on()) { rotDeg = 0; chroma = 0; sc = 1; }
         if (settings.quality.get() == 0) glow = 0;
         if (settings.highContrast.on()) { color = -1; readNow = 1; }
         FormattedCharSequence s = c.getVisualOrderText();
         ps.pushPose();
         ps.translate(cx, cy, 0.0F);
         if (Math.abs(rotDeg) > 0.01F) {
            rot(ps, com.mojang.math.Axis.ZP.rotationDegrees(rotDeg));
         }

         ps.scale(sc, sc, 1.0F);
         float x = -width / 2.0F;
         float y = -4.0F;
         if (readNow >= 0.0F) {
            float k = 0.22F + 0.5F * readNow;
            ps.pushPose();
            ps.translate(0.7F, 0.8F, -0.9F);
            text(ps, out, x, y, s, false, mode, Draw.alpha(-16053740, alpha * k));
            ps.popPose();
         }

         if (glow > 0.01F) {
            for (int i = 1; i <= (NocturneClient.SETTINGS.quality.get() == 2 ? 2 : 1); i++) {
               float g = 1.0F + i * 0.09F;
               ps.pushPose();
               ps.translate(0.0F, 0.0F, -0.35F * i);
               ps.scale(g, g, 1.0F);
               text(ps, out, x, y, s, false, mode, Draw.alpha(glowColor, alpha * glow * (i == 1 ? 0.32F : 0.16F)));
               ps.popPose();
            }
         }

         if (chroma > 0.05F) {
            ps.pushPose();
            ps.translate(-chroma, 0.0F, -0.15F);
            text(ps, out, x, y, s, false, mode, Draw.alpha(-50325, alpha * 0.6F));
            ps.popPose();
            ps.pushPose();
            ps.translate(chroma, 0.0F, -0.2F);
            text(ps, out, x, y, s, false, mode, Draw.alpha(-12850945, alpha * 0.6F));
            ps.popPose();
         }

         text(ps, out, x, y, s, readNow < 0.0F, mode, Draw.alpha(color, alpha));
         ps.popPose();
      }
   }

   private static void text(PoseStack ps, SubmitNodeCollector out, float x, float y, FormattedCharSequence s, boolean shadow, DisplayMode mode, int color) {
      if (!culled && Draw.visible(color)) {
         if (hud) {
            HudLyrics.text(ps.last().pose(), x, y, s, shadow, color);
         } else {
            out.submitText(ps, x, y, s, shadow, mode, 15728880, color, 0, 0);
            LyricsDebug.worldPiece();
         }
      }
   }

   private static void quad(PoseStack ps, SubmitNodeCollector out, float x0, float y0, float x1, float y1, int c) {
      if (!culled && Draw.visible(c) && !(x1 - x0 < 0.1F)) {
         if (hud) {
            HudLyrics.quad(ps.last().pose(), x0, y0, x1, y1, c);
         } else {
            out.submitCustomGeometry(ps, RenderTypes.debugFilledBox(), (pose, buf) -> {
               buf.addVertex(pose, x0, y0, 0.0F).setColor(c);
               buf.addVertex(pose, x1, y0, 0.0F).setColor(c);
               buf.addVertex(pose, x1, y1, 0.0F).setColor(c);
               buf.addVertex(pose, x0, y1, 0.0F).setColor(c);
               buf.addVertex(pose, x0, y1, 0.0F).setColor(c);
               buf.addVertex(pose, x1, y1, 0.0F).setColor(c);
               buf.addVertex(pose, x1, y0, 0.0F).setColor(c);
               buf.addVertex(pose, x0, y0, 0.0F).setColor(c);
            });
         }
      }
   }

   private static void rot(PoseStack ps, Quaternionfc q) {
      ps.mulPose(q);
   }

   @Environment(EnvType.CLIENT)
   private static final class BattleWord {
      LyricsRenderer.Place place;
      Component comp;
      int width;
      int color;
      float age;
      float life;
      float roll;
      boolean big;
   }

   @Environment(EnvType.CLIENT)
   private static final class Mist {
      LyricsRenderer.Place place;
      float x;
      float y;
      float vx;
      float vy;
      float age;
      float life;
      float r;
      float grow;
      float alpha = 1.0F;
      int color;
   }

   @Environment(EnvType.CLIENT)
   private static final class Place {
      Vec3 at;
      Vec3 smoothed;
      float yaw;
      float pitch;
      float lift;
      float liftTarget;
      float roll;
      float size = 1.0F;
      boolean ready;
      boolean floor;
      boolean drift;
      Vec3 rel;
      Vec3 off;
      float baseYaw;
      float hudVis = 1.0F;
      int visFrame;
      int kind;

      Place(Vec3 at) {
         this.at = at;
      }

      Vec3 pos() {
         return this.smoothed == null ? this.at.add(0.0, this.lift, 0.0) : this.smoothed;
      }

      void update(Vec3 cam, float dt, boolean turn) {
         this.lift = this.lift + (this.liftTarget - this.lift) * (1.0F - (float)Math.exp(-dt * 6.0F));
         Vec3 targetPosition = this.at.add(0.0, this.lift, 0.0);
         this.smoothed = this.smoothed == null || NocturneClient.SETTINGS.reducedMotion.on() ? targetPosition : this.smoothed.lerp(targetPosition, -Math.expm1(-dt * 5 / NocturneClient.SETTINGS.transition.get()));
         Vec3 to = cam.subtract(this.pos());
         float ty = (float)Math.atan2(to.x, to.z);
         float tp = this.floor
            ? (float) (Math.PI / 2)
            : (float)Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z));
         if (!this.ready) {
            this.yaw = ty;
            this.pitch = tp;
            this.ready = true;
         } else if (turn) {
            float k = 1.0F - (float)Math.exp(-dt * 4.0F);
            float dy = ty - this.yaw;

            while (dy > Math.PI) {
               dy -= (float) (Math.PI * 2);
            }

            while (dy < -Math.PI) {
               dy += (float) (Math.PI * 2);
            }

            this.yaw += dy * k;
            this.pitch = this.pitch + (tp - this.pitch) * k;
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private record Placed(Lyrics.Word word, Component comp, String text, float cx, float cy, int width, int index, boolean big, float sc) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Shown {
      final int index;
      final List<LyricsRenderer.Placed> words;
      final float height;
      final float phase;
      LyricsRenderer.Place place;
      LyricsRenderer.Place orbit;
      float age;
      float ended = -1.0F;
      boolean dismissed;

      Shown(int index, List<LyricsRenderer.Placed> words, float height, LyricsRenderer.Place place) {
         this.index = index;
         this.words = words;
         this.height = height;
         this.place = place;
         this.phase = LyricsRenderer.phaseSeed += 2.39996F;
      }

      float halfWidth() {
         float m = 0.0F;

         for (LyricsRenderer.Placed w : this.words) {
            m = Math.max(m, Math.abs(w.cx()) + w.width() * w.sc() / 2.0F);
         }

         return m;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Spark {
      LyricsRenderer.Place place;
      float x;
      float y;
      float vx;
      float vy;
      float age;
      float life;
      float size;
      float spin;
      int color;
      String glyph;
   }
}
