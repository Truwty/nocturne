package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.truwty.nocturne.NocturneClient;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class LyricsFinder {
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6L)).followRedirects(Redirect.NORMAL).build();
   private static final String UA = "Nocturne Lyrics (Minecraft client mod)";
   private static final Pattern BRACKETS = Pattern.compile("\\s*[(\\[{【（][^)\\]}】）]*[)\\]}】）]");
   private static final Pattern FEAT = Pattern.compile("(?i)\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$");
   private static final Pattern DASH_TAIL = Pattern.compile(
      "(?i)\\s+[-–—]\\s+.*\\b(remaster(ed)?|version|edit|mix|mono|stereo|live|deluxe|bonus|single|demo|acoustic|instrumental|sped up|slowed|from|soundtrack|anniversary)\\b.*$"
   );
   private static final Pattern NOISE = Pattern.compile(
      "(?i)\\b(official\\s+(music\\s+)?(video|audio|lyric video|visualizer)|lyrics?\\s+video|lyrics?|audio|hd|hq|4k|mv|m/v|explicit|clean)\\b"
   );
   static final Pattern CREDIT = Pattern.compile(
      "^\\s*(作词|作曲|编曲|制作人|制作|混音|母带|和声|吉他|贝斯|鼓|录音|监制|出品|发行|统筹|词|曲|lyrics by|composed by|produced by|written by|arranged by|mixed by|mastered by|composer|lyricist|producer)\\s*[:：].*",
      2
   );
   boolean reached;
   private long deadline;
   private final java.util.function.Function<String, JsonElement> transport;
   LyricsFinder() { this(null); }
   LyricsFinder(java.util.function.Function<String, JsonElement> transport) { this.transport = transport; }

   LyricsFinder.Result find(NowPlaying np, boolean allowPlain) {
      this.reached = false;
      this.deadline = System.nanoTime() + Duration.ofSeconds(18).toNanos();
      long durMs = np.durationMs();
      List<LyricsFinder.Query> queries = queries(np);
      String plain = null;
      boolean instrumental = false;
      LyricsFinder.Query q0 = queries.get(0);
      JsonElement got = this.get(
         "https://lrclib.net/api/get?track_name="
            + enc(q0.title())
            + "&artist_name="
            + enc(q0.artist())
            + (np.album().isBlank() ? "" : "&album_name=" + enc(np.album()))
            + (durMs > 0L ? "&duration=" + Math.round(durMs / 1000.0) : ""),
         false
      );
      if (got != null && got.isJsonObject()) {
         JsonObject o = got.getAsJsonObject();
         Lyrics l = version(str(o,"trackName",q0.title())).equals(version(q0.title()))?synced(o):null;
         if (l != null) {
            return new LyricsFinder.Result(l, "Synced lyrics");
         }

         if (bool(o, "instrumental")) {
            instrumental = true;
         }

         if (plain == null) {
            plain = str(o, "plainLyrics");
         }
      }

      for (LyricsFinder.Query q : queries) {
         JsonElement s = this.get(
            "https://lrclib.net/api/search?track_name=" + enc(q.title()) + (q.artist().isBlank() ? "" : "&artist_name=" + enc(q.artist())), false
         );
         LyricsFinder.Pick p = pickLrclib(s, q, durMs);
         if (p.synced != null) {
            return new LyricsFinder.Result(p.synced, "Synced lyrics");
         }

         if (plain == null) {
            plain = p.plain;
         }

         instrumental |= p.instrumental;
      }

      LyricsFinder.Query qc = queries.get(queries.size() > 1 ? 1 : 0);
      JsonElement free = this.get("https://lrclib.net/api/search?q=" + enc((qc.artist() + " " + qc.title()).trim()), false);
      LyricsFinder.Pick pf = pickLrclib(free, qc, durMs);
      if (pf.synced != null) {
         return new LyricsFinder.Result(pf.synced, "Synced lyrics");
      }

      if (plain == null) {
         plain = pf.plain;
      }

      if (!instrumental && allowPlain && plain != null && !plain.isBlank()) {
         Lyrics estimated = Lyrics.fromPlain(plain, durMs);
         if (!estimated.lines.isEmpty()) return new Result(estimated, "Lyrics (timing estimated)");
      }
      if (!instrumental) {
         for (LyricsFinder.Query q : queries) {
            if(q.artist().isBlank())continue;
            Lyrics l = this.netease(q, durMs);
            if (l != null) {
               return new LyricsFinder.Result(l, "Synced lyrics (NetEase)");
            }
         }
      }

      if (instrumental) {
         return new LyricsFinder.Result(null, "Instrumental");
      }

      if (!instrumental && allowPlain && plain != null && !plain.isBlank()) {
         Lyrics l = Lyrics.fromPlain(plain, durMs);
         if (!l.lines.isEmpty()) {
            return new LyricsFinder.Result(l, "Lyrics (timing estimated)");
         }
      }

      return null;
   }

   static List<LyricsFinder.Query> queries(NowPlaying np) {
      String title = np.title().trim().replaceFirst("(?i)\\s+[-|]\\s+(youtube( music)?|spotify|apple music|tidal)(\\s.*)?$", "");
      String artist = mainArtist(np.artist());
      String first = np.artist() == null ? "" : np.artist().split(",")[0].trim();
      Set<LyricsFinder.Query> out = new LinkedHashSet<>();
      if(first.isBlank()) {
         String[] browser=title.split("\\s+[-–—|]\\s+",2);
         if(browser.length==2)out.add(new Query(browser[1].trim(),mainArtist(browser[0])));
      }
      out.add(new LyricsFinder.Query(title, first));
      out.add(new LyricsFinder.Query(cleanTitle(title) + (version(title).equals("studio")?"":" ("+version(title)+")"), artist));
      if (!first.equals(artist)) {
         out.add(new LyricsFinder.Query(cleanTitle(title), first));
      }

      String[] parts = title.split("\\s+[-–—|]\\s+", 2);
      if (parts.length == 2 && !np.source().toLowerCase(Locale.ROOT).contains("spotify")) {
         out.add(new LyricsFinder.Query(cleanTitle(parts[1]), mainArtist(cleanTitle(parts[0]))));
         out.add(new LyricsFinder.Query(cleanTitle(parts[0]), mainArtist(cleanTitle(parts[1]))));
      }

      String wantedVersion=version(title);
      out.removeIf(q -> q.title().isBlank() || !version(q.title()).equals(wantedVersion));
      if (out.isEmpty()) {
         out.add(new LyricsFinder.Query(title, artist));
      }

      return new ArrayList<>(out);
   }

   private static String version(String title) {
      var match=Pattern.compile("(?i)\\b(live|acoustic|instrumental|remix|demo|sped up|slowed)\\b").matcher(title);
      return match.find()?match.group(1).toLowerCase(Locale.ROOT):"studio";
   }

   private static String cleanTitle(String t) {
      String s = t.replaceFirst("(?i)\\s+[-|]\\s+(youtube( music)?|spotify|apple music|tidal)(\\s.*)?$", "");
      s = FEAT.matcher(s).replaceAll("");
      s = DASH_TAIL.matcher(s).replaceAll("");
      s = BRACKETS.matcher(s).replaceAll("");
      s = NOISE.matcher(s).replaceAll("");
      s = s.replaceAll("\\s{2,}", " ").replaceAll("[\\s\\-–—|]+$", "").trim();
      return s.isEmpty() ? t.trim() : s;
   }

   private static String mainArtist(String a) {
      String s = a == null ? "" : a;
      s = s.split("(?i)\\s*(,|;|&|/|\\s+x\\s+|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+|\\s+and\\s+)\\s*", 2)[0];
      s = s.replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("(?i)vevo$", "").replaceAll("(?i)\\s+official$", "");
      return s.trim();
   }

   private static String norm(String s) {
      String n = Normalizer.normalize(cleanTitle(s), Form.NFKD).replaceAll("\\p{M}", "");
      return n.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
   }

   private static boolean titleMatches(String found, String wanted) {
      String a = norm(found);
      String b = norm(wanted);
      if (a.isEmpty() || b.isEmpty()) {
         return false;
      }

      if (a.equals(b)) {
         return true;
      }

      String shorter = a.length() < b.length() ? a : b;
      String longer = shorter == a ? b : a;
      return shorter.length() >= 4 && longer.contains(shorter) && shorter.length() >= longer.length() * 0.55;
   }

   private static boolean artistMatches(String found,String wanted) {
      String artist=norm(mainArtist(wanted)).replace(" ","");
      if(artist.isEmpty())return true;
      return norm(mainArtist(found)).replace(" ","").equals(artist);
   }

   private static LyricsFinder.Pick pickLrclib(JsonElement res, LyricsFinder.Query q, long durMs) {
      LyricsFinder.Pick p = new LyricsFinder.Pick();
      if (res != null && res.isJsonArray()) {
         JsonArray arr = res.getAsJsonArray();
         double bestSynced = Double.MAX_VALUE;
         double bestPlain = Double.MAX_VALUE;
         Set<String> artists=new java.util.HashSet<>();

         for (JsonElement e : arr) {
            if (e.isJsonObject()) {
               JsonObject o = e.getAsJsonObject();
               if (version(str(o,"trackName","")).equals(version(q.title())) && titleMatches(str(o, "trackName", ""), q.title()) && artistMatches(str(o, "artistName", ""), q.artist())) {
                  double diff = durMs > 0L && o.has("duration") && !o.get("duration").isJsonNull()
                     ? Math.abs(o.get("duration").getAsDouble() - durMs / 1000.0)
                     : 0.0;
                  if (!(diff > 5.0)) {
                     artists.add(norm(str(o,"artistName","")));
                     if (bool(o, "instrumental") && diff < 2.0) {
                        p.instrumental = true;
                     }

                     Lyrics l = synced(o);
                     if (l != null && diff < bestSynced) {
                        if (!l.lines.isEmpty()) {
                           p.synced = l;
                           bestSynced = diff;
                        }
                     }

                     String pl = str(o, "plainLyrics");
                     if (pl != null && !pl.isBlank() && diff < bestPlain) {
                        p.plain = pl;
                        bestPlain = diff;
                     }
                  }
               }
            }
         }

         if(q.artist().isBlank() && artists.size()>1) { p.synced=null;p.plain=null;p.instrumental=false; }
         return p;
      } else {
         return p;
      }
   }

   private static Lyrics synced(JsonObject o) {return LyricsFormats.fromRecord(o);}

   private Lyrics netease(LyricsFinder.Query q, long durMs) {
      JsonElement res = this.get("https://music.163.com/api/search/get/web?type=1&offset=0&limit=10&s=" + enc((q.title() + " " + q.artist()).trim()), true);
      if (res != null && res.isJsonObject()) {
         JsonObject root = res.getAsJsonObject();
         if (root.has("result") && root.get("result").isJsonObject()) {
            JsonObject result = root.getAsJsonObject("result");
            if (result.has("songs") && result.get("songs").isJsonArray()) {
               for (JsonElement e : result.getAsJsonArray("songs")) {
                  if (e.isJsonObject()) {
                     JsonObject song = e.getAsJsonObject();
                     if (version(str(song,"name","")).equals(version(q.title())) && titleMatches(str(song, "name", ""), q.title())) {
                        StringBuilder artists = new StringBuilder();
                        if (song.has("artists") && song.get("artists").isJsonArray()) {
                           for (JsonElement a : song.getAsJsonArray("artists")) {
                              if (a.isJsonObject()) {
                                 artists.append(str(a.getAsJsonObject(), "name", "")).append(' ');
                              }
                           }
                        }

                        if (artistMatches(artists.toString(), q.artist())
                           && (durMs <= 0L || !song.has("duration") || Math.abs(song.get("duration").getAsLong() - durMs) <= 4000L)
                           && song.has("id")) {
                           JsonElement lr = this.get("https://music.163.com/api/song/lyric?id=" + song.get("id").getAsLong() + "&lv=-1&kv=-1&tv=-1&rv=-1&yv=-1", true);
                           if (lr != null && lr.isJsonObject()) {
                              JsonObject lo = lr.getAsJsonObject();
                              Lyrics rich=LyricsFormats.netease(lo);
                              if(!rich.lines.isEmpty())return rich;
                              if (lo.has("lrc") && lo.get("lrc").isJsonObject()) {
                                 String lrc = str(lo.getAsJsonObject("lrc"), "lyric");
                                 if (lrc != null && !lrc.isBlank()) {
                                    StringBuilder clean = new StringBuilder();

                                    for (String row : lrc.split("\\r?\\n")) {
                                       String body = row.replaceAll("^(\\[[^\\]]*])+", "");
                                       if (!CREDIT.matcher(body).matches() && !body.contains("纯音乐")) {
                                          clean.append(row).append('\n');
                                       }
                                    }

                                    Lyrics l = Lyrics.parse(clean.toString());
                                    if (l.lines.size() >= 3) {
                                       return l;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               return null;
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private JsonElement get(String url, boolean browserish) {
      if (transport != null) { reached = true; return transport.apply(url); }
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0 || Thread.currentThread().isInterrupted()) return null;
      try {
         Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(4).toNanos()))).GET();
         if (browserish) {
            b.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Nocturne Lyrics (Minecraft client mod)")
               .header("Referer", "https://music.163.com/");
         } else {
            b.header("User-Agent", "Nocturne Lyrics (Minecraft client mod)");
         }

         HttpResponse<String> res = HTTP.send(b.build(), BodyHandlers.ofString());
         this.reached |= res.statusCode() == 200 || res.statusCode() == 404;
         return res.statusCode() != 200 ? null : JsonParser.parseString(res.body());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      } catch (Exception e) {
         NocturneClient.LOG.debug("Lyrics request failed: {}", url, e);
         return null;
      }
   }

   private static String str(JsonObject o, String key) {
      return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
   }

   private static String str(JsonObject o, String key, String def) {
      String s = str(o, key);
      return s == null ? def : s;
   }

   private static boolean bool(JsonObject o, String key) {
      return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }

   @Environment(EnvType.CLIENT)
   private static final class Pick {
      Lyrics synced;
      String plain;
      boolean instrumental;
   }

   @Environment(EnvType.CLIENT)
   record Query(String title, String artist) {
   }

   @Environment(EnvType.CLIENT)
   record Result(Lyrics lyrics, String status) {
   }
}
