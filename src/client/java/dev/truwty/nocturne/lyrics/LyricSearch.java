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
            .timeout(Duration.ofSeconds(12)).header("User-Agent", "Nocturne/0.5 (Minecraft lyric correction)").GET().build();
        HttpResponse<java.io.InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var input = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Lyric search returned HTTP " + response.statusCode());
            byte[] bytes = input.readNBytes(2_097_153);
            if (bytes.length > 2_097_152) throw new IOException("Lyric search response is too large");
            return parse(new String(bytes, StandardCharsets.UTF_8));
        }
    }
    static List<Candidate> parse(String json) {
        List<Candidate> result = new ArrayList<>();
        for (JsonElement value : JsonParser.parseString(json).getAsJsonArray()) {
            var item = value.getAsJsonObject();
            if (item.has("syncedLyrics") && !item.get("syncedLyrics").isJsonNull()) {
                String lrc = item.get("syncedLyrics").getAsString();
                if (!Lyrics.parse(lrc).lines.isEmpty()) result.add(new Candidate(item.get("trackName").getAsString(),
                    item.get("artistName").getAsString(), item.get("duration").getAsDouble(), lrc));
            }
            if (result.size() == 30) break;
        }
        return List.copyOf(result);
    }
}
