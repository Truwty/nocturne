package dev.truwty.nocturne;

import com.google.gson.JsonObject;
import dev.truwty.nocturne.lyrics.LyricsService;
import dev.truwty.nocturne.lyrics.NowPlaying;
import java.io.IOException;

public final class SongAppearance {
    private static String key="";
    private static JsonObject baseline;
    private SongAppearance() {}
    public static boolean active() {return baseline!=null;}
    public static void persistGlobal(JsonObject settings) {
        if(baseline!=null) baseline.entrySet().forEach(e->settings.add(e.getKey(),e.getValue().deepCopy()));
    }
    public static void restore() {
        if(baseline==null)return;
        JsonObject saved=baseline; baseline=null;
        Studio.apply("song",saved,false); NocturneClient.SETTINGS.adoptPreset(); Studio.adoptPlacement();
    }
    public static void tick() {
        NowPlaying track=LyricsService.INSTANCE.now(); String next=track==null?"":track.key();
        if(next.equals(key))return;
        restore();key=next;
        if(track==null)return;
        JsonObject profile;
        try {profile=Studio.library().preset("songAppearance",StudioLibrary.trackId(next));}
        catch(RuntimeException ex) {NocturneClient.LOG.warn("Invalid song appearance ignored",ex);return;}
        if(profile==null)return;
        JsonObject global=Studio.snapshot("song");
        try { Studio.apply("song",profile,false); baseline=global; NocturneClient.SETTINGS.preset.set(0); NocturneClient.SETTINGS.adoptPreset(); Studio.adoptPlacement(); }
        catch(RuntimeException ex) {Studio.apply("song",global,false);NocturneClient.LOG.warn("Invalid song appearance ignored",ex);}
    }
    public static void save(NowPlaying track) throws IOException {
        if(track==null)throw new IllegalStateException("Play a song first");
        Studio.library().preset("songAppearance",StudioLibrary.trackId(track.key()),Studio.snapshot("song"));
        key="";tick();
    }
    public static void remove(NowPlaying track) throws IOException {
        if(track==null)throw new IllegalStateException("Play a song first");
        Studio.library().removePreset("songAppearance",StudioLibrary.trackId(track.key()));restore();key="";tick();
    }
}
