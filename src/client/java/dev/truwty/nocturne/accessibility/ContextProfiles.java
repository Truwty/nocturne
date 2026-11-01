package dev.truwty.nocturne.accessibility;

import com.google.gson.*;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.lyrics.LyricsRenderer;
import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Render-scoped overrides sit above global/per-song values and never enter config saves. */
public final class ContextProfiles {
    private static final Map<GameplayContext, JsonObject> profiles =
            new EnumMap<>(GameplayContext.class);
    private static boolean loaded, screenshot;
    private static Set<String> allowedNames;
    private static long storageVersion, previewVersion, revision;
    private static String appliedKey = "";

    public static long revision() {
        return revision;
    }

    private static Screen previewOwner;
    private static JsonObject preview;

    private ContextProfiles() {}

    public static boolean screenshot() {
        return screenshot;
    }

    public static void screenshot(boolean value) {
        screenshot = value;
    }

    public static GameplayContext current() {
        var mc = Minecraft.getInstance();
        return GameplayContext.choose(screenshot, ClientAccess.screen (mc) != null, LyricsRenderer.inCombat());
    }

    public static void tick(Minecraft mc) {
        if (ClientAccess.screen (mc) != previewOwner
                && !(ClientAccess.screen (mc) instanceof dev.truwty.nocturne.gui.ColorPickerScreen))
            clearPreview();
        if (mc.level == null) screenshot = false;
    }

    public static void clearPreview() {
        if (preview != null) previewVersion++;
        preview = null;
        previewOwner = null;
    }

    public static void preview(Screen owner, JsonObject value) {
        Studio.validate("context", value);
        previewOwner = owner;
        preview = value.deepCopy();
        previewVersion++;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        for (var context : GameplayContext.values()) {
            try {
                var value = Studio.library().preset("contextProfiles", context.label);
                if (value != null) {
                    Studio.validate("context", value);
                    profiles.put(context, value);
                }
            } catch (RuntimeException ex) {
                NocturneClient.LOG.warn("Invalid {} context profile ignored", context.label, ex);
            }
        }
    }

    public static JsonObject profile(GameplayContext context) {
        load();
        var value = profiles.get(context);
        return value == null ? null : value.deepCopy();
    }

    public static void save(GameplayContext context, JsonObject value) throws java.io.IOException {
        Studio.validate("context", value);
        Studio.library().preset("contextProfiles", context.label, value);
        loaded = false;
        profiles.clear();
        storageVersion++;
    }

    public static void remove(GameplayContext context) throws java.io.IOException {
        Studio.library().removePreset("contextProfiles", context.label);
        profiles.remove(context);
        storageVersion++;
    }

    public static JsonObject suggested(GameplayContext context) {
        var draft = Studio.snapshot("context");
        var s = NocturneClient.SETTINGS;
        if (context != GameplayContext.EXPLORATION) {
            draft.addProperty(s.flashEffects.name, false);
            draft.addProperty(s.cameraMovement.name, false);
            draft.addProperty(s.particleEffects.name, false);
            draft.addProperty(s.animatedText.name, false);
            draft.addProperty(s.cinematic.name, false);
            draft.addProperty(s.topBar.name, false);
            draft.addProperty(s.lingeringLimit.name, 0);
            draft.addProperty(s.lyricBackplate.name, true);
            draft.addProperty(s.contrastPreset.name, s.contrastPreset.options.get(1));
            draft.addProperty(
                    s.follow.name,
                    s.follow.options.get(context == GameplayContext.SCREENSHOTS ? 0 : 2));
            draft.addProperty(s.size.name, context == GameplayContext.SCREENSHOTS ? 120 : 80);
        }
        if (context == GameplayContext.MENUS || context == GameplayContext.SCREENSHOTS) {
            draft.addProperty(s.lyricConfidence.name, false);
            draft.addProperty(s.vocalCountdown.name, false);
            draft.addProperty(s.titleCard.name, false);
        }
        Studio.validate("context", draft);
        return draft;
    }

    public static SettingLayer scope() {
        load();
        JsonObject value =
                preview != null
                        ? preview
                        : NocturneClient.SETTINGS.contextProfiles.on()
                                ? profiles.get(current())
                                : null;
        String key =
                value == null
                        ? "global"
                        : preview != null
                                ? "preview:" + previewVersion
                                : current().name() + ":" + storageVersion;
        if (!key.equals(appliedKey)) {
            appliedKey = key;
            revision++;
        }
        if (allowedNames == null)
            allowedNames =
                    NocturneClient.SETTINGS.settings.stream()
                            .map(s -> s.name)
                            .filter(n -> Studio.accepts("context", n))
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return SettingLayer.apply(NocturneClient.SETTINGS.settings, value, allowedNames::contains);
    }
}
