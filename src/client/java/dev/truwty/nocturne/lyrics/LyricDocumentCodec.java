package dev.truwty.nocturne.lyrics;
import com.google.gson.Gson;
import java.util.List;
public final class LyricDocumentCodec {
    private static final Gson JSON=new Gson();
    private record Document(int schema,boolean estimated,boolean manual,List<Lyrics.Line> lines) {}
    private LyricDocumentCodec() {}
    public static String encode(Lyrics lyrics) {return JSON.toJson(new Document(1,lyrics.estimated,lyrics.manual,lyrics.lines));}
    public static Lyrics decode(String json) {
        if(json.length()>2_097_152)throw new IllegalArgumentException("Lyric document exceeds 2 MiB");
        var d=JSON.fromJson(json,Document.class);
        if(d==null||d.schema!=1||d.lines==null||d.lines.size()>10000)throw new IllegalArgumentException("Invalid lyric document");
        long previous=-1;
        for(var line:d.lines) {
            if(line==null||line.start()<0||line.start()<previous||line.end()<line.start()||line.end()>86_400_000||line.text()==null||line.words().size()>10000)throw new IllegalArgumentException("Invalid line bounds");previous=line.start();
            long wordStart=line.start();for(var w:line.words()){if(w.text()==null||w.start()<wordStart||w.end()<w.start()||w.end()>line.end())throw new IllegalArgumentException("Invalid word bounds");wordStart=w.start();}
        }
        Lyrics result=Lyrics.restored(d.lines,d.estimated);return d.manual?result.manuallyCorrected():result;
    }
}
