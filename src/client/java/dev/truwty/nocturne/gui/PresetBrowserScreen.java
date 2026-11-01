package dev.truwty.nocturne.gui;

import com.google.gson.JsonObject;

import dev.truwty.nocturne.*;

import dev.truwty.nocturne.compat.ClientAccess;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class PresetBrowserScreen extends StudioPanel {
    private final String group;
    private String selected = "", query = "", saveName = "";
    private boolean favorites;
    private int page;
    private String hudContext = "Gameplay";
    private List<String> names = List.of();
    private JsonObject imported;

    public PresetBrowserScreen(Screen parent, String group) {
        super(
                parent,
                "Presets / "
                        + (group.equals("themes")
                                ? "Themes"
                                : group.equals("hudLayouts") ? "HUD layouts" : "Placement"));
        this.group = group;
        if (parent instanceof HudEditorScreen editor) hudContext = editor.editingContext();
    }

    @Override
    protected boolean undo() {
        return !group.equals("hudLayouts") && StudioEdits.undo(group);
    }

    @Override
    protected boolean redo() {
        return !group.equals("hudLayouts") && StudioEdits.redo(group);
    }

    @Override
    protected int contentHeight() {
        return group.equals("hudLayouts") ? 314 : 286;
    }

    public static JsonObject palette(String name) {
        var values = Studio.defaults("themes");
        int bg, accent;
        switch (name) {
            case "Lagoon" -> {
                bg = 0xff0a191d;
                accent = 0xff62e9d0;
            }
            case "Ember" -> {
                bg = 0xff211411;
                accent = 0xffffac77;
            }
            default -> {
                bg = 0xff100e18;
                accent = 0xffb77fff;
            }
        }
        values.addProperty("UI background", String.format("#%08X", bg));
        values.addProperty("UI accent", String.format("#%08X", accent));
        values.addProperty("UI text", "#FFF0EEF5");
        return values;
    }

    private List<String> allNames() {
        var names = new ArrayList<String>();
        if (group.equals("themes")) names.addAll(List.of("Midnight", "Lagoon", "Ember"));
        if (group.equals("hudLayouts")) names.addAll(List.of("Gameplay", "Menu"));
        for (String name : Studio.library().presets(group))
            if (!names.contains(name)) names.add(name);
        return names;
    }

    private JsonObject values(String name) {
        var saved = Studio.library().preset(group, name);
        if (saved != null) return saved;
        if (group.equals("themes")) return palette(name);
        if (group.equals("hudLayouts")) return HudLayout.defaults(name.equals("Menu")).json();
        throw new IllegalArgumentException("Preset no longer exists");
    }

    private JsonObject current() {
        return group.equals("hudLayouts")
                ? (parent instanceof HudEditorScreen editor
                        ? editor.draft(hudContext)
                        : HudLayouts.load(hudContext).json())
                : Studio.snapshot(group);
    }

    @Override
    protected void build() {
        var filter = field(x + 16, body, w - 144, "Search preset names", query);
        filter.setMaxLength(48);
        filter.setResponder(value -> {
            query = value;
            page = 0;
            pending = true;
        });
        button(
                x + w - 124,
                body,
                108,
                favorites ? "Favorites" : "All presets",
                "Show favorites or all presets",
                () -> {
                    favorites = !favorites;
                    page = 0;
                    rebuildWidgets();
                });
        names =
                allNames().stream()
                        .filter(name ->
                                name.toLowerCase(Locale.ROOT)
                                        .contains(query.toLowerCase(Locale.ROOT)))
                        .filter(name -> !favorites || Studio.library().favorite(group, name))
                        .sorted(
                                Comparator.comparing((String name) ->
                                                !Studio.library().favorite(group, name))
                                        .thenComparing(String::compareToIgnoreCase))
                        .toList();
        page = Math.clamp(page, 0, Math.max(0, (names.size() - 1) / 2));
        if (selected.isBlank() && !names.isEmpty()) selected = names.getFirst();
        for (int i = page * 2; i < Math.min(names.size(), page * 2 + 2); i++) {
            String name = names.get(i);
            int by = body + 27 + (i % 2) * 24;
            button(
                    x + 16,
                    by,
                    w - 104,
                    (selected.equals(name) ? "› " : "") + name,
                    "Select to preview before applying",
                    () -> {
                        selected = name;
                        imported = null;
                        rebuildWidgets();
                    });
            button(
                    x + w - 84,
                    by,
                    68,
                    Studio.library().favorite(group, name) ? "★ Saved" : "☆ Save",
                    "Toggle favorite",
                    () -> {
                        Studio.library()
                                .favorite(group, name, !Studio.library().favorite(group, name));
                        rebuildWidgets();
                    });
        }
        button(
                x + 16,
                body + 78,
                60,
                "Prev",
                "Previous preset page",
                () -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                });
        button(
                x + 80,
                body + 78,
                60,
                "Next",
                "Next preset page",
                () -> {
                    if ((page + 1) * 2 < names.size()) page++;
                    rebuildWidgets();
                });
        var name = field(x + 16, body + 235, w - 136, "Name for saved preset", saveName);
        name.setMaxLength(48);
        name.setResponder(value -> saveName = value);
        button(
                x + w - 116,
                body + 235,
                100,
                "Save current",
                "Save the currently applied settings/layout under this name",
                () -> {
                    Studio.library().preset(group, saveName, current());
                    selected = saveName;
                    notice = "Preset saved";
                    rebuildWidgets();
                });
        int bw = (w - 40) / 3;
        button(
                x + 16,
                body + 260,
                bw,
                "Export JSON",
                "Copy the selected preset to the clipboard",
                () -> {
                    minecraft.keyboardHandler.setClipboard(
                            PresetCodec.encode(group, selected, preview()));
                    notice = "Preset copied";
                });
        button(
                x + 20 + bw,
                body + 260,
                bw,
                "Import JSON",
                "Validate clipboard JSON and preview it; Apply is still required",
                () -> {
                    imported = PresetCodec.decode(group, minecraft.keyboardHandler.getClipboard());
                    selected = "Imported preview";
                    notice = "Import validated; preview before applying";
                    rebuildWidgets();
                });
        button(
                x + 24 + bw * 2,
                body + 260,
                bw,
                "Reset preview",
                "Preview defaults without changing active settings",
                () -> {
                    imported =
                            group.equals("hudLayouts")
                                    ? HudLayout.defaults(hudContext.equals("Menu")).json()
                                    : Studio.defaults(group);
                    selected = "Default preview";
                    rebuildWidgets();
                });
        if (group.equals("hudLayouts")) {
            button(
                    x + 16,
                    body + 288,
                    (w - 36) / 2,
                    "Target: " + hudContext,
                    "Choose which layout receives the selected preset",
                    () -> {
                        hudContext = hudContext.equals("Gameplay") ? "Menu" : "Gameplay";
                        rebuildWidgets();
                    });
            button(
                    x + 20 + (w - 36) / 2,
                    body + 288,
                    (w - 36) / 2,
                    "Drag HUD layout",
                    "Open the draggable HUD editor",
                    () -> ClientAccess.setScreen(minecraft, new HudEditorScreen(this)));
        }
    }

    private boolean pending;

    @Override
    public void tick() {
        if (pending) {
            pending = false;
            rebuildWidgets();
            for (var child : children())
                if (child instanceof net.minecraft.client.gui.components.EditBox box) {
                    setFocused(box);
                    box.setCursorPosition(box.getValue().length());
                    break;
                }
        }
    }

    private JsonObject preview() {
        JsonObject values;
        if (imported != null) values = imported.deepCopy();
        else {
            if (selected.isBlank()) throw new IllegalStateException("Select a preset first");
            values = values(selected);
        }
        if (group.equals("themes")) {
            Studio.validate(group, values);
            var complete = Studio.snapshot("themes");
            values.entrySet()
                    .forEach(entry -> complete.add(entry.getKey(), entry.getValue().deepCopy()));
            return complete;
        }
        return values;
    }

    @Override
    protected void footer() {
        super.footer();
        button(
                x + 106,
                y + h - 29,
                110,
                "Apply preset",
                "Apply the selected preview; theme/placement changes can be undone",
                () -> {
                    if (group.equals("hudLayouts")) {
                        if (parent instanceof HudEditorScreen editor)
                            editor.applyPreset(hudContext, preview());
                        else HudLayouts.save(hudContext, HudLayout.parse(preview()));
                    } else {
                        var before = Studio.snapshot(group);
                        Studio.apply(group, preview());
                        StudioEdits.record(group, before, Studio.snapshot(group));
                    }
                    notice =
                            "Applied "
                                    + selected
                                    + (group.equals("hudLayouts") ? " to " + hudContext : "");
                });
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (selected.isBlank()) {
            text(
                    g,
                    "No presets match. Save current settings to create one.",
                    body + 110,
                    NocturneClient.SETTINGS.uiText.argb());
            return;
        }
        JsonObject values;
        try {
            values = preview();
        } catch (RuntimeException ex) {
            text(g, "Invalid preset: " + ex.getMessage(), body + 110, 0xffffa088);
            return;
        }
        text(g, "Preview / " + selected, body + 106, NocturneClient.SETTINGS.uiText.argb());
        if (group.equals("themes"))
            ThemeEditorScreen.preview(g, x + 16, body + 120, w - 32, 104, values);
        else if (group.equals("hudLayouts")) {
            var layout = HudLayout.parse(values);
            int px = x + 16, py = body + 120, pw = w - 32, ph = 100;
            Draw.round(g, px, py, pw, ph, 4, 0xff20212a);
            for (String id : HudLayout.WIDGETS) {
                var n = layout.node(id);
                if (!n.visible()) continue;
                var b = n.box(pw, ph, id.equals("Karaoke") ? 100 : 70, 10);
                Draw.round(
                        g,
                        px + (int) b.x(),
                        py + (int) b.y(),
                        (int) b.width(),
                        (int) b.height(),
                        2,
                        NocturneClient.SETTINGS.uiAccent.argb());
            }
        } else {
            int px = x + w / 2, py = body + 170;
            Draw.round(g, x + 16, body + 120, w - 32, 104, 4, 0xff20212a);
            double size = values.has("Size") ? values.get("Size").getAsDouble() : 100;
            double
                    ox =
                            values.has("Editor horizontal")
                                    ? values.get("Editor horizontal").getAsDouble()
                                    : 0,
                    oy =
                            values.has("Editor vertical")
                                    ? values.get("Editor vertical").getAsDouble()
                                    : 0;
            int ww = (int) Math.clamp(size, 35, 160);
            int cx = (int) Math.clamp(px + ox * 6, x + 24 + ww / 2, x + w - 24 - ww / 2),
                    cy = (int) Math.clamp(py - oy * 3, body + 135, body + 205);
            Draw.round(g, cx - ww / 2, cy - 9, ww, 18, 4, NocturneClient.SETTINGS.uiAccent.argb());
            text(
                    g,
                    "Placement preview · " + values.toString(),
                    body + 208,
                    NocturneClient.SETTINGS.uiText.argb());
        }
    }
}
