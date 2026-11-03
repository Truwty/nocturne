package dev.truwty.nocturne.experience;

import dev.truwty.nocturne.gui.StudioPanel;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.*;

import java.util.*;

public final class LocalPaletteScreen extends StudioPanel {
    public record Ghost(Vec3 at, int color) {}

    private static final List<Ghost> ghosts = new ArrayList<>();
    private static int selected;
    private static final String[] NAMES = {
        "Mint glass", "Rose glass", "Cyber violet", "Amber", "Ice"
    };
    private static final int[] COLORS = {
        0x77A8F1D3, 0x77FF97B2, 0x779B70FF, 0x77FFBF70, 0x779FE8FF
    };

    public LocalPaletteScreen(Screen parent) {
        super(parent, "Visual block palette / J");
    }

    public static List<Ghost> ghosts() {
        return Collections.unmodifiableList(ghosts);
    }

    public static void clear() {
        ghosts.clear();
    }

    @Override
    protected void build() {
        button(
                x + 16,
                body,
                w - 32,
                "Material: " + NAMES[selected],
                "Choose a translucent hologram material",
                () -> {
                    selected = (selected + 1) % NAMES.length;
                    rebuildWidgets();
                });
        button(
                x + 16,
                body + 28,
                w - 32,
                "Place hologram at crosshair",
                "Adds a local visual cube beside the targeted block, capped at 64",
                () -> {
                    if (!(minecraft.hitResult instanceof BlockHitResult hit))
                        throw new IllegalStateException("Look at a nearby block first");
                    if (minecraft.player == null
                            || hit.getLocation().distanceToSqr(minecraft.player.getEyePosition())
                                    > 36)
                        throw new IllegalStateException("Target must be within six blocks");
                    if (ghosts.size() >= 64)
                        throw new IllegalStateException("64 hologram limit; remove some first");
                    ghosts.add(
                            new Ghost(
                                    Vec3.atLowerCornerOf(
                                            hit.getBlockPos().relative(hit.getDirection())),
                                    COLORS[selected]));
                    notice = "Placed local hologram " + ghosts.size() + " / 64";
                });
        button(
                x + 16,
                body + 56,
                w - 32,
                "Undo last hologram",
                "Remove the most recently placed visual cube",
                () -> {
                    if (!ghosts.isEmpty()) ghosts.removeLast();
                });
        button(
                x + 16,
                body + 84,
                w - 32,
                "Clear holograms",
                "Clear local visuals; world blocks are never edited",
                () -> ghosts.clear());
    }
}
