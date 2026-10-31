package dev.truwty.nocturne;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class LyricOverlay {
    private static Vec3 anchor;
    private static Object level;
    private static final Animation entrance = new Animation(0);
    private static String previousLine = "";

    private LyricOverlay() {}

    public static void pin() {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        anchor = client.player.getEyePosition().add(client.player.getLookAngle().scale(5));
        level = client.level;
        NocturneClient.preferences.lyricsMode = 2;
        NocturneClient.preferences.lyricsHud = true;
        NocturneClient.preferences.save();
    }

    public static boolean pinned() { return anchor != null && level == Minecraft.getInstance().level; }

    public static void draw(GuiGraphicsExtractor g, TrackState track) {
        if (track.source().isEmpty()) return;
        LyricsService.Snapshot state = NocturneClient.LYRICS.snapshot();
        Lyrics lyrics = state.lyrics();
        var prefs = NocturneClient.preferences;
        Theme theme = Theme.current();
        double position = track.currentPosition() + prefs.lyricsOffset;
        int index = lyrics.activeIndex(position);
        String current = index >= 0 ? lyrics.lines().get(index).text() : state.message();
        if (current.isBlank()) current = "...";
        String following = index >= 0 && index + 1 < lyrics.lines().size() ? lyrics.lines().get(index + 1).text() : "";
        if (!current.equals(previousLine)) { entrance.snap(0); previousLine = current; }
        double progress = entrance.approach(1, 15, prefs.animations);
        int width = Math.min(300, g.guiWidth() - 24);
        int x = (g.guiWidth() - width) / 2, y = g.guiHeight() - 102;
        float scale = (float) prefs.lyricsScale;
        if (prefs.lyricsMode == 1) { width = Math.min(200, g.guiWidth() - 20); x = g.guiWidth() - width - 10; y = g.guiHeight() / 2 - 22; }
        if (prefs.lyricsMode == 2) {
            if (!pinned()) return;
            var client = Minecraft.getInstance(); var camera = client.gameRenderer.getMainCamera();
            Vec3 origin = camera.position();
            double distance = origin.distanceTo(anchor);
            if (distance > 32 || distance < 0.5) return;
            HitResult hit = client.level.clip(new ClipContext(origin, anchor, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() != HitResult.Type.MISS && origin.distanceTo(hit.getLocation()) + 0.15 < distance) return;
            Vector3f relative = new Vector3f((float) (anchor.x - origin.x), (float) (anchor.y - origin.y), (float) (anchor.z - origin.z));
            relative.rotate(new Quaternionf(camera.rotation()).conjugate());
            if (relative.z >= -0.1) return;
            double focal = g.guiHeight() / (2 * Math.tan(Math.toRadians(camera.getFov()) / 2));
            x = (int) (g.guiWidth() / 2.0 + relative.x * focal / -relative.z) - width / 2;
            y = (int) (g.guiHeight() / 2.0 - relative.y * focal / -relative.z) - 24;
            scale *= (float) Math.clamp(5 / distance, 0.45, 1.6);
        }
        g.pose().pushMatrix();
        g.pose().translate(x + width / 2f, y + 24f); g.pose().scale(scale, scale); g.pose().translate(-width / 2f, -24);
        MusicVisuals.panel(g, 0, 0, width, 48, Theme.alpha(theme.background, prefs.hudOpacity / 100.0));
        g.fill(10, 13, 12, 23, theme.highlight());
        MusicVisuals.text(g, current, 22, 12 + (int) ((1 - progress) * 5), Theme.alpha(theme.text, 0.6 + progress * 0.4), width - 34);
        MusicVisuals.text(g, following.isBlank() ? track.artist() : following, 22, 29, theme.muted, width - 34);
        g.pose().popMatrix();
    }
}
