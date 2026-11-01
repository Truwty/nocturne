package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.setting.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class SettingsSearchScreen extends StudioPanel {
    private String query = "";
    private int category, page;
    private List<Setting<?>> results = List.of();

    public SettingsSearchScreen(Screen parent) {
        super(parent, "Settings / Search & categories");
    }

    @Override
    protected boolean undo() {
        return StudioEdits.undo("placement");
    }

    @Override
    protected boolean redo() {
        return StudioEdits.redo("placement");
    }

    @Override
    protected int contentHeight() {
        return 280;
    }

    @Override
    protected void build() {
        var search =
                field(x + 16, body, w - 156, "Search names, categories and descriptions", query);
        search.setMaxLength(160);
        search.setResponder(value -> {
            query = value;
            page = 0;
            refreshResults();
        });
        button(
                x + w - 136,
                body,
                120,
                SettingsCatalog.CATEGORIES.get(category),
                "Filter settings by category",
                () -> {
                    category = (category + 1) % SettingsCatalog.CATEGORIES.size();
                    page = 0;
                    rebuildWidgets();
                });
        results =
                SettingsCatalog.search(
                        NocturneClient.SETTINGS.settings,
                        SettingsCatalog.CATEGORIES.get(category),
                        query);
        page = Math.clamp(page, 0, Math.max(0, (results.size() - 1) / 6));
        for (int i = page * 6; i < Math.min(results.size(), page * 6 + 6); i++)
            addSetting(results.get(i), body + 42 + (i % 6) * 31);
        button(
                x + 16,
                body + 240,
                75,
                "Previous",
                "Previous results page",
                () -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                });
        button(
                x + 95,
                body + 240,
                75,
                "Next",
                "Next results page",
                () -> {
                    if ((page + 1) * 6 < results.size()) page++;
                    rebuildWidgets();
                });
    }

    // Rebuild on the next tick so the text field can finish processing the character event.
    private boolean pending;

    private void refreshResults() {
        pending = true;
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

    private void changed(Setting<?> setting, Runnable action) {
        String group = setting.name.startsWith("UI ") ? "themes" : "placement";
        var before = Studio.snapshot(group);
        action.run();
        StudioEdits.record(group, before, Studio.snapshot(group));
        NocturneClient.SETTINGS.save();
    }

    private void addSetting(Setting<?> setting, int by) {
        String tip = SettingsCatalog.help(setting);
        if (setting.name.startsWith("UI ")) {
            button(
                    x + 16,
                    by,
                    w - 32,
                    setting.name + " · preview editor",
                    tip,
                    () -> minecraft.setScreen(new ThemeEditorScreen(this)));
            return;
        }
        if (setting instanceof NumberSetting number) {
            addRenderableWidget(
                    new StudioSlider(x + 16, by, w - 32, 20, Component.empty(), number.fraction()) {
                        private com.google.gson.JsonObject before = Studio.snapshot("placement");

                        {
                            updateMessage();
                            setTooltip(Tooltip.create(Component.literal(tip)));
                        }

                        protected void updateMessage() {
                            setMessage(Draw.comp(number.name + ": " + number.display()));
                        }

                        protected void applyValue() {
                            number.setFraction(value);
                            updateMessage();
                        }

                        private void commit() {
                            StudioEdits.record("placement", before, Studio.snapshot("placement"));
                            before = Studio.snapshot("placement");
                            NocturneClient.SETTINGS.save();
                        }

                        public void onRelease(MouseButtonEvent event) {
                            super.onRelease(event);
                            commit();
                        }

                        public boolean keyPressed(KeyEvent event) {
                            boolean handled = super.keyPressed(event);
                            if (handled) commit();
                            return handled;
                        }
                    });
        } else if (setting instanceof ColorSetting color)
            button(
                    x + 16,
                    by,
                    w - 32,
                    setting.name + ": " + color.save().getAsString(),
                    tip,
                    () ->
                            minecraft.setScreen(
                                    new ColorPickerScreen(
                                            this,
                                            color.name,
                                            color.argb(),
                                            value -> changed(color, () -> color.set(value)))));
        else if (setting instanceof BoolSetting bool)
            button(
                    x + 16,
                    by,
                    w - 32,
                    setting.name + ": " + (bool.on() ? "On" : "Off"),
                    tip,
                    () -> {
                        changed(setting, bool::toggle);
                        rebuildWidgets();
                    });
        else if (setting instanceof ModeSetting mode)
            button(
                    x + 16,
                    by,
                    w - 32,
                    setting.name + ": " + mode.current(),
                    tip,
                    () -> {
                        changed(setting, () -> mode.cycle(1));
                        rebuildWidgets();
                    });
        else if (setting instanceof ActionSetting action)
            button(x + 16, by, w - 32, setting.name, tip, action.action::run);
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        text(
                g,
                results.size()
                        + " matches · page "
                        + (page + 1)
                        + " / "
                        + Math.max(1, (results.size() + 5) / 6),
                body + 26,
                NocturneClient.SETTINGS.uiAccent.argb());
        for (int i = page * 6; i < Math.min(results.size(), page * 6 + 6); i++)
            text(
                    g,
                    SettingsCatalog.category(results.get(i)),
                    body + 64 + (i % 6) * 31,
                    Draw.alpha(NocturneClient.SETTINGS.uiText.argb(), .55f));
    }
}
