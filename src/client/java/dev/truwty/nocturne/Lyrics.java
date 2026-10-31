package dev.truwty.nocturne;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public record Lyrics(List<Line> lines, String plain, boolean instrumental, String credit) {
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):([0-5]?\\d)(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD = Pattern.compile("<(\\d{1,3}):([0-5]?\\d)(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d+)]\\s*$");
    public record Word(double seconds, String text) {}
    public record Line(double seconds, String text, List<Word> words) {}

    public Lyrics { lines = List.copyOf(lines); }
    public boolean synced() { return !lines.isEmpty(); }

    public static Lyrics parse(String lrc, String plain, boolean instrumental, String credit) {
        if (lrc.length() > 1_000_000 || plain.length() > 1_000_000) throw new IllegalArgumentException("Lyrics exceed size limit");
        double offset = 0;
        var offsets = OFFSET.matcher(lrc);
        while (offsets.find()) {
            try { offset = Math.clamp(Double.parseDouble(offsets.group(1)), -600_000, 600_000) / 1000; }
            catch (NumberFormatException ex) { offset = 0; }
        }
        List<Line> lines = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            var stamps = STAMP.matcher(raw);
            List<Double> times = new ArrayList<>();
            int end = 0;
            while (stamps.find() && raw.substring(end, stamps.start()).isBlank()) {
                times.add(seconds(stamps.group(1), stamps.group(2), stamps.group(3)) - offset);
                end = stamps.end();
            }
            if (times.isEmpty()) continue;
            String content = raw.substring(end).strip();
            List<Word> words = new ArrayList<>();
            var matcher = WORD.matcher(content);
            List<Double> wordTimes = new ArrayList<>();
            List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
            while (matcher.find()) {
                wordTimes.add(seconds(matcher.group(1), matcher.group(2), matcher.group(3)) - offset);
                starts.add(matcher.start()); ends.add(matcher.end());
            }
            for (int i = 0; i < starts.size(); i++) {
                String text = content.substring(ends.get(i), i + 1 < starts.size() ? starts.get(i + 1) : content.length());
                words.add(new Word(Math.max(0, wordTimes.get(i)), text));
            }
            String text = WORD.matcher(content).replaceAll("").strip();
            for (double time : times) lines.add(new Line(Math.max(0, time), text, times.size() == 1 ? List.copyOf(words) : List.of()));
        }
        lines.sort(Comparator.comparingDouble(Line::seconds));
        return new Lyrics(lines, plain, instrumental, credit);
    }

    private static double seconds(String minutes, String seconds, String fraction) {
        return Integer.parseInt(minutes) * 60 + Integer.parseInt(seconds)
                + (fraction == null ? 0 : Integer.parseInt(fraction) / Math.pow(10, fraction.length()));
    }

    public int activeIndex(double position) {
        int low = 0, high = lines.size() - 1, result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).seconds() <= position) { result = mid; low = mid + 1; }
            else high = mid - 1;
        }
        return result;
    }

    public double wordProgress(int index, double position) {
        if (index < 0 || index >= lines.size()) return 0;
        Line line = lines.get(index);
        if (line.words().isEmpty()) return 0;
        int total = line.words().stream().mapToInt(word -> word.text().length()).sum();
        if (total == 0) return 0;
        double sung = 0;
        for (int i = 0; i < line.words().size(); i++) {
            Word word = line.words().get(i);
            double end = i + 1 < line.words().size() ? line.words().get(i + 1).seconds()
                    : index + 1 < lines.size() ? lines.get(index + 1).seconds() : word.seconds() + 1;
            double fraction = Math.clamp((position - word.seconds()) / Math.max(0.01, end - word.seconds()), 0, 1);
            sung += word.text().length() * fraction;
        }
        return sung / total;
    }
}
