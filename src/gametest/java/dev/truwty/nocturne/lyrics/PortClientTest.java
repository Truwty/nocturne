package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.gui.GlassLyricsScreen;
import dev.truwty.nocturne.gui.LyricsScreen;
import dev.truwty.nocturne.gui.StudioScreen;
import dev.truwty.nocturne.gui.PlacementScreen;
import dev.truwty.nocturne.Studio;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public final class PortClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            if(System.getenv("NOCTURNE_TEST_SHADER")!=null) {
                context.runOnClient(mc->{ if(!ShaderCompat.shaderPackOn())throw new AssertionError("Requested real shader pack did not activate"); });
                NocturneClient.LOG.info("REAL SHADER PACK ACTIVE");
            }
            if(System.getenv("NOCTURNE_TEST_RESOURCE")!=null) {
                var resourceReload=context.computeOnClient(mc->{
                    mc.getResourcePackRepository().reload();
                    NocturneClient.LOG.info("Available compatibility packs: {}",mc.getResourcePackRepository().getAvailableIds());
                    mc.getResourcePackRepository().setSelected(java.util.List.of("vanilla","file/compat-resource.zip"));
                    return mc.reloadResourcePacks();
                });
                context.waitFor(mc->resourceReload.isDone(),1000);resourceReload.join();
                context.runOnClient(mc->{if(!mc.getResourcePackRepository().getSelectedIds().contains("file/compat-resource.zip"))throw new AssertionError("Requested resource pack did not load");});
                NocturneClient.LOG.info("REAL RESOURCE PACK ACTIVE");
                if(System.getenv("NOCTURNE_TEST_SHADER")!=null)context.runOnClient(mc->{if(!ShaderCompat.shaderPackOn())throw new AssertionError("Shader disabled after resource reload");});
            }
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
            if ("true".equals(System.getenv("NOCTURNE_LIVE_LYRICS"))) {
                context.runOnClient(mc -> {
                    LyricsService.INSTANCE.stop();
                    var media = new com.google.gson.JsonObject();
                    media.addProperty("title", "Blinding Lights"); media.addProperty("artist", "The Weeknd");
                    media.addProperty("album", "After Hours"); media.addProperty("app", "Spotify.exe");
                    media.addProperty("playing", true); media.addProperty("pos", 35000); media.addProperty("dur", 200000);
                    media.addProperty("updated", 1); media.addProperty("now", System.currentTimeMillis());
                    LyricsService.INSTANCE.update(SystemMediaSource.decodeSession(media));
                });
                context.waitFor(mc -> LyricsService.INSTANCE.lyrics() != null, 500);
                context.waitTicks(10);
                context.runOnClient(mc -> {
                    if (!LyricsRenderer.active() || (LyricsDebug.worldPieces == 0 && LyricsDebug.hudDrawn == 0)) throw new AssertionError("Live provider lyrics did not reach world geometry");
                    NocturneClient.LOG.info("LIVE LYRICS RENDER PASSED: detected metadata, invalid timestamp recovery, public lookup and visible world geometry");
                    LyricsService.INSTANCE.start();
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
                if (!LyricsRenderer.active() || LyricsDebug.lastRender == 0 || (LyricsDebug.worldPieces == 0 && LyricsDebug.hudDrawn == 0)) {
                    throw new AssertionError("World lyrics did not submit visible geometry");
                }
                if (System.getenv("NOCTURNE_TEST_SHADER")==null && LyricsRain.lastFrame() == 0) throw new AssertionError("Weather mixin did not run");
            });
            takeShot(context, "nocturne-world");
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
            takeShot(context, "nocturne-hud-fallback");
            context.setScreen(() -> new GlassLyricsScreen(null));
            context.waitTicks(15);
            takeShot(context, "nocturne-glass-settings");
            for (LyricsScreen.Page page : LyricsScreen.Page.values()) {
                context.setScreen(() -> new LyricsScreen(null, page));
                context.waitTicks(3);
            }
            takeShot(context, "nocturne-vanilla-settings");
            context.runOnClient(mc -> {
                try {
                    LyricsService.INSTANCE.startDemo();
                    var file = Studio.directory().resolve("test-correction.lrc");
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, "[00:01.00]Original test correction\n[00:05.00]Second test line");
                    LyricsService.INSTANCE.importLocal(file);
                    if (!LyricsService.INSTANCE.lyrics().lines.getFirst().text().equals("Original test correction")) throw new AssertionError("Local correction not applied");
                    Studio.library().offset(LyricsService.INSTANCE.now().key(), 200);
                    if (Studio.songOffset(LyricsService.INSTANCE.now()) != 200) throw new AssertionError("Song offset not applied");
                    var theme = Studio.snapshot("themes");
                    Studio.library().preset("themes", "Test theme", theme);
                    NocturneClient.SETTINGS.uiAccent.set(0xFF12AB34);
                    Studio.apply("themes", Studio.library().preset("themes", "Test theme"));
                    if (!Studio.snapshot("themes").equals(theme)) throw new AssertionError("Theme round trip failed");
                    NocturneClient.SETTINGS.reducedMotion.set(true);
                    NocturneClient.SETTINGS.flashEffects.set(true);
                    NocturneClient.SETTINGS.highContrast.set(true);
                } catch (java.io.IOException ex) { throw new AssertionError(ex); }
            });
            context.waitTicks(5);
            context.runOnClient(mc -> {
                if (LyricsRenderer.flash() != 0 || LyricsRenderer.fovKick() != 1) throw new AssertionError("Reduced motion did not suppress flash/FOV effects");
                NocturneClient.SETTINGS.reducedMotion.set(false);
                NocturneClient.SETTINGS.flashEffects.set(false);
                NocturneClient.SETTINGS.highContrast.set(false);
            });
            for (int tab = 0; tab < 7; tab++) {
                int page = tab;
                context.setScreen(() -> new StudioScreen(null, page));
                context.waitTicks(3);
                takeShot(context, "nocturne-studio-" + page);
            }
            context.setScreen(() -> new StudioScreen(null, 0));
            context.runOnClient(mc -> { NocturneClient.SETTINGS.editorX.set(8.0); NocturneClient.SETTINGS.follow.set(0); });
            context.clickScreenButton("Restore visible lyrics");
            context.waitTicks(4);
            context.runOnClient(mc -> {
                if (NocturneClient.SETTINGS.follow.get()!=2 || NocturneClient.SETTINGS.editorX.get()!=0 || NocturneClient.SETTINGS.shaderMode.get()!=1)
                    throw new AssertionError("Visibility recovery was not persisted through placement profile switching");
            });
            context.setScreen(() -> new StudioScreen(null, 3));
            context.clickScreenButton("Lagoon"); context.waitTicks(3); takeShot(context, "nocturne-lagoon-theme");
            context.runOnClient(mc -> { if (NocturneClient.SETTINGS.uiAccent.argb()!=0xFF62E9D0) throw new AssertionError("Theme shortcut did not apply"); });
            context.setScreen(() -> new PlacementScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> {
                var settings = NocturneClient.SETTINGS;
                var editor = mc.screen;
                var event = new net.minecraft.client.input.MouseButtonEvent(editor.width / 2.0, editor.height / 2.0,
                    new net.minecraft.client.input.MouseButtonInfo(0, 0));
                double before = settings.editorX.get();
                editor.mouseClicked(event, false); editor.mouseDragged(event, 25, -20); editor.mouseReleased(event);
                if (settings.editorX.get() <= before) throw new AssertionError("Placement drag did not move lyrics");
            });
            context.clickScreenButton("Rotate");
            context.runOnClient(mc -> {
                var editor = mc.screen;
                var event = new net.minecraft.client.input.MouseButtonEvent(editor.width / 2.0, editor.height / 2.0,
                    new net.minecraft.client.input.MouseButtonInfo(0, 0));
                double before = NocturneClient.SETTINGS.editorYaw.get();
                editor.mouseClicked(event, false); editor.mouseDragged(event, 25, 10); editor.mouseReleased(event);
                if (NocturneClient.SETTINGS.editorYaw.get() <= before) throw new AssertionError("Placement drag did not rotate lyrics");
                try { Studio.savePlacement(); } catch (java.io.IOException ex) { throw new AssertionError(ex); }
                int placement = NocturneClient.SETTINGS.follow.get();
                double edited = NocturneClient.SETTINGS.editorX.get();
                NocturneClient.SETTINGS.follow.set((placement + 1) % 7); Studio.tick();
                NocturneClient.SETTINGS.editorX.set(-4.0);
                NocturneClient.SETTINGS.follow.set(placement); Studio.tick();
                if (NocturneClient.SETTINGS.editorX.get() != edited) throw new AssertionError("Placement profile was not restored");
            });
            context.clickScreenButton("Reset");
            takeShot(context, "nocturne-placement-editor");
            context.runOnClient(mc -> { mc.getWindow().setWindowed(640, 480); mc.options.guiScale().set(2); mc.resizeGui(); });
            context.waitTicks(10);
            for (int tab = 0; tab < 7; tab++) {
                int page = tab;
                context.setScreen(() -> new StudioScreen(null, page));
                context.waitTicks(2);
                context.runOnClient(mc -> {
                    ScreenBounds.assertVisibleWidgetsFit(mc.screen);
                    mc.screen.mouseScrolled(100, 100, 0, -8);
                });
                context.waitTicks(2);
                context.runOnClient(mc -> ScreenBounds.assertVisibleWidgetsFit(mc.screen));
            }
            takeShot(context, "nocturne-small-window");
            context.runOnClient(mc -> { mc.getWindow().setWindowed(854, 480); mc.options.guiScale().set(1); mc.resizeGui(); });
            context.setScreen(() -> null);
            for(int guiScale:new int[]{1,2,3,4}) {
                context.runOnClient(mc->{mc.getWindow().setWindowed(1280,960);mc.options.guiScale().set(guiScale);mc.resizeGui();});
                context.setScreen(()->new StudioScreen(null,0));context.waitTicks(3);
                context.runOnClient(mc->ScreenBounds.assertVisibleWidgetsFit(mc.screen));
            }
            context.setScreen(()->null);
            var reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
            context.waitFor(mc -> reload.isDone(), 400);
            reload.join();
            context.setScreen(() -> null);
            NocturneClient.LOG.info("STUDIO TEST PASSED: world lyrics, placements, fonts, UI tabs, local correction, song offset, theme round trip, reduced motion, small-window bounds and resource reload");
        }
        try (TestSingleplayerContext reconnected = context.worldBuilder().create()) {
            reconnected.getClientLevel().waitForChunksRender();
            context.runOnClient(mc -> LyricsService.INSTANCE.startDemo());
            context.waitTicks(25);
            context.runOnClient(mc -> { if (!LyricsRenderer.active()) throw new AssertionError("Lyrics did not recover after reconnect"); });
        }
    }

    private static void takeShot(ClientGameTestContext context, String name) {
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (loader.isModLoaded("sodium")) {
            // Fabric's screenshot utility re-renders outside Sodium's normal uniform lifecycle.
            // Read the already rendered framebuffer for compatibility runs instead.
            String renderer = loader.isModLoaded("iris") ? "iris" : "sodium";
            context.runOnClient(mc -> net.minecraft.client.Screenshot.grab(mc.gameDirectory,
                renderer + "-" + name + ".png", mc.getMainRenderTarget(), 1, message -> {}));
            context.waitTicks(2);
        } else context.takeScreenshot(name);
    }

    private static final class ScreenBounds {
        static void assertVisibleWidgetsFit(net.minecraft.client.gui.screens.Screen screen) {
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible &&
                    (widget.getX() < 0 || widget.getY() < 0 || widget.getRight() > screen.width || widget.getBottom() > screen.height)) {
                    throw new AssertionError("Widget outside resized screen: " + widget.getMessage().getString());
                }
            }
        }
    }
}
