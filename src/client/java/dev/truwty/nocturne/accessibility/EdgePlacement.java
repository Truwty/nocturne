package dev.truwty.nocturne.accessibility;

public final class EdgePlacement {
    private EdgePlacement() {}

    public record Position(int x, int y, String direction) {}

    public static boolean outside(double x, double y, boolean behind, boolean previouslyOutside) {
        double edge = previouslyOutside ? .85 : 1;
        return behind
                || !Double.isFinite(x)
                || !Double.isFinite(y)
                || Math.abs(x) > edge
                || Math.abs(y) > edge;
    }

    public static Position locate(
            double nx,
            double ny,
            boolean behind,
            int width,
            int height,
            int boxWidth,
            int boxHeight) {
        int margin = 8;
        int maxX = Math.max(margin, width - margin - boxWidth),
                maxY = Math.max(margin, height - margin - boxHeight);
        if (behind || !Double.isFinite(nx) || !Double.isFinite(ny))
            return new Position(
                    Math.clamp((width - boxWidth) / 2, margin, maxX), maxY, "Behind you");
        double edge = Math.max(1, Math.max(Math.abs(nx), Math.abs(ny)));
        double x = nx / edge, y = ny / edge;
        String direction = Math.abs(nx) > Math.abs(ny) ? nx > 0 ? ">" : "<" : ny > 0 ? "^" : "v";
        return new Position(
                Math.clamp((int) Math.round((x + 1) * width / 2 - boxWidth / 2.0), margin, maxX),
                Math.clamp((int) Math.round((1 - y) * height / 2 - boxHeight / 2.0), margin, maxY),
                direction);
    }
}
