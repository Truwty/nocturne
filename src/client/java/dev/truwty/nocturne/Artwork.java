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
                Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "Nocturne album", image));
                ready = true;
            }
        } catch (IOException | IllegalArgumentException ex) {
            NocturneClient.LOG.debug("Cannot decode media artwork", ex);
        }
    }

    public static boolean draw(GuiGraphicsExtractor graphics, int x, int y, int size) {
        if (!ready) return false;
        graphics.blit(ID, x, y, x + size, y + size, 0, 1, 0, 1);
        return true;
    }

    public static void close() {
        if (ready) Minecraft.getInstance().getTextureManager().release(ID);
        ready = false;
    }
}
