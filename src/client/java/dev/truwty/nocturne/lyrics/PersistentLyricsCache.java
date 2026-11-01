package dev.truwty.nocturne.lyrics;

import com.google.gson.Gson;
import dev.truwty.nocturne.StudioLibrary;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

final class PersistentLyricsCache {
    private static final Gson JSON = new Gson();
    private final Path root;
    private final LongSupplier clock;
    private record Entry(int schema, long saved, String status, boolean estimated, List<Lyrics.Line> lines) {}
    PersistentLyricsCache(Path root) { this(root, System::currentTimeMillis); }
    PersistentLyricsCache(Path root, LongSupplier clock) { this.root=root; this.clock=clock; }
    private Path path(String key) { return root.resolve(StudioLibrary.trackId(key)+".json"); }
    record Stored(LyricsFinder.Result result,long savedAt) {}
    synchronized LyricsFinder.Result read(String key,long maxAgeMillis) throws IOException {
        Stored stored=readEntry(key,maxAgeMillis);return stored==null?null:stored.result();
    }
    synchronized Stored readEntry(String key, long maxAgeMillis) throws IOException {
        Path file=path(key); if(!Files.exists(file)) return null;
        try {
            if(Files.size(file)>2_097_152) throw new IllegalArgumentException("Cache entry too large");
            Entry e=JSON.fromJson(Files.readString(file),Entry.class);
            if(e==null || e.schema!=1 || e.status==null || e.lines==null || e.lines.isEmpty() || e.lines.size()>10000) throw new IllegalArgumentException("Invalid cache entry");
            if(e.saved>clock.getAsLong() || clock.getAsLong()-e.saved>maxAgeMillis) { Files.delete(file); return null; }
            long previous=-1;
            for(var line:e.lines) {
                if(line==null || line.text()==null || line.text().length()>10000 || line.words()==null || line.start()<previous || line.end()<line.start()) throw new IllegalArgumentException("Invalid cached line");
                previous=line.start();
                for(var word:line.words()) if(word==null || word.text()==null || word.start()<line.start() || word.end()<word.start()) throw new IllegalArgumentException("Invalid cached word");
            }
            return new Stored(new LyricsFinder.Result(Lyrics.restored(e.lines,e.estimated),e.status+" / disk cache"),e.saved);
        } catch(RuntimeException ex) {
            Files.move(file,file.resolveSibling(file.getFileName()+".corrupt-"+UUID.randomUUID()));
            return null;
        }
    }
    synchronized void write(String key,LyricsFinder.Result result) throws IOException {
        if(result.lyrics()==null || result.lyrics().lines.isEmpty()) return;
        String json=JSON.toJson(new Entry(1,clock.getAsLong(),result.status(),result.lyrics().estimated,result.lyrics().lines));
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2_097_152) return;
        StudioLibrary.write(path(key),json);
        try(var stream=Files.list(root)) {
            var files=new ArrayList<Path>(stream.filter(p->p.getFileName().toString().endsWith(".json")).toList());
            files.sort(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException ex){return Long.MIN_VALUE;}}));
            long bytes=0;for(var f:files)bytes+=Files.size(f);
            while(files.size()>256 || bytes>67_108_864) {Path f=files.removeFirst();bytes-=Files.size(f);Files.deleteIfExists(f);}
        }
    }
    synchronized void remove(String key) throws IOException { Files.deleteIfExists(path(key)); }
}
