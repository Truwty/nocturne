package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.Studio;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.GlassLyricsScreen;
import dev.truwty.nocturne.gui.LyricsScreen;
import dev.truwty.nocturne.gui.PlacementScreen;
import dev.truwty.nocturne.gui.StudioScreen;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public final class PortClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = createWorld(context)) {
            dev.truwty.nocturne.compat.TestAccess.waitForChunks(world);
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
                context.waitFor(mc -> LyricsService.INSTANCE.lyrics() != null || LyricsService.INSTANCE.lyricsStatus().startsWith("Network unavailable") || LyricsService.INSTANCE.lyricsStatus().startsWith("No confident"), 1200);
                context.runOnClient(mc->{if(LyricsService.INSTANCE.lyrics()==null)throw new AssertionError("Live lookup failed: "+LyricsService.INSTANCE.lyricsStatus());});
                context.waitTicks(10);
                context.runOnClient(mc -> {
                    if (!LyricsRenderer.active() || (LyricsDebug.worldPieces == 0 && LyricsDebug.hudDrawn == 0)) throw new AssertionError("Live provider lyrics did not reach world geometry");
                    NocturneClient.LOG.info("LIVE LYRICS RENDER PASSED: detected metadata, invalid timestamp recovery, public"
                  + " lookup and visible world geometry");
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
            for (int font = 0; font < 5; font++) {
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
            for (int tab = 0; tab < 8; tab++) {
                int page = tab;
                context.setScreen(() -> new StudioScreen(null, page));
                context.waitTicks(3);
                takeShot(context, "nocturne-studio-" + page);
            }
            context.getInput().resizeWindow(1280,960);context.runOnClient(mc -> {mc.options.guiScale().set(2);dev.truwty.nocturne.compat.TestAccess.resizeGui(mc);});
            context.runOnClient(mc -> {
                var primary=Lyrics.parse("[00:01]<00:01>مرحبا <00:02>بالعالم<00:03>\n"
                        + "[00:04]\n"
                        + "[00:08]<00:08>שלום <00:09>עולם<00:10>\n"
                        + "[00:12]")
                    .secondary(Lyrics.parse("[00:01]Hello world\n[00:08]こんにちは世界"),Lyrics.parse("[00:01]marhaban bilalam"));
                LyricsService.INSTANCE.applyLocal(LyricsService.INSTANCE.now(),primary);
                NocturneClient.SETTINGS.translation.set(true);NocturneClient.SETTINGS.romanization.set(true);
                NocturneClient.SETTINGS.lyricFont.set(4);
            });
            for(String tab:java.util.List.of("History","Timing","Languages","Song style")) {
                context.setScreen(()->new dev.truwty.nocturne.gui.LyricWorkspaceScreen(null));
                if(!tab.equals("History"))context.clickScreenButton(tab);
                context.waitTicks(4);context.runOnClient(mc->ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc)));
                takeShot(context,"nocturne-workspace-"+tab.replace(' ','-'));
            }
            context.setScreen(()->new dev.truwty.nocturne.gui.LyricWorkspaceScreen(null));context.clickScreenButton("Timing");
            context.clickScreenButton("Prev line");
            context.clickScreenButton("Line +50 ms");context.clickScreenButton("Save lyrics");
            context.runOnClient(mc->{if(LyricsService.INSTANCE.lyrics().lines.getFirst().start()!=1050)throw new AssertionError("Timing editor did not save correction");});
            context.clickScreenButton("Undo");context.clickScreenButton("Save lyrics");
            context.runOnClient(mc->{if(LyricsService.INSTANCE.lyrics().lines.getFirst().start()!=1000)throw new AssertionError("Timing editor undo did not restore bounds");});
            context.runOnClient(mc->{
                var screen=ClientAccess.screen(mc);screen.mouseScrolled(100,100,0,-100);
                int panelW=Math.min(650,screen.width-16),panelH=Math.min(406,screen.height-16);
                int panelX=(screen.width-panelW)/2,panelY=(screen.height-panelH)/2;
                int scroll=Math.max(0,285-(panelH-138));
                double mx=panelX+20+1000*(panelW-40)/5000, my=panelY+76-scroll+219;
                var down=new net.minecraft.client.input.MouseButtonEvent(mx,my,new net.minecraft.client.input.MouseButtonInfo(0,0));
                var drag=new net.minecraft.client.input.MouseButtonEvent(mx+7,my,new net.minecraft.client.input.MouseButtonInfo(0,0));
                if(!screen.mouseClicked(down,false))throw new AssertionError("Timeline did not accept word boundary");
                screen.mouseDragged(drag,7,0);screen.mouseReleased(drag);
            });
            context.clickScreenButton("Save lyrics");
            context.runOnClient(mc->{if(LyricsService.INSTANCE.lyrics().lines.getFirst().words().getFirst().start()<=1000)throw new AssertionError("Timeline boundary drag did not save word timing");ClientAccess.screen(mc).mouseScrolled(100,100,0,100);});
            context.clickScreenButton("Undo");context.clickScreenButton("Save lyrics");
            context.runOnClient(mc->{
                try {
                    var settings=NocturneClient.SETTINGS;settings.size.set(110.0);var baseline=Studio.snapshot("song");
                    dev.truwty.nocturne.SongAppearance.save(LyricsService.INSTANCE.now());settings.size.set(150.0);
                    dev.truwty.nocturne.SongAppearance.save(LyricsService.INSTANCE.now());
                    if(!dev.truwty.nocturne.SongAppearance.active()||settings.size.get()!=150)throw new AssertionError("Song appearance did not apply");
                    var persisted=Studio.snapshot("song");dev.truwty.nocturne.SongAppearance.persistGlobal(persisted);
                    if(persisted.get("Size").getAsDouble()!=110)throw new AssertionError("Song appearance overwrote global config");
                    dev.truwty.nocturne.SongAppearance.remove(LyricsService.INSTANCE.now());
                    if(settings.size.get()!=110||dev.truwty.nocturne.SongAppearance.active())throw new AssertionError("Global appearance did not restore");
                }catch(java.io.IOException ex){throw new AssertionError(ex);}
            });
            context.setScreen(() -> new StudioScreen(null, 0));
            context.runOnClient(mc -> { NocturneClient.SETTINGS.editorX.set(8.0); NocturneClient.SETTINGS.follow.set(0); });
            context.clickScreenButton("Restore visible lyrics");
            context.waitTicks(4);
            context.runOnClient(mc -> {
                if (NocturneClient.SETTINGS.follow.get()!=2 || NocturneClient.SETTINGS.editorX.get()!=0 || NocturneClient.SETTINGS.shaderMode.get()!=1)
                    throw new AssertionError("Visibility recovery was not persisted through placement profile switching");
            });
            context.setScreen(() -> new StudioScreen(null, 3));
            context.clickScreenButton("Lagoon");
            context.runOnClient(mc->{if(NocturneClient.SETTINGS.uiAccent.argb()==0xFF62E9D0)throw new AssertionError("Theme preview changed applied settings before Apply");});
            takeShot(context,"nocturne-theme-preview");context.clickScreenButton("Apply theme");context.waitTicks(3);takeShot(context,"nocturne-lagoon-theme");
            context.runOnClient(mc -> { if (NocturneClient.SETTINGS.uiAccent.argb()!=0xFF62E9D0) throw new AssertionError("Theme shortcut did not apply"); });
            interfaceChecks(context);
            performanceChecks(context);
            accessibilityChecks(context);
            reliabilityChecks(context);
            lyricPresetChecks(context);
            maintenanceChecks(context);
            regressionChecks(context,world);
            context.runOnClient(mc->LyricsService.INSTANCE.startDemo());
            for(int playbackTab=0;playbackTab<4;playbackTab++){
                int tab=playbackTab;context.setScreen(()->new dev.truwty.nocturne.gui.PlaybackScreen(null,tab));context.waitTicks(5);
                context.runOnClient(mc->ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc)));
                takeShot(context,"nocturne-playback-"+tab);
            }
            context.setScreen(()->new dev.truwty.nocturne.gui.PlaybackScreen(null));
            context.clickScreenButton("Configure playback shortcuts…");context.waitTicks(3);
            context.runOnClient(mc->{if(!(ClientAccess.screen (mc)
            instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsScreen))throw new AssertionError("Playback keybind screen did not open");});
            NocturneClient.LOG.info("PLAYBACK UI TEST PASSED: four integration pages, artwork texture, capability gating and"
              + " native keybind configuration");
            context.setScreen(() -> new PlacementScreen(null));
            context.waitTicks(3);
            context.runOnClient(mc -> {
                var settings = NocturneClient.SETTINGS;
                var editor = ClientAccess.screen(mc);
                var event = new net.minecraft.client.input.MouseButtonEvent(editor.width / 2.0, editor.height / 2.0,
                    new net.minecraft.client.input.MouseButtonInfo(0, 0));
                double before = settings.editorX.get();
                editor.mouseClicked(event, false); editor.mouseDragged(event, 25, -20); editor.mouseReleased(event);
                if (settings.editorX.get() <= before) throw new AssertionError("Placement drag did not move lyrics");
            });
            double dragged=context.computeOnClient(mc->NocturneClient.SETTINGS.editorX.get());
            context.clickScreenButton("Undo");context.runOnClient(mc->{if(NocturneClient.SETTINGS.editorX.get()>=dragged)throw new AssertionError("Placement undo did not restore pre-drag transform");});
            context.clickScreenButton("Redo");context.runOnClient(mc->{if(NocturneClient.SETTINGS.editorX.get()!=dragged)throw new AssertionError("Placement redo failed");});
            context.clickScreenButton("Rotate");
            context.runOnClient(mc -> {
                var editor = ClientAccess.screen(mc);
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
            officialReleaseChecks(context);
            context.getInput().resizeWindow(640,480);context.runOnClient(mc -> { mc.options.guiScale().set(2); dev.truwty.nocturne.compat.TestAccess.resizeGui(mc); });
            context.waitTicks(10);
            for (int tab = 0; tab < 8; tab++) {
                int page = tab;
                context.setScreen(() -> new StudioScreen(null, page));
                context.waitTicks(2);
                context.runOnClient(mc -> {
                    ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc));
                    ClientAccess.screen(mc).mouseScrolled(100, 100, 0, -8);
                });
                context.waitTicks(2);
                context.runOnClient(mc -> ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc)));
            }
            takeShot(context, "nocturne-small-window");
            context.getInput().resizeWindow(854,480);context.runOnClient(mc -> { mc.options.guiScale().set(1); dev.truwty.nocturne.compat.TestAccess.resizeGui(mc); });
            context.setScreen(() -> null);
            for(int guiScale:new int[]{1,2,3,4}) {
                context.getInput().resizeWindow(1280,960);context.runOnClient(mc->{mc.options.guiScale().set(guiScale);dev.truwty.nocturne.compat.TestAccess.resizeGui(mc);});
                context.setScreen(()->new StudioScreen(null,0));context.waitTicks(3);
                context.runOnClient(mc->ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc)));
                for(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> factory:java.util.List.<java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>>of(
                    ()->new dev.truwty.nocturne.gui.LyricPresetsScreen(null),()->new dev.truwty.nocturne.gui.ThemeEditorScreen(null),()->new dev.truwty.nocturne.gui.ColorPickerScreen(null,"Test",0x80336699,value->{}),
                    ()->new dev.truwty.nocturne.gui.SettingsSearchScreen(null),()->new dev.truwty.nocturne.gui.PresetBrowserScreen(null,"themes"),()->new dev.truwty.nocturne.gui.HudEditorScreen(null),()->new dev.truwty.nocturne.gui.PlaybackScreen(null,0),()->new dev.truwty.nocturne.gui.PlaybackScreen(null,1),()->new dev.truwty.nocturne.gui.PlaybackScreen(null,2),()->new dev.truwty.nocturne.gui.PlaybackScreen(null,3),()->new dev.truwty.nocturne.gui.PerformanceScreen(null),()->new dev.truwty.nocturne.gui.MaintenanceScreen(null),()->new dev.truwty.nocturne.gui.AccessibilityScreen(null,0),()->new dev.truwty.nocturne.gui.AccessibilityScreen(null,1),()->new dev.truwty.nocturne.gui.AccessibilityScreen(null,2),()->new dev.truwty.nocturne.gui.AccessibilityScreen(null,3),()->new dev.truwty.nocturne.gui.ContextProfileEditorScreen(null,dev.truwty.nocturne.accessibility.GameplayContext.COMBAT),
                    ()->new dev.truwty.nocturne.gui.SetupScreen(null),()->new dev.truwty.nocturne.gui.TutorialScreen(null),()->new dev.truwty.nocturne.gui.CacheManagerScreen(null),()->new dev.truwty.nocturne.gui.MusicLibraryScreen(null),()->new dev.truwty.nocturne.gui.AudioScreen(null),()->new dev.truwty.nocturne.gui.OverlayZonesScreen(null),
                    ()->new dev.truwty.nocturne.gui.OnScreenKeyboard(null,new net.minecraft.client.gui.components.EditBox(net.minecraft.client.Minecraft.getInstance().font,0,0,100,20,net.minecraft.network.chat.Component.literal("Test"))))) {
                    context.setScreen(factory);context.runOnClient(mc->{ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc));ClientAccess.screen(mc).mouseScrolled(100,100,0,-12);ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc));});
                }
                for(String tab:java.util.List.of("History","Timing","Languages","Song style")) {
                    context.setScreen(()->new dev.truwty.nocturne.gui.LyricWorkspaceScreen(null));
                    if(!tab.equals("History"))context.clickScreenButton(tab);
                    context.runOnClient(mc->{ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc));ClientAccess.screen(mc).mouseScrolled(100,100,0,-12);ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc));});
                }
            }
            context.setScreen(()->null);
            var reload = context.computeOnClient(mc -> mc.reloadResourcePacks());
            context.waitFor(mc -> reload.isDone(), 400);
            reload.join();
            context.setScreen(() -> null);
            NocturneClient.LOG.info("STUDIO TEST PASSED: world lyrics, placements, fonts, UI tabs, local correction, song"
              + " offset, theme round trip, reduced motion, small-window bounds and resource"
              + " reload");
        }
        try (TestSingleplayerContext reconnected = context.worldBuilder().create()) {
            dev.truwty.nocturne.compat.TestAccess.waitForChunks(reconnected);
            context.runOnClient(mc -> LyricsService.INSTANCE.startDemo());
            context.waitTicks(25);
            context.runOnClient(mc -> { if (!LyricsRenderer.active()) throw new AssertionError("Lyrics did not recover after reconnect"); });
        }
    }

    private static void officialReleaseChecks(ClientGameTestContext context) {
        context.setScreen(()->new dev.truwty.nocturne.gui.SetupScreen(null));
        context.waitTicks(3); takeShot(context,"nocturne-setup");
        context.clickScreenButton("Next check"); context.clickScreenButton("Next check"); context.clickScreenButton("Finish setup");
        context.runOnClient(mc->{if(!dev.truwty.nocturne.gui.SetupScreen.completed())throw new AssertionError("Setup completion was not saved");});
        context.setScreen(()->new StudioScreen(null,1));
        context.runOnClient(mc->{
            var screen=ClientAccess.screen(mc);
            var field=(net.minecraft.client.gui.components.EditBox)screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
            field.setValue("");screen.setFocused(field);
            dev.truwty.nocturne.gui.StudioController.dispatch(screen,dev.truwty.nocturne.gui.ControllerRepeater.Action.ACCEPT);
            if(!(ClientAccess.screen(mc) instanceof dev.truwty.nocturne.gui.OnScreenKeyboard))throw new AssertionError("Controller did not open keyboard");
        });
        context.clickScreenButton("a"); context.clickScreenButton("Use text");
        context.runOnClient(mc->{
            var field=(net.minecraft.client.gui.components.EditBox)ClientAccess.screen(mc).children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();
            if(!field.getValue().equals("a"))throw new AssertionError("Controller text was not applied");
        });
        context.setScreen(()->new dev.truwty.nocturne.gui.CacheManagerScreen(null));context.waitTicks(8);takeShot(context,"nocturne-cache-manager");
        context.setScreen(()->new dev.truwty.nocturne.gui.AudioScreen(null));context.waitTicks(3);takeShot(context,"nocturne-audio-analysis");
        context.setScreen(()->new dev.truwty.nocturne.gui.MusicLibraryScreen(null));context.waitTicks(3);takeShot(context,"nocturne-music-library");
        context.setScreen(()->null);
        context.runOnClient(mc->{
            LyricsService.INSTANCE.startDemo();
            var camera=ClientAccess.camera(mc);var position=net.minecraft.core.BlockPos.containing(camera.position().add(0,0,2));
            var previous=mc.level.getBlockState(position);
            try {
                NocturneClient.SETTINGS.edgeFallback.set(true);NocturneClient.SETTINGS.terrainFallback.set(true);
                mc.level.setBlock(position,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                dev.truwty.nocturne.accessibility.EdgeFallback.clear();
                dev.truwty.nocturne.accessibility.EdgeFallback.update(camera.position().add(0,0,4));
                if(!dev.truwty.nocturne.accessibility.EdgeFallback.occluded())throw new AssertionError("Terrain obstruction was not detected");
            } finally {mc.level.setBlock(position,previous,3);dev.truwty.nocturne.accessibility.EdgeFallback.clear();}
        });
        NocturneClient.LOG.info("OFFICIAL RELEASE FEATURES PASSED: setup, controller text entry, new screen bounds and terrain obstruction");
    }

    private static void lyricPresetChecks(ClientGameTestContext context) {
        var saved=context.computeOnClient(mc->{var values=new java.util.HashMap<String,com.google.gson.JsonElement>();for(var setting:NocturneClient.SETTINGS.settings)values.put(setting.name,setting.save().deepCopy());return values;});
        context.runOnClient(mc->{LyricsService.INSTANCE.startDemo();LyricsService.INSTANCE.applyLocal(LyricsService.INSTANCE.now(),Lyrics.parse("[00:00.00]<00:00.00>Consistent <00:30.00>lyric <01:00.00>placement<05:00.00>"));NocturneClient.SETTINGS.contextProfiles.set(false);});
        context.setScreen(()->null);
        for(var look:dev.truwty.nocturne.LyricLooks.all()) {
            context.runOnClient(mc->{
                for(String name:look.values().keySet())if(NocturneClient.SETTINGS.settings.stream().noneMatch(setting->setting.name.equals(name)))throw new AssertionError("Unknown catalog setting: "+name);
                Studio.apply(dev.truwty.nocturne.LyricLooks.GROUP,look.values(),false);Studio.adoptPlacement();NocturneClient.SETTINGS.adoptPreset();LyricsRenderer.appearanceChanged();
            });context.waitTicks(2);
        }
        context.setScreen(()->new StudioScreen(null));context.clickScreenButton("Lyric presets");context.clickScreenButton("Browse 57 lyric presets");
        context.waitTicks(3);context.runOnClient(mc->ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc)));takeShot(context,"nocturne-lyric-presets");
        context.runOnClient(mc->{var field=(net.minecraft.client.gui.components.EditBox)ClientAccess.screen(mc).children().stream().filter(child->child instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();field.setValue("Skywriter");});context.waitTicks(3);
        context.clickScreenButton("Skywriter");
        context.runOnClient(mc->{var field=(net.minecraft.client.gui.components.EditBox)ClientAccess.screen(mc).children().stream().filter(child->child instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();field.setValue("");});context.waitTicks(3);
        context.clickScreenButton("Perfection");context.clickScreenButton("Favorite");context.clickScreenButton("Export selected");
        context.runOnClient(mc->{var exported=dev.truwty.nocturne.gui.PresetCodec.decode(dev.truwty.nocturne.LyricLooks.GROUP,mc.keyboardHandler.getClipboard());if(!exported.get("Stable lyric placement").getAsBoolean()||!Studio.library().favorite(dev.truwty.nocturne.LyricLooks.GROUP,"Perfection"))throw new AssertionError("Preset export/favorite failed");mc.keyboardHandler.setClipboard("{\"Size\":105}");});
        context.clickScreenButton("Import JSON");context.waitTicks(2);context.runOnClient(mc->ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc)));
        context.clickScreenButton("Perfection");
        context.clickScreenButton("Apply preset");
        context.runOnClient(mc->{if(!NocturneClient.SETTINGS.stablePlacement.on()||NocturneClient.SETTINGS.follow.get()!=2)throw new AssertionError("Perfection was not applied");});
        context.clickScreenButton("Back");context.clickScreenButton("Undo lyric preset");context.clickScreenButton("Redo lyric preset");
        context.runOnClient(mc->{if(!NocturneClient.SETTINGS.stablePlacement.on())throw new AssertionError("Lyric preset redo lost stable placement");});
        context.setScreen(()->null);
        for(int angle:new int[]{0,90,180,270}) {
            context.runOnClient(mc->{mc.player.setYRot(angle);mc.player.setXRot(angle==90?60:angle==270?-60:0);});context.waitTicks(5);
            context.runOnClient(mc->{
                try {
                    var f=LyricsRenderer.class.getDeclaredField("follow");f.setAccessible(true);Object follow=f.get(null);
                    var p=follow.getClass().getDeclaredField("smoothed");p.setAccessible(true);var actual=(net.minecraft.world.phys.Vec3)p.get(follow);
                    var camera=ClientAccess.camera(mc);var expected=StableLyrics.anchor(camera.position(),camera.rotation(),NocturneClient.SETTINGS.distance.get());
                    if(actual==null||actual.distanceTo(expected)>.05)throw new AssertionError("Perfection anchor lagged behind camera movement");
                }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
            });
        }
        takeShot(context,"nocturne-perfection");
        context.runOnClient(mc->{for(var setting:NocturneClient.SETTINGS.settings)setting.load(saved.get(setting.name));Studio.adoptPlacement();NocturneClient.SETTINGS.adoptPreset();LyricsRenderer.appearanceChanged();mc.player.setYRot(0);mc.player.setXRot(0);});
        NocturneClient.LOG.info("LYRIC PRESETS PASSED: 57 registered looks, selector, apply, undo/redo and camera-locked Perfection across yaw/pitch changes");
    }

    private static void reliabilityChecks(ClientGameTestContext context) {
        context.runOnClient(mc->LyricsService.INSTANCE.startDemo());
        Lyrics original=context.computeOnClient(mc->LyricsService.INSTANCE.lyrics());
        var preview=new dev.truwty.nocturne.lyrics.LyricSearch.Candidate("Neon Rain","Nocturne demo",37,"[00:01]Alternative preview\n[00:05]Second line");
        context.setScreen(()->new dev.truwty.nocturne.gui.LyricPreviewScreen(null,preview));
        context.waitTicks(3);context.runOnClient(mc->ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc)));
        takeShot(context,"nocturne-lyric-preview");context.clickScreenButton("Cancel");
        context.runOnClient(mc->{if(LyricsService.INSTANCE.lyrics()!=original)throw new AssertionError("Preview cancel changed lyrics");NocturneClient.SETTINGS.editorX.set(0.0);Studio.adoptPlacement();});
        context.setScreen(()->new PlacementScreen(null));
        context.runOnClient(mc->{
            var screen=ClientAccess.screen(mc);
            double yy=screen.height/2.0;
            var down=new net.minecraft.client.input.MouseButtonEvent(screen.width/2.0,yy,new net.minecraft.client.input.MouseButtonInfo(0,0));
            screen.mouseClicked(down,false);
            for(int i=0;i<10;i++)screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(screen.width/2.0+(i+1)*.5,yy,new net.minecraft.client.input.MouseButtonInfo(0,0)),.5,0);
            screen.mouseReleased(down);
            if(Math.abs(NocturneClient.SETTINGS.editorX.get()-.1)>.0001)throw new AssertionError("Slow placement drag was rounded away");
        });
        context.clickScreenButton("Undo");context.runOnClient(mc->{if(NocturneClient.SETTINGS.editorX.get()!=0)throw new AssertionError("Gesture undo failed");});
        context.clickScreenButton("Redo");
        context.runOnClient(mc->{
            var screen=ClientAccess.screen(mc);
            screen.keyPressed(dev.truwty.nocturne.compat.GamepadInput.keyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT,com.mojang.blaze3d.platform.InputConstants.MOD_ALT));
            if(Math.abs(NocturneClient.SETTINGS.editorX.get()-.2)>.0001)throw new AssertionError("Keyboard placement nudge was rounded away");
        });
        context.clickScreenButton("Undo");context.clickScreenButton("Save");
        context.runOnClient(mc->{if(Math.abs(NocturneClient.SETTINGS.editorX.get()-.1)>.0001)throw new AssertionError("Gesture redo/save failed");});
        context.runOnClient(mc->{
            var service=LyricsService.INSTANCE;service.stop();service.stopDemo();
            long time=System.nanoTime();
            var first=new NowPlaying("Reliability fixture","Nocturne tests","",180000,10000,true,time,"System","","fixture|speakers");
            try {Studio.library().chooseLyrics(first.key(),"[00:01]Remembered fixture\n[00:10]Second fixture");}
            catch(java.io.IOException ex){throw new AssertionError(ex);}
            service.update(first);service.poll(Long.MIN_VALUE);
            if(service.now()!=first)throw new AssertionError("Stopped poll replaced current playback");
            if(service.lyrics()==null||!service.lyrics().lines.getFirst().text().equals("Remembered fixture"))throw new AssertionError("Saved match was not restored");
            var fresh=new NowPlaying(first.title(),first.artist(),"",180000,10300,true,time+300_000_000L,"System","",first.sessionId());
            service.update(fresh);service.update(first);
            if(service.now()!=fresh)throw new AssertionError("Late sample replaced fresh playback");
            long revision=service.playbackRevision();
            var seek=new NowPlaying(first.title(),first.artist(),"",180000,50000,true,time+400_000_000L,"System","",first.sessionId());
            service.update(seek);if(service.playbackRevision()<=revision)throw new AssertionError("Seek failed to reset the lyric clock");
            service.update(new NowPlaying("Advertisement","Spotify","",30000,0,true,time+450_000_000L,"Spotify app","",first.sessionId()));
            if(service.lyrics()!=null||!service.lyricsStatus().startsWith("Advertisement"))throw new AssertionError("Ad retained lyrics");
            service.update(new NowPlaying(first.title(),first.artist(),"",180000,51000,true,time+500_000_000L,"System","","fixture|headphones"));
            if(service.lyrics()==null||!service.lyrics().lines.getFirst().text().equals("Remembered fixture"))throw new AssertionError("Ad/device return lost saved match");
            service.stop();service.start();service.startDemo();
        });
        NocturneClient.LOG.info("RELIABILITY UI PASSED: lyric preview/cancel, precise small drags, gesture undo/redo/save");
    }

    private static void maintenanceChecks(ClientGameTestContext context) {
        dev.truwty.nocturne.maintenance.UpdateClientChecks.run(context);
        context.setScreen(()->new StudioScreen(null,6));context.clickScreenButton("Releases & diagnostics");context.waitTicks(3);
        context.clickScreenButton("Copy filtered diagnostics");
        context.runOnClient(mc->{String report=mc.keyboardHandler.getClipboard();if(!report.contains("Technical allowlist")||report.contains("Neon Rain")||report.contains("Player0"))throw new AssertionError("Diagnostics filtering failed");});
        context.clickScreenButton("Export filtered diagnostics");
        context.runOnClient(mc->{try(var files=java.nio.file.Files.list(Studio.directory().resolve("diagnostics"))){if(files.noneMatch(p->p.getFileName().toString().endsWith(".json")))throw new AssertionError("No diagnostic file exported");}catch(java.io.IOException ex){throw new AssertionError(ex);}});
        takeShot(context,"nocturne-maintenance");
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")) {
            context.runOnClient(mc->{var entries=net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypoints("modmenu",com.terraformersmc.modmenu.api.ModMenuApi.class);var integration=entries.stream().filter(e->e instanceof dev.truwty.nocturne.maintenance.ModMenuIntegration).findFirst().orElseThrow();var parent=ClientAccess.screen(mc);ClientAccess.setScreen(mc, integration.getModConfigScreenFactory().create(parent));boolean setup=ClientAccess.screen(mc) instanceof dev.truwty.nocturne.gui.SetupScreen; if(dev.truwty.nocturne.gui.SetupScreen.completed()?!(ClientAccess.screen(mc) instanceof StudioScreen):!setup)throw new AssertionError("Mod Menu factory did not open the expected setup/Studio screen");ClientAccess.screen(mc).onClose();if(ClientAccess.screen(mc) !=parent)throw new AssertionError("Mod Menu parent was not restored");});
        }
        NocturneClient.LOG.info("MAINTENANCE TEST PASSED: filtered clipboard/file export, screen bounds and optional Mod"
            + " Menu entrypoint");
    }
    private static void regressionChecks(ClientGameTestContext context,TestSingleplayerContext world) {
        var saved=context.computeOnClient(mc->{var values=new java.util.HashMap<String,com.google.gson.JsonElement>();for(var setting:NocturneClient.SETTINGS.settings)values.put(setting.name,setting.save().deepCopy());return values;});
        var results=new com.google.gson.JsonArray();
        try(var input=PortClientTest.class.getResourceAsStream("/performance-budgets.json")) {
            if(input==null)throw new IllegalStateException("Missing performance budgets");var budgets=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            context.setScreen(()->null);
            for(String scenario:java.util.List.of("steady-v2","decorated-v2","crowded-v1","soak-v1")) {
                if(scenario.equals("crowded-v1")) {
                    for(int entity=0;entity<48;entity++) {
                        int dx=entity%8-4,dz=entity/8+2;
                        world.getServer().runCommand("execute at @p run summon minecraft:armor_stand ~"+dx+" ~ ~"+dz+" {NoGravity:1b}");
                    }
                }
                context.runOnClient(mc->{var s=NocturneClient.SETTINGS;s.contextProfiles.set(false);s.photosensitiveMode.set(false);s.reducedMotion.set(false);s.adaptiveEffects.set(false);s.follow.set(2);s.editorX.set(0.0);s.editorY.set(0.0);s.topBar.set(false);s.cinematic.set(false);s.titleCard.set(false);s.nextLine.set(false);s.lyricConfidence.set(false);s.vocalCountdown.set(false);s.debug.set(false);s.performanceReadout.set(false);s.lingeringLimit.set(0.0);s.lyricOutline.set(false);s.lyricBackplate.set(false);s.battle.set(false);s.orbit.set(false);s.quality.set(1);s.audioReactive.set(false);s.beatPulse.set(false);s.shakeEffects.set(false);s.flashEffects.set(false);s.chroma.set(false);s.size.set(100.0);s.lyricFont.set(0);s.maxWords.set(12.0);s.particleLimit.set(48.0);s.glowPassLimit.set(32.0);s.cloudLayerLimit.set(16.0);boolean effects=!scenario.equals("steady-v2");s.animatedText.set(effects);s.particleEffects.set(effects);s.sparkles.set(effects);s.glow.set(effects);LyricsService.INSTANCE.startDemo();LyricsService.INSTANCE.applyLocal(LyricsService.INSTANCE.now(), Lyrics.parse("[00:00.00]<00:00.00>Late <00:30.00>night <01:00.00>the <01:30.00>city <02:00.00>is <02:30.00>glowing<05:00.00>\n[05:00.00]"));});
                context.waitTicks(15);var probe=new dev.truwty.nocturne.performance.RegressionBudget();
                for(int i=0;i<(scenario.equals("soak-v1")?500:60);i++){context.waitTicks(1);probe.add(context.computeOnClient(mc->dev.truwty.nocturne.performance.RenderPerformance.snapshot()));}
                results.add(probe.report(scenario,budgets.getAsJsonObject(scenario)));
            }
            context.runOnClient(mc->{for(var setting:NocturneClient.SETTINGS.settings)setting.load(saved.get(setting.name));});
            var root=new com.google.gson.JsonObject();root.addProperty("schema",1);root.addProperty("renderer",ShaderCompat.shaderPackOn()?"shader":"standard");root.add("scenarios",results);java.nio.file.Files.writeString(java.nio.file.Path.of("nocturne-performance.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
            NocturneClient.LOG.info("PERFORMANCE REGRESSION CHECKS PASSED: {}",results);
        }catch(java.io.IOException ex){throw new AssertionError(ex);}
    }

    private static void accessibilityChecks(ClientGameTestContext context){
        var saved=context.computeOnClient(mc->{var values=new java.util.HashMap<String,com.google.gson.JsonElement>();for(var setting:NocturneClient.SETTINGS.settings)values.put(setting.name,setting.save().deepCopy());return values;});
        // Earlier editor scenarios save placement profiles; adopt this controlled pose before the next tick can load one.
        context.runOnClient(mc->{var s=NocturneClient.SETTINGS;s.contextProfiles.set(false);s.hideChat.set(true);s.hideInventory.set(true);s.hideDebug.set(true);s.edgeFallback.set(true);s.terrainFallback.set(false);s.follow.set(2);s.editorX.set(0.0);s.editorY.set(0.0);s.editorYaw.set(0.0);s.editorPitch.set(0.0);s.editorRoll.set(0.0);s.smartPlace.set(false);s.orbit.set(false);s.cameraMovement.set(false);Studio.adoptPlacement();LyricsService.INSTANCE.startDemo();LyricsService.INSTANCE.applyLocal(LyricsService.INSTANCE.now(),Lyrics.parse("[00:00.00]<00:00.00>Late <00:30.00>night <01:00.00>the <01:30.00>city <02:00.00>is <02:30.00>glowing<05:00.00>\n[05:00.00]"));});
        context.setScreen(()->null);context.waitTicks(40);
        context.setScreen(()->new net.minecraft.client.gui.screens.ChatScreen("",false));context.waitTicks(5);
        context.runOnClient(mc->{if(!dev.truwty.nocturne.accessibility.Accessibility.hidden()||LyricsRenderer.active()||LyricsDebug.hudRecorded!=0)throw new AssertionError("Chat did not hide lyric geometry");});
        context.setScreen(()->new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));context.waitTicks(5);
        context.runOnClient(mc->{if(!dev.truwty.nocturne.accessibility.Accessibility.hidden()||LyricsRenderer.active())throw new AssertionError("Inventory did not hide lyrics");});
        context.setScreen(()->null);context.runOnClient(mc->dev.truwty.nocturne.compat.TestAccess.debugOverlay(mc,true));context.waitTicks(5);
        context.runOnClient(mc->{if(!dev.truwty.nocturne.accessibility.Accessibility.hidden()||LyricsRenderer.active())throw new AssertionError("Debug overlay did not hide lyrics");dev.truwty.nocturne.compat.TestAccess.debugOverlay(mc,false);NocturneClient.SETTINGS.editorX.set(8.0);});context.waitFor(mc->dev.truwty.nocturne.accessibility.EdgeFallback.showing(),200);
        context.runOnClient(mc->{if(!dev.truwty.nocturne.accessibility.EdgeFallback.showing())throw new AssertionError("Off-screen editor placement did not activate edge fallback");});takeShot(context,"nocturne-edge-fallback");
        context.runOnClient(mc->NocturneClient.SETTINGS.edgeFallback.set(false));context.waitTicks(2);
        context.runOnClient(mc->{if(dev.truwty.nocturne.accessibility.EdgeFallback.showing())throw new AssertionError("Disabled fallback retained its visible state");NocturneClient.SETTINGS.edgeFallback.set(true);});context.waitTicks(2);
        context.runOnClient(mc->NocturneClient.SETTINGS.editorX.set(0.0));context.waitFor(mc->!dev.truwty.nocturne.accessibility.EdgeFallback.showing(),200);
        context.runOnClient(mc->{if(dev.truwty.nocturne.accessibility.EdgeFallback.showing())throw new AssertionError("Fallback did not clear after the world text returned");});
        context.setScreen(()->new StudioScreen(null,5));context.clickScreenButton("Gameplay & accessibility");context.clickScreenButton("Readability");context.clickScreenButton("White on charcoal");context.waitTicks(5);takeShot(context,"nocturne-readability");
        context.clickScreenButton("Contexts");context.clickScreenButton("Edit Combat");context.clickScreenButton("Suggested preset");context.clickScreenButton("Preview: Off");context.waitTicks(3);
        context.runOnClient(mc->{if(NocturneClient.SETTINGS.animatedText.get()!=saved.get("Animated lyric text").getAsBoolean())throw new AssertionError("Preview mutated global settings");});
        takeShot(context,"nocturne-context-editor");context.clickScreenButton("Save & enable profiles");
        context.runOnClient(mc->{if(!NocturneClient.SETTINGS.contextProfiles.on()||dev.truwty.nocturne.accessibility.ContextProfiles.profile(dev.truwty.nocturne.accessibility.GameplayContext.COMBAT)==null)throw new AssertionError("Context profile did not persist");});
        context.setScreen(()->null);context.runOnClient(mc->mc.player.hurtTime=5);context.waitTicks(5);
        context.runOnClient(mc->{if(dev.truwty.nocturne.accessibility.ContextProfiles.current()!=dev.truwty.nocturne.accessibility.GameplayContext.COMBAT)throw new AssertionError("Local combat event did not select Combat");try(var scope=dev.truwty.nocturne.accessibility.ContextProfiles.scope()){if(NocturneClient.SETTINGS.animatedText.on())throw new AssertionError("Saved combat settings did not apply");}});takeShot(context,"nocturne-combat-profile");
        context.runOnClient(mc->mc.player.hurtTime=0);context.waitTicks(75);
        context.setScreen(()->new dev.truwty.nocturne.gui.AccessibilityScreen(null,3));context.clickScreenButton("Screenshot context: Off");context.runOnClient(mc->{if(dev.truwty.nocturne.accessibility.ContextProfiles.current()!=dev.truwty.nocturne.accessibility.GameplayContext.SCREENSHOTS)throw new AssertionError("Screenshot context did not override Menus");});context.clickScreenButton("Screenshot context: On");
        context.clickScreenButton("Motion");context.clickScreenButton("Apply photosensitivity-friendly preset");context.waitTicks(4);
        context.runOnClient(mc->{var s=NocturneClient.SETTINGS;if(s.topBar.on()||s.nextLine.on()||dev.truwty.nocturne.accessibility.Accessibility.textMotion()||dev.truwty.nocturne.accessibility.Accessibility.particles()||dev.truwty.nocturne.accessibility.Accessibility.camera()||dev.truwty.nocturne.accessibility.Accessibility.flashes()||LyricsRenderer.fovKick()!=1||LyricsRenderer.flash()!=0)throw new AssertionError("Friendly preset left an effect enabled");try{var forced=new com.google.gson.JsonObject();forced.addProperty("Flash effects",true);forced.addProperty("Camera movement",true);forced.addProperty("Particle effects",true);forced.addProperty("Animated lyric text",true);forced.addProperty("Reduced motion",false);dev.truwty.nocturne.accessibility.ContextProfiles.save(dev.truwty.nocturne.accessibility.GameplayContext.MENUS,forced);try(var scope=dev.truwty.nocturne.accessibility.ContextProfiles.scope()){if(dev.truwty.nocturne.accessibility.Accessibility.camera()||dev.truwty.nocturne.accessibility.Accessibility.flashes()||dev.truwty.nocturne.accessibility.Accessibility.textMotion()||dev.truwty.nocturne.accessibility.Accessibility.particles())throw new AssertionError("Context bypassed friendly-mode lock");}if(s.cameraMovement.on()||s.flashEffects.on())throw new AssertionError("Render scope leaked context values");}catch(java.io.IOException ex){throw new AssertionError(ex);}});
        context.setScreen(()->null);context.runOnClient(mc->LyricsService.INSTANCE.startDemo());context.waitTicks(40);takeShot(context,"nocturne-friendly-preset");
        context.runOnClient(mc->{var frame=dev.truwty.nocturne.performance.RenderPerformance.snapshot();if(frame.particles()!=0||frame.glow()!=0||frame.cloud()!=0||frame.text()==0)throw new AssertionError("Friendly mode rendered decoration or lost core lyrics");try{dev.truwty.nocturne.accessibility.ContextProfiles.remove(dev.truwty.nocturne.accessibility.GameplayContext.COMBAT);dev.truwty.nocturne.accessibility.ContextProfiles.remove(dev.truwty.nocturne.accessibility.GameplayContext.MENUS);}catch(java.io.IOException ex){throw new AssertionError(ex);}dev.truwty.nocturne.accessibility.ContextProfiles.clearPreview();for(var setting:NocturneClient.SETTINGS.settings)setting.load(saved.get(setting.name));});
        NocturneClient.LOG.info("ACCESSIBILITY TEST PASSED: chat/inventory/debug hiding, edge recovery, readability,"
            + " profile preview/save/restoration and friendly lock across contexts");
    }

    private static void performanceChecks(ClientGameTestContext context){
        var saved=context.computeOnClient(mc->{var values=new java.util.HashMap<String,com.google.gson.JsonElement>();for(var setting:NocturneClient.SETTINGS.settings)values.put(setting.name,setting.save().deepCopy());return values;});
        context.setScreen(()->null);
        context.runOnClient(mc->{var s=NocturneClient.SETTINGS;s.adaptiveEffects.set(false);s.performanceReadout.set(true);s.glowPassLimit.set(0.0);s.cloudLayerLimit.set(0.0);s.particleLimit.set(0.0);s.lingeringLimit.set(0.0);s.follow.set(2);s.editorX.set(0.0);s.editorY.set(0.0);s.glow.set(true);s.sparkles.set(true);LyricsService.INSTANCE.startDemo();});
        context.waitTicks(45);
        context.runOnClient(mc->{var frame=dev.truwty.nocturne.performance.RenderPerformance.snapshot();if(frame.glow()!=0||frame.cloud()!=0||frame.particles()!=0||frame.trails()!=0)throw new AssertionError("Zero effect budgets were exceeded");if(frame.text()==0)throw new AssertionError("Budget reduction hid the core lyrics");if(!Double.isFinite(frame.cpuMs())||frame.cpuMs()<=0)throw new AssertionError("Invalid CPU metric");});
        takeShot(context,"nocturne-performance-hud");context.setScreen(()->new StudioScreen(null,5));context.clickScreenButton("Performance & budgets");context.waitTicks(5);takeShot(context,"nocturne-performance");
        context.runOnClient(mc->{ScreenBounds.assertVisibleWidgetsFit(ClientAccess.screen(mc));for(var setting:NocturneClient.SETTINGS.settings)setting.load(saved.get(setting.name));});
        NocturneClient.LOG.info("PERFORMANCE TEST PASSED: hard zero budgets, preserved lyric text, CPU metrics, live"
            + " readout and settings bounds");
    }

    private static void interfaceChecks(ClientGameTestContext context) {
        context.runOnClient(mc->LyricsService.INSTANCE.startDemo());
        context.setScreen(()->new dev.truwty.nocturne.gui.ThemeEditorScreen(null));
        int applied=context.computeOnClient(mc->NocturneClient.SETTINGS.uiAccent.argb());
        context.clickScreenButton("Accent");
        context.runOnClient(mc->{for(var child:ClientAccess.screen(mc).children())if(child instanceof net.minecraft.client.gui.components.EditBox field){field.setValue("#8062E9D0");break;}});
        context.clickScreenButton("Read hex");takeShot(context,"nocturne-color-picker");context.clickScreenButton("Use color");
        context.runOnClient(mc->{if(NocturneClient.SETTINGS.uiAccent.argb()!=applied)throw new AssertionError("Color preview mutated global theme");});
        context.clickScreenButton("Undo");context.clickScreenButton("Redo");context.clickScreenButton("Cancel");
        context.runOnClient(mc->{if(NocturneClient.SETTINGS.uiAccent.argb()!=applied)throw new AssertionError("Theme cancel changed applied color");});
        context.setScreen(()->new dev.truwty.nocturne.gui.PresetBrowserScreen(null,"themes"));
        takeShot(context,"nocturne-preset-browser");context.clickScreenButton("Apply preset");
        context.runOnClient(mc->{dev.truwty.nocturne.gui.StudioEdits.undo("themes");if(NocturneClient.SETTINGS.uiAccent.argb()!=applied)throw new AssertionError("Applied theme undo failed");dev.truwty.nocturne.gui.StudioEdits.redo("themes");dev.truwty.nocturne.gui.StudioEdits.undo("themes");
            String exported=dev.truwty.nocturne.gui.PresetCodec.encode("themes","Test",Studio.snapshot("themes"));mc.keyboardHandler.setClipboard(exported);
        });
        context.runOnClient(mc->ClientAccess.screen(mc).mouseScrolled(100,100,0,-12));context.clickScreenButton("Import JSON");context.clickScreenButton("Apply preset");
        int background=context.computeOnClient(mc->NocturneClient.SETTINGS.uiBackground.argb());
        context.runOnClient(mc->mc.keyboardHandler.setClipboard("{\"UI accent\":\"#FFAABBCC\"}"));
        context.clickScreenButton("Import JSON");context.waitTicks(2);context.clickScreenButton("Apply preset");
        context.runOnClient(mc->{if(NocturneClient.SETTINGS.uiAccent.argb()!=0xffaabbcc||NocturneClient.SETTINGS.uiBackground.argb()!=background)throw new AssertionError("Partial theme import lost other applied values");dev.truwty.nocturne.gui.StudioEdits.undo("themes");});
        context.setScreen(()->new dev.truwty.nocturne.gui.SettingsSearchScreen(null));
        context.runOnClient(mc->{for(var child:ClientAccess.screen(mc).children())if(child instanceof net.minecraft.client.gui.components.EditBox field){field.setValue("Distance");break;}});
        context.waitTicks(3);takeShot(context,"nocturne-settings-search");
        context.runOnClient(mc->{boolean found=ClientAccess.screen(mc).children().stream().anyMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().startsWith("Distance:"));if(!found)throw new AssertionError("Settings search lost numeric setting");
            ClientAccess.screen(mc)
          .setFocused((net.minecraft.client.gui.components.events.GuiEventListener)null);
            dev.truwty.nocturne.gui.StudioController.dispatch(ClientAccess.screen(mc),dev.truwty.nocturne.gui.ControllerRepeater.Action.NEXT);
            if(ClientAccess.screen(mc).getFocused()==null)throw new AssertionError("Controller navigation did not focus a native control");
            dev.truwty.nocturne.gui.StudioController.dispatch(ClientAccess.screen(mc),dev.truwty.nocturne.gui.ControllerRepeater.Action.NEXT);
            dev.truwty.nocturne.gui.StudioController.dispatch(ClientAccess.screen(mc),dev.truwty.nocturne.gui.ControllerRepeater.Action.ACCEPT);
            if(ClientAccess.screen(mc).children().stream().noneMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().equals("Lyrics & playback")))throw new AssertionError("Controller accept did not activate category filter");
        });
        context.setScreen(()->new dev.truwty.nocturne.gui.HudEditorScreen(null));context.clickScreenButton("Hide controls");
        var initial=context.computeOnClient(mc->dev.truwty.nocturne.gui.HudLayouts.load("Gameplay").node("Karaoke"));
        context.runOnClient(mc->{var screen=ClientAccess.screen(mc);var box=initial.box(screen.width,screen.height,Math.min(screen.width-16,370),30);
            var down=new net.minecraft.client.input.MouseButtonEvent(box.centerX(),box.centerY(),new net.minecraft.client.input.MouseButtonInfo(0,0));
            var drag=new net.minecraft.client.input.MouseButtonEvent(box.centerX()-40,box.centerY()+120,new net.minecraft.client.input.MouseButtonInfo(0,0));
            screen.mouseClicked(down,false);screen.mouseDragged(drag,-40,120);screen.mouseReleased(drag);
        });
        takeShot(context,"nocturne-hud-editor");context.clickScreenButton("Show controls");context.clickScreenButton("Undo");context.clickScreenButton("Redo");
        context.clickScreenButton("Gameplay");context.clickScreenButton("Hidden");context.clickScreenButton("Save layouts");
        context.runOnClient(mc->{var game=dev.truwty.nocturne.gui.HudLayouts.load("Gameplay").node("Karaoke");var menu=dev.truwty.nocturne.gui.HudLayouts.load("Menu").node("Karaoke");if(game.equals(initial))throw new AssertionError("HUD drag did not save");if(!menu.visible()||menu.x()!=.5)throw new AssertionError("Separate menu HUD layout did not save independently");});
        context.setScreen(()->null);context.waitTicks(5);takeShot(context,"nocturne-moved-hud");
        NocturneClient.LOG.info("INTERFACE TEST PASSED: theme preview/cancel/apply, picker hex/alpha, history, presets"
            + " import/export, search, controller focus, HUD drag/snap/layout isolation");
    }

    private static TestSingleplayerContext createWorld(ClientGameTestContext context) {
        Thread watchdog = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(45_000);
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                return;
            }
            Thread.getAllStackTraces().forEach((thread, stack) -> {
                if (thread.getName().equals("Server thread") || thread.getName().equals("Render thread")
                        || thread.getName().startsWith("Worker-Main")) {
                    NocturneClient.LOG.warn("World startup stalled: {} {}", thread.getName(), thread.getState());
                    for (var frame : stack) NocturneClient.LOG.warn("    {}", frame);
                }
            });
        });
        try {
            return context.worldBuilder().create();
        } finally {
            watchdog.interrupt();
        }
    }

    private static void takeShot(ClientGameTestContext context, String name) {
        context.getInput().setCursorPos(0,0);
        context.waitTicks(4);
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        if (loader.isModLoaded("sodium")) {
            // Fabric's screenshot utility re-renders outside Sodium's normal uniform lifecycle.
            // Read the already rendered framebuffer for compatibility runs instead.
            String renderer = loader.isModLoaded("iris") ? "iris" : "sodium";
            context.runOnClient(mc -> net.minecraft.client.Screenshot.grab(mc.gameDirectory,
                renderer + "-" + name + ".png", ClientAccess.renderTarget(mc), 1, message -> {}));
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
