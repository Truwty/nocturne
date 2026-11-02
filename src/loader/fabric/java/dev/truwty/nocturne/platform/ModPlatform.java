package dev.truwty.nocturne.platform;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ModPlatform {
    private ModPlatform() {}
    public static String loader() { return "fabric"; }
    public static Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }
    public static boolean loaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    public static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
    }
}
