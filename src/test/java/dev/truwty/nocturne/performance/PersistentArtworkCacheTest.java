package dev.truwty.nocturne.performance;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentArtworkCacheTest {
  @TempDir Path root;

  private byte[] png() throws java.io.IOException {
    var output = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB),
        "png",
        output);
    return output.toByteArray();
  }

  @Test
  void survivesRestartAndExpires() throws Exception {
    var clock = new AtomicLong(1000);
    new PersistentArtworkCache(root, clock::get)
        .write("track", new ArtworkCache.Entry(png(), 123, clock.get()));
    var reopened = new PersistentArtworkCache(root, clock::get);
    assertArrayEquals(png(), reopened.read("track").png());
    clock.addAndGet(7 * 86400000L);
    assertNull(reopened.read("track"));
  }

  @Test
  void boundedAndSelectiveDeletionPreservesOtherFiles() throws Exception {
    var cache = new PersistentArtworkCache(root.resolve("artwork"));
    for (int i = 0; i < 70; i++)
      cache.write("track" + i, new ArtworkCache.Entry(png(), 0, System.currentTimeMillis()));
    var inventory = new CacheInventory(root);
    assertEquals(64, inventory.scan().size());
    Files.writeString(root.resolve("artwork/personal.json"), "keep");
    assertTrue(inventory.delete(inventory.scan().getFirst()));
    assertEquals(63, inventory.scan().size());
    assertEquals("keep", Files.readString(root.resolve("artwork/personal.json")));
    assertThrows(
        java.io.IOException.class,
        () ->
            inventory.delete(
                new CacheInventory.Entry(CacheInventory.Kind.ARTWORK, "../personal.json", 0, 0)));
  }

  @Test
  void rejectsOversizedAndTruncatedEntries() throws Exception {
    var cache = new PersistentArtworkCache(root);
    cache.write("track", new ArtworkCache.Entry(png(), 0, System.currentTimeMillis()));
    Path file;
    try (var files = Files.list(root)) {
      file = files.findFirst().orElseThrow();
    }
    Files.write(file, new byte[3]);
    assertNull(cache.read("track"));
    assertFalse(Files.exists(file));
  }
}
