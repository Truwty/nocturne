package dev.truwty.nocturne.gui;

import com.google.gson.*;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.accessibility.*;
import dev.truwty.nocturne.setting.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

public final class ContextProfileEditorScreen extends StudioPanel {
    private final GameplayContext context;
    private final SnapshotHistory history;
    private JsonObject draft;
    private int page;
    private String query = "";
    private boolean previewing, pending;

    public ContextProfileEditorScreen(Screen parent, GameplayContext context) {
        super(parent, context.label + " / Context profile");
        this.context = context;
        draft = Studio.snapshot("context");
        var saved = ContextProfiles.profile(context);
        if (saved != null)
            saved.entrySet()
                    .forEach(e -> {
                        if (Studio.accepts("context", e.getKey()))
                            draft.add(e.getKey(), e.getValue());
                    });
        history = new SnapshotHistory(draft);
    }

    private void change(String name, JsonElement value) {
        var next = draft.deepCopy();
        next.add(name, value);
        Studio.validate("context", next);
        history.change(next);
        draft = history.value();
        if (previewing) ContextProfiles.preview(this, draft);
    }

    @Override
    protected int contentHeight() {
        return 340;
    }

    @Override
    protected boolean undo() {
        boolean changed = history.undo();
        draft = history.value();
        if (previewing) ContextProfiles.preview(this, draft);
        return changed;
    }

    @Override
    protected boolean redo() {
        boolean changed = history.redo();
        draft = history.value();
        if (previewing) ContextProfiles.preview(this, draft);
        return changed;
    }

    @Override
    public void tick() {
        if (pending) {
            pending = false;
            rebuildWidgets();
            for (var child : children())
                if (child instanceof EditBox box) {
                    setFocused(box);
                    box.setCursorPosition(box.getValue().length());
                    break;
                }
        }
    }

