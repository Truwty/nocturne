package dev.truwty.nocturne.playback;

import java.awt.image.BufferedImage;
import java.io.*;

import javax.imageio.ImageIO;

public final class ArtworkImage {
    private ArtworkImage() {}

    public static byte[] thumbnail(byte[] bytes) throws IOException {
        if (bytes.length > 4194304) throw new IOException("Artwork exceeds 4 MiB");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported artwork format");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 2048 || h > 2048)
                    throw new IOException("Artwork dimensions exceed limit");
                var source = reader.read(0);
                var thumb = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                var graphics = thumb.createGraphics();
                try {
                    graphics.setRenderingHint(
                            java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, 128, 128, null);
                } finally {
                    graphics.dispose();
                }
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(thumb, "png", output))
                    throw new IOException("PNG encoder unavailable");
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        }
    }
}
