package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.accessibility.*;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.setting.*;
import java.util.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class AccessibilityScreen extends StudioPanel {
    private int page;
    private String classes = String.join(", ", Accessibility.screens());

    public AccessibilityScreen(Screen parent) {
        this(parent, 0);
    }

    public AccessibilityScreen(Screen parent, int page) {
        super(parent, "Gameplay / Accessibility");
        this.page = page;
    }

    @Override
    protected int contentHeight() {
        return page == 0 ? 410 : page == 3 ? 340 : 310;
    }

    private void toggle(BoolSetting setting, int yy) {
        button(
                x + 16,
                yy,
                w - 32,
                setting.name + ": " + (setting.on() ? "On" : "Off"),
                SettingsCatalog.help(setting),
                () -> {
                    setting.toggle();
                    NocturneClient.SETTINGS.save();
                    rebuildWidgets();
                });
    }

    @Override
    protected void build() {
        int cw = (w - 44) / 4;
        String[] names = {"Visibility", "Motion", "Readability", "Contexts"};
        for (int i = 0; i < 4; i++) {
            int target = i;
            button(
                    x + 16 + i * (cw + 4),
                    body,
                    cw,
                    (page == i ? "• " : "") + names[i],
                    "Open " + names[i],
                    () -> {
                        page = target;
                        scroll = 0;
                        rebuildWidgets();
                    });
        }
        int top = body + 36;
        var s = NocturneClient.SETTINGS;
        switch (page) {
            case 0 -> {
                int yy = top;
                for (var setting :
                        new BoolSetting[] {
                            s.hideChat,
                            s.hideInventory,
                            s.hideDebug,
                            s.hidePause,
                            s.hideOtherScreens,
                            s.hideWithGui,
                            s.edgeFallback
                        }) {
                    toggle(setting, yy);
                    yy += 24;
                }
                var edit =
                        field(
                                x + 16,
                                top + 192,
                                w - 32,
                                "Exact screen classes, comma separated",
                                classes);
                edit.setResponder(v -> classes = v);
                button(
                        x + 16,
                        top + 218,
                        (w - 36) / 2,
                        "Save selected screens",
                        "Add exact class names to the hidden-screen rules",
                        () -> {
                            Accessibility.screens(Arrays.asList(classes.split(",", -1)));
                            notice = "Hidden-screen rules saved";
                        });
                button(
                        x + 20 + (w - 36) / 2,
                        top + 218,
                        (w - 36) / 2,
                        "Add last game screen",
                        "Remember the last screen visited outside Nocturne",
                        () -> {
                            if (Accessibility.lastScreen().isBlank())
                                throw new IllegalStateException("Visit a game screen first");
                            var values = new LinkedHashSet<>(Accessibility.screens());
                            values.add(Accessibility.lastScreen());
                            Accessibility.screens(values);
                            classes = String.join(", ", values);
                            rebuildWidgets();
                        });
            }
            case 1 -> {
                int yy = top;
                for (var setting :
                        new BoolSetting[] {
                            s.flashEffects,
                            s.cameraMovement,
                            s.particleEffects,
                            s.animatedText,
                            s.shakeEffects,
                            s.reducedMotion
                        }) {
                    toggle(setting, yy);
                    yy += 24;
                }
                button(
                        x + 16,
                        top + 162,
                        w - 32,
                        "Apply photosensitivity-friendly preset",
                        "Steady Nocturne text; disable flashes, camera effects, particles, glow and"
                            + " animated decoration",
                        () -> {
                            Accessibility.applyFriendly();
                            rebuildWidgets();
                        });
                toggle(s.photosensitiveMode, top + 188);
            }
            case 2 -> {
                toggle(s.lyricBackplate, top);
                toggle(s.lyricOutline, top + 24);
                button(
                        x + 16,
                        top + 52,
                        w - 32,
                        "Contrast: " + s.contrastPreset.current(),
                        "Contrast presets include a matching backplate",
                        () -> {
                            s.contrastPreset.cycle(1);
                            s.save();
                            rebuildWidgets();
                        });
                addRenderableWidget(
                        new StudioSlider(
                                x + 16,
                                top + 78,
                                w - 32,
                                20,
                                net.minecraft.network.chat.Component.empty(),
                                s.backplateOpacity.fraction()) {
                            {
                                updateMessage();
                            }

                            @Override
                            protected void updateMessage() {
                                setMessage(
                                        net.minecraft.network.chat.Component.literal(
                                                "Backplate opacity: "
                                                        + s.backplateOpacity.display()));
                            }

                            @Override
                            protected void applyValue() {
                                s.backplateOpacity.setFraction(value);
                                updateMessage();
                            }

                            @Override
                            public void onRelease(
                                    net.minecraft.client.input.MouseButtonEvent event) {
                                super.onRelease(event);
                                s.save();
                            }
                        });
                button(
                        x + 16,
                        top + 106,
                        (w - 36) / 2,
                        "White on charcoal",
                        "White text with a dark backplate and outline",
                        () -> contrast(1));
                button(
                        x + 20 + (w - 36) / 2,
                        top + 106,
                        (w - 36) / 2,
                        "Ink on parchment",
                        "Dark text with a light backplate and outline",
                        () -> contrast(2));
                button(
                        x + 16,
                        top + 136,
                        w - 32,
                        "Custom colors",
                        "Return to your configured lyric palette",
                        () -> contrast(0));
            }
            case 3 -> {
                toggle(s.contextProfiles, top);
                button(
                        x + 16,
                        top + 26,
                        w - 32,
                        "Screenshot context: " + (ContextProfiles.screenshot() ? "On" : "Off"),
                        "Save a Screenshots profile and enable automatic profiles first. Enable"
                            + " this context before F2; toggle again to exit. A keybind is in"
                            + " Minecraft Controls.",
                        () -> {
                            ContextProfiles.screenshot(!ContextProfiles.screenshot());
                            rebuildWidgets();
                        });
                int yy = top + 62;
                for (var context : GameplayContext.values()) {
                    button(
                            x + 16,
                            yy,
                            w - 32,
                            "Edit " + context.label,
                            "Draft separate appearance, placement, HUD and effect settings",
                            () ->
                                    ClientAccess.setScreen(
                                            minecraft, new ContextProfileEditorScreen(this, context)));
                    yy += 26;
                }
            }
        }
    }

    private void contrast(int preset) {
        var s = NocturneClient.SETTINGS;
        s.contrastPreset.set(preset);
        if (preset != 0) {
            s.lyricBackplate.set(true);
            s.lyricOutline.set(true);
        }
        s.save();
        rebuildWidgets();
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int top = body + 36, color = NocturneClient.SETTINGS.uiText.argb();
        switch (page) {
            case 0 -> {
                text(g, "Last screen: " + Accessibility.lastScreen(), top + 252, color);
                text(
                        g,
                        "Hiding also suppresses fallback lyrics and Nocturne camera effects.",
                        top + 268,
                        color);
            }
            case 1 -> {
                text(
                        g,
                        "Friendly mode locks Nocturne flashes and motion off across profiles.",
                        top + 222,
                        color);
                text(
                        g,
                        "Minecraft, shaders and other mods retain their own visual settings.",
                        top + 238,
                        color);
            }
            case 2 -> {
                text(
                        g,
                        "Outlines add four text passes per word; backplates are one per line.",
                        top + 174,
                        color);
                text(g, "Friendly mode uses a stable white-on-dark palette.", top + 190, color);
            }
            case 3 -> {
                text(g, "Priority: Screenshots → Menus → Combat → Exploration", top + 184, color);
                text(g, "Current: " + ContextProfiles.current().label, top + 202, color);
                text(g, "Unset profiles use your global/per-song settings.", top + 220, color);
                text(
                        g,
                        "Profiles apply only during rendering; saves keep global settings.",
                        top + 238,
                        color);
            }
        }
    }

    @Override
    public void onClose() {
        NocturneClient.SETTINGS.save();
        super.onClose();
    }
}
