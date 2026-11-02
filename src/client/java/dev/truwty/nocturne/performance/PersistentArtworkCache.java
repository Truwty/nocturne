package dev.truwty.nocturne.performance;

import dev.truwty.nocturne.StudioLibrary;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded, versioned thumbnail storage. Never contains original full-size artwork. */
public final class PersistentArtworkCache {
  public static final int MAGIC = 0x4e415232;
  private static final int MAX_IMAGE = 131072;
  private static final long TTL = 7 * 86400000L;
  private final Path root;
  private final LongSupplier clock;

  public PersistentArtworkCache(Path root) {
    this(root, System::currentTimeMillis);
  }

  public PersistentArtworkCache(Path root, LongSupplier clock) {
    this.root = root.toAbsolutePath().normalize();
    this.clock = clock;
  }

  private Path path(String key) {
    return root.resolve(StudioLibrary.trackId(key) + ".bin");
  }

  public synchronized ArtworkCache.Entry read(String key) throws IOException {
    if (Files.isSymbolicLink(root))
      throw new IOException("Artwork cache cannot be a symbolic link");
    Path file = path(key);
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
    if (Files.size(file) > MAX_IMAGE + 1024) {
      Files.delete(file);
      return null;
    }
    try (var in = new DataInputStream(Files.newInputStream(file))) {
      if (in.readInt() != MAGIC) throw new IOException("Unknown artwork cache format");
      long saved = in.readLong();
      int color = in.readInt(), length = in.readInt();
      in.readUTF();
      if (length < 33 || length > MAX_IMAGE) throw new IOException("Invalid thumbnail size");
      byte[] png = in.readNBytes(length);
      if (png.length != length
          || in.read() != -1
          || png[0] != (byte) 137
          || png[1] != 'P'
          || png[2] != 'N'
          || png[3] != 'G') throw new IOException("Invalid cached PNG");
      var header = java.nio.ByteBuffer.wrap(png);
      int width = header.getInt(16), height = header.getInt(20);
      if (width < 1 || width > 128 || height < 1 || height > 128)
        throw new IOException("Cached thumbnail dimensions exceed limit");
      if (javax.imageio.ImageIO.read(new ByteArrayInputStream(png)) == null)
        throw new IOException("Unreadable cached thumbnail");
      long now = clock.getAsLong();
      if (saved > now || now - saved >= TTL) {
        Files.delete(file);
        return null;
      }
      return new ArtworkCache.Entry(png, color, saved);
    } catch (EOFException ex) {
      Files.deleteIfExists(file);
      return null;
    }
  }

  public synchronized void write(String key, ArtworkCache.Entry entry) throws IOException {
    if (entry.bytes() > MAX_IMAGE) return;
    Files.createDirectories(root);
    if (Files.isSymbolicLink(root))
      throw new IOException("Artwork cache cannot be a symbolic link");
    Path temporary = Files.createTempFile(root, "thumbnail-", ".tmp");
    try {
      try (var out = new DataOutputStream(Files.newOutputStream(temporary))) {
        out.writeInt(MAGIC);
        out.writeLong(entry.saved());
        out.writeInt(entry.color());
        out.writeInt(entry.bytes());
        String label = key.split("\\|", 2)[0];
        out.writeUTF(label.substring(0, Math.min(300, label.length())));
        out.write(entry.png());
      }
      try {
        Files.move(
            temporary,
            path(key),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temporary, path(key), StandardCopyOption.REPLACE_EXISTING);
      }
      prune();
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void prune() throws IOException {
    record File(Path path, long saved, long size) {}
    var entries = new ArrayList<File>();
    try (var files = Files.list(root)) {
      for (Path path :
          files.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.bin")).toList()) {
        var attr = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attr.isRegularFile())
          entries.add(new File(path, attr.lastModifiedTime().toMillis(), attr.size()));
      }
    }
    entries.sort(Comparator.comparingLong(File::saved).thenComparing(e -> e.path().toString()));
    long bytes = entries.stream().mapToLong(File::size).sum();
    int count = entries.size();
    for (var entry : entries) {
      if (count <= 64 && bytes <= 8 * 1024 * 1024) break;
      Files.deleteIfExists(entry.path());
      count--;
      bytes -= entry.size();
    }
  }
}
