package dev.truwty.nocturne.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Lyrics {
   public final List<Lyrics.Line> lines;
   public final long beatMs;
   public final long beatOrigin;
   public final boolean estimated;
   private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d+(?:[.:]\\d+)?)]");
   private static final Pattern WORD_TIME = Pattern.compile("<(\\d+):(\\d+(?:[.:]\\d+)?)>");

   private Lyrics(List<Lyrics.Line> lines) {
      this(lines, false);
   }

   private Lyrics(List<Lyrics.Line> lines, boolean estimated) {
      this.lines = lines;
      this.estimated = estimated;
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
      return Long.parseLong(min) * 60000L + Math.round(Double.parseDouble(sec.replace(':', '.')) * 1000.0);
   }

   public static Lyrics parse(String lrc) {
      @Environment(EnvType.CLIENT)
      record Raw(long t, String body) {
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
               raws.add(new Raw(t, body));
            }
         }
      }

      raws.sort((a, b) -> Long.compare(a.t, b.t));
      List<Lyrics.Line> lines = new ArrayList<>();

      for (int i = 0; i < raws.size(); i++) {
         Raw r = raws.get(i);
         long next = i + 1 < raws.size() ? raws.get(i + 1).t : r.t + 5000L;
         String plain = WORD_TIME.matcher(r.body).replaceAll("").replaceAll("\\s+", " ").trim();
         if (!plain.isEmpty()) {
            lines.add(new Lyrics.Line(r.t, next, plain, words(r.body, plain, r.t, next)));
         }
      }

      return new Lyrics(lines);
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
               texts.add(body.substring(last, m.start()).trim());
            } else if (m.start() > 0 && !body.substring(0, m.start()).isBlank()) {
               texts.add(body.substring(0, m.start()).trim());
               marks.add(new long[]{start});
            }

            long lastT = ms(m.group(1), m.group(2));
            marks.add(new long[]{lastT});
         }

         texts.add(body.substring(Math.max(0, last)).trim());

         for (int i = 0; i < marks.size() && i < texts.size(); i++) {
            String t = texts.get(i);
            if (!t.isEmpty()) {
               long ws = marks.get(i)[0];
               long we = i + 1 < marks.size() ? marks.get(i + 1)[0] : Math.min(end, ws + 800L);

               for (String piece : t.split(" ")) {
                  if (!piece.isEmpty()) {
                     out.add(new Lyrics.Word(piece, ws, we));
                  }
               }
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
                  text.append(text.isEmpty() ? "" : " ").append(w.text());
               }

               out.add(new Lyrics.Line(start, end, text.toString(), List.copyOf(ws)));
               from += size;
            }
         }
      }

      return new Lyrics(out, this.estimated);
   }

   public int indexAt(long pos) {
      int idx = -1;
      int i = 0;

      while (i < this.lines.size() && this.lines.get(i).start <= pos) {
         idx = i++;
      }

      return idx;
   }

   @Environment(EnvType.CLIENT)
   public record Line(long start, long end, String text, List<Lyrics.Word> words) {
   }

   @Environment(EnvType.CLIENT)
   public record Word(String text, long start, long end) {
   }
}
