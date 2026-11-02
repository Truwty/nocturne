package dev.truwty.nocturne.performance;

import java.util.*;

/** Compressed thumbnails only; the renderer retains one GPU texture. */
public final class ArtworkCache {
    public record Entry(byte[] png, int color, long saved) {
        public Entry {
            png = png.clone();
        }

        @Override
        public byte[] png() {
            return png.clone();
        }

        public int bytes() {
            return png.length;
        }
    }

    private final int maximumEntries, maximumBytes;
    private final long ttl;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private int bytes;

    public ArtworkCache(int maximumEntries, int maximumBytes, long ttl) {
        if (maximumEntries < 1 || maximumBytes < 1 || ttl < 1)
            throw new IllegalArgumentException("Invalid artwork cache limits");
        this.maximumEntries = maximumEntries;
        this.maximumBytes = maximumBytes;
        this.ttl = ttl;
    }

    public synchronized Entry get(String key, long now) {
        var e = entries.get(key);
        if (e != null && (now < e.saved() || now - e.saved() >= ttl)) {
            entries.remove(key);
            bytes -= e.bytes();
            return null;
        }
        return e;
    }

    public synchronized void put(String key, Entry entry) {
        var old = entries.remove(key);
        if (old != null) bytes -= old.bytes();
        if (entry.bytes() > maximumBytes) return;
        entries.put(key, entry);
        bytes += entry.bytes();
        while (entries.size() > maximumEntries || bytes > maximumBytes) {
            var first = entries.pollFirstEntry();
            bytes -= first.getValue().bytes();
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() { entries.clear(); bytes = 0; }

    public synchronized int bytes() {
        return bytes;
    }
}
