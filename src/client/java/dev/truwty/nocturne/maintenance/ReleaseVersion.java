package dev.truwty.nocturne.maintenance;

import java.util.*;
import java.util.regex.Pattern;

public record ReleaseVersion(int major, int minor, int patch, List<String> pre)
        implements Comparable<ReleaseVersion> {
    private static final Pattern FORM =
            Pattern.compile(
                    "v?(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");

    public ReleaseVersion {
        pre = List.copyOf(pre);
    }

    public static ReleaseVersion parse(String text) {
        if (text == null || text.length() > 80)
            throw new IllegalArgumentException("Invalid release version");
        var match = FORM.matcher(text);
        if (!match.matches()) throw new IllegalArgumentException("Invalid release version");
        var pre = match.group(4) == null ? List.<String>of() : List.of(match.group(4).split("\\."));
        if (pre.stream().anyMatch(p -> p.matches("0[0-9]+")))
            throw new IllegalArgumentException("Invalid numeric prerelease");
        return new ReleaseVersion(
                Integer.parseInt(match.group(1)),
                Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)),
                pre);
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        int order = Integer.compare(major, other.major);
        if (order != 0) return order;
        order = Integer.compare(minor, other.minor);
        if (order != 0) return order;
        order = Integer.compare(patch, other.patch);
        if (order != 0) return order;
        if (pre.isEmpty() || other.pre.isEmpty())
            return pre.isEmpty() == other.pre.isEmpty() ? 0 : pre.isEmpty() ? 1 : -1;
        for (int i = 0; i < Math.min(pre.size(), other.pre.size()); i++) {
            String a = pre.get(i), b = other.pre.get(i);
            boolean an = a.matches("[0-9]+"), bn = b.matches("[0-9]+");
            order =
                    an && bn
                            ? (a.length() == b.length()
                                    ? a.compareTo(b)
                                    : Integer.compare(a.length(), b.length()))
                            : an != bn ? an ? -1 : 1 : a.compareTo(b);
            if (order != 0) return order;
        }
        return Integer.compare(pre.size(), other.pre.size());
    }
}
