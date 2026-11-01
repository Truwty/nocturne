package dev.truwty.nocturne.lyrics;

final class PlaybackSync {
    private PlaybackSync() {}
    static String transition(NowPlaying before,NowPlaying after) {
        if(after==null)return before==null?"":"Player disconnected";
        if(before==null)return "Player connected";
        if(!before.sessionId().equals(after.sessionId()))return "Player/device changed";
        if(!before.key().equals(after.key()))return "Track changed";
        if(before.playing()!=after.playing())return after.playing()?"Playback resumed":"Playback paused";
        if(Math.abs(before.positionMs()-after.progressMs())>900)return "Seek synchronized";
        return "";
    }
}
