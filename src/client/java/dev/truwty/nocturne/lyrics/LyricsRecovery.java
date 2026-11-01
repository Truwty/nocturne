package dev.truwty.nocturne.lyrics;

public final class LyricsRecovery {
    public enum Action { NONE, ENABLE, RETRY, RESYNC, SHOW, RECONNECT }
    public record Diagnosis(Action action,String summary,String detail,String button) {}
    private LyricsRecovery() {}
    static Diagnosis classify(boolean enabled,boolean track,boolean lyrics,String status,boolean activeLine,boolean hidden,long clockError,boolean rendering) {
        if(!enabled)return new Diagnosis(Action.ENABLE,"Lyrics disabled","Enable lyrics to render the current song","Enable lyrics");
        if(!track)return new Diagnosis(Action.RECONNECT,"No desktop song","Start playback; reconnect if your app is missing","Reconnect player");
        if(status.startsWith("Advertisement") || status.startsWith("Instrumental"))return new Diagnosis(Action.NONE,status,"Playback is detected; no lyric repair is needed","Restore visible lyrics");
        if(!lyrics) {
            boolean waiting=status.startsWith("Finding") || status.contains("trying again");
            return new Diagnosis(waiting?Action.NONE:Action.RETRY,waiting?"Looking up lyrics":"Lyric lookup failed",waiting?"The lookup is still running":"Retry, search alternatives, or import a local LRC",waiting?"Restore visible lyrics":"Retry lyric lookup");
        }
        if(rendering && Math.abs(clockError)>2000)return new Diagnosis(Action.RESYNC,"Playback timing drift","Resynchronize after a seek, pause, or device switch","Resync playback");
        if(rendering && activeLine && hidden)return new Diagnosis(Action.SHOW,"Lyrics are out of view","Reset placement and use the compatible renderer","Restore visible lyrics");
        return new Diagnosis(Action.NONE,"Playback ready","Use Lyrics to calibrate an inaccurate provider match","Restore visible lyrics");
    }
    public static Diagnosis current() {
        var s=dev.truwty.nocturne.NocturneClient.SETTINGS;
        var svc=LyricsService.INSTANCE;var n=svc.now();var lyrics=svc.lyrics();
        if(n==null&&dev.truwty.nocturne.playback.PlaybackPreferences.cloud())return new Diagnosis(Action.RECONNECT,"Spotify account playback unavailable",dev.truwty.nocturne.playback.Playback.spotify().status(),"Open Spotify connection");
        boolean rendering=System.nanoTime()-LyricsDebug.lastRender<1_000_000_000L;
        long expected=n==null?0:n.positionMs()+Math.round(s.offset.get()+dev.truwty.nocturne.Studio.songOffset(n));
        int index=lyrics==null?-1:lyrics.indexAt(expected);
        boolean active=index>=0 && expected<lyrics.lines.get(index).end() && !lyrics.lines.get(index).words().isEmpty();
        boolean hidden=LyricsDebug.hud ? LyricsDebug.hudRecorded>0 && LyricsDebug.hudDrawn==0 && LyricsDebug.hudOff>0 : LyricsDebug.worldPieces==0;
        return classify(s.isEnabled(),n!=null,lyrics!=null,svc.lyricsStatus(),active,hidden,LyricsRenderer.clockMs()-expected,rendering);
    }
}
