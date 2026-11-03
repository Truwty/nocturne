package dev.truwty.nocturne.experience;

import com.mojang.blaze3d.platform.NativeImage;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.compat.ClientAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.*;

/** Optional, memory-only first-person replay. Does not rewind or slow a server. */
public final class KillReplay {
    public static final int WIDTH = 320, HEIGHT = 180, LIMIT = 40;

    public static final class Frame {
        private final long nanos;
        private final int[] pixels;

        public Frame(long nanos, int[] pixels) {
            if (pixels.length != WIDTH * HEIGHT)
                throw new IllegalArgumentException("Invalid replay frame dimensions");
            this.nanos = nanos;
            this.pixels = pixels.clone();
        }

        public long nanos() {
            return nanos;
        }

        public int pixel(int index) {
            return pixels[index];
        }
    }

    private static final Deque<Frame> ring = new ArrayDeque<>();
    private static List<Frame> clip = List.of();
    private static long next, epoch, postRoll;
    private static boolean pending;

    private KillReplay() {}

    public static int frames() {
        return ring.size();
    }

    public static void reset() {
        epoch++;
        ring.clear();
        clip = List.of();
        next = 0;
        postRoll = 0;
    }

    public static void capture(Minecraft mc) {
        if (!Experience.SETTINGS.on(ExperienceSettings.Option.KILLCAM)) {
            if (!ring.isEmpty() || !clip.isEmpty()) reset();
            return;
        }
        if (pending
                || mc.level == null
                || mc.player == null
                || mc.screen != null
                || mc.options.hideGui
                || mc.isPaused()
                || !mc.isWindowActive()
                || !Accessibility.camera()) return;
        long now = System.nanoTime();
        if (now < next) return;
        double frameMs = dev.truwty.nocturne.performance.RenderPerformance.snapshot().frameMs();
        next = now + (frameMs > 60 ? 500_000_000L : frameMs > 30 ? 200_000_000L : 50_000_000L);
        pending = true;
        long generation = epoch;
        try {
            Screenshot.takeScreenshot(
                    ClientAccess.renderTarget(mc),
                    source -> {
                        int[] pixels;
                        try (source;
                                var small = new NativeImage(WIDTH, HEIGHT, true)) {
                            source.resizeSubRectTo(
                                    0, 0, source.getWidth(), source.getHeight(), small);
                            pixels = small.getPixels();
                        } catch (RuntimeException error) {
                            mc.execute(
                                    () -> {
                                        pending = false;
                                        NocturneClient.LOG.warn(
                                                "Replay frame could not be resized", error);
                                    });
                            return;
                        }
                        mc.execute(
                                () -> {
                                    pending = false;
                                    if (generation != epoch
                                            || !Experience.SETTINGS.on(
                                                    ExperienceSettings.Option.KILLCAM)) return;
                                    if (!ring.isEmpty()
                                            && now - ring.getLast().nanos() > 500_000_000L)
                                        ring.clear();
                                    while (!ring.isEmpty()
                                            && (ring.size() >= LIMIT
                                                    || now - ring.getFirst().nanos()
                                                            > 2_000_000_000L)) ring.removeFirst();
                                    ring.addLast(new Frame(now, pixels));
                                });
                    });
        } catch (RuntimeException error) {
            pending = false;
            next = now + 5_000_000_000L;
            NocturneClient.LOG.warn("Replay capture unavailable", error);
        }
    }

    public static void markKill() {
        if (Experience.SETTINGS.on(ExperienceSettings.Option.KILLCAM))
            postRoll = System.nanoTime() + 350_000_000L;
    }

    public static void tick() {
        if (postRoll != 0 && System.nanoTime() >= postRoll) {
            postRoll = 0;
            clip = List.copyOf(ring);
            if (Experience.SETTINGS.get(ExperienceSettings.Option.KILLCAM) == 2) play();
        }
    }

    public static void preview() {
        clip = List.copyOf(ring);
        play();
    }

    public static void play() {
        var mc = Minecraft.getInstance();
        if (clip.size() < 2) {
            ClientAccess.overlay(
                    mc,
                    net.minecraft.network.chat.Component.literal(
                            "No replay yet. Enable capture and play for a few seconds."),
                    false);
            return;
        }
        if (mc.player != null && mc.screen == null && Accessibility.camera())
            ClientAccess.setScreen(mc, new KillReplayScreen(clip));
    }
}
