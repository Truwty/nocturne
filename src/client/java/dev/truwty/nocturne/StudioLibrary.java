package dev.truwty.nocturne;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.truwty.nocturne.lyrics.Lyrics;
import dev.truwty.nocturne.lyrics.LyricDocumentCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class StudioLibrary {
    private final Path root;
    private JsonObject data = new JsonObject();
    private IOException loadError;

    private String recoveryNotice="";
    public StudioLibrary(Path root) throws IOException {
        this.root=root; Path file=root.resolve("library.json");
        if(!Files.exists(file))return;
        try {data=readLibrary(file);}
        catch(RuntimeException ex) {
            try {Files.copy(file,file.resolveSibling("library.json.corrupt-"+java.util.UUID.randomUUID()));}
            catch(IOException backupFailure) {loadError=new IOException("Library backup failed; saving disabled",backupFailure);return;}
            Path backup=root.resolve("library.json.bak");
            if(Files.exists(backup)) {
                try {data=readLibrary(backup);recoveryNotice="Library recovered from backup; damaged file preserved";return;}
                catch(RuntimeException|IOException badBackup) {ex.addSuppressed(badBackup);}
            }
            data=new JsonObject();recoveryNotice="Damaged library preserved; safe empty library loaded";
        } catch(IOException ex) {data=new JsonObject();loadError=new IOException("Library cannot be read; saving disabled",ex);}
    }
    private static JsonObject readLibrary(Path file) throws IOException {
        if(Files.size(file)>1_048_576)throw new IllegalArgumentException("Library exceeds 1 MiB");
        var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for(var entry:data.entrySet()) {
            if(entry.getKey().equals("player")) {
                if(!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid pinned player");
            } else if(!entry.getValue().isJsonObject())throw new IllegalArgumentException("Invalid preset group");
        }
        if(data.has("offsets"))for(var entry:data.getAsJsonObject("offsets").entrySet()) {
            double value=entry.getValue().getAsDouble();if(!Double.isFinite(value)||Math.abs(value)>30000)throw new IllegalArgumentException("Invalid saved offset");
        }
        return data;
    }

    public static String trackId(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public synchronized double offset(String key) {
        JsonObject offsets = group("offsets");
        return offsets.has(trackId(key)) ? offsets.get(trackId(key)).getAsDouble() : 0;
    }

    public synchronized void offset(String key, double millis) throws IOException {
        if (!Double.isFinite(millis) || Math.abs(millis) > 30000) throw new IllegalArgumentException("Offset must be within 30 seconds");
        group("offsets").addProperty(trackId(key), millis); save();
    }

    public Path lyricPath(String key) { return root.resolve("lyrics").resolve(trackId(key) + ".lrc"); }

    public Path documentPath(String key) {return root.resolve("lyrics").resolve(trackId(key)+".document.json");}
    public Lyrics local(String key) throws IOException {
        if(Files.exists(documentPath(key))) {
            if(Files.size(documentPath(key))>2_097_152)throw new IOException("Lyric document too large");
            try {return secondary(key,LyricDocumentCodec.decode(Files.readString(documentPath(key)))).manuallyCorrected();}
            catch(RuntimeException ex) {throw new IOException("Invalid edited lyric document",ex);}
        }
        Path file = lyricPath(key);
        if (!Files.exists(file)) return null;
        if (Files.size(file) > 1_048_576) throw new IOException("Local LRC exceeds 1 MiB");
        return secondary(key,Lyrics.parse(Files.readString(file))).manuallyCorrected();
    }

    public void importLyrics(String key, Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 1_048_576) throw new IOException("Choose an LRC file smaller than 1 MiB");
        String text=Files.readString(file);
        if(file.toString().endsWith(".yaml")||file.toString().endsWith(".yml"))saveDocument(key,dev.truwty.nocturne.lyrics.LyricsFormats.lyricsfile(text).manuallyCorrected());
        else chooseLyrics(key,text);
    }

    public void chooseLyrics(String key, String lrc) throws IOException {
        if (lrc.length() > 1_048_576 || Lyrics.parse(lrc).lines.isEmpty()) throw new IOException("No synchronized timestamps found in this LRC");
        write(lyricPath(key), lrc);Files.deleteIfExists(documentPath(key));
    }

    public void clearLyrics(String key) throws IOException { Files.deleteIfExists(lyricPath(key));Files.deleteIfExists(documentPath(key)); }
    public void saveDocument(String key,Lyrics document) throws IOException {
        String json=LyricDocumentCodec.encode(document.manuallyCorrected());
        if(json.getBytes(StandardCharsets.UTF_8).length>2_097_152)throw new IOException("Lyric document too large");
        try {LyricDocumentCodec.decode(json);}catch(RuntimeException ex){throw new IOException("Invalid lyric document",ex);}
        write(documentPath(key),json);
    }
    private Path secondaryPath(String key,boolean romanized) {return root.resolve("lyrics").resolve(trackId(key)+(romanized?".romanization.lrc":".translation.lrc"));}
    public void importSecondary(String key,Path file,boolean romanized) throws IOException {
        if(!Files.isRegularFile(file)||Files.size(file)>1_048_576)throw new IOException("Choose a synchronized LRC smaller than 1 MiB");
        String lrc=Files.readString(file);if(Lyrics.parse(lrc).lines.isEmpty())throw new IOException("Secondary lyrics need timestamps");write(secondaryPath(key,romanized),lrc);
    }
    public Lyrics secondary(String key,Lyrics primary) throws IOException {
        Lyrics translated=null,romanized=null;
        for(boolean roman:new boolean[]{false,true}) {
            Path file=secondaryPath(key,roman);if(!Files.exists(file))continue;
            if(Files.size(file)>1_048_576)throw new IOException("Secondary LRC too large");
            if(roman)romanized=Lyrics.parse(Files.readString(file));else translated=Lyrics.parse(Files.readString(file));
        }
        return primary.secondary(translated,romanized);
    }
    public synchronized void removePreset(String group,String name) throws IOException {group(group).remove(name);save();}

    public synchronized void preset(String group, String name, JsonObject settings) throws IOException {
        if (name.isBlank() || (group.equals("songAppearance") ? !name.matches("[0-9a-f]{64}") : name.length() > 48)) throw new IllegalArgumentException("Preset name must be 1–48 characters");
        group(group).add(name, settings.deepCopy()); save();
    }

    public synchronized JsonObject preset(String group, String name) {
        return group(group).has(name) ? group(group).getAsJsonObject(name).deepCopy() : null;
    }

    public synchronized List<String> presets(String group) { return List.copyOf(group(group).keySet()); }

    public synchronized String player() { return data.has("player") ? data.get("player").getAsString() : ""; }
    public synchronized void player(String id) throws IOException { data.addProperty("player", id); save(); }

    private JsonObject group(String name) {
        if (!data.has(name)) data.add(name, new JsonObject());
        return data.getAsJsonObject(name);
    }

    public String problem() { return loadError == null ? recoveryNotice : loadError.getMessage(); }
    private void save() throws IOException {
        if(loadError!=null)throw loadError;
        Path file=root.resolve("library.json");
        if(Files.exists(file)) {
            boolean valid=true;
            try {readLibrary(file);}catch(RuntimeException ex){valid=false;}
            if(valid)Files.copy(file,root.resolve("library.json.bak"),StandardCopyOption.REPLACE_EXISTING);
        }
        write(file,new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }

    public static void write(Path target, String text) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), "nocturne-", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
