package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class PlacementScreen extends Screen {
    private final Screen parent;
    private int mode;
    private boolean dragging;
    public PlacementScreen(Screen parent) { super(Component.literal("Nocturne placement editor")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void init() {
        String[] modes = {"Move", "Rotate", "Resize"};
        for (int i = 0; i < 3; i++) { int next = i; addRenderableWidget(Button.builder(Component.literal(modes[i]), b -> mode = next).bounds(12 + i * 76, 12, 72, 20).build()); }
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            var s = NocturneClient.SETTINGS; s.editorX.set(0.0); s.editorY.set(0.0); s.editorYaw.set(0.0); s.editorPitch.set(0.0); s.editorRoll.set(0.0); s.size.set(100.0); s.distance.set(3.5);
        }).bounds(width - 154, 12, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> onClose()).bounds(width - 80, 12, 66, 20).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        var s = NocturneClient.SETTINGS;
        g.fill(12, height - 53, width - 12, height - 12, 0xCC101118);
        Draw.text(g, font, "Drag to " + new String[]{"move", "rotate", "resize"}[mode] + " · Wheel: distance · Right drag: roll", 20, height - 45, -1, false);
        Draw.text(g, font, "X " + s.editorX.display() + "  Y " + s.editorY.display() + "  Yaw " + s.editorYaw.display() + "  Pitch " + s.editorPitch.display() + "  Size " + s.size.display(), 20, height - 28, -1, false);
        Draw.roundOutline(g, width / 2 - 110, height / 2 - 45, 220, 90, 6, 0x888DD6C0);
        Draw.textCentered(g, font, minecraft.level == null ? "Join a world for a live preview" : "Live lyric placement", width / 2f, height / 2f + 51, -1, 1, false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) return true;
        dragging = event.y() > 40 && event.y() < height - 60; return dragging;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return super.mouseDragged(event, dx, dy);
        var s = NocturneClient.SETTINGS;
        if (event.button() == 1) adjust(s.editorRoll, dx);
        else if (mode == 0) { adjust(s.editorX, dx * 0.02); adjust(s.editorY, -dy * 0.02); }
        else if (mode == 1) { adjust(s.editorYaw, dx); adjust(s.editorPitch, dy); }
        else adjust(s.size, dx * 0.5 - dy * 0.5);
        return true;
    }
    private void adjust(NumberSetting setting, double delta) { setting.setFraction((setting.get() + delta - setting.min) / (setting.max - setting.min)); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { dragging = false; return super.mouseReleased(event); }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { adjust(NocturneClient.SETTINGS.distance, -sy * 0.5); return true; }
    @Override public void onClose() { NocturneClient.SETTINGS.save();
        try { dev.truwty.nocturne.Studio.savePlacement(); } catch (java.io.IOException ex) { NocturneClient.LOG.warn("Placement save failed", ex); }
        minecraft.setScreen(parent); }
}
