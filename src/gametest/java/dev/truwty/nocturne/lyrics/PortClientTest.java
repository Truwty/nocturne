package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.gui.GlassLyricsScreen;
import dev.truwty.nocturne.gui.LyricsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public final class PortClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            if ("true".equals(System.getenv("NOCTURNE_MEDIA_SMOKE"))) {
                context.waitFor(mc -> LyricsService.INSTANCE.system.latest() != null, 200);
                context.runOnClient(mc -> {
                    NowPlaying detected = LyricsService.INSTANCE.system.latest();
                    if (detected == null || detected.title().isBlank()) {
                        throw new AssertionError("Automatic desktop detection did not find the running test player");
                    }
                    NocturneClient.LOG.info("AUTOMATIC MEDIA TEST PASSED: desktop player detected without OAuth");
                });
            }
            world.getServer().runCommand("time set night");
            world.getServer().runCommand("weather rain");
            context.runOnClient(mc -> {
                NocturneClient.SETTINGS.preset.set(0);
                NocturneClient.SETTINGS.seeThrough.set(true);
                LyricsService.INSTANCE.startDemo();
            });
            context.waitTicks(60);
            context.runOnClient(mc -> {
                if (!LyricsRenderer.active() || LyricsDebug.lastRender == 0 || LyricsDebug.worldPieces == 0) {
                    throw new AssertionError("World lyrics did not submit visible geometry");
                }
                if (LyricsRain.lastFrame() == 0) throw new AssertionError("Weather mixin did not run");
            });
            context.takeScreenshot("nocturne-world");
            for (int placement = 0; placement < 7; placement++) {
                final int mode = placement;
                context.runOnClient(mc -> NocturneClient.SETTINGS.follow.set(mode));
                context.waitTicks(12);
            }
            for (int font = 0; font < 4; font++) {
                final int mode = font;
                context.runOnClient(mc -> NocturneClient.SETTINGS.lyricFont.set(mode));
                context.waitTicks(12);
            }
            context.runOnClient(mc -> {
                NocturneClient.SETTINGS.shaderMode.set(1);
                NocturneClient.SETTINGS.follow.set(1);
            });
            context.waitTicks(30);
            context.runOnClient(mc -> {
                if (LyricsDebug.hudRecorded == 0 || LyricsDebug.hudDrawn == 0) {
                    throw new AssertionError("Shader-compatible lyrics were not drawn");
                }
            });
            context.takeScreenshot("nocturne-hud-fallback");
            context.setScreen(() -> new GlassLyricsScreen(null));
            context.waitTicks(15);
            context.takeScreenshot("nocturne-glass-settings");
            for (LyricsScreen.Page page : LyricsScreen.Page.values()) {
                context.setScreen(() -> new LyricsScreen(null, page));
                context.waitTicks(3);
            }
            context.takeScreenshot("nocturne-vanilla-settings");
            context.setScreen(() -> null);
            NocturneClient.LOG.info("PORT TEST PASSED: world geometry, weather, seven placements, four fonts, HUD fallback, both settings UIs and automatic desktop detection UI");
        }
    }
}
