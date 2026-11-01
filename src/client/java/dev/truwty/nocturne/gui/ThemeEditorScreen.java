package dev.truwty.nocturne.gui;

import com.google.gson.JsonObject;

import dev.truwty.nocturne.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ThemeEditorScreen extends StudioPanel {
    private final SnapshotHistory history;
    private JsonObject draft;

    public ThemeEditorScreen(Screen parent) {
        this(parent, Studio.snapshot("themes"));
    }

    public ThemeEditorScreen(Screen parent, JsonObject initial) {
        super(parent, "Theme / Live preview");
        Studio.validate("themes", initial);
        draft = Studio.snapshot("themes");
        initial.entrySet().forEach(e -> draft.add(e.getKey(), e.getValue().deepCopy()));
        history = new SnapshotHistory(draft);
    }

    private void change(JsonObject next) {
        history.change(next);
        draft = history.value();
        rebuildWidgets();
    }

    @Override
    protected int contentHeight() {
        return 288;
    }

    @Override
    protected boolean undo() {
        boolean moved = history.undo();
        draft = history.value();
        return moved;
    }

    @Override
    protected boolean redo() {
        boolean moved = history.redo();
        draft = history.value();
        return moved;
    }

    @Override
    protected void build() {
        String[] keys = {"UI background", "UI accent", "UI text"};
        int cw = (w - 40) / 3;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            button(
                    x + 16 + i * (cw + 4),
                    body,
                    cw,
                    Character.toUpperCase(key.charAt(3)) + key.substring(4),
                    "Preview a color with hue, saturation, brightness and alpha",
                    () ->
                            minecraft.setScreen(
                                    new ColorPickerScreen(
                                            this,
                                            key,
                                            PickerColor.hex(draft.get(key).getAsString()),
                                            color -> {
                                                var next = draft.deepCopy();
                                                next.addProperty(
                                                        key, String.format("#%08X", color));
                                                history.change(next);
                                                draft = history.value();
                                            })));
        }
        draftSlider(body + 28, "UI opacity", 30, 100);
        draftSlider(body + 52, "UI row spacing", 22, 32);
        button(
                x + 16,
                body + 76,
                w - 32,
                "Font: " + draft.get("UI font").getAsString(),
                "Preview the next interface font",
                () -> {
                    var values = NocturneClient.SETTINGS.uiFont.options;
                    var next = draft.deepCopy();
                    next.addProperty(
                            "UI font",
                            values.get(
                                    (values.indexOf(draft.get("UI font").getAsString()) + 1)
                                            % values.size()));
                    change(next);
                });
        button(
                x + 16,
                body + 104,
                70,
                "Undo",
                "Undo the last draft edit (Ctrl+Z)",
                () -> {
                    undo();
                    rebuildWidgets();
                });
        button(
                x + 90,
                body + 104,
                70,
                "Redo",
                "Redo the last draft edit (Ctrl+Y)",
                () -> {
                    redo();
                    rebuildWidgets();
                });
        button(
                x + 164,
                body + 104,
                w - 180,
                "Reset draft",
                "Preview default interface settings; Apply is still required",
                () -> change(Studio.defaults("themes")));
    }

    private void draftSlider(int by, String key, double min, double max) {
        addRenderableWidget(
                new StudioSlider(
                        x + 16,
                        by,
                        w - 32,
                        20,
                        Component.empty(),
                        (draft.get(key).getAsDouble() - min) / (max - min)) {
                    {
                        updateMessage();
                    }

                    protected void updateMessage() {
                        setMessage(Draw.comp(key + ": " + Math.round(min + value * (max - min))));
                    }

                    protected void applyValue() {
                        draft.addProperty(key, Math.round(min + value * (max - min)));
                        updateMessage();
                    }

                    public void onRelease(MouseButtonEvent event) {
                        super.onRelease(event);
                        history.change(draft);
                    }

                    public boolean keyPressed(KeyEvent event) {
                        boolean handled = super.keyPressed(event);
                        if (handled) history.change(draft);
                        return handled;
                    }
                });
    }

    @Override
    protected void footer() {
        button(x + 12, y + h - 29, 90, "Cancel", "Discard all preview edits", this::onClose);
        button(
                x + 106,
                y + h - 29,
                110,
                "Apply theme",
                "Apply and save the entire draft as one undoable change",
                () -> {
                    var before = Studio.snapshot("themes");
                    Studio.apply("themes", draft);
                    StudioEdits.record("themes", before, Studio.snapshot("themes"));
                    minecraft.setScreen(parent);
                });
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        preview(g, x + 16, body + 140, w - 32, 120, draft);
    }

    public static void preview(
            GuiGraphicsExtractor g, int px, int py, int width, int height, JsonObject values) {
        int bg = PickerColor.hex(values.get("UI background").getAsString()),
                accent = PickerColor.hex(values.get("UI accent").getAsString()),
                text = PickerColor.hex(values.get("UI text").getAsString());
        var settings = NocturneClient.SETTINGS;
        int fontIndex = settings.uiFont.options.indexOf(values.get("UI font").getAsString());
        for (int ix = 0; ix < width; ix += 16)
            for (int iy = 0; iy < height; iy += 16)
                g.fill(
                        px + ix,
                        py + iy,
                        px + Math.min(width, ix + 16),
                        py + Math.min(height, iy + 16),
                        ((ix + iy) / 16 % 2 == 0) ? 0xff26232d : 0xff37313e);
        Draw.round(
                g,
                px,
                py,
                width,
                height,
                6,
                Draw.alpha(bg, values.get("UI opacity").getAsFloat() / 100));
        Draw.roundOutline(g, px, py, width, height, 6, accent);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        g.text(
                font,
                Draw.lyric("Nocturne · live theme preview", true, fontIndex),
                px + 12,
                py + 12,
                text,
                false);
        Draw.round(g, px + 12, py + 34, width - 24, 22, 4, Draw.alpha(accent, .25f));
        g.text(
                font,
                Draw.lyric("Selected control", false, fontIndex),
                px + 20,
                py + 41,
                text,
                false);
        g.fill(px + 12, py + 65, px + width - 12, py + 66, accent);
        int spacing = values.get("UI row spacing").getAsInt();
        g.text(
                font,
                Draw.lyric("Playback and lyrics", false, fontIndex),
                px + 12,
                py + 76,
                text,
                false);
        if (76 + spacing + 8 <= height - 8)
            g.text(
                    font,
                    Draw.lyric("Preview only — Apply to save", false, fontIndex),
                    px + 12,
                    py + 76 + spacing,
                    Draw.alpha(text, .65f),
                    false);
    }
}
