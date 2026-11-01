package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class LyricsFormats {
    private LyricsFormats() {}
    private static long number(Map<?,?> row,String key,long fallback) {
        Object n=row.get(key);if(n==null)return fallback;
        if(!(n instanceof Number value) || value.doubleValue()!=value.longValue() || value.longValue()<0 || value.longValue()>86_400_000)throw new IllegalArgumentException("Invalid timestamp: "+key);
        return value.longValue();
    }
    private static String text(Map<?,?> row,String key) {Object v=row.get(key);return v instanceof String s?s:"";}
    public static Lyrics lyricsfile(String document) {
        if(document.length()>1_048_576)throw new IllegalArgumentException("Lyricsfile too large");
        var options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);options.setNestingDepthLimit(20);options.setCodePointLimit(1_048_576);
        Object loaded=new Yaml(new SafeConstructor(options)).load(document);
        if(!(loaded instanceof Map<?,?> root) || !"1.0".equals(root.get("version")))throw new IllegalArgumentException("Unsupported Lyricsfile version");
        if(!(root.get("lines") instanceof List<?> rows))return Lyrics.restored(List.of(),false);
        if(rows.size()>10000)throw new IllegalArgumentException("Too many lyric lines");
        var lines=new ArrayList<Lyrics.Line>();long previous=-1;
        for(int i=0;i<rows.size();i++) {
            if(!(rows.get(i) instanceof Map<?,?> row))throw new IllegalArgumentException("Invalid lyric line");
            long start=number(row,"start_ms",-1);if(start<previous || start<0)throw new IllegalArgumentException("Unordered lyric lines");previous=start;
            long end=number(row,"end_ms",i+1<rows.size() && rows.get(i+1) instanceof Map<?,?> next?number(next,"start_ms",start+5000):start+5000);
            if(end<start||end>86_400_000)throw new IllegalArgumentException("Line ends before it starts");
            var words=new ArrayList<Lyrics.Word>();
            if(row.get("words") instanceof List<?> segments) {
                if(segments.size()>10000)throw new IllegalArgumentException("Too many words");
                long last=start;
                for(int j=0;j<segments.size();j++) {
                    if(!(segments.get(j) instanceof Map<?,?> w))throw new IllegalArgumentException("Invalid timed segment");
                    long a=number(w,"start_ms",-1),b=number(w,"end_ms",j+1<segments.size()&&segments.get(j+1) instanceof Map<?,?> next?number(next,"start_ms",end):end);
                    if(a<last || b<a || b>end)throw new IllegalArgumentException("Invalid word timing");last=a;
                    words.add(new Lyrics.Word(text(w,"text"),a,b,true));
                }
            }
            String line=text(row,"text");if(line.isBlank())continue;
            if(words.isEmpty()) {
                var generated=Lyrics.parse("[00:00.000]"+line).lines.getFirst().words();
                long span=Math.max(1,end-start);long total=Math.max(1,generated.getLast().end());
                for(var w:generated)words.add(new Lyrics.Word(w.text(),start+w.start()*span/total,start+w.end()*span/total));
            } else if(!words.stream().map(Lyrics.Word::text).reduce("",String::concat).equals(line))throw new IllegalArgumentException("Timed segments do not reproduce the line");
            lines.add(new Lyrics.Line(start,end,line,words,text(row,"translation"),text(row,"romanization")));
        }
        return Lyrics.restored(lines,false);
    }
    public static Lyrics fromRecord(JsonObject record) {
        if(record.has("lyricsfile") && !record.get("lyricsfile").isJsonNull()) {
            try {Lyrics result=lyricsfile(record.get("lyricsfile").getAsString());if(!result.lines.isEmpty())return result;}
            catch(RuntimeException ex) {dev.truwty.nocturne.NocturneClient.LOG.debug("Unsupported Lyricsfile; using legacy LRC",ex);}
        }
        if(record.has("syncedLyrics")&&!record.get("syncedLyrics").isJsonNull()) {
            Lyrics result=Lyrics.parse(record.get("syncedLyrics").getAsString());if(!result.lines.isEmpty())return result;
        }
        return null;
    }
    private static String nested(JsonObject root,String field) {
        return root.has(field)&&root.get(field).isJsonObject()&&root.getAsJsonObject(field).has("lyric")&&!root.getAsJsonObject(field).get("lyric").isJsonNull()?root.getAsJsonObject(field).get("lyric").getAsString():"";
    }
    public static Lyrics netease(JsonObject root) {
        try {return neteaseTimed(root);}
        catch(RuntimeException ex){
            dev.truwty.nocturne.NocturneClient.LOG.debug("Provider word timestamps invalid; using line lyrics",ex);
            JsonObject fallback=root.deepCopy();fallback.remove("yrc");fallback.remove("klyric");return neteaseTimed(fallback);
        }
    }
    private static Lyrics neteaseTimed(JsonObject root) {
        String timed=nested(root,"yrc");if(timed.isBlank())timed=nested(root,"klyric");
        if(timed.length()>1_048_576)throw new IllegalArgumentException("Provider lyrics too large");
        var rows=new ArrayList<Lyrics.Line>();
        var linePattern=Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");var wordPattern=Pattern.compile("\\((\\d+),(\\d+),\\d+\\)");
        for(String line:timed.split("\\R")) {
            var lm=linePattern.matcher(line);if(!lm.matches())continue;
            long start=Long.parseLong(lm.group(1)),end=start+Long.parseLong(lm.group(2));if(start<0||end<start||end>86_400_000||rows.size()>10000)throw new IllegalArgumentException("Invalid provider line timing");var matcher=wordPattern.matcher(lm.group(3));
            var marks=new ArrayList<long[]>();var texts=new ArrayList<String>();int last=-1;
            while(matcher.find()) {if(last>=0)texts.add(lm.group(3).substring(last,matcher.start()));long a=Long.parseLong(matcher.group(1));if(a<start)a+=start;marks.add(new long[]{a,a+Long.parseLong(matcher.group(2))});last=matcher.end();}
            if(last>=0)texts.add(lm.group(3).substring(last));var words=new ArrayList<Lyrics.Word>();
            for(int i=0;i<texts.size();i++)if(!texts.get(i).isEmpty()) {var mark=marks.get(i);if(mark[0]<start||mark[1]>end||mark[1]<mark[0]||(!words.isEmpty()&&mark[0]<words.getLast().start()))throw new IllegalArgumentException("Invalid provider word timing");words.add(new Lyrics.Word(texts.get(i),mark[0],mark[1],true));}
            if(!words.isEmpty())rows.add(new Lyrics.Line(start,end,String.join("",texts),words));
        }
        Lyrics result=rows.isEmpty()?Lyrics.parse(nested(root,"lrc")):Lyrics.restored(rows,false);
        rows.sort(java.util.Comparator.comparingLong(Lyrics.Line::start));
        if(!rows.isEmpty())result=Lyrics.restored(rows,false);
        result=Lyrics.restored(result.lines.stream().filter(l->!LyricsFinder.CREDIT.matcher(l.text()).matches()&&!l.text().contains("纯音乐")).toList(),false);
        return result.secondary(Lyrics.parse(nested(root,"tlyric")),Lyrics.parse(nested(root,"romalrc")));
    }
}