    @Override
    protected void build() {
        var search = field(x + 16, body, w - 32, "Search context settings", query);
        search.setResponder(v -> {
            query = v;
            page = 0;
            pending = true;
        });
        var settings =
                NocturneClient.SETTINGS.settings.stream()
                        .filter(s -> Studio.accepts("context", s.name))
                        .filter(s ->
                                (s.name + " " + SettingsCatalog.help(s))
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query.toLowerCase(Locale.ROOT)))
                        .toList();
        int yy = body + 30;
        for (int i = page * 6; i < Math.min(settings.size(), page * 6 + 6); i++) {
            addSetting(settings.get(i), yy);
            yy += 24;
        }
        button(
                x + 16,
                body + 180,
                75,
                "Previous",
                "Previous context settings",
                () -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                });
        button(
                x + 95,
                body + 180,
                75,
                "Next",
                "Next context settings",
                () -> {
                    if ((page + 1) * 6 < settings.size()) page++;
                    rebuildWidgets();
                });
        button(
                x + 16,
                body + 208,
                (w - 36) / 2,
                "Preview: " + (previewing ? "On" : "Off"),
                "Preview this draft without changing global settings",
                () -> {
                    previewing = !previewing;
                    if (previewing) ContextProfiles.preview(this, draft);
                    else ContextProfiles.clearPreview();
                    rebuildWidgets();
                });
        button(
                x + 20 + (w - 36) / 2,
                body + 208,
                (w - 36) / 2,
                "Suggested preset",
                "Use a quiet combat/menu or clean screenshot layout",
                () -> {
                    history.change(ContextProfiles.suggested(context));
                    draft = history.value();
                    if (previewing) ContextProfiles.preview(this, draft);
                    rebuildWidgets();
                });
        button(
                x + 16,
                body + 234,
                (w - 36) / 2,
                "Undo",
                "Undo this draft edit",
                () -> {
                    undo();
                    rebuildWidgets();
                });
        button(
                x + 20 + (w - 36) / 2,
                body + 234,
                (w - 36) / 2,
                "Redo",
                "Redo this draft edit",
                () -> {
                    redo();
                    rebuildWidgets();
                });
        button(
                x + 16,
                body + 262,
                (w - 36) / 2,
                "Remove saved profile",
                "Return this context to global/per-song values",
                () -> {
                    ContextProfiles.remove(context);
                    notice = "Saved profile removed";
                });
        button(
                x + 20 + (w - 36) / 2,
                body + 262,
                (w - 36) / 2,
                "Use current editor settings",
                "Copy global/per-song settings into this draft",
                () -> {
                    history.change(Studio.snapshot("context"));
                    draft = history.value();
                    if (previewing) ContextProfiles.preview(this, draft);
                    rebuildWidgets();
                });
    }

    private void addSetting(Setting<?> setting, int yy) {
        String name = setting.name;
        JsonElement value = draft.get(name);
        String tip = SettingsCatalog.help(setting);
        if (setting instanceof BoolSetting)
            button(
                    x + 16,
                    yy,
                    w - 32,
                    name + ": " + (value.getAsBoolean() ? "On" : "Off"),
                    tip,
                    () -> {
                        change(name, new JsonPrimitive(!draft.get(name).getAsBoolean()));
                        rebuildWidgets();
                    });
        else if (setting instanceof ModeSetting mode)
            button(
                    x + 16,
                    yy,
                    w - 32,
                    name + ": " + value.getAsString(),
                    tip,
                    () -> {
                        int index = mode.options.indexOf(draft.get(name).getAsString());
                        change(
                                name,
                                new JsonPrimitive(
                                        mode.options.get((index + 1) % mode.options.size())));
                        rebuildWidgets();
                    });
        else if (setting instanceof ColorSetting)
            button(
                    x + 16,
                    yy,
                    w - 32,
                    name + ": " + value.getAsString(),
                    tip,
                    () ->
                            minecraft.setScreen(
                                    new ColorPickerScreen(
                                            this,
                                            name,
                                            PickerColor.hex(value.getAsString()),
                                            color -> {
                                                change(
                                                        name,
                                                        new JsonPrimitive(
                                                                String.format("#%08X", color)));
                                            })));
        else if (setting instanceof NumberSetting number)
            addRenderableWidget(
                    new StudioSlider(
                            x + 16,
                            yy,
                            w - 32,
                            20,
                            Component.empty(),
                            (value.getAsDouble() - number.min) / (number.max - number.min)) {
                        {
                            updateMessage();
                            setTooltip(Tooltip.create(Component.literal(tip)));
                        }

                        @Override
                        protected void updateMessage() {
                            setMessage(
                                    Component.literal(
                                            name
                                                    + ": "
                                                    + String.format(
                                                            Locale.ROOT,
                                                            number.step < 1 ? "%.1f" : "%.0f",
                                                            draft.get(name).getAsDouble())
                                                    + number.unit));
                        }

                        @Override
                        protected void applyValue() {
                            double raw = number.min + this.value * (number.max - number.min);
                            change(
                                    name,
                                    new JsonPrimitive(
                                            Math.clamp(
                                                    Math.round(raw / number.step) * number.step,
                                                    number.min,
                                                    number.max)));
                            updateMessage();
                        }
                    });
    }

    @Override
    protected void footer() {
        button(
                x + 12,
                y + h - 29,
                90,
                "Cancel",
                "Discard this draft and its preview",
                this::onClose);
        button(
                x + w - 190,
                y + h - 29,
                178,
                "Save & enable profiles",
                "Save this context and turn on automatic profiles",
                () -> {
                    ContextProfiles.save(context, draft);
                    NocturneClient.SETTINGS.contextProfiles.set(true);
                    NocturneClient.SETTINGS.save();
                    onClose();
                });
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        text(
                g,
                "Draft only · Ctrl+Z / Ctrl+Y · Friendly mode remains a global lock",
                body + 302,
                NocturneClient.SETTINGS.uiText.argb());
    }

    @Override
    public void onClose() {
        ContextProfiles.clearPreview();
        super.onClose();
    }
}
