package dev.truwty.nocturne.gui;

import com.mojang.blaze3d.platform.NativeImage;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.lyrics.LyricsService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** GPU texture changes are confined to the client render thread. */
public final class AlbumArtwork {
    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("nocturne", "album_artwork");
    private static LyricsService.Artwork uploaded;
    private static boolean registered;

    private AlbumArtwork() {}

    public static boolean draw(GuiGraphicsExtractor g, int x, int y, int size, float alpha) {
        var art = LyricsService.INSTANCE.artwork();
        if (!NocturneClient.SETTINGS.artworkDisplay.on() || art == null) {
            close();
            return false;
        }
        if (art != uploaded) {
            close();
            uploaded = art;
            try {
                var image = NativeImage.read(art.png());
                Minecraft.getInstance()
                        .getTextureManager()
                        .register(ID, new DynamicTexture(() -> "Nocturne album artwork", image));
                registered = true;
            } catch (java.io.IOException ex) {
                NocturneClient.LOG.debug("Artwork texture unavailable", ex);
            }
        }
        if (!registered) return false;
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                ID,
                x,
                y,
                0F,
                0F,
                size,
                size,
                128,
                128,
                128,
                128,
                Draw.alpha(-1, alpha));
        return true;
    }

    public static void close() {
        if (registered) Minecraft.getInstance().getTextureManager().release(ID);
        registered = false;
        uploaded = null;
    }
}
