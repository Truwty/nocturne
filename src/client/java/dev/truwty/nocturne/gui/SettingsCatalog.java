package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.setting.*;

import java.util.*;

public final class SettingsCatalog {
    public static final List<String> CATEGORIES =
            List.of(
                    "All",
                    "Lyrics & playback",
                    "Appearance",
                    "Placement",
                    "Motion & performance",
                    "Interface");

    private SettingsCatalog() {}

    public static String category(Setting<?> s) {
        String n = s.name.toLowerCase(Locale.ROOT);
        if(n.startsWith("hide lyrics")||n.contains("context profiles"))return "Interface";
        if (n.startsWith("ui ") || n.contains("settings style") || n.contains("controller"))
            return "Interface";
        if (n.contains("limit")||n.contains("effect detail")||n.contains("budget")||n.contains("frame time")||n.contains("performance"))return "Motion & performance";
        if (n.startsWith("editor ")
                || n.contains("placement")
                || n.equals("distance")
                || n.contains("floor")
                || n.contains("face you")
                || n.contains("orbit")
                || n.contains("cloud")
                || n.contains("running")
                || n.contains("walls")) return "Placement";
        if (n.contains("photosensitivity")||n.contains("camera movement")||n.contains("animated lyric")||n.contains("motion")
                || n.contains("flash")
                || n.contains("shake")
                || n.contains("particle")
                || n.contains("quality")
                || n.contains("reactive")
                || n.contains("sensitivity")
                || n.contains("transition")
                || n.contains("render distance")
                || n.contains("beat")) return "Motion & performance";
        if (s instanceof ColorSetting
                || n.contains("font")
                || n.contains("animation")
                || n.contains("preset")
                || n.contains("intensity")
                || n.contains("glow")
                || n.contains("chromatic")
                || n.contains("sparkles")
                || n.equals("size")
                || n.contains("cinematic")
                || n.contains("contrast") || n.contains("backplate") || n.contains("lyric outline")) return "Appearance";
        return "Lyrics & playback";
    }

    public static String help(Setting<?> s) {
        String detail =
                s instanceof NumberSetting n
                        ? "Range " + n.min + "–" + n.max + n.unit + "; step " + n.step
                        : s instanceof ModeSetting m
                                ? "Choices: " + String.join(", ", m.options)
                                : s instanceof ColorSetting
                                        ? "ARGB color; opens the full color picker"
                                        : s instanceof BoolSetting
                                                ? "Toggle this option"
                                                : "Run this action";
        String purpose=switch(s.name){
            case "Adaptive effect budgets"->" · Reduce decorations after sustained slow frames; recover gradually";
            case "Target frame time"->" · Set 1000 / your target FPS: 16.7 ms for 60 FPS, 33.3 ms for 30 FPS";
            case "Glow pass limit"->" · Maximum extra glow text passes per frame; primary text is preserved";
            case "Cloud lettering layer limit"->" · Maximum decorative cloud puffs and text copies per frame";
            case "Particle limit"->" · Shared cap for sparks, mist and flying battle words; quality may lower it";
            case "Lingering line limit"->" · Maximum retained previous lines; zero keeps only the current line";
            case "Distance effect detail", "Full effect detail distance"->" · Fade decorative detail toward the lyric render distance";
            case "Performance readout"->" · CPU-side elapsed submission time and geometry counters; move Diagnostics in the HUD editor";
            case "Automatic context profiles"->" · Apply saved Screenshots, Menus, Combat or Exploration settings during rendering";
            case "Camera movement"->" · Allow Nocturne beat-driven FOV changes; never changes the player's aim";
            case "Particle effects"->" · Allow sparks, mist and flying words independently of text animation";
            case "Animated lyric text"->" · Disable to show steady complete lines without word transforms or orbit motion";
            case "Photosensitivity-friendly mode"->" · Lock Nocturne flashes, motion, glow and particles off across every context";
            case "Screen edge lyric fallback"->" · Keep the current lyric near a safe screen edge when its world anchor is outside the view";
            case "Lyric backplate", "Backplate opacity"->" · Add one matching background quad per lyric line";
            case "Lyric outline"->" · Add four cardinal text passes for stronger edges";
            case "Hide lyrics with F1"->" · Also hide world lyrics when F1 hides Minecraft's HUD";
            default->"";
        };
        return category(s) + " · " + detail+purpose;
    }

    public static List<Setting<?>> search(
            List<Setting<?>> settings, String category, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return settings.stream()
                .filter(s -> category.equals("All") || category(s).equals(category))
                .filter(s ->
                        (s.name + " " + category(s) + " " + help(s))
                                .toLowerCase(Locale.ROOT)
                                .contains(needle))
                .toList();
    }
}
