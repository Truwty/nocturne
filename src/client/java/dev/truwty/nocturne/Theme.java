package dev.truwty.nocturne;

public enum Theme {
    GRAPHITE("Graphite", 0xFF101113, 0xFF191B1F, 0xFF24272C, 0xFF34373D, 0xFFF0F0EB, 0xFF969B9F, 0xFFD7E8BA, 0xFF172114),
    PAPER("Paper", 0xFFE9E6DE, 0xFFF7F4ED, 0xFFE0DDD4, 0xFFC8C5BC, 0xFF22252A, 0xFF63676C, 0xFF345B4B, 0xFFF7F4ED),
    EMBER("Ember", 0xFF171210, 0xFF231B17, 0xFF30251E, 0xFF48352A, 0xFFF9EDE1, 0xFFB7A091, 0xFFF0A66A, 0xFF2B1B11),
    MOSS("Moss", 0xFF101611, 0xFF18221A, 0xFF243027, 0xFF354A3B, 0xFFE4EFDD, 0xFF99AD9A, 0xFFA8CE87, 0xFF172016),
    ROSE("Rose", 0xFF181216, 0xFF241B22, 0xFF33262F, 0xFF49343F, 0xFFF6E8EC, 0xFFB39CA8, 0xFFE9ABBF, 0xFF301B25),
    ALBUM("Album", 0xFF111317, 0xFF1B1E24, 0xFF282D36, 0xFF3D4652, 0xFFF1F2F4, 0xFF9FA8B5, 0xFF9CB5DE, 0xFF151C28);

    public final String label;
    public final int background, surface, elevated, border, text, muted, accent, onAccent;

    Theme(String label, int background, int surface, int elevated, int border, int text, int muted, int accent, int onAccent) {
        this.label = label; this.background = background; this.surface = surface; this.elevated = elevated;
        this.border = border; this.text = text; this.muted = muted; this.accent = accent; this.onAccent = onAccent;
    }

    public int highlight() { return this == ALBUM ? Artwork.accent() : accent; }

    public static Theme current() {
        return fromName(NocturneClient.preferences == null ? "GRAPHITE" : NocturneClient.preferences.theme);
    }

    public static Theme fromName(String name) {
        if (name != null) for (Theme theme : values()) if (theme.name().equals(name)) return theme;
        return GRAPHITE;
    }

    public static int blend(int from, int to, double amount) {
        double t = Math.clamp(amount, 0, 1);
        int result = 0;
        for (int shift : new int[]{0, 8, 16, 24}) {
            int channel = (int) Math.round(((from >>> shift) & 255) * (1 - t) + ((to >>> shift) & 255) * t);
            result |= channel << shift;
        }
        return result;
    }

    public static int alpha(int color, double alpha) { return (color & 0xFFFFFF) | ((int) (Math.clamp(alpha, 0, 1) * 255) << 24); }
}
