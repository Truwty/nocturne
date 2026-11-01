package dev.truwty.nocturne.playback;

import dev.truwty.nocturne.lyrics.SystemMediaSource.Player;

import java.util.*;

public final class PlayerSelection {
    public static final List<String> DEFAULTS =
            List.of("Spotify", "Cider", "YouTube Music", "TIDAL", "foobar");

    private PlayerSelection() {}

    public static List<String> validate(List<String> priorities) {
        if (priorities.size() > 32)
            throw new IllegalArgumentException("Use at most 32 priority entries");
        var result = new ArrayList<String>();
        for (String entry : priorities) {
            String value = entry.strip();
            if (value.isEmpty()
                    || value.length() > 160
                    || value.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid player priority");
            if (result.stream().noneMatch(v -> v.equalsIgnoreCase(value))) result.add(value);
        }
        return List.copyOf(result);
    }

    public static int rank(String id, List<String> priorities) {
        for (int i = 0; i < priorities.size(); i++)
            if (id.toLowerCase(Locale.ROOT).contains(priorities.get(i).toLowerCase(Locale.ROOT)))
                return i;
        return priorities.size();
    }

    public static List<Player> ordered(
            List<Player> players, List<String> priorities, String active) {
        return players.stream()
                .sorted(
                        Comparator.comparingInt((Player p) -> p.playing() ? 0 : 1)
                                .thenComparingInt(p -> rank(p.id(), priorities))
                                .thenComparingInt(p -> p.id().equals(active) ? 0 : 1)
                                .thenComparing(Player::id, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static Player choose(
            List<Player> players, List<String> priorities, String pin, String active) {
        if (!pin.isBlank())
            return players.stream().filter(p -> p.id().equals(pin)).findFirst().orElse(null);
        return ordered(players, priorities, active).stream().findFirst().orElse(null);
    }
}
