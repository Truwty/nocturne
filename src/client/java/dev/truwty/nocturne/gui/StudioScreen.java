package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.LyricsSettings;
import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.Studio;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.lyrics.LyricsService;
import dev.truwty.nocturne.lyrics.NowPlaying;
import dev.truwty.nocturne.setting.BoolSetting;
import dev.truwty.nocturne.setting.ModeSetting;
import dev.truwty.nocturne.setting.NumberSetting;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class StudioScreen extends Screen {
    private static final String[] TABS = {"Player", "Lyrics", "Apps", "Theme", "Place", "Motion", "Health", "Lyric presets"};
    private final Screen parent;
    private final String platformDescription = "Minecraft " + NocturneClient.minecraftVersion() + " / "
        + dev.truwty.nocturne.platform.ModPlatform.displayName() + " / client only";
    private int page, resultPage, x, y, w, h, body, row, scroll;
    private String queryValue, fileValue = "", presetValue = "";
    private String notice = "";
    private List<dev.truwty.nocturne.lyrics.LyricSearch.Candidate> shownCandidates = List.of();
    private List<dev.truwty.nocturne.lyrics.SystemMediaSource.Player> shownPlayers = List.of();
    private EditBox query, file, name;
    private boolean seeking;
    private List<Button> transport = List.of();
    private SeekSlider seek;
    private Button recovery;
    private dev.truwty.nocturne.lyrics.LyricsRecovery.Diagnosis recoveryDiagnosis;

    public StudioScreen(Screen parent) { super(Component.literal("Nocturne Studio")); this.parent = parent; }
    public StudioScreen(Screen parent, int page) { this(parent); this.page = page; }
    private LyricsSettings s() { return NocturneClient.SETTINGS; }
    private LyricsService service() { return LyricsService.INSTANCE; }
    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        w = Math.min(650, width - 16); h = Math.min(380, height - 16); x = (width - w) / 2; y = (height - h) / 2;
        int tabW = (w - 24) / 4;
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            Button tab = button(x + 12 + (i % 4) * tabW, y + 39 + (i / 4) * 24, tabW - 3, TABS[i], () -> { page = index; scroll = 0; notice = ""; rebuildWidgets(); });
            if (page == i) tab.setMessage(Draw.comp("• " + TABS[i]).withStyle(style -> style.withColor(s().uiAccent.argb() & 0xFFFFFF)));
        }
        body = y + 96 - scroll; row = body;
        int contentStart = children().size(); seek = null; transport = List.of(); query = file = name = null;
        switch (page) {
            case 0 -> player(); case 1 -> lyrics(); case 2 -> apps(); case 3 -> theme();
            case 4 -> placement(); case 5 -> motion(); case 7 -> lyricPresets(); default -> health();
        }
        for (var child : children().subList(contentStart, children().size())) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.visible = widget.getY() >= y + 92 && widget.getY() + widget.getHeight() <= y + h - 48;
        }
        button(x + 12, y + h - 29, 120, "Search settings", () -> ClientAccess.setScreen(minecraft, new SettingsSearchScreen(this)));
        button(x + w - 76, y + h - 29, 64, "Done", this::onClose);
        recovery=null;
        if (page == 1) button(x + 140, y + h - 29, Math.max(40,w - 224), "Lyric workspace", () -> ClientAccess.setScreen(minecraft, new LyricWorkspaceScreen(this)));
        if (page == 0) recovery=button(x + 140, y + h - 29, Math.max(40, w - 224), "Restore visible lyrics", this::repair);
        if (notice.isBlank() && !Studio.library().problem().isBlank()) notice = Studio.library().problem();
        tick();
    }

    private Button button(int bx, int by, int width, String text, Runnable action) {
        return addRenderableWidget(StudioButton.create(Draw.comp(text), b -> attempt(action)).bounds(bx, by, width, 20).build());
    }
    private void attempt(Runnable action) { try { action.run(); } catch (RuntimeException ex) { notice = ex.getMessage(); NocturneClient.LOG.debug("Studio action failed", ex); } }
    private void io(IoAction action) { try { action.run(); notice = "Saved"; } catch (Exception ex) { notice = ex.getMessage(); NocturneClient.LOG.debug("Studio save/import failed", ex); } }
    @FunctionalInterface private interface IoAction { void run() throws Exception; }
    private EditBox field(int by, int width, String hint) {
        EditBox box = new EditBox(font, x + 16, by, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint)); box.setMaxLength(1024); return addRenderableWidget(box);
    }
    private void toggle(BoolSetting setting) {
        button(x + 16, row, w - 32, setting.name + ": " + (setting.on() ? "On" : "Off"), () -> { setting.toggle(); s().save(); rebuildWidgets(); }); row += 24;
    }
    private void mode(ModeSetting setting) {
        button(x + 16, row, w - 32, setting.name + ": " + setting.current(), () -> { setting.cycle(1); s().save(); rebuildWidgets(); }); row += 24;
    }
    private void number(NumberSetting setting) {
        addRenderableWidget(new StudioSlider(x + 16, row, w - 32, 20, Component.empty(), setting.fraction()) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.literal(setting.name + ": " + setting.display())); }
            private com.google.gson.JsonObject before;
            @Override protected void applyValue() {
                if(page==4 && before==null)before=Studio.snapshot("placement");
                setting.setFraction(value);updateMessage();
            }
            private void commit() {
                if(before!=null) {StudioEdits.record("placement",before,Studio.snapshot("placement"));before=null;}
                s().save();
            }
            @Override public void onRelease(MouseButtonEvent event) {super.onRelease(event);commit();}
            @Override public boolean keyPressed(KeyEvent event) {
                boolean handled=super.keyPressed(event);if(handled && before!=null)commit();return handled;
            }
        }); row += 24;
    }

    private void lyricPresets() {
        button(x+16,row,w-32,"Browse 57 lyric presets",()->ClientAccess.setScreen(minecraft,new LyricPresetsScreen(this)));row+=28;
        button(x+16,row,w-32,"Apply Perfection",()->io(()->{dev.truwty.nocturne.LyricLooks.apply(dev.truwty.nocturne.LyricLooks.named("Perfection").values());notice="Perfection applied: stable placement and uniform lettering";}));row+=28;
        button(x+16,row,w-32,service().demoPlaying()?"Stop visual demo":"Start visual demo",()->{if(service().demoPlaying())service().stopDemo();else service().startDemo();rebuildWidgets();});row+=28;
        button(x+16,row,(w-36)/2,"Undo lyric preset",()->StudioEdits.undo(dev.truwty.nocturne.LyricLooks.GROUP));
        button(x+20+(w-36)/2,row,(w-36)/2,"Redo lyric preset",()->StudioEdits.redo(dev.truwty.nocturne.LyricLooks.GROUP));
    }

    private void player() {
        row += 32;
        int bw = (w - 48) / 5;
        transport = List.of(
            button(x + 16, row, bw, "Previous", () -> dev.truwty.nocturne.playback.Playback.command("previous", 0)),
            button(x + 20 + bw, row, bw, "-10s", () -> relativeSeek(-10000)),
            button(x + 24 + bw * 2, row, bw, "Play / pause", () -> dev.truwty.nocturne.playback.Playback.command("toggle", 0)),
            button(x + 28 + bw * 3, row, bw, "+10s", () -> relativeSeek(10000)),
            button(x + 32 + bw * 4, row, bw, "Next", () -> dev.truwty.nocturne.playback.Playback.command("next", 0)));
        row += 24; seek = addRenderableWidget(new SeekSlider(x + 16, row, w - 32)); row += 24;
        int third = (w - 40) / 3;
        button(x + 16, row, third, "Lyrics: " + (s().isEnabled() ? "On" : "Off"), () -> { s().setEnabled(!s().isEnabled()); s().save(); rebuildWidgets(); });
        button(x + 20 + third, row, third, service().demoPlaying() ? "Stop demo" : "Visual demo", () -> { if (service().demoPlaying()) service().stopDemo(); else service().startDemo(); rebuildWidgets(); });
        button(x + 24 + third * 2, row, third, "Retry lyrics", () -> { service().refetch(); notice = "Looking for lyrics again"; });
        row += 26;button(x+16,row,w-32,"Playback & integrations",()->ClientAccess.setScreen(minecraft, new PlaybackScreen(this)));
        row += 26;button(x+16,row,w-32,"Browse music library",()->ClientAccess.setScreen(minecraft, new MusicLibraryScreen(this)));
        row += 26;button(x+16,row,w-32,"Music spectrum & beat effects",()->ClientAccess.setScreen(minecraft, new AudioScreen(this)));
        row += 26;button(x+16,row,w-32,"Restore visible lyrics",this::restoreVisible);
    }
    private void repair() {
        var diagnosis=recoveryDiagnosis==null?dev.truwty.nocturne.lyrics.LyricsRecovery.current():recoveryDiagnosis;
        switch(diagnosis.action()) {
            case ENABLE -> {s().setEnabled(true);s().save();}
            case RETRY -> service().refetch();
            case RESYNC -> service().resynchronize();
            case RECONNECT -> {if(dev.truwty.nocturne.playback.PlaybackPreferences.cloud())ClientAccess.setScreen(minecraft, new PlaybackScreen(this,2));else{service().system.stop();service().system.start();}}
            case SEARCH -> {page=1;scroll=0;rebuildWidgets();if(queryValue!=null&&!queryValue.isBlank())service().search(queryValue);}
            case TIMING -> ClientAccess.setScreen(minecraft,new LyricWorkspaceScreen(this,1));
            case SHOW -> restoreVisible();
            case NONE -> {}
        }
        notice=diagnosis.detail();
    }
    private void restoreVisible() {
        s().setEnabled(true); s().follow.set(2); Studio.tick(); s().shaderMode.set(1); s().faceYou.set(true); s().floor.set(false); s().seeThrough.set(true);
        s().distance.set(3.5); s().size.set(100.0); s().editorX.set(0.0); s().editorY.set(0.0);
        s().editorYaw.set(0.0); s().editorPitch.set(0.0); s().editorRoll.set(0.0); s().save(); io(Studio::savePlacement);
        notice = "Lyrics follow your view; compatible renderer enabled"; rebuildWidgets();
    }
    private void relativeSeek(long delta) { NowPlaying track = dev.truwty.nocturne.playback.Playback.current(); if (track != null) dev.truwty.nocturne.playback.Playback.command("seek", track.positionMs() + delta); }

    private void lyrics() {
        query = field(row, w - 116, "Artist and title"); query.setMaxLength(200);
        NowPlaying track = dev.truwty.nocturne.playback.Playback.current(); if (queryValue == null && track != null) queryValue = track.artist() + " " + track.title();
        query.setValue(queryValue == null ? "" : queryValue); query.setResponder(value -> queryValue = value);
        button(x + w - 92, row, 76, "Search", () -> { service().search(query.getValue()); resultPage = 0; }); row += 29;
        var candidates = service().candidates(); shownCandidates = candidates;
        for (int i = resultPage * 3; i < Math.min(candidates.size(), resultPage * 3 + 3); i++) {
            var candidate = candidates.get(i);
            button(x + 16, row, w - 32, font.plainSubstrByWidth(candidate.label(), w - 50), () -> ClientAccess.setScreen(minecraft, new LyricPreviewScreen(this,candidate))); row += 23;
        }
        row = body + 101;
        button(x + 16, row, 60, "Back", () -> { resultPage = Math.max(0, resultPage - 1); rebuildWidgets(); });
        button(x + 80, row, 60, "More", () -> { if ((resultPage + 1) * 3 < service().candidates().size()) resultPage++; rebuildWidgets(); });
        button(x + 144, row, w - 160, "Use automatic lyrics again", () -> io(() -> service().clearLocal())); row += 29;
        file = field(row, w - 116, "Full path to UTF-8 .lrc or Lyricsfile .yaml"); file.setValue(fileValue); file.setResponder(value -> fileValue = value);
        button(x + w - 92, row, 76, "Import", () -> io(() -> service().importLocal(Path.of(file.getValue())))); row += 29;
        button(x + 16, row, 90, "Earlier 0.1s", () -> offset(100));
        button(x + 110, row, 90, "Later 0.1s", () -> offset(-100));
        button(x + 204, row, 90, "Reset offset", () -> io(() -> { NowPlaying now = service().now(); if (now != null) Studio.library().offset(now.key(), 0); }));
    }
    private void offset(int delta) { io(() -> { NowPlaying now = service().now(); if (now == null) throw new IllegalStateException("Play a song first"); Studio.library().offset(now.key(), Studio.songOffset(now) + delta); }); }

    private void apps() {
        button(x+16,row,w-32,"Player priorities & integrations",()->ClientAccess.setScreen(minecraft, new PlaybackScreen(this,1)));row+=26;
        button(x + 16, row, w - 32, "Auto: playing first, then your player priorities", () -> select("")); row += 26;
        var players = service().system.players(); shownPlayers = players;
        for (var app : players) {
            button(x + 16, row, w - 32, (service().system.selected().equals(app.id()) ? "Pinned: " : "Pin: ") + font.plainSubstrByWidth(app.label(), w - 85), () -> select(app.id())); row += 42;
        }
        button(x + 16, row + 4, w - 32, "Reconnect desktop media", () -> { service().system.stop(); service().system.start(); });
    }
    private void select(String id) { io(() -> { dev.truwty.nocturne.playback.Playback.route(false); Studio.library().player(id); service().system.select(id); rebuildWidgets(); }); }

    private void theme() {
        button(x+16,row,w-32,"Live theme editor",()->ClientAccess.setScreen(minecraft, new ThemeEditorScreen(this)));row+=26;
        int paletteWidth=(w-40)/3;String[] palettes={"Midnight","Lagoon","Ember"};
        for(int i=0;i<palettes.length;i++){String palette=palettes[i];button(x+16+i*(paletteWidth+4),row,paletteWidth,palette,()->ClientAccess.setScreen(minecraft, new ThemeEditorScreen(this,PresetBrowserScreen.palette(palette))));}row+=26;
        button(x+16,row,(w-36)/2,"Undo theme",()->{StudioEdits.undo("themes");rebuildWidgets();});
        button(x+20+(w-36)/2,row,(w-36)/2,"Redo theme",()->{StudioEdits.redo("themes");rebuildWidgets();});row+=26;
        button(x+16,row,w-32,"Browse theme presets",()->ClientAccess.setScreen(minecraft, new PresetBrowserScreen(this,"themes")));row+=26;
        button(x+16,row,w-32,"Search interface settings",()->ClientAccess.setScreen(minecraft, new SettingsSearchScreen(this)));row+=26;
        toggle(s().controllerNavigation);
    }
    private void placement() {
        mode(s().follow);
        button(x+16,row,w-32,"Edit in world: drag, rotate and resize",()->ClientAccess.setScreen(minecraft, new PlacementScreen(this)));row+=24;
        button(x+16,row,w-32,"Drag HUD / gameplay & menu layouts",()->ClientAccess.setScreen(minecraft, new HudEditorScreen(this)));row+=27;
        number(s().distance);number(s().size);
        button(x+16,row,(w-36)/2,"Undo placement",()->StudioEdits.undo("placement"));
        button(x+20+(w-36)/2,row,(w-36)/2,"Redo placement",()->StudioEdits.redo("placement"));row+=24;
        button(x+16,row,w-32,"Browse placement presets",()->ClientAccess.setScreen(minecraft, new PresetBrowserScreen(this,"placement:"+s().follow.get())));row+=24;
        button(x+16,row,w-32,"Browse HUD presets",()->ClientAccess.setScreen(minecraft, new PresetBrowserScreen(this,"hudLayouts")));row+=24;
        button(x+16,row,w-32,"Reserve space for other overlays",()->ClientAccess.setScreen(minecraft, new OverlayZonesScreen(this)));
    }
    private void motion() {
        // Split into compact columns so keyboard-accessible controls remain on screen.
        int left = x, widthBefore = w; w = (widthBefore - 16) / 2;
        toggle(s().reducedMotion); toggle(s().flashEffects); toggle(s().shakeEffects); toggle(s().highContrast);
        number(s().transition); number(s().size);button(x+16,row,w-32,"Gameplay & accessibility",()->ClientAccess.setScreen(minecraft, new AccessibilityScreen(this))); x = left + w + 16; row = body;
        toggle(s().audioReactive); number(s().audioGain); mode(s().quality); number(s().particleLimit); number(s().renderDistance);
        button(x + 16, row, w - 32, "Apply lightweight preset", () -> { s().quality.set(0); s().particleLimit.set(20.0); s().glow.set(false); s().chroma.set(false); s().cloudDrift.set(false); s().save(); rebuildWidgets(); });
        row+=26;button(x+16,row,w-32,"Performance & budgets",()->ClientAccess.setScreen(minecraft, new PerformanceScreen(this)));
        x = left; w = widthBefore;
    }
    private void health() {
        button(x + 16, row + 115, w - 32, "Copy diagnostics", () -> { minecraft.keyboardHandler.setClipboard(dev.truwty.nocturne.lyrics.LyricsDebug.report()); notice = "Diagnostics copied"; });
        row=body+141; number(s().cacheDays);
        button(x+16,body+169,w-32,"Resynchronize playback",()->service().resynchronize());
        button(x+16,body+195,w-32,"Releases & diagnostics",()->ClientAccess.setScreen(minecraft, new MaintenanceScreen(this)));
        button(x+16,body+221,w-32,"Manage lyric & artwork downloads",()->ClientAccess.setScreen(minecraft, new CacheManagerScreen(this)));
        button(x+16,body+247,w-32,"Help & setup",()->ClientAccess.setScreen(minecraft, new TutorialScreen(this)));
    }

    @Override public void tick() {
        if(recovery!=null) {recoveryDiagnosis=dev.truwty.nocturne.lyrics.LyricsRecovery.current();recovery.setMessage(Draw.comp(recoveryDiagnosis.button()));recovery.active=recoveryDiagnosis.action()!=dev.truwty.nocturne.lyrics.LyricsRecovery.Action.NONE;}
        if (page == 1 && shownCandidates != service().candidates()) rebuildWidgets();
        if (page == 2 && !shownPlayers.equals(service().system.players())) rebuildWidgets();
        if (transport.size() == 5) {
            String[] actions = {"previous", "seek", "toggle", "seek", "next"};
            for (int i = 0; i < 5; i++) transport.get(i).active = dev.truwty.nocturne.playback.Playback.can(actions[i]) && !service().demoPlaying();
        }
        if (seek != null) { seek.active = dev.truwty.nocturne.playback.Playback.can("seek") && !service().demoPlaying(); seek.refresh(); }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { g.fill(0, 0, width, height, 0x88000000); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Draw.round(g, x, y, w, h, 12, Draw.alpha(s().uiBackground.argb(), s().uiOpacity.f()/100));
        Draw.text(g, font, "Nocturne", x + 16, y + 13, s().uiText.argb(), 1.25F, false);
        Draw.text(g, font, TABS[page] + " / Studio", x + 112, y + 16, Draw.alpha(s().uiText.argb(), .5f), false);
        Draw.rect(g, x + 16, y + 32, w - 32, 1, Draw.alpha(s().uiAccent.argb(), .3f));
        super.extractRenderState(g, mx, my, delta);
        if (maxScroll() > 0) {
            int top = y + 92, available = h - 140;
            g.fill(x + w - 8, top, x + w - 6, top + available, 0x33444455);
            int thumb = Math.max(12, available * available / (available + maxScroll()));
            int thumbY = top + (available - thumb) * scroll / maxScroll();
            g.fill(x + w - 8, thumbY, x + w - 6, thumbY + thumb, s().uiAccent.argb());
            Draw.text(g, font, "Scroll ↓", x + 140, y + h - 23, s().uiText.argb(), false);
        }
        g.enableScissor(x + 12, y + 92, x + w - 12, y + h - 48);
        NowPlaying track = dev.truwty.nocturne.playback.Playback.current();
        if (page == 0) {
            AlbumArtwork.draw(g,x+w-45,body,28,1);
            text(g, track == null ? "Start music in your desktop player" : font.plainSubstrByWidth(track.title(),w-80), body);
            text(g, track == null ? service().system.status() : track.artist() + " · " + track.source(), body + 18);
        } else if (page == 1) {
            text(g, track == null ? "No current song" : "Song offset: " + Math.round(Studio.songOffset(track)) + " ms · " + service().lyricsStatus(), body + 209);
            text(g, service().searchStatus(), body + 223);
        } else if (page == 7) {
            text(g,"Choose placement, animation, lettering and effects as one look.",body+116);
            text(g,"Perfection keeps a fixed camera-relative anchor while you move.",body+134);
            text(g,"Preset changes preserve player connections, timing and hiding rules.",body+152);
        } else if (page == 2) {
            int appY=body+75;
            for(var app:shownPlayers) {text(g,app.diagnostic(),appY);appY+=42;}
        } else if (page == 5) text(g, service().system.audioStatus() + " · Audio is local; no recording", body + 157);
        else if (page == 6) {
            int line = body;
            for (String value : List.of(platformDescription, service().system.status(), service().lyricsStatus(),
                service().playbackStatus(), s().configNotice().isBlank()?"Config schema 1 / healthy":s().configNotice(), "Pinned app: " + (service().system.selected().isEmpty() ? "Auto" : service().system.selected()), "Shader packs: compatibility depends on the pack")) { text(g, value, line); line += 15; }
        }
        if ((page == 3 || page == 4) && name != null) text(g, "Saved: " + String.join(", ", Studio.library().presets(page == 3 ? "themes" : "placement:" + s().follow.get())), body + 211);
        g.disableScissor();
        if (!notice.isBlank()) text(g, notice, y + h - 44);
        else if (page == 0) text(g,(recoveryDiagnosis==null?"Checking playback":recoveryDiagnosis.summary())+" · "+service().lyricsStatus(),y+h-44);
    }
    private void text(GuiGraphicsExtractor g, String value, int by) { Draw.text(g, font, font.plainSubstrByWidth(value, w - 32), x + 16, by, s().uiText.argb(), false); }
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP
        ||event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN){mouseScrolled(0,0,0,event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP ?5:-5);return true;}
        if(!(getFocused() instanceof EditBox)&&(event.modifiers()&com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL)!=0&&(page==3||page==4)){
            String group=page==3?"themes":"placement";
            if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_Z){if((event.modifiers()&com.mojang.blaze3d.platform.InputConstants.MOD_SHIFT)!=0)StudioEdits.redo(group);else StudioEdits.undo(group);rebuildWidgets();return true;}
            if(event.key()==com.mojang.blaze3d.platform.InputConstants.KEY_Y){StudioEdits.redo(group);rebuildWidgets();return true;}
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.clamp(scroll - (int)Math.round(sy * 24), 0, maxScroll());
        rebuildWidgets(); return true;
    }
    private int maxScroll() {
        int contentHeight = page == 2 ? service().system.players().size() * 42 + 90 : page == 0 ? 212 : page == 1 ? 244 : page == 4 ? 270 : page == 3 ? 230 : page == 6 ? 275 : 204;
        return Math.max(0, contentHeight - (h - 140));
    }
    @Override public void onClose() { s().save(); io(Studio::savePlacement); ClientAccess.setScreen(minecraft, parent); }

    private final class SeekSlider extends StudioSlider {
        SeekSlider(int bx, int by, int width) { super(bx, by, width, 20, Component.literal("No track"), 0); }
        void refresh() {
            NowPlaying track = dev.truwty.nocturne.playback.Playback.current();
            if (track != null && !seeking) { value = track.durationMs() > 0 ? (double)track.positionMs() / track.durationMs() : 0; updateMessage(); }
        }
        @Override protected void updateMessage() { NowPlaying track = dev.truwty.nocturne.playback.Playback.current(); setMessage(Component.literal(track == null ? "No track" : Math.round(value * track.durationMs() / 1000) + " / " + track.durationMs() / 1000 + " s")); }
        @Override protected void applyValue() { seeking = true; updateMessage(); }
        private void commit() { NowPlaying track = dev.truwty.nocturne.playback.Playback.current(); if (track != null) dev.truwty.nocturne.playback.Playback.command("seek", Math.round(value * track.durationMs())); seeking = false; }
        @Override public void onRelease(MouseButtonEvent event) { super.onRelease(event); commit(); }
        @Override public boolean keyPressed(KeyEvent event) { boolean handled = super.keyPressed(event); if (handled && seeking) commit(); return handled; }
    }
}
