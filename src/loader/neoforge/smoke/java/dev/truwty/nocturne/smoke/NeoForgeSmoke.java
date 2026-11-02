package dev.truwty.nocturne.smoke;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.gui.StudioScreen;
import dev.truwty.nocturne.performance.RenderPerformance;
import dev.truwty.nocturne.lyrics.Lyrics;
import dev.truwty.nocturne.lyrics.LyricsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "nocturne_smoke", dist = Dist.CLIENT)
public final class NeoForgeSmoke {
    private final long deadline = System.nanoTime() + 240_000_000_000L;
    private int ticks;
    private int stage = -1;
    private boolean finished;
    private int startupTicks;

    public NeoForgeSmoke() {
        LyricsService.INSTANCE.stop();
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new AssertionError("Native client startup timed out at "
                + (mc.screen == null ? "no screen" : mc.screen.getClass().getName() + ": " + mc.screen.getTitle().getString()));
            if (mc.level == null || mc.player == null) {
                if (++startupTicks == 200) {
                    NocturneClient.LOG.info("Smoke startup screen: {}", mc.screen == null ? "none" : mc.screen.getClass().getName());
                    Screenshot.grab(mc.gameDirectory, "nocturne-neoforge-startup.png", mc.getMainRenderTarget(), 1,
                        message -> NocturneClient.LOG.info("Smoke startup screenshot: {}", message.getString()));
                }
                return;
            }
            if (stage == -1) {
                if (NocturneClient.SETTINGS == null) throw new AssertionError("Nocturne bootstrap missing");
                if (Arrays.stream(mc.options.keyMappings).noneMatch(key -> key == NocturneClient.OPEN_KEY))
                    throw new AssertionError("Nocturne shortcut was not registered");
                mc.setScreen(null);
                var settings = NocturneClient.SETTINGS;
                settings.follow.set(2);
                settings.editorX.set(0.0);
                settings.editorY.set(0.0);
                settings.adaptiveEffects.set(false);
                settings.glowPassLimit.set(0.0);
                settings.cloudLayerLimit.set(0.0);
                settings.particleLimit.set(0.0);
                settings.lingeringLimit.set(0.0);
                LyricsService.INSTANCE.startDemo();
                LyricsService.INSTANCE.saveEdited(LyricsService.INSTANCE.now(), Lyrics.parse(
                    "[00:00.00]<00:00.00>Late <00:30.00>night <01:00.00>the <01:30.00>city <02:00.00>is <02:30.00>glowing<05:00.00>\n[05:00.00]"));
                stage = 0;
                ticks = 0;
            }
            if (++ticks < 60) return;
            ticks = 0;
            if (stage == 0) {
                var frame = RenderPerformance.snapshot();
                if (frame.text() == 0 || !Double.isFinite(frame.cpuMs()) || frame.cpuMs() <= 0)
                    throw new AssertionError("Missing lyric rendering performance measurements");
                if (frame.glow() != 0 || frame.cloud() != 0 || frame.particles() != 0 || frame.trails() != 0)
                    throw new AssertionError("Effect budgets exceeded");
                Screenshot.grab(mc.gameDirectory, "nocturne-neoforge-world.png", mc.getMainRenderTarget(), 1,
                    message -> NocturneClient.LOG.info("Smoke screenshot: {}", message.getString()));
                KeyMapping.click(NocturneClient.OPEN_KEY.getDefaultKey());
            } else if (stage == 1) {
                if (!(mc.screen instanceof StudioScreen) && !(mc.screen instanceof dev.truwty.nocturne.gui.SetupScreen)) throw new AssertionError("Shortcut did not open setup/Studio");
                var container = net.neoforged.fml.ModList.get().getModContainerById(NocturneClient.ID).orElseThrow();
                var factory = container.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class).orElseThrow();
                var parent = mc.screen;
                mc.setScreen(factory.createScreen(container, parent));
                if (!(mc.screen instanceof StudioScreen) && !(mc.screen instanceof dev.truwty.nocturne.gui.SetupScreen)) throw new AssertionError("Registered config factory did not open setup/Studio");
                mc.screen.onClose();
                if (mc.screen != parent) throw new AssertionError("Config factory screen did not restore its parent");
                var diagnostics = com.google.gson.JsonParser.parseString(dev.truwty.nocturne.maintenance.Diagnostics.report()).getAsJsonObject();
                if (!diagnostics.get("loader").getAsString().equals("neoforge")
                        || !diagnostics.get("minecraft").getAsString().equals("1.21.11"))
                    throw new AssertionError("Diagnostics reported the wrong runtime target");
            }
            if (stage >= 1 && stage <= 7) {
                for (var child : mc.screen.children()) {
                    if (child instanceof AbstractWidget widget && widget.visible &&
                        (widget.getX() < 0 || widget.getY() < 0 || widget.getRight() > mc.screen.width || widget.getBottom() > mc.screen.height))
                        throw new AssertionError("Widget outside Studio: " + widget.getMessage().getString());
                }
                Screenshot.grab(mc.gameDirectory, "nocturne-neoforge-studio-" + stage + ".png", mc.getMainRenderTarget(), 1,
                    message -> NocturneClient.LOG.info("Smoke screenshot: {}", message.getString()));
                if (stage < 7) mc.setScreen(new StudioScreen(null, stage));
            }
            if (++stage > 8) finish(mc, true, "World lyrics, zero effect budgets, registered shortcut and seven Studio tabs passed");
        } catch (Throwable failure) {
            NocturneClient.LOG.error("NeoForge native smoke failed", failure);
            finish(mc, false, failure.toString());
        }
    }

    private void finish(Minecraft mc, boolean success, String detail) {
        finished = true;
        var report = new com.google.gson.JsonObject();
        report.addProperty("success", success);
        report.addProperty("detail", detail);
        try {
            Files.writeString(Path.of(mc.gameDirectory.getPath(), "nocturne-neoforge-smoke.json"), report.toString() + "\n");
        } catch (java.io.IOException failure) {
            NocturneClient.LOG.error("Could not write native smoke report", failure);
        }
        mc.stop();
    }
}
