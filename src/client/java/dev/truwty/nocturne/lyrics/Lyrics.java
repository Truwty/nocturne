package dev.truwty.nocturne.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {
   public final List<Lyrics.Line> lines;
   public final long beatMs;
   public final long beatOrigin;
   public final boolean estimated;
   public final boolean manual;
   private final String confidenceLabel;
   private final List<long[]> vocalRanges;
   private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d+(?:[.:]\\d+)?)]");
   private static final Pattern WORD_TIME = Pattern.compile("<(\\d+):(\\d+(?:[.:]\\d+)?)>");

   private Lyrics(List<Lyrics.Line> lines) {
      this(lines, false);
   }

   private Lyrics(List<Lyrics.Line> lines, boolean estimated) { this(lines,estimated,false); }
   private Lyrics(List<Lyrics.Line> lines, boolean estimated,boolean manual) {
      this.lines = List.copyOf(lines);
      this.estimated = estimated; this.manual=manual;
      List<Long> gaps = new ArrayList<>();
      long first = -1L;
      long prev = -1L;

      for (Lyrics.Line l : lines) {
         for (Lyrics.Word w : l.words()) {
            if (first < 0L) {
               first = w.start();
            }

            if (prev >= 0L) {
               long d = w.start() - prev;
               if (d >= 120L && d <= 1500L) {
                  gaps.add(d);
               }
            }

            prev = w.start();
         }
      }

      long beat = 500L;
      if (gaps.size() >= 8) {
         gaps.sort(Long::compare);
         beat = gaps.get(gaps.size() / 2);

         while (beat < 400L) {
            beat *= 2L;
         }

         while (beat > 900L) {
            beat /= 2L;
         }
      }

      this.beatMs = beat;
      this.beatOrigin = Math.max(0L, first);
      long total=lines.stream().flatMap(l->l.words().stream()).count(),timed=lines.stream().flatMap(l->l.words().stream()).filter(Word::sourceTimed).count();
      String timing=lines.isEmpty()?"No lyrics":estimated?"Estimated line timing":total>0&&timed==total?"Word synchronized":timed>0?"Mixed word timing / some estimates":"Line synchronized / word timing estimated";
      confidenceLabel=manual?"Manually supplied / corrected · "+timing:timing;
      var ranges=new ArrayList<long[]>();
      for(var line:lines){boolean authored=!line.words().isEmpty()&&line.words().stream().allMatch(Word::sourceTimed);
         long a=authored?line.words().getFirst().start():line.start(),b=authored?line.words().stream().mapToLong(Word::end).max().orElse(line.end()):line.end();
         ranges.add(new long[]{a,b});}
      ranges.sort(java.util.Comparator.comparingLong(a->a[0]));var merged=new ArrayList<long[]>();
      for(var range:ranges){if(!merged.isEmpty()&&merged.getLast()[1]>=range[0])merged.getLast()[1]=Math.max(merged.getLast()[1],range[1]);else merged.add(range);}
      vocalRanges=List.copyOf(merged);

   }

   static Lyrics restored(List<Line> lines, boolean estimated) { return new Lyrics(List.copyOf(lines), estimated); }

   public float beat(long pos) {
      if (pos < this.beatOrigin) {
         return 0.0F;
      }

      float phase = (float)((pos - this.beatOrigin) % this.beatMs) / (float)this.beatMs;
      return (float)Math.exp(-phase * 7.0F);
   }

   private static long ms(String min, String sec) {
      double seconds=Double.parseDouble(sec.replace(':','.'));
      long minutes=Long.parseLong(min);
      if(!Double.isFinite(seconds)||seconds<0||seconds>=60||minutes>1440)throw new IllegalArgumentException("Invalid LRC timestamp");
      long result=minutes*60000L+Math.round(seconds*1000);
      if(result>86_400_000)throw new IllegalArgumentException("LRC exceeds 24 hours");return result;
   }

   public static Lyrics parse(String lrc) {
      if(lrc==null || lrc.length()>1_048_576)throw new IllegalArgumentException("LRC must be smaller than 1 MiB");
      long offset=0;
      Matcher offsetTag=Pattern.compile("(?im)^\\s*\\[offset:([+-]?\\d+)]").matcher(lrc);
      if(offsetTag.find())offset=Long.parseLong(offsetTag.group(1));
      if(Math.abs(offset)>300000)throw new IllegalArgumentException("LRC offset exceeds five minutes");

      record Raw(long t, long origin, String body) {
      }

      List<Raw> raws = new ArrayList<>();

      for (String row : lrc.split("\\r?\\n")) {
         Matcher m = TIME.matcher(row);
         List<Long> times = new ArrayList<>();

         int end;
         for (end = 0; m.find() && m.start() == end; end = m.end()) {
            times.add(ms(m.group(1), m.group(2)));
         }

         if (!times.isEmpty()) {
            String body = row.substring(end).trim();

            for (long t : times) {
               if(raws.size()>=10000)throw new IllegalArgumentException("Too many lyric lines");
               raws.add(new Raw(t,times.getFirst(),body));
            }
         }
      }

      final long timeOffset=offset;
      raws.sort((a, b) -> Long.compare(a.t, b.t));
      List<Lyrics.Line> lines = new ArrayList<>();

      for (int i = 0; i < raws.size(); i++) {
         Raw r = raws.get(i);
         long next = i + 1 < raws.size() ? raws.get(i + 1).t : r.t + 5000L;
         String plain = WORD_TIME.matcher(r.body).replaceAll("").replaceAll("\\s+", " ").trim();
         if (!plain.isEmpty()) {
            long delta=r.t-r.origin;
            var ws=words(r.body,plain,r.origin,next-delta).stream().map(w->new Word(w.text(),w.start()+delta,w.end()+delta,w.sourceTimed())).toList();
            lines.add(new Lyrics.Line(r.t, Math.max(next,ws.stream().mapToLong(Word::end).max().orElse(next)), plain, ws));
         }
      }

      if(timeOffset!=0)lines=lines.stream().map(l->new Line(Math.max(0,l.start()+timeOffset),Math.max(0,l.end()+timeOffset),l.text(),l.words().stream().map(w->new Word(w.text(),Math.max(0,w.start()+timeOffset),Math.max(0,w.end()+timeOffset),w.sourceTimed())).toList())).toList();
      if(lrc.contains("[nocturne:word-estimates]"))lines=lines.stream().map(l->new Line(l.start(),l.end(),l.text(),l.words().stream().map(w->new Word(w.text(),w.start(),w.end(),false)).toList())).toList();
      return new Lyrics(lines,lrc.contains("[nocturne:estimated]"),lrc.contains("[nocturne:manual]"));
   }

   public static Lyrics fromPlain(String text, long durationMs) {
      List<String> rows = new ArrayList<>();

      for (String row : text.split("\\r?\\n")) {
         String t = row.replaceAll("\\s+", " ").trim();
         if (!t.matches("^[\\[(][^\\])]*[\\])]$")) {
            rows.add(t);
         }
      }

      while (!rows.isEmpty() && rows.get(0).isEmpty()) {
         rows.remove(0);
      }

      while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) {
         rows.remove(rows.size() - 1);
      }

      int sung = 0;

      for (String r : rows) {
         if (!r.isEmpty()) {
            sung++;
         }
      }

      if (sung == 0) {
         return new Lyrics(List.of(), true);
      }

      long dur = durationMs > 30000L ? durationMs : 210000L;
      long start = Math.max(4000L, Math.min(18000L, dur * 7L / 100L));
      long end = dur - Math.max(5000L, Math.min(22000L, dur * 8L / 100L));
      if (end - start < sung * 1200L) {
         start = 500L;
         end = dur;
      }

      double total = 0.0;

      for (String r : rows) {
         total += r.isEmpty() ? 3.0 : r.split(" ").length + 2;
      }

      List<Lyrics.Line> lines = new ArrayList<>();
      double t = start;

      for (String r : rows) {
         double d = (end - start) * (r.isEmpty() ? 3 : r.split(" ").length + 2) / total;
         if (!r.isEmpty()) {
            long s0 = Math.round(t);
            long s1 = Math.round(t + d);
            lines.add(new Lyrics.Line(s0, s1, r, words(r, r, s0, s1)));
         }

         t += d;
      }

      return new Lyrics(lines, true);
   }

   private static List<Lyrics.Word> words(String body, String plain, long start, long end) {
      List<Lyrics.Word> out = new ArrayList<>();
      Matcher m = WORD_TIME.matcher(body);
      if (m.find()) {
         List<long[]> marks = new ArrayList<>();
         List<String> texts = new ArrayList<>();
         m.reset();

         int last;
         for (last = -1; m.find(); last = m.end()) {
            if (last >= 0) {
               texts.add(body.substring(last,m.start()));
            } else if (m.start() > 0 && !body.substring(0, m.start()).isBlank()) {
               texts.add(body.substring(0,m.start()));
               marks.add(new long[]{start});
            }

            long lastT = ms(m.group(1), m.group(2));
            marks.add(new long[]{lastT});
         }

         texts.add(body.substring(Math.max(0,last)));

         for (int i = 0; i < marks.size() && i < texts.size(); i++) {
            String t = texts.get(i);
            if (!t.isEmpty()) {
               long ws = marks.get(i)[0];
               long we = i + 1 < marks.size() ? marks.get(i + 1)[0] : Math.min(end, ws + 800L);

               if(ws<start || we<ws)throw new IllegalArgumentException("Word timestamps must be ordered and inside the line");
               out.add(new Lyrics.Word(t,ws,we,true));
            }
         }

         if (!out.isEmpty()) {
            return out;
         }
      }

      String[] parts = plain.split(" ");
      int chars = 0;

      for (String p : parts) {
         chars += p.length() + 1;
      }

      long sung = Math.min((long)((end - start) * 0.9), chars * 85L + 350L);
      long t = start;

      for (String p : parts) {
         long d = Math.round((double)(sung * (p.length() + 1)) / Math.max(1, chars));
         out.add(new Lyrics.Word(p, t, t + d));
         t += d;
      }

      return out;
   }

   public Lyrics chunked(int maxWords) {
      if (maxWords <= 0) {
         return this;
      }

      List<Lyrics.Line> out = new ArrayList<>();

      for (Lyrics.Line l : this.lines) {
         int n = l.words().size();
         if (n <= maxWords) {
            out.add(l);
         } else {
            int parts = (n + maxWords - 1) / maxWords;
            int from = 0;

            for (int p = 0; p < parts; p++) {
               int size = (n - from) / (parts - p) + ((n - from) % (parts - p) > 0 ? 1 : 0);
               List<Lyrics.Word> ws = l.words().subList(from, from + size);
               long start = p == 0 ? l.start() : ws.get(0).start();
               long end = p == parts - 1 ? l.end() : l.words().get(from + size).start();
               StringBuilder text = new StringBuilder();

               for (Lyrics.Word w : ws) {
                  text.append(text.isEmpty() || l.explicitSpacing() ? "" : " ").append(w.text());
               }

               out.add(new Lyrics.Line(start, end, text.toString(), List.copyOf(ws),l.translation(),l.romanization()));
               from += size;
            }
         }
      }

      return new Lyrics(out, this.estimated,this.manual);
   }

   public Lyrics manuallyCorrected() {return new Lyrics(lines,estimated,true);}
   public String confidence() {return confidenceLabel;}
   public long nextVocal(long position) {
      int low=0,high=vocalRanges.size();
      while(low<high){int mid=(low+high)>>>1;if(vocalRanges.get(mid)[1]<=position)low=mid+1;else high=mid;}
      return low<vocalRanges.size()&&vocalRanges.get(low)[0]>position?vocalRanges.get(low)[0]:-1;
   }
   public Lyrics secondary(Lyrics translation,Lyrics romanization) {
      return new Lyrics(lines.stream().map(l->new Line(l.start(),l.end(),l.text(),l.words(),matchSecondary(translation,l.start(),l.translation()),matchSecondary(romanization,l.start(),l.romanization()))).toList(),estimated,manual);
   }
   private static String matchSecondary(Lyrics source,long start,String fallback) {
      if(source==null)return fallback;
      return source.lines.stream().filter(l->Math.abs(l.start()-start)<=500).min(java.util.Comparator.comparingLong(l->Math.abs(l.start()-start))).map(Line::text).orElse(fallback);
   }
   public Lyrics corrected(List<Line> lines) {return new Lyrics(lines,estimated,true);}
   private static String stamp(long ms,boolean word) {
      return (word?"<":"[")+String.format(java.util.Locale.ROOT,"%02d:%02d.%03d",ms/60000,ms/1000%60,ms%1000)+(word?">":"]");
   }
   public String enhancedLrc() {
      StringBuilder out=new StringBuilder(manual?"[nocturne:manual]\n":"");
      if(estimated)out.append("[nocturne:estimated]\n");
      if(lines.stream().flatMap(l->l.words().stream()).anyMatch(w->!w.sourceTimed()))out.append("[nocturne:word-estimates]\n");
      for(int i=0;i<lines.size();i++) {
         var line=lines.get(i);out.append(stamp(line.start(),false));
         if(!line.words().isEmpty()) {
            for(int j=0;j<line.words().size();j++) {var word=line.words().get(j);out.append(stamp(word.start(),true)).append(word.text());if(!line.explicitSpacing()&&j+1<line.words().size())out.append(' ');}
            out.append(stamp(line.words().getLast().end(),true));
         } else out.append(line.text());
         out.append('\n');
         if(i+1==lines.size()||line.end()<lines.get(i+1).start())out.append(stamp(line.end(),false)).append('\n');
      }
      return out.toString();
   }
   public String secondaryLrc(boolean romanized) {
      var out=new StringBuilder();
      for(var line:lines) {String text=romanized?line.romanization():line.translation();if(!text.isBlank())out.append(stamp(line.start(),false)).append(text).append('\n');}
      return out.toString();
   }

   public int indexAt(long pos) {
      int low=0,high=lines.size();
      while(low<high){int mid=(low+high)>>>1;if(lines.get(mid).start()<=pos)low=mid+1;else high=mid;}
      return low-1;
   }

   public record Line(long start,long end,String text,List<Word> words,String translation,String romanization) {
      public Line(long start,long end,String text,List<Word> words) {this(start,end,text,words,"","");}
      public boolean explicitSpacing() {return words.stream().map(Word::text).collect(java.util.stream.Collectors.joining()).strip().equals(text.strip());}
      public Line {translation=translation==null?"":translation;romanization=romanization==null?"":romanization;words=List.copyOf(words);}
   }

   public record Word(String text,long start,long end,boolean sourceTimed) {
      public Word(String text,long start,long end) {this(text,start,end,false);}
      public float progress(long position) {return Math.clamp((float)(position-start)/Math.max(1,end-start),0,1);}
   }
}
