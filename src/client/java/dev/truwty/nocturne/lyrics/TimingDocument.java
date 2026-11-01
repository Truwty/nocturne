package dev.truwty.nocturne.lyrics;

import java.util.*;

public final class TimingDocument {
    private Lyrics lyrics;
    private final Deque<Lyrics> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public TimingDocument(Lyrics lyrics) {this.lyrics=Objects.requireNonNull(lyrics);}
    public Lyrics lyrics() {return lyrics;}
    public void replace(Lyrics next) {apply(Objects.requireNonNull(next));}
    private void apply(Lyrics next) {undo.addLast(lyrics);while(undo.size()>40)undo.removeFirst();redo.clear();lyrics=next;}
    public boolean undo() {if(undo.isEmpty())return false;redo.addLast(lyrics);lyrics=undo.removeLast();return true;}
    public boolean redo() {if(redo.isEmpty())return false;undo.addLast(lyrics);lyrics=redo.removeLast();return true;}
    private static void bounds(long start,long end) {if(start<0 || end<start || end>86_400_000)throw new IllegalArgumentException("Timing must be ordered and within 24 hours");}
    public void line(int index,long start,long end) {
        bounds(start,end);var current=lyrics.lines.get(index);
        if(index>0&&start<lyrics.lines.get(index-1).start() || index+1<lyrics.lines.size()&&start>lyrics.lines.get(index+1).start())throw new IllegalArgumentException("Line would cross a neighboring line");
        long shift=start-current.start();var words=current.words().stream().map(w->new Lyrics.Word(w.text(),w.start()+shift,w.end()+shift,w.sourceTimed())).toList();
        if(words.stream().anyMatch(w->w.start()<start||w.end()>end))throw new IllegalArgumentException("Line end must contain its words; adjust word bounds first");
        var lines=new ArrayList<>(lyrics.lines);lines.set(index,new Lyrics.Line(start,end,current.text(),words,current.translation(),current.romanization()));apply(lyrics.corrected(lines));
    }
    private Lyrics changedWord(int line,int index,long start,long end) {
        bounds(start,end);var current=lyrics.lines.get(line);
        if(start<current.start()||end>current.end())throw new IllegalArgumentException("Word must remain inside the line");
        var words=new ArrayList<>(current.words());
        if(index>0&&start<words.get(index-1).start() || index+1<words.size()&&start>words.get(index+1).start())throw new IllegalArgumentException("Word would cross a neighboring word");
        words.set(index,new Lyrics.Word(words.get(index).text(),start,end,true));var lines=new ArrayList<>(lyrics.lines);
        lines.set(line,new Lyrics.Line(current.start(),current.end(),current.text(),words,current.translation(),current.romanization()));return lyrics.corrected(lines);
    }
    public void word(int line,int index,long start,long end) {apply(changedWord(line,index,start,end));}
    public void previewWord(int line,int index,long start,long end) {lyrics=changedWord(line,index,start,end);}
    public void commitPreview(Lyrics original) {if(lyrics!=original){undo.addLast(original);while(undo.size()>40)undo.removeFirst();redo.clear();}}
}
