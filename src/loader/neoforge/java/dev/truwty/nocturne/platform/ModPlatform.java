package dev.truwty.nocturne.platform;

import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

public final class ModPlatform {
    private ModPlatform() {}
    public static String loader() { return "neoforge"; }
    public static Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }
    public static boolean loaded(String id) { return ModList.get().isLoaded(id); }
    public static String version(String id) {
        return ModList.get().getModContainerById(id).orElseThrow().getModInfo().getVersion().toString();
    }
}
