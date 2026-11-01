package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.lyrics.*;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class LyricWorkspaceScreen extends Screen {
    private static final String[] TABS={"History","Timing","Languages","Song style"};
    private final Screen parent;
    private final NowPlaying track;
    private final TimingDocument document;
    private int page,line,word,x,y,w,h,body,scroll;
    private String notice="",path="";
    private EditBox lineStart,lineEnd,wordStart,wordEnd;
    private Lyrics dragOriginal;
    private boolean dragEnd;
    public LyricWorkspaceScreen(Screen parent) {
        super(Component.literal("Nocturne lyric workspace"));this.parent=parent;
        track=LyricsService.INSTANCE.now(); Lyrics lyrics=LyricsService.INSTANCE.lyrics();
        document=new TimingDocument(lyrics==null?Lyrics.parse(""):lyrics);
        line=Math.max(0,document.lyrics().indexAt(LyricsRenderer.clockMs()));
    }
    private LyricsSettings s(){return NocturneClient.SETTINGS;}
    private boolean hasLine(){return !document.lyrics().lines.isEmpty();}
    private Lyrics.Line selected(){return document.lyrics().lines.get(line);}
    private Lyrics.Word selectedWord(){return selected().words().get(word);}
    @Override public boolean isPauseScreen(){return false;}
    private void attempt(Action action){try{action.run();}catch(Exception ex){notice=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();NocturneClient.LOG.debug("Lyric workspace action failed",ex);}}
    @FunctionalInterface private interface Action{void run()throws Exception;}
    private Button button(int bx,int by,int bw,String label,Action action){return addRenderableWidget(StudioButton.create(Draw.comp(label),b->attempt(action)).bounds(bx,by,Math.max(30,bw),20).build());}
    private EditBox field(int bx,int by,int bw,String label,String value){EditBox box=new EditBox(font,bx,by,bw,20,Component.literal(label));box.setMaxLength(1024);box.setValue(value);box.setHint(Component.literal(label));return addRenderableWidget(box);}
    @Override protected void init(){
        w=Math.min(650,width-16);h=Math.min(406,height-16);x=(width-w)/2;y=(height-h)/2;scroll=Math.clamp(scroll,0,maxScroll());body=y+76-scroll;
        for(int i=0;i<TABS.length;i++){final int tab=i;button(x+12+i*(w-24)/4,y+39,(w-24)/4-4,(page==i?"• ":"")+TABS[i],()->{page=tab;scroll=0;notice="";rebuildWidgets();});}
        int first=children().size();
        if(hasLine()){
            line=Math.clamp(line,0,document.lyrics().lines.size()-1);word=Math.clamp(word,0,Math.max(0,selected().words().size()-1));
            if(page==0)history();else if(page==1)timing();else if(page==2)languages();else appearance();
        }else if(page==2)languages();else if(page==3)appearance();
        for(var child:children().subList(first,children().size()))if(child instanceof AbstractWidget widget)widget.visible=widget.getY()>=y+70&&widget.getY()+widget.getHeight()<=y+h-62;
        button(x+12,y+h-30,78,"Back",this::onClose);
        button(x+94,y+h-30,100,"Save lyrics",()->{requireTrack();LyricsService.INSTANCE.saveEdited(track,document.lyrics());notice="Timing and language edits saved for this song";});
        button(x+198,y+h-30,Math.min(120,w-210),"Copy Enhanced LRC",()->{minecraft.keyboardHandler.setClipboard(document.lyrics().enhancedLrc());notice="Copied; secondary lines remain in the local document";});
    }
    private void requireTrack(){NowPlaying current=LyricsService.INSTANCE.now();if(track==null||current==null||!track.key().equals(current.key()))throw new IllegalStateException("Song changed. Reopen the workspace before saving.");}
    private void selectLine(int next){line=Math.clamp(next,0,document.lyrics().lines.size()-1);word=0;rebuildWidgets();}
    private void history(){
        button(x+16,body,w/3-20,"Previous lines",()->selectLine(line-5));
        button(x+w/3,body,w/3-8,"Follow playback",()->selectLine(Math.max(0,document.lyrics().indexAt(LyricsRenderer.clockMs()))));
        button(x+2*w/3,body,w/3-16,"Upcoming lines",()->selectLine(line+5));
        int first=Math.max(0,line-2);
        for(int i=first;i<Math.min(document.lyrics().lines.size(),first+7);i++){
            final int index=i;var item=document.lyrics().lines.get(i);
            button(x+16,body+28+(i-first)*25,w-32,(i==line?"› ":"  ")+stamp(item.start())+"  "+font.plainSubstrByWidth(item.text(),w-140),()->{line=index;word=0;page=1;scroll=0;rebuildWidgets();});
        }
    }
    private void timing(){
        button(x+16,body,70,"Prev line",()->selectLine(line-1));button(x+90,body,70,"Next line",()->selectLine(line+1));
        button(x+164,body,60,"Undo",()->{document.undo();rebuildWidgets();});button(x+228,body,60,"Redo",()->{document.redo();rebuildWidgets();});
        lineStart=field(x+16,body+40,90,"Line start ms",Long.toString(selected().start()));lineEnd=field(x+110,body+40,90,"Line end ms",Long.toString(selected().end()));
        button(x+204,body+40,w-220,"Apply line bounds",()->{document.line(line,Long.parseLong(lineStart.getValue()),Long.parseLong(lineEnd.getValue()));rebuildWidgets();});
        button(x+16,body+64,90,"Line -50 ms",()->shift(-50));button(x+110,body+64,90,"Line +50 ms",()->shift(50));
        button(x+204,body+64,w-220,"Line starts at playhead",()->shift(LyricsService.INSTANCE.now().positionMs()-selected().start()));
        if(!selected().words().isEmpty()){
            button(x+16,body+103,90,"Prev word",()->{word=Math.max(0,word-1);rebuildWidgets();});button(x+110,body+103,90,"Next word",()->{word=Math.min(selected().words().size()-1,word+1);rebuildWidgets();});
            wordStart=field(x+16,body+140,90,"Word start ms",Long.toString(selectedWord().start()));wordEnd=field(x+110,body+140,90,"Word end ms",Long.toString(selectedWord().end()));
            button(x+204,body+140,w-220,"Apply word bounds",()->{document.word(line,word,Long.parseLong(wordStart.getValue()),Long.parseLong(wordEnd.getValue()));rebuildWidgets();});
            button(x+16,body+164,(w-36)/2,"Word starts at playhead",()->{document.word(line,word,position(),selectedWord().end());rebuildWidgets();});
            button(x+20+(w-36)/2,body+164,(w-36)/2,"Word ends at playhead",()->{document.word(line,word,selectedWord().start(),position());rebuildWidgets();});
        }
        button(x+16,body+245,w-32,"Seek player to selected line",()->{requireTrack();if(!LyricsService.INSTANCE.system.can("seek")||LyricsService.INSTANCE.demoPlaying())throw new IllegalStateException("This player does not support seeking");LyricsService.INSTANCE.system.command("seek",selected().start());});
    }
    private long position(){requireTrack();return LyricsService.INSTANCE.now().positionMs();}
    private void shift(long delta){document.line(line,selected().start()+delta,selected().end()+delta);rebuildWidgets();}
    private void languages(){
        button(x+16,body,w-32,"Translation: "+(s().translation.on()?"On":"Off"),()->{s().translation.toggle();s().save();rebuildWidgets();});
        button(x+16,body+24,w-32,"Romanization: "+(s().romanization.on()?"On":"Off"),()->{s().romanization.toggle();s().save();rebuildWidgets();});
        EditBox input=field(x+16,body+69,w-32,"Full path to synchronized UTF-8 LRC",path);input.setResponder(value->path=value);
        button(x+16,body+93,(w-36)/2,"Import translation",()->importSecondary(false));button(x+20+(w-36)/2,body+93,(w-36)/2,"Import romanization",()->importSecondary(true));
        button(x+16,body+136,(w-36)/2,"Copy translation LRC",()->minecraft.keyboardHandler.setClipboard(document.lyrics().secondaryLrc(false)));
        button(x+20+(w-36)/2,body+136,(w-36)/2,"Copy romanization LRC",()->minecraft.keyboardHandler.setClipboard(document.lyrics().secondaryLrc(true)));
        button(x+16,body+180,w-32,"Vocal countdown: "+(s().vocalCountdown.on()?"On":"Off"),()->{s().vocalCountdown.toggle();s().save();rebuildWidgets();});
        button(x+16,body+204,w-32,"Confidence label: "+(s().lyricConfidence.on()?"On":"Off"),()->{s().lyricConfidence.toggle();s().save();rebuildWidgets();});
    }
    private void importSecondary(boolean romanized)throws Exception{
        requireTrack();LyricsService.INSTANCE.importSecondary(Path.of(path),romanized);
        Lyrics updated=Studio.library().secondary(track.key(),document.lyrics());
        // Keep unsaved timing edits when adding a language layer.
        document.replace(updated);notice="Secondary lines imported; no automatic translation was generated";rebuildWidgets();
    }
    private void appearance(){
        button(x+16,body+24,w-32,"Save current appearance for this song",()->{requireTrack();SongAppearance.save(track);notice="Song appearance saved";});
        button(x+16,body+48,w-32,"Remove song appearance",()->{requireTrack();SongAppearance.remove(track);notice="Global appearance restored";});
        button(x+16,body+88,w-32,"Font: "+s().lyricFont.current(),()->{s().lyricFont.cycle(1);s().save();rebuildWidgets();});
        addRenderableWidget(new StudioSlider(x+16,body+112,w-32,20,Component.literal("Size"),s().size.fraction()){
            {updateMessage();} protected void updateMessage(){setMessage(Draw.comp("Size: "+s().size.display()));}protected void applyValue(){s().size.setFraction(value);updateMessage();}public void onRelease(MouseButtonEvent event){super.onRelease(event);s().save();}});
        button(x+16,body+140,w-32,"Placement and effects in Studio",()->minecraft.setScreen(new StudioScreen(this,4)));
    }
    private long timelineStart(){return Math.max(0,selected().start()-1000);}
    private long timelineEnd(){return Math.max(timelineStart()+2000,selected().end()+1000);}
    private int timelineX(long time){return x+20+(int)Math.clamp((time-timelineStart())*(w-40)/(timelineEnd()-timelineStart()),0,w-40);}
    private long timelineTime(double mx){return timelineStart()+Math.round(Math.clamp((mx-x-20)/(w-40),0,1)*(timelineEnd()-timelineStart()));}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(page==1&&hasLine()&&!selected().words().isEmpty()&&event.y()>=body+205&&event.y()<=body+234&&event.y()>=y+70&&event.y()<y+h-62){
            int left=timelineX(selectedWord().start()),right=timelineX(selectedWord().end());
            if(Math.min(Math.abs(event.x()-left),Math.abs(event.x()-right))<=10){dragEnd=Math.abs(event.x()-right)<Math.abs(event.x()-left);dragOriginal=document.lyrics();return true;}
        }return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(dragOriginal!=null){attempt(()->{long time=timelineTime(event.x());document.previewWord(line,word,dragEnd?selectedWord().start():time,dragEnd?time:selectedWord().end());});return true;}return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){if(dragOriginal!=null){document.commitPreview(dragOriginal);dragOriginal=null;rebuildWidgets();return true;}return super.mouseReleased(event);}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){scroll=Math.clamp(scroll-(int)Math.round(sy*24),0,maxScroll());rebuildWidgets();return true;}
    private int maxScroll(){return Math.max(0,(page==1?285:page==2?230:page==0?205:185)-(h-138));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0x88000000);}
    private void text(GuiGraphicsExtractor g,String text,int yy,int color){Draw.text(g,font,font.plainSubstrByWidth(text,w-32),x+16,yy,color,false);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        Draw.round(g,x,y,w,h,12,Draw.withAlpha(s().uiBackground.argb(),Math.round(s().uiOpacity.f()*2.55f)));
        text(g,"Nocturne / "+TABS[page],y+16,s().uiText.argb());super.extractRenderState(g,mx,my,delta);
        g.enableScissor(x+12,y+70,x+w-12,y+h-62);
        if(!hasLine()&&page<2)text(g,"Load lyrics in Studio first",body,s().uiText.argb());
        if(page==1&&hasLine()){
            text(g,"Line "+(line+1)+" / "+document.lyrics().lines.size()+" · start / end in milliseconds",body+25,s().uiText.argb());
            if(!selected().words().isEmpty()){
                text(g,"Word "+(word+1)+" / "+selected().words().size()+"   "+selectedWord().text(),body+128,s().uiAccent.argb());
                text(g,"Drag a word boundary. Times are raw player milliseconds (before offsets).",body+192,Draw.alpha(s().uiText.argb(),.65f));
                int top=body+212;g.fill(x+20,top,x+w-20,top+14,0x44333344);
                for(var item:selected().words())g.fill(timelineX(item.start()),top+4,Math.max(timelineX(item.start())+1,timelineX(item.end())),top+10,Draw.alpha(s().uiAccent.argb(),.35f));
                int a=timelineX(selectedWord().start()),b=timelineX(selectedWord().end());g.fill(a,top,b,top+14,s().uiAccent.argb());g.fill(a-2,top-3,a+2,top+17,-1);g.fill(b-2,top-3,b+2,top+17,-1);
                NowPlaying playing=LyricsService.INSTANCE.now();if(playing!=null){int play=timelineX(playing.positionMs());g.fill(play,top-5,play+1,top+19,0xFFFFC16A);}
                text(g,stamp(timelineStart())+" → "+stamp(timelineEnd())+" · player "+stamp(LyricsService.INSTANCE.now()==null?0:LyricsService.INSTANCE.now().positionMs()),body+231,s().uiText.argb());
            }
        }else if(page==2){
            text(g,"Optional source lyrics or your synchronized LRC sidecars",body+51,s().uiText.argb());
            long translations=document.lyrics().lines.stream().filter(l->!l.translation().isBlank()).count(),romanized=document.lyrics().lines.stream().filter(l->!l.romanization().isBlank()).count();
            text(g,translations+" translated / "+romanized+" romanized lines available",body+119,s().uiAccent.argb());
        }else if(page==3){text(g,SongAppearance.active()?"Song profile active; global appearance is preserved":"Using global appearance",body,s().uiText.argb());text(g,"Save again after editing placement, font, size or effects.",body+169,Draw.alpha(s().uiText.argb(),.65f));}
        g.disableScissor();
        if(maxScroll()>0){int top=y+70,available=h-132;int thumb=Math.max(10,available*available/(available+maxScroll()));int ty=top+(available-thumb)*scroll/Math.max(1,maxScroll());g.fill(x+w-8,top,x+w-6,top+available,0x22333344);g.fill(x+w-8,ty,x+w-6,ty+thumb,s().uiAccent.argb());}

        String status=!notice.isBlank()?notice:(maxScroll()>0?"Scroll for more · ":"")+document.lyrics().confidence()+" · "+(track==null?"No player":track.title());text(g,status,y+h-48,s().uiText.argb());
    }
    private static String stamp(long ms){return "%d:%02d.%03d".formatted(ms/60000,(ms/1000)%60,ms%1000);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
