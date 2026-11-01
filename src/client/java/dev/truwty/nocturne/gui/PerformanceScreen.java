package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.lyrics.LyricsService;
import dev.truwty.nocturne.performance.*;
import dev.truwty.nocturne.setting.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class PerformanceScreen extends StudioPanel {
    public PerformanceScreen(Screen parent) {
        super(parent, "Performance / Live budgets");
    }

    @Override
    protected int contentHeight() {
        return 462;
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

    private void number(NumberSetting setting, int yy) {
        addRenderableWidget(
                new StudioSlider(
                        x + 16,
                        yy,
                        w - 32,
                        20,
                        net.minecraft.network.chat.Component.empty(),
                        setting.fraction()) {
                    {
                        updateMessage();
                        setTooltip(
                                net.minecraft.client.gui.components.Tooltip.create(
                                        net.minecraft.network.chat.Component.literal(
                                                SettingsCatalog.help(setting))));
                    }

                    @Override
                    protected void updateMessage() {
                        setMessage(
                                net.minecraft.network.chat.Component.literal(
                                        setting.name + ": " + setting.display()));
                    }

                    @Override
                    protected void applyValue() {
                        setting.setFraction(value);
                        updateMessage();
                    }

                    @Override
                    public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
                        super.onRelease(event);
                        NocturneClient.SETTINGS.save();
                    }
                });
    }

    @Override
    protected void build() {
        var s = NocturneClient.SETTINGS;
        toggle(s.adaptiveEffects, body + 130);
        toggle(s.distanceEffects, body + 156);
        toggle(s.performanceReadout, body + 182);
        int yy = body + 216;
        for (var setting :
                new NumberSetting[] {
                    s.effectTargetMs,
                    s.particleLimit,
                    s.glowPassLimit,
                    s.cloudLayerLimit,
                    s.lingeringLimit,
                    s.effectNearDistance
                }) {
            number(setting, yy);
            yy += 26;
        }
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var s = NocturneClient.SETTINGS;
        int color = s.uiText.argb();
        var frame = RenderPerformance.snapshot();
        text(g, RenderPerformance.timing(), body, color);
        text(
                g,
                "Particles "
                        + frame.particles()
                        + " · lingering "
                        + frame.trails()
                        + " · text submissions "
                        + frame.text(),
                body + 16,
                color);
        text(
                g,
                "Meshes "
                        + frame.meshes()
                        + " · mesh vertices "
                        + frame.vertices()
                        + " · projected "
                        + frame.projected(),
                body + 32,
                color);
        text(
                g,
                String.format(
                        java.util.Locale.ROOT,
                        "Active frame %.1f ms · glow %d · cloud layers %d",
                        frame.frameMs(),
                        frame.glow(),
                        frame.cloud()),
                body + 48,
                color);
        text(g, "Artwork: " + LyricsService.INSTANCE.artworkCacheStatus(), body + 64, color);
        text(g, "CPU-side elapsed time; excludes font batching and GPU time.", body + 80, color);
        text(
                g,
                "Move the readout with the Diagnostics widget in the HUD editor.",
                body + 96,
                color);
        text(g, "Budget reductions preserve the current lyric text.", body + 112, color);
    }

    @Override
    public void onClose() {
        NocturneClient.SETTINGS.save();
        super.onClose();
    }
}
