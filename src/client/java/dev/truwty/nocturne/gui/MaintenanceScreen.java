package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.Studio;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.maintenance.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class MaintenanceScreen extends StudioPanel {
    private String preview = Diagnostics.report();

    public MaintenanceScreen(Screen parent) {
        super(parent, "Releases / Diagnostics");
    }

    @Override
    protected int contentHeight() {
        return 670;
    }

    @Override
    protected void build() {
        var s = NocturneClient.SETTINGS;
        button(
                x + 16,
                body,
                w - 32,
                "Automatic update checks: " + (s.updateChecks.on() ? "On" : "Off"),
                "Opt-in GitHub request; no game or account data. At most once per 24 hours while"
                    + " running",
                () -> {
                    s.updateChecks.toggle();
                    s.save();
                    UpdateChecker.reset();
                    rebuildWidgets();
                });
        button(
                x + 16,
                body + 24,
                w - 32,
                "Include preview releases: " + (s.previewUpdates.on() ? "On" : "Off"),
                "Notify about compatible beta/prerelease builds as well as stable releases",
                () -> {
                    s.previewUpdates.toggle();
                    s.save();
                    UpdateChecker.reset();
                    rebuildWidgets();
                });
        button(
                x + 16,
                body + 52,
                (w - 36) / 2,
                "Check now",
                "Contact GitHub once; manual checks have a one-minute cooldown",
                () -> UpdateChecker.check(true));
        button(
                x + 20 + (w - 36) / 2,
                body + 52,
                (w - 36) / 2,
                "Changelog / downloads",
                "Open the available release, or the official release list",
                () ->
                        ClientAccess.openUri(
                                        UpdateChecker.available() == null
                                                ? ReleaseCatalog.RELEASES
                                                : UpdateChecker.available().changelog()));
        button(
                x + 16,
                body + 104,
                w - 32,
                "Minecraft support & build guide",
                "Open supported versions and reproducible-build instructions",
                () ->
                        ClientAccess.openUri(
                                        java.net.URI.create(
                                                "https://github.com/Truwty/nocturne/blob/main/SUPPORT.md")));
        button(
                x + 16,
                body + 148,
                (w - 36) / 2,
                "Refresh preview",
                "Capture only allowlisted technical facts; no logs are read",
                () -> {
                    preview = Diagnostics.report();
                    notice = "Filtered preview refreshed";
                });
        button(
                x + 20 + (w - 36) / 2,
                body + 148,
                (w - 36) / 2,
                "Copy filtered diagnostics",
                "Copy exactly the preview below",
                () -> {
                    minecraft.keyboardHandler.setClipboard(preview);
                    notice = "Filtered diagnostics copied";
                });
        button(
                x + 16,
                body + 174,
                w - 32,
                "Export filtered diagnostics",
                "Write a new JSON file in config/nocturne/diagnostics; nothing is uploaded",
                () -> {
                    var facts = Diagnostics.capture();
                    preview = DiagnosticsReport.encode(facts);
                    var file =
                            DiagnosticsReport.write(
                                    Studio.directory().resolve("diagnostics"), facts);
                    notice = "Saved diagnostics/" + file.getFileName();
                });
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int ink = NocturneClient.SETTINGS.uiText.argb();
        text(
                g,
                "Installed: "
                        + UpdateChecker.version()
                        + " · Minecraft "
                        + NocturneClient.minecraftVersion(),
                body + 82,
                ink);
        text(g, UpdateChecker.status(), body + 128, ink);
        text(
                g,
                "Preview · excludes songs, players, paths, servers and credentials",
                body + 208,
                ink);
        int yy = body + 226;
        for (String line : preview.split("\n")) {
            text(g, line, yy, ink);
            yy += 11;
        }
    }
}
