package dev.truwty.nocturne;

import com.google.gson.JsonObject;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.StudioScreen;
import dev.truwty.nocturne.lyrics.NowPlaying;
import dev.truwty.nocturne.setting.Setting;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class Studio {
    private static StudioLibrary library;
    private static int placement = -1;
    private Studio() {}
    public static synchronized StudioLibrary library() {
        if (library == null) {
            try { library = new StudioLibrary(directory()); }
            catch (IOException ex) { throw new IllegalStateException("Cannot load Nocturne library", ex); }
        }
        return library;
    }
    public static Path directory() { return FabricLoader.getInstance().getConfigDir().resolve("nocturne"); }
    public static void open() { Minecraft mc = Minecraft.getInstance(); ClientAccess.setScreen(mc, new StudioScreen(ClientAccess.screen(mc))); }
    public static double songOffset(NowPlaying track) { return track == null ? 0 : library().offset(track.key()); }

    public static void tick() {
        int next = NocturneClient.SETTINGS.follow.get();
        if (SongAppearance.active()) { placement=next; return; }
        if (next == placement) return;
        try {
            if (placement >= 0) library().preset("placement:" + placement, "Active", snapshot("placement"));
            JsonObject profile = library().preset("placement:" + next, "Active");
            if (profile != null) apply("placement", profile);
            else if (placement >= 0) {
                var settings = NocturneClient.SETTINGS;
                settings.editorX.set(0.0); settings.editorY.set(0.0); settings.editorYaw.set(0.0); settings.editorPitch.set(0.0); settings.editorRoll.set(0.0);
            }
            placement = next;
        } catch (IOException ex) { placement = next; NocturneClient.LOG.warn("Placement profile could not be saved", ex); }
    }
    public static void savePlacement() throws IOException {
        if (!SongAppearance.active()) library().preset("placement:" + NocturneClient.SETTINGS.follow.get(), "Active", snapshot("placement"));
    }

    public static JsonObject snapshot(String group) {
        JsonObject result = new JsonObject();
        for (Setting<?> setting : NocturneClient.SETTINGS.settings) {
            if (included(group, setting.name)) result.add(setting.name, setting.save());
        }
        return result;
    }

    public static void apply(String group, JsonObject values) { apply(group,values,true); }
    public static void adoptPlacement() { placement=NocturneClient.SETTINGS.follow.get(); }
    public static void apply(String group, JsonObject values, boolean persist) {
        validate(group, values);
        for (Setting<?> setting : NocturneClient.SETTINGS.settings) {
            if (included(group, setting.name) && values.has(setting.name)) setting.load(values.get(setting.name));
        }
        if(persist) NocturneClient.SETTINGS.save();
    }

    public static void validate(String group, JsonObject values) {
        if (values == null) throw new IllegalArgumentException("Preset not found");
        for (Setting<?> setting : NocturneClient.SETTINGS.settings) {
            if (!included(group, setting.name) || !values.has(setting.name)) continue;
            var value = values.get(setting.name);
            if (!value.isJsonPrimitive()) throw new IllegalArgumentException("Invalid preset value: " + setting.name);
            if (setting instanceof dev.truwty.nocturne.setting.BoolSetting && !value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean: "+setting.name);
            if (setting instanceof dev.truwty.nocturne.setting.NumberSetting number) {
                if(!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected number: "+setting.name);
                double n = value.getAsDouble();
                if (!Double.isFinite(n) || n < number.min || n > number.max) throw new IllegalArgumentException("Out of range: " + setting.name);
            } else if (setting instanceof dev.truwty.nocturne.setting.ColorSetting && !value.getAsString().matches("#[0-9a-fA-F]{8}")) throw new IllegalArgumentException("Invalid colour: " + setting.name);
            else if (setting instanceof dev.truwty.nocturne.setting.ModeSetting mode && !mode.options.contains(value.getAsString())) throw new IllegalArgumentException("Unknown mode: " + setting.name);
        }
    }
    public static JsonObject defaults(String group) {
        var result=new JsonObject();
        for(var setting:NocturneClient.SETTINGS.settings)if(included(group,setting.name)) {
            if(setting instanceof dev.truwty.nocturne.setting.ColorSetting)result.addProperty(setting.name,String.format("#%08X",setting.defaultValue));
            else if(setting instanceof dev.truwty.nocturne.setting.ModeSetting mode)result.addProperty(setting.name,mode.options.get((Integer)setting.defaultValue));
            else result.add(setting.name,new com.google.gson.Gson().toJsonTree(setting.defaultValue));
        }
        return result;
    }
    public static boolean accepts(String group,String name) {return included(group,name);}

    private static boolean included(String group, String name) {
        if(group.equals("context"))return !name.equals("Preset")&&(included("song",name)||java.util.Set.of("Camera movement","Particle effects","Animated lyric text","Lyric backplate","Lyric outline","Backplate opacity","Lyric contrast","Screen edge lyric fallback","Show translation","Show romanization","Vocal countdown","Lyric confidence","Glow pass limit","Cloud lettering layer limit","Lingering line limit").contains(name));
        if(group.equals("song")) return name.startsWith("Editor ") || java.util.Set.of(
            "Preset","Placement","Animation","Intensity","Lyrics font","Distance","Size","Colour mode","Colour","Second colour",
            "Glow","Chromatic split","Sparkles","Impact shake + flash","Cinematic bars","Beat zoom","Turn to face you","Words on the floor",
            "Reduced motion","Flash effects","Shake effects","High contrast lyrics","Transition duration","Words per line","Lines stay for",
            "Show through walls","Show next line","Karaoke bar","Song title intro","Auto readability","Beat pulse","Album colour rain","Battle words when fighting","Smart placement","Keep up when running","Orbit you in F5 front view","Orbit speed",
            "Effect quality","Particle limit","Cloud style","Drift with the clouds","Clouds follow time of day").contains(name);
        return group.equals("themes") ? name.startsWith("UI ") :
            name.startsWith("Editor ") || name.equals("Distance") || name.equals("Size");
    }
}
