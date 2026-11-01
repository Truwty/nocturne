package dev.truwty.nocturne.gui;

import com.google.gson.*;

import java.util.*;

public final class HudLayout {
    public static final List<String> WIDGETS =
            List.of("Karaoke", "Secondary", "Countdown", "Confidence", "Diagnostics");
    public static final int MARGIN = 8;

    public record Box(double x, double y, double width, double height) {
        public double centerX() {
            return x + width / 2;
        }

        public double centerY() {
            return y + height / 2;
        }

        public boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    public record Node(double x, double y, double scale, boolean visible) {
        public Node {
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(scale)
                    || x < 0
                    || x > 1
                    || y < 0
                    || y > 1
                    || scale < .5
                    || scale > 2) throw new IllegalArgumentException("Invalid HUD position/scale");
        }

        public Box box(int sw, int sh, double logicalWidth, double logicalHeight) {
            double fit =
                    Math.min(
                            scale,
                            Math.min(
                                    Math.max(1, sw - 2 * MARGIN) / Math.max(1, logicalWidth),
                                    Math.max(1, sh - 2 * MARGIN) / Math.max(1, logicalHeight)));
            double ww = Math.max(1, logicalWidth * fit), hh = Math.max(1, logicalHeight * fit);
            return new Box(
                    Math.clamp(x * sw - ww / 2, MARGIN, Math.max(MARGIN, sw - MARGIN - ww)),
                    Math.clamp(y * sh - hh / 2, MARGIN, Math.max(MARGIN, sh - MARGIN - hh)),
                    ww,
                    hh);
        }
    }

    public record Snap(Node node, Double guideX, Double guideY) {}

    private final Map<String, Node> nodes;

    public HudLayout(Map<String, Node> nodes) {
        this.nodes = Map.copyOf(nodes);
        if (!this.nodes.keySet().equals(Set.copyOf(WIDGETS)))
            throw new IllegalArgumentException("HUD layout needs all widgets");
    }

    public Node node(String id) {
        return Objects.requireNonNull(nodes.get(id), "Unknown HUD widget");
    }

    public HudLayout with(String id, Node node) {
        var copy = new HashMap<>(nodes);
        copy.put(id, node);
        return new HudLayout(copy);
    }

    public static HudLayout defaults(boolean menu) {
        Map<String, Node> n = new LinkedHashMap<>();
        n.put("Karaoke", new Node(.5, .055, 1, !menu));
        n.put("Secondary", new Node(.5, .78, 1, !menu));
        n.put("Countdown", new Node(.5, .72, 1, !menu));
        n.put("Confidence", new Node(.5, .22, 1, !menu));
        n.put("Diagnostics", new Node(.27, .3, 1, !menu));
        return new HudLayout(n);
    }

    public JsonObject json() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        JsonObject values = new JsonObject();
        nodes.forEach((id, n) -> {
            JsonObject row = new JsonObject();
            row.addProperty("x", n.x);
            row.addProperty("y", n.y);
            row.addProperty("scale", n.scale);
            row.addProperty("visible", n.visible);
            values.add(id, row);
        });
        root.add("widgets", values);
        return root;
    }

    public static HudLayout parse(JsonObject json) {
        if (json == null
                || !json.has("schema")
                || json.get("schema").getAsInt() != 1
                || !json.has("widgets")
                || !json.get("widgets").isJsonObject())
            throw new IllegalArgumentException("Unsupported HUD layout");
        var rows = json.getAsJsonObject("widgets");
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (String id : WIDGETS) {
            if (!rows.has(id) || !rows.get(id).isJsonObject())
                throw new IllegalArgumentException("Missing HUD widget: " + id);
            var row = rows.getAsJsonObject(id);
            for (String name : List.of("x", "y", "scale"))
                if (!row.has(name)
                        || !row.get(name).isJsonPrimitive()
                        || !row.getAsJsonPrimitive(name).isNumber())
                    throw new IllegalArgumentException("Invalid HUD number: " + name);
            if (!row.has("visible")
                    || !row.get("visible").isJsonPrimitive()
                    || !row.getAsJsonPrimitive("visible").isBoolean())
                throw new IllegalArgumentException("Invalid HUD visibility");
            nodes.put(
                    id,
                    new Node(
                            row.get("x").getAsDouble(),
                            row.get("y").getAsDouble(),
                            row.get("scale").getAsDouble(),
                            row.get("visible").getAsBoolean()));
        }
        return new HudLayout(nodes);
    }

    public Snap move(
            String id,
            double centerX,
            double centerY,
            int sw,
            int sh,
            double ww,
            double hh,
            boolean snap,
            Map<String, Box> others) {
        Node old = node(id);
        Box initial =
                new Node(
                                Math.clamp(centerX / sw, 0, 1),
                                Math.clamp(centerY / sh, 0, 1),
                                old.scale,
                                old.visible)
                        .box(sw, sh, ww, hh);
        double cx = initial.centerX(), cy = initial.centerY();
        Double gx = null, gy = null;
        if (snap) {
            List<Double> xs =
                    new ArrayList<>(List.of((double) MARGIN, sw / 2.0, (double) sw - MARGIN));
            List<Double> ys =
                    new ArrayList<>(List.of((double) MARGIN, sh / 2.0, (double) sh - MARGIN));
            for (var entry : others.entrySet())
                if (!entry.getKey().equals(id)) {
                    Box b = entry.getValue();
                    xs.addAll(List.of(b.x, b.centerX(), b.x + b.width));
                    ys.addAll(List.of(b.y, b.centerY(), b.y + b.height));
                }
            double bestX = 7, bestY = 7;
            for (double target : xs)
                for (double edge :
                        new double[] {cx - initial.width / 2, cx, cx + initial.width / 2})
                    if (Math.abs(target - edge) < bestX) {
                        bestX = Math.abs(target - edge);
                        gx = target;
                        centerX = cx + target - edge;
                    }
            for (double target : ys)
                for (double edge :
                        new double[] {cy - initial.height / 2, cy, cy + initial.height / 2})
                    if (Math.abs(target - edge) < bestY) {
                        bestY = Math.abs(target - edge);
                        gy = target;
                        centerY = cy + target - edge;
                    }
            if (gx != null) cx = centerX;
            if (gy != null) cy = centerY;
        }
        Box clamped =
                new Node(
                                Math.clamp(cx / sw, 0, 1),
                                Math.clamp(cy / sh, 0, 1),
                                old.scale,
                                old.visible)
                        .box(sw, sh, ww, hh);
        return new Snap(
                new Node(clamped.centerX() / sw, clamped.centerY() / sh, old.scale, old.visible),
                gx,
                gy);
    }
}
