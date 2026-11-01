package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;

import dev.truwty.nocturne.compat.ClientAccess;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudLayouts {
    private static final Map<String, HudLayout> CACHE = new HashMap<>();

    private HudLayouts() {}

    public static String context() {
        return ClientAccess.screen(Minecraft.getInstance()) == null ? "Gameplay" : "Menu";
    }

    public static HudLayout current() {
        return load(context());
    }

    public static HudLayout load(String context) {
        return CACHE.computeIfAbsent(
                context,
                key -> {
                    try {
                        var json = Studio.library().preset("hudLayouts", key);
                        return json == null
                                ? HudLayout.defaults(key.equals("Menu"))
                                : HudLayout.parse(json);
                    } catch (RuntimeException ex) {
                        NocturneClient.LOG.warn("Invalid {} HUD layout; using defaults", key, ex);
                        return HudLayout.defaults(key.equals("Menu"));
                    }
                });
    }

    public static void save(String context, HudLayout layout) throws IOException {
        if (!context.equals("Gameplay") && !context.equals("Menu"))
            throw new IllegalArgumentException("Unknown layout context");
        Studio.library().preset("hudLayouts", context, layout.json());
        CACHE.put(context, layout);
    }

    public static Scope begin(
            GuiGraphicsExtractor g, String id, int sw, int sh, double width, double height) {
        var node = current().node(id);
        var box = node.box(sw, sh, width, height);
        float scale =
                (float)
                        Math.min(
                                node.scale(),
                                Math.min(
                                        box.width() / Math.max(1, width),
                                        box.height() / Math.max(1, height)));
        g.pose().pushMatrix();
        g.pose().translate((float) box.x(), (float) box.y());
        g.pose().scale(scale, scale);
        return new Scope(g);
    }

    public record Scope(GuiGraphicsExtractor graphics) implements AutoCloseable {
        public void close() {
            graphics.pose().popMatrix();
        }
    }
}
