package dev.truwty.nocturne;

import com.google.gson.*;
import dev.truwty.nocturne.gui.StudioEdits;
import dev.truwty.nocturne.lyrics.LyricsRenderer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LyricLooks {
    public static final String GROUP="lyricPresets";
    public record Look(String name,String category,String description,JsonObject values) {
        public Look { values=values.deepCopy(); }
        @Override public JsonObject values() { return values.deepCopy(); }
    }
    private static final List<Look> LOOKS=load();
    private static final Set<String> SETTINGS=Set.copyOf(LOOKS.getFirst().values().keySet());
    private LyricLooks() {}
    private static List<Look> load() {
        try(var input=LyricLooks.class.getResourceAsStream("/nocturne/lyric-presets.json")) {
            if(input==null)throw new IOException("Lyric preset catalog missing");
            var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IOException("Unknown lyric preset catalog schema");
            var result=new ArrayList<Look>();var names=new HashSet<String>();
            for(var element:root.getAsJsonArray("presets")) {
                var entry=element.getAsJsonObject();String name=entry.get("name").getAsString();
                if(name.isBlank()||name.length()>48||!names.add(name))throw new IOException("Invalid/duplicate lyric preset name");
                result.add(new Look(name,entry.get("category").getAsString(),entry.get("description").getAsString(),entry.getAsJsonObject("values")));
            }
            if(result.size()<51)throw new IOException("Incomplete lyric preset catalog");
            return List.copyOf(result);
        }catch(IOException | RuntimeException ex){throw new ExceptionInInitializerError(ex);}
    }
    public static List<Look> all() {return LOOKS;}
    public static boolean controls(String name) {return SETTINGS.contains(name);}
    public static Look named(String name) {return LOOKS.stream().filter(p->p.name().equals(name)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown lyric preset: "+name));}
    public static List<Look> search(String query,String category) {
        String needle=query.strip().toLowerCase(Locale.ROOT);
        return LOOKS.stream().filter(p->category.equals("All")||p.category().equals(category))
            .filter(p->(p.name()+" "+p.category()+" "+p.description()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public static void apply(JsonObject values) throws IOException {
        var before=Studio.snapshot(GROUP);
        Studio.tick();
        try {
            Studio.apply(GROUP,values,false);Studio.adoptPlacement();Studio.savePlacement();
            NocturneClient.SETTINGS.adoptPreset();NocturneClient.SETTINGS.save();
            LyricsRenderer.appearanceChanged();StudioEdits.record(GROUP,before,Studio.snapshot(GROUP));
        }catch(IOException | RuntimeException ex) {
            Studio.apply(GROUP,before,false);Studio.adoptPlacement();NocturneClient.SETTINGS.adoptPreset();LyricsRenderer.appearanceChanged();throw ex;
        }
    }
}
