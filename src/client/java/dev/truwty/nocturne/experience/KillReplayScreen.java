package dev.truwty.nocturne.experience;

import com.mojang.blaze3d.platform.NativeImage;

import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.Draw;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class KillReplayScreen extends Screen {
    private static final Identifier
            LEFT = Identifier.fromNamespaceAndPath("nocturne", "replay/left"),
            RIGHT = Identifier.fromNamespaceAndPath("nocturne", "replay/right");
    private final List<KillReplay.Frame> frames;
    private final long duration;
    private long start;
    private float initialHealth;
    private int left = -1, right = -1;

    public KillReplayScreen(List<KillReplay.Frame> frames) {
        super(Component.literal("Nocturne / local slow-motion replay"));
        if (frames.size() < 2 || frames.size() > KillReplay.LIMIT)
            throw new IllegalArgumentException("Replay needs 2–40 frames");
        this.frames = List.copyOf(frames);
        duration = frames.getLast().nanos() - frames.getFirst().nanos();
        if (duration <= 0) throw new IllegalArgumentException("Replay timestamps must increase");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (start == 0) start = System.nanoTime();
        initialHealth = minecraft.player == null ? 0 : minecraft.player.getHealth();
    }

    @Override
    public void tick() {
        if ((System.nanoTime() - start) * .4 > duration
                || minecraft.player == null
                || minecraft.player.getHealth() < initialHealth) onClose();
    }

    private void upload(Identifier id, KillReplay.Frame frame) {
        var image = new NativeImage(KillReplay.WIDTH, KillReplay.HEIGHT, true);
        for (int y = 0; y < KillReplay.HEIGHT; y++)
            for (int x = 0; x < KillReplay.WIDTH; x++)
                image.setPixel(x, y, frame.pixel(y * KillReplay.WIDTH + x));
        minecraft.getTextureManager().release(id);
        minecraft
                .getTextureManager()
                .register(id, new DynamicTexture(() -> "Nocturne replay", image));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Draw.rect(g, 0, 0, width, height, 0xFF060A10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long at = frames.getFirst().nanos() + (long) ((System.nanoTime() - start) * .4);
        int index = 0;
        while (index + 1 < frames.size() && frames.get(index + 1).nanos() <= at) index++;
        int next = Math.min(frames.size() - 1, index + 1);
        if (left != index) {
            upload(LEFT, frames.get(index));
            left = index;
        }
        if (right != next) {
            upload(RIGHT, frames.get(next));
            right = next;
        }
        double blend =
                next == index
                        ? 0
                        : Math.clamp(
                                (double) (at - frames.get(index).nanos())
                                        / (frames.get(next).nanos() - frames.get(index).nanos()),
                                0,
                                1);
        int ww = Math.min(width, (height - 68) * 16 / 9),
                hh = ww * 9 / 16,
                xx = (width - ww) / 2,
                yy = (height - hh) / 2;
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                LEFT,
                xx,
                yy,
                0,
                0,
                ww,
                hh,
                KillReplay.WIDTH,
                KillReplay.HEIGHT,
                KillReplay.WIDTH,
                KillReplay.HEIGHT,
                -1);
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                RIGHT,
                xx,
                yy,
                0,
                0,
                ww,
                hh,
                KillReplay.WIDTH,
                KillReplay.HEIGHT,
                KillReplay.WIDTH,
                KillReplay.HEIGHT,
                Draw.withAlpha(-1, (int) (blend * 255)));
        Draw.text(g, font, "LOCAL REPLAY / 0.4×", 12, 12, Experience.color(), false);
        Draw.text(
                g,
                font,
                "World continues live / Esc to return",
                12,
                height - 20,
                0xFFB5C9D6,
                false);
    }

    @Override
    public void removed() {
        minecraft.getTextureManager().release(LEFT);
        minecraft.getTextureManager().release(RIGHT);
    }

    @Override
    public void onClose() {
        if (minecraft.screen == this) ClientAccess.setScreen(minecraft, null);
    }
}
