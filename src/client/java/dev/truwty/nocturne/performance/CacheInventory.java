package dev.truwty.nocturne.performance;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public final class CacheInventory {
  public enum Kind {
    LYRICS("lyrics", "[a-f0-9]{64}\\.json(?:\\.corrupt-[a-f0-9-]{36})?"),
    ARTWORK("artwork", "[a-f0-9]{64}\\.bin");
    final String directory, pattern;

    Kind(String directory, String pattern) {
      this.directory = directory;
      this.pattern = pattern;
    }
  }

  public record Entry(Kind kind, String filename, long bytes, long modified, String title) {
    public Entry(Kind kind, String filename, long bytes, long modified) {
      this(kind, filename, bytes, modified, "");
    }

    public String label() {
      return kind
          + " · "
          + (title.isBlank() ? filename.substring(0, Math.min(12, filename.length())) : title)
          + " · "
          + (bytes + 1023) / 1024
          + " KiB";
    }
  }

  private final Path root;

  public CacheInventory(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  private Path directory(Kind kind) throws IOException {
    Path dir = root.resolve(kind.directory);
    if (Files.isSymbolicLink(root) || Files.isSymbolicLink(dir))
      throw new IOException("Cache directory is a symbolic link");
    return dir;
  }

  public List<Entry> scan() throws IOException {
    var result = new ArrayList<Entry>();
    for (var kind : Kind.values()) {
      Path dir = directory(kind);
      if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) continue;
      try (var files = Files.list(dir)) {
        for (var file :
            files.filter(p -> p.getFileName().toString().matches(kind.pattern)).toList()) {
          var attr =
              Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
          if (attr.isRegularFile())
            result.add(
                new Entry(
                    kind,
                    file.getFileName().toString(),
                    attr.size(),
                    attr.lastModifiedTime().toMillis(),
                    title(kind, file, attr.size())));
        }
      }
    }
    result.sort(
        Comparator.comparingLong(Entry::modified).reversed().thenComparing(Entry::filename));
    return List.copyOf(result);
  }

  private static String title(Kind kind, Path path, long size) throws IOException {
    if (size > 2_097_152) return "Oversized cache entry";
    try {
      String label = "";
      if (kind == Kind.ARTWORK) {
        try (var input = new java.io.DataInputStream(Files.newInputStream(path))) {
          if (input.readInt() == PersistentArtworkCache.MAGIC) {
            input.readLong();
            input.readInt();
            input.readInt();
            label = input.readUTF();
          }
        }
      } else {
        var json = com.google.gson.JsonParser.parseString(Files.readString(path));
        if (json.isJsonObject() && json.getAsJsonObject().has("label"))
          label = json.getAsJsonObject().get("label").getAsString();
      }
      label = label.replaceAll("[\\p{Cntrl}§]", "");
      return label.substring(0, Math.min(200, label.length()));
    } catch (RuntimeException | java.io.EOFException ex) {
      return "Damaged download";
    }
  }

  public boolean delete(Entry entry) throws IOException {
    if (!entry.filename().matches(entry.kind().pattern))
      throw new IOException("Invalid cache filename");
    Path path = directory(entry.kind()).resolve(entry.filename());
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
    return Files.deleteIfExists(path);
  }
}
