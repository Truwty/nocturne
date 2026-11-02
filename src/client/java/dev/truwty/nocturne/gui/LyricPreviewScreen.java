package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.lyrics.LyricSearch;
import dev.truwty.nocturne.lyrics.Lyrics;
import dev.truwty.nocturne.lyrics.LyricsService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class LyricPreviewScreen extends StudioPanel {
    private final LyricSearch.Candidate candidate;
    private final Lyrics document;
    private final String trackKey;
    private final String matchConfidence;
    public LyricPreviewScreen(Screen parent,LyricSearch.Candidate candidate) {
        super(parent,"Preview lyric match");this.candidate=candidate;document=Lyrics.parse(candidate.lrc());
        var track=LyricsService.INSTANCE.now();trackKey=track==null?"":track.key();
        matchConfidence=LyricSearch.confidence(candidate,track);
    }
    @Override protected int contentHeight() { return 116+document.lines.size()*22; }
    @Override protected void build() {}
    @Override protected void footer() {
        button(x+12,y+h-29,86,"Cancel","Keep the currently selected lyrics",this::onClose);
        button(x+104,y+h-29,w-116,"Use for current song","Save this lyric selection for future playback",()->{
            var track=LyricsService.INSTANCE.now();
            if(track==null||!track.key().equals(trackKey))throw new java.io.IOException("Song changed; reopen the preview for the current song");
            LyricsService.INSTANCE.choose(candidate);onClose();
        });
    }
    @Override protected void content(GuiGraphicsExtractor g,int mx,int my,float delta) {
        int color=NocturneClient.SETTINGS.uiText.argb();
        text(g,candidate.label(),body,color);
        text(g,matchConfidence,body+20,color);
        text(g,document.confidence(),body+40,color);
        text(g,"Scroll to inspect the complete lyrics. Nothing changes until you save.",body+64,color);
        int first=Math.max(0,(y+48-(body+94))/22);
        int last=Math.min(document.lines.size(),(y+h-52-(body+94))/22+1);
        int yy=body+94+first*22;
        for(int index=first;index<last;index++) {
            var line=document.lines.get(index);
            text(g,String.format(java.util.Locale.ROOT,"%02d:%02d.%01d  %s",line.start()/60000,line.start()/1000%60,line.start()/100%10,line.text()),yy,color);
            yy+=22;
        }
    }
}
