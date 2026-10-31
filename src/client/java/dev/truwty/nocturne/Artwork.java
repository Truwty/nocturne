package dev.truwty.nocturne;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public final class Artwork {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("nocturne", "album");
    private static String previous = "";
    private static boolean ready;
    private static int accent = 0xFF9CB5DE;
    private static final Animation reveal = new Animation(1);
    public static int accent() { return accent; }

    private Artwork() {}

    public static void update(String encoded) {
        if (previous.equals(encoded)) return;
        previous = encoded;
        close();
        if (encoded.isEmpty() || encoded.length() > 2_700_000) return;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (var stream = new ByteArrayInputStream(bytes)) {
                NativeImage image = NativeImage.read(stream);
                if (image.getWidth() > 2048 || image.getHeight() > 2048) {
                    image.close();
                    return;
                }
                long red = 0, green = 0, blue = 0, weight = 0;
                for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 32)) {
                    for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 32)) {
                        int pixel = image.getPixel(x, y);
                        int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, b = pixel & 255;
                        int saturation = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                        if (saturation < 25) continue;
                        red += (long) r * saturation; green += (long) g * saturation; blue += (long) b * saturation; weight += saturation;
                    }
                }
                accent = weight == 0 ? 0xFF9CB5DE : Theme.blend(0xFF000000 | ((int) (red / weight) << 16)
                        | ((int) (green / weight) << 8) | (int) (blue / weight), 0xFFFFFFFF, 0.4);
                Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "Nocturne album", image));
                ready = true;
                reveal.snap(0);
            }
        } catch (IOException | IllegalArgumentException ex) {
            NocturneClient.LOG.debug("Cannot decode media artwork", ex);
        }
    }

    public static boolean draw(GuiGraphicsExtractor graphics, int x, int y, int size) {
        if (!ready) return false;
        graphics.blit(ID, x, y, x + size, y + size, 0, 1, 0, 1);
        double progress = reveal.approach(1, 12, NocturneClient.preferences == null || NocturneClient.preferences.animations);
        if (progress < 0.995) graphics.fill(x, y, x + size, y + size, Theme.alpha(Theme.current().surface, 1 - progress));
        return true;
    }

    public static void close() {
        if (ready) Minecraft.getInstance().getTextureManager().release(ID);
        ready = false;
    }
}
