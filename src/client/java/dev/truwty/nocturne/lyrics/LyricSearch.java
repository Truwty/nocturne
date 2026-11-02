package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class LyricSearch {
    public record Candidate(String title, String artist, double duration, String lrc) {
        public String label() { return title + " · " + artist + " · " + Math.round(duration) + "s"; }
    }
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private LyricSearch() {}
    public static List<Candidate> search(String query) throws IOException, InterruptedException {
        if (query.isBlank() || query.length() > 200) throw new IllegalArgumentException("Search needs 1–200 characters");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://lrclib.net/api/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(12)).header("User-Agent", "Nocturne/1.0.1 (https://github.com/Truwty/nocturne)").GET().build();
        ProviderBackoff.check(request.uri());
        var response=HTTP.send(request,dev.truwty.nocturne.playback.BoundedBody.handler(2_097_152));
        ProviderBackoff.response(request.uri(),response.statusCode(),response.headers());
        if(response.statusCode()!=200)throw new IOException("Lyric search returned HTTP "+response.statusCode());
        return parse(new String(response.body(),StandardCharsets.UTF_8));
    }
    public static String confidence(Candidate candidate, NowPlaying track) {
        if (track==null) return "No current song";
        boolean title=LyricsFinder.queries(track).stream().anyMatch(q -> LyricsFinder.titleMatches(candidate.title(),q.title()));
        boolean artist=!track.artist().isBlank() && LyricsFinder.artistMatches(candidate.artist(),track.artist());
        boolean version=LyricsFinder.version(candidate.title()).equals(LyricsFinder.version(track.title()));
        boolean duration=track.durationMs()>0 && candidate.duration()>0 && Math.abs(candidate.duration()*1000-track.durationMs())<=5000;
        if(title && artist && version && duration) return "Strong metadata match";
        if(!version) return "Different recording version  -  check timing";
        if(title && artist) return "Title and artist match  -  check duration";
        return "Uncertain match  -  preview before saving";
    }
    static List<Candidate> rank(List<Candidate> candidates, NowPlaying track) {
        return candidates.stream().sorted(java.util.Comparator.comparingInt((Candidate c) -> {
            String confidence=confidence(c,track);
            return confidence.startsWith("Strong")?0:confidence.startsWith("Title")?1:2;
        }).thenComparingDouble(c -> track.durationMs()>0&&c.duration()>0?Math.abs(c.duration()*1000-track.durationMs()):Double.MAX_VALUE)).toList();
    }
    static List<Candidate> parse(String json) {
        List<Candidate> result = new ArrayList<>();
        for (JsonElement value : JsonParser.parseString(json).getAsJsonArray()) {
            if (!value.isJsonObject()) continue;
            var item = value.getAsJsonObject();
            try {
                if (!item.has("trackName") || !item.has("artistName")) continue;
                String title=item.get("trackName").getAsString(), artist=item.get("artistName").getAsString();
                double duration=LyricsFinder.duration(item);
                if(title.isBlank() || !Double.isFinite(duration)) continue;
                Lyrics parsed=LyricsFormats.fromRecord(item);
                if(parsed!=null)result.add(new Candidate(title,artist,duration,parsed.enhancedLrc()));
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException ex) {
                dev.truwty.nocturne.NocturneClient.LOG.debug("Invalid manual lyric result skipped",ex);
            }
            if (result.size() == 30) break;
        }
        return List.copyOf(result);
    }
}
