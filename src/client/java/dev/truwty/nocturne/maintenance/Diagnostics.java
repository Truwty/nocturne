package dev.truwty.nocturne.maintenance;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.lyrics.*;
import dev.truwty.nocturne.performance.RenderPerformance;

import net.minecraft.client.Minecraft;

import java.util.*;

public final class Diagnostics {
    private Diagnostics() {}

    public static Map<String, Object> capture() {
        var mc = Minecraft.getInstance();
        var s = NocturneClient.SETTINGS;
        var svc = LyricsService.INSTANCE;
        var song = svc.now();
        var lyrics = svc.lyrics();
        var frame = RenderPerformance.snapshot();
        var facts = new LinkedHashMap<String, Object>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        facts.put("modVersion", UpdateChecker.version());
        facts.put("minecraft", ReleaseCatalog.MINECRAFT);
        facts.put(
                "os",
                os.contains("win")
                        ? "Windows"
                        : os.contains("mac") ? "macOS" : os.contains("linux") ? "Linux" : "Other");
        facts.put("javaFeature", Runtime.version().feature());
        facts.put("guiScale", mc.options.guiScale().get());
        facts.put("width", mc.getWindow().getGuiScaledWidth());
        facts.put("height", mc.getWindow().getGuiScaledHeight());
        facts.put("enabled", s.isEnabled());
        facts.put("trackDetected", song != null);
        facts.put("lyricsLoaded", lyrics != null);
        facts.put("playing", song != null && song.playing());
        facts.put("lyricLines", lyrics == null ? 0 : lyrics.lines.size());
        facts.put("detectedPlayers", svc.system.players().size());
        facts.put("accountConnected", dev.truwty.nocturne.playback.Playback.spotify().connected());
        facts.put("recovery", LyricsRecovery.current().action().name());
        facts.put("shaders", ShaderCompat.shaderPackOn());
        facts.put("renderer", ShaderCompat.hudMode(s) ? "PROJECTED" : "WORLD");
        facts.put("hidden", Accessibility.hidden());
        facts.put("friendlyMode", Accessibility.photosensitive());
        facts.put("automaticUpdates", s.updateChecks.on());
        facts.put("frameCpuMs", frame.cpuMs());
        facts.put("particles", frame.particles());
        facts.put("textSubmissions", frame.text());
        facts.put("meshVertices", frame.vertices());
        facts.put("projectedCommands", frame.projected());
        return facts;
    }

    public static String report() {
        return DiagnosticsReport.encode(capture());
    }
}
