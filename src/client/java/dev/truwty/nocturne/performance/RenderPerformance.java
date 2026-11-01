package dev.truwty.nocturne.performance;

import dev.truwty.nocturne.NocturneClient;

import net.minecraft.client.Minecraft;

import java.util.Locale;

public final class RenderPerformance {
    private static final AdaptiveBudget ADAPTIVE = new AdaptiveBudget();
    private static long lastFrame, cpuNanos;
    private static int text, meshes, vertices, projected, particles, trails, glow, cloud;
    private static int glowCap, cloudCap;
    private static double scale = 1, distance = 1;
    private static boolean idle, pendingWorld;
    private static Snapshot previous = new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 16.667, 0);

    public record Snapshot(
            double cpuMs,
            int text,
            int meshes,
            int vertices,
            int projected,
            int particles,
            int trails,
            int glow,
            int cloud,
            double scale,
            double frameMs,
            long sampled) {}

    private RenderPerformance() {}

    public static long time() {
        return System.nanoTime();
    }

    public static void addTime(long start) {
        cpuNanos += Math.max(0, time() - start);
    }

    public static void begin(Minecraft mc) {
        pendingWorld = true;
        long now = System.nanoTime();
        previous =
                new Snapshot(
                        cpuNanos / 1e6,
                        text,
                        meshes,
                        vertices,
                        projected,
                        particles,
                        trails,
                        glow,
                        cloud,
                        scale,
                        ADAPTIVE.frameMs(),
                        lastFrame);
        cpuNanos = 0;
        text = meshes = vertices = projected = particles = trails = glow = cloud = 0;
        distance = 1;
        var settings = NocturneClient.SETTINGS;
        var song = dev.truwty.nocturne.lyrics.LyricsService.INSTANCE.now();
        idle =
                mc.isPaused()
                        || mc.getWindow().isIconified()
                        || !mc.isWindowActive()
                        || song == null
                        || !song.playing();
        double delta = lastFrame == 0 ? 16.667 : (now - lastFrame) / 1e6;
        lastFrame = now;
        scale =
                ADAPTIVE.update(
                        delta, settings.effectTargetMs.get(), settings.adaptiveEffects.on(), idle);
        glowCap = AdaptiveBudget.limit(settings.glowPassLimit.i(), scale);
        cloudCap = AdaptiveBudget.limit(settings.cloudLayerLimit.i(), scale);
    }

    public static void hudBegin() {
        if (!pendingWorld) begin(Minecraft.getInstance());
    }

    public static void hudEnd() {
        pendingWorld = false;
    }

    public static Snapshot snapshot() {
        return previous;
    }

    public static boolean idle() {
        return idle;
    }

    public static double scale() {
        return scale;
    }

    public static void distance(double blocks) {
        var s = NocturneClient.SETTINGS;
        distance =
                s.distanceEffects.on()
                        ? AdaptiveBudget.distanceDetail(
                                blocks, s.effectNearDistance.get(), s.renderDistance.get())
                        : 1;
    }

    public static double detail() {
        return scale * distance;
    }

    public static int particles(int maximum) {
        return AdaptiveBudget.limit(maximum, detail());
    }

    public static int lingering(int maximum) {
        return dev.truwty.nocturne.accessibility.Accessibility.textMotion()?AdaptiveBudget.limit(maximum, scale):0;
    }

    public static int layers(int maximum) {
        return AdaptiveBudget.limit(maximum, distance);
    }

    public static boolean glow() {
        if (glow >= glowCap) return false;
        glow++;
        return true;
    }

    public static boolean cloud() {
        if (cloud >= cloudCap) return false;
        cloud++;
        return true;
    }

    public static void counts(int count, int lines) {
        particles = count;
        trails = lines;
    }

    public static void text() {
        text++;
    }

    public static void mesh(int count) {
        meshes++;
        vertices += count;
    }

    public static void projected() {
        projected++;
    }

    public static String timing() {
        var s = previous;
        return String.format(
                Locale.ROOT,
                "Nocturne CPU submit %.3f ms · effects %.0f%%",
                s.cpuMs(),
                s.scale() * 100);
    }

    public static String geometry() {
        var s = previous;
        return "Particles "
                + s.particles()
                + " · lines "
                + s.trails()
                + " · text "
                + s.text()
                + " · meshes "
                + s.meshes()
                + " · mesh verts "
                + s.vertices()
                + " · projected "
                + s.projected();
    }
}
