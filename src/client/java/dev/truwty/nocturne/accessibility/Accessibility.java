package dev.truwty.nocturne.accessibility;

import com.google.gson.*;

import dev.truwty.nocturne.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.*;

public final class Accessibility {
    private static Set<String> screens;
    private static String lastScreen = "";

    private Accessibility() {}

    public static boolean photosensitive() {
        return NocturneClient.SETTINGS.photosensitiveMode.on();
    }

    public static boolean textMotion() {
        var s = NocturneClient.SETTINGS;
        return s.animatedText.on() && !s.reducedMotion.on() && !photosensitive();
    }

    public static boolean particles() {
        var s = NocturneClient.SETTINGS;
        return s.particleEffects.on() && !s.reducedMotion.on() && !photosensitive();
    }

    public static boolean camera() {
        var s = NocturneClient.SETTINGS;
        return s.cameraMovement.on() && !s.reducedMotion.on() && !photosensitive();
    }

    public static boolean flashes() {
        var s = NocturneClient.SETTINGS;
        return s.flashEffects.on() && !s.reducedMotion.on() && !photosensitive();
    }

    public static boolean plate() {
        return photosensitive()
                || NocturneClient.SETTINGS.lyricBackplate.on()
                || NocturneClient.SETTINGS.contrastPreset.get() != 0;
    }

    public static int ink() {
        var s = NocturneClient.SETTINGS;
        return photosensitive() || s.contrastPreset.get() == 1
                ? -1
                : s.contrastPreset.get() == 2 ? 0xFF121212 : s.color.argb();
    }

    public static int background() {
        return !photosensitive() && NocturneClient.SETTINGS.contrastPreset.get() == 2
                ? 0xFFF2EEE4
                : 0xFF101015;
    }

    public static double plateOpacity() {
        return photosensitive() ? .94 : NocturneClient.SETTINGS.backplateOpacity.get() / 100;
    }

    public static Set<String> screens() {
        if (screens == null) {
            try {
                var saved = Studio.library().preset("accessibility", "Hidden screens");
                var names = new ArrayList<String>();
                if (saved != null && saved.has("classes"))
                    for (var value : saved.getAsJsonArray("classes"))
                        names.add(value.getAsString());
                screens = ScreenPolicy.validate(names);
            } catch (RuntimeException ex) {
                NocturneClient.LOG.warn("Invalid hidden-screen rules ignored", ex);
                screens = Set.of();
            }
        }
        return screens;
    }

    public static void screens(Collection<String> values) throws java.io.IOException {
        var checked = ScreenPolicy.validate(values);
        var json = new JsonObject();
        var array = new JsonArray();
        checked.forEach(array::add);
        json.add("classes", array);
        Studio.library().preset("accessibility", "Hidden screens", json);
        screens = checked;
    }

    public static String lastScreen() {
        return lastScreen;
    }

    public static void tick(Minecraft mc) {
        if (mc.screen != null && !mc.screen.getClass().getName().startsWith("dev.truwty.nocturne."))
            lastScreen = mc.screen.getClass().getName();
        ContextProfiles.tick(mc);
    }

    public static boolean hidden() {
        var mc = Minecraft.getInstance();
        var s = NocturneClient.SETTINGS;
        if (s == null) return false;
        var screen = mc.screen;
        return new ScreenPolicy(
                        s.hideChat.on(),
                        s.hideInventory.on(),
                        s.hideDebug.on(),
                        s.hidePause.on(),
                        s.hideOtherScreens.on(),
                        s.hideWithGui.on(),
                        screens())
                .hides(
                        screen == null ? null : screen.getClass().getName(),
                        screen instanceof ChatScreen,
                        screen instanceof AbstractContainerScreen<?>,
                        screen instanceof PauseScreen,
                        mc.getDebugOverlay().showDebugScreen(),
                        mc.options.hideGui);
    }

    public static void applyFriendly() {
        var s = NocturneClient.SETTINGS;
        s.photosensitiveMode.set(true);
        s.flashEffects.set(false);
        s.cameraMovement.set(false);
        s.particleEffects.set(false);
        s.animatedText.set(false);
        s.reducedMotion.set(true);
        s.shakeEffects.set(false);
        s.beatZoom.set(false);
        s.beatPulse.set(false);
        s.audioReactive.set(false);
        s.glow.set(false);
        s.chroma.set(false);
        s.cloudDrift.set(false);
        s.orbit.set(false);
        s.battle.set(false);
        s.cinematic.set(false);
        s.topBar.set(false);
        s.nextLine.set(false);
        s.albumRain.set(false);
        s.colorMode.set(0);
        s.color.set(-1);
        s.color2.set(-1);
        s.highContrast.set(true);
        s.lyricBackplate.set(true);
        s.lyricOutline.set(true);
        s.contrastPreset.set(1);
        s.lingeringLimit.set(0.0);
        s.preset.set(0);
        s.adoptPreset();
        s.save();
    }
}
