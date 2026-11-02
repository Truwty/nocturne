package dev.truwty.nocturne.accessibility;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.Draw;
import dev.truwty.nocturne.lyrics.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

public final class EdgeFallback {
    private static boolean outside, behind;
    private static double x, y;
    private static String key = "";
    private static boolean occluded;
    private static long nextRay;

    private EdgeFallback() {}

    public static boolean showing() {
        return outside || occluded;
    }
    public static boolean occluded() { return occluded; }

    public static void clear() {
        outside = false;
        occluded = false;
        nextRay = 0;
        key = "";
    }

    public static void update(Vec3 anchor) {
        var mc = Minecraft.getInstance();
        var song = LyricsService.INSTANCE.now();
        if (!NocturneClient.SETTINGS.isEnabled()
                || mc.player == null
                || mc.getWindow().isIconified()
                || anchor == null
                || song == null
                || !NocturneClient.SETTINGS.edgeFallback.on()
                || Accessibility.hidden()) {
            clear();
            return;
        }
        if (!song.key().equals(key)) {
            outside = false;
            key = song.key();
        }
        var camera = ClientAccess.camera(mc);
        Vec3 delta = anchor.subtract(camera.position());
        var forward = new org.joml.Vector3f(0, 0, -1).rotate(camera.rotation());
        behind = delta.dot(new Vec3(forward.x, forward.y, forward.z)) <= .01;
        var ndc = mc.gameRenderer.projectPointToScreen(anchor);
        x = ndc.x;
        y = ndc.y;
        outside = EdgePlacement.outside(x, y, behind, outside);
        long now = System.nanoTime();
        if (NocturneClient.SETTINGS.terrainFallback.on() && mc.level != null && now >= nextRay) {
            nextRay = now + 100_000_000L;
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(camera.position(), anchor,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
            occluded = hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                    && hit.getLocation().distanceToSqr(camera.position()) + .04 < anchor.distanceToSqr(camera.position());
        } else if (!NocturneClient.SETTINGS.terrainFallback.on()) occluded = false;
    }

    public static void render(GuiGraphicsExtractor g) {
        var s = NocturneClient.SETTINGS;
        var service = LyricsService.INSTANCE;
        var lyrics = service.lyrics();
        if (!(outside || occluded) || !s.edgeFallback.on() || Accessibility.hidden() || lyrics == null) return;
        long clock = LyricsRenderer.clockMs();
        int index = lyrics.indexAt(clock);
        if (index < 0 || clock >= lyrics.lines.get(index).end()) return;
        var mc = Minecraft.getInstance();
        int max = Math.max(40, Math.min(270, g.guiWidth() - 24));
        var lines =
                mc.font.split(
                        Draw.lyric(lyrics.lines.get(index).text(), false, s.lyricFont.get()),
                        max - 16);
        int count = Math.min(3, lines.size()), height = 16 + count * 11, width = max;
        var placement =
                EdgePlacement.locate(x, y, behind, g.guiWidth(), g.guiHeight(), width, height);
        var safe = dev.truwty.nocturne.gui.HudAvoidance.place(new dev.truwty.nocturne.gui.HudLayout.Box(placement.x(), placement.y(), width, height), g.guiWidth(), g.guiHeight());
        int left = (int)safe.x(), top = (int)safe.y();
        g.fill(
                left,
                top,
                left + width,
                top + height,
                Draw.alpha(
                        Accessibility.background(),
                        (float) Math.max(.85, Accessibility.plateOpacity())));
        g.text(
                mc.font,
                occluded && !outside ? "Lyrics · behind scenery" : "Lyrics " + placement.direction(),
                left + 8,
                top + 4,
                Accessibility.ink(),
                false);
        for (int i = 0; i < count; i++)
            HudReadability.text(
                    g, mc.font, lines.get(i), left + 8, top + 16 + i * 11, Accessibility.ink());
    }
}
