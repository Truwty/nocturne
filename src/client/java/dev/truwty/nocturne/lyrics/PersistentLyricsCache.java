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
    private static final long MAX_TIME=86_400_000L;
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
            if(e==null || e.schema!=1 || e.saved<0 || e.status==null || e.status.length()>1000 || e.lines==null || e.lines.isEmpty() || e.lines.size()>10000) throw new IllegalArgumentException("Invalid cache entry");
            if(e.saved>clock.getAsLong() || clock.getAsLong()-e.saved>maxAgeMillis) { Files.delete(file); return null; }
            long previous=-1;
            for(var line:e.lines) {
                if(line==null || line.text()==null || line.text().length()>10000 || line.translation().length()>10000 || line.romanization().length()>10000 || line.words()==null || line.start()<0 || line.start()<previous || line.end()<line.start() || line.end()>MAX_TIME) throw new IllegalArgumentException("Invalid cached line");
                previous=line.start();
                long previousWord=line.start();
                for(var word:line.words()) {
                    if(word==null || word.text()==null || word.text().length()>10000 || word.start()<previousWord || word.end()<word.start() || word.end()>line.end()) throw new IllegalArgumentException("Invalid cached word");
                    previousWord=word.start();
                }
            }
            return new Stored(new LyricsFinder.Result(Lyrics.restored(e.lines,e.estimated),e.status+" / disk cache"),e.saved);
        } catch(RuntimeException ex) {
            Files.move(file,file.resolveSibling(file.getFileName()+".corrupt-"+UUID.randomUUID()));
            prune(true,16,33_554_432);
            return null;
        }
    }
    synchronized void write(String key,LyricsFinder.Result result) throws IOException {
        if(result.lyrics()==null || result.lyrics().lines.isEmpty()) return;
        String json=JSON.toJson(new Entry(1,clock.getAsLong(),result.status(),result.lyrics().estimated,result.lyrics().lines));
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2_097_152) return;
        StudioLibrary.write(path(key),json);
        prune(false,256,67_108_864);
        prune(true,16,33_554_432);
    }
    private void prune(boolean corrupt,int limit,long byteLimit) throws IOException {
        record CachedFile(Path path,long modified,long bytes) {}
        var files=new ArrayList<CachedFile>();
        String pattern=corrupt?"[a-f0-9]{64}\\.json\\.corrupt-[a-f0-9-]{36}":"[a-f0-9]{64}\\.json";
        var accepted=java.util.regex.Pattern.compile(pattern);
        long bytes=0;
        try(var stream=Files.list(root)) {
            for(var path:stream.filter(p->accepted.matcher(p.getFileName().toString()).matches()).toList()) {
                var attributes=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(!attributes.isRegularFile())continue;
                files.add(new CachedFile(path,attributes.lastModifiedTime().toMillis(),attributes.size()));bytes+=attributes.size();
            }
        }
        files.sort(Comparator.comparingLong(CachedFile::modified).thenComparing(f->f.path().toString()));
        int removed=0;
        while(files.size()-removed>limit || bytes>byteLimit) {
            var file=files.get(removed++);Files.deleteIfExists(file.path());bytes-=file.bytes();
        }
    }
    synchronized void remove(String key) throws IOException { Files.deleteIfExists(path(key)); }
}
