package dev.truwty.nocturne.platform;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ExperiencePacks {
    private ExperiencePacks() {}

    public static void register() {
        var mod = FabricLoader.getInstance().getModContainer("nocturne").orElseThrow();
        if (!ResourceLoader.registerBuiltinPack(
                Identifier.fromNamespaceAndPath("nocturne", "hd_weather"),
                mod,
                Component.literal("Nocturne / HD rain & snow"),
                PackActivationType.NORMAL))
            throw new IllegalStateException("Could not register HD weather pack");
        for (String theme : new String[] {"mint", "cyber", "amber"})
            for (String group : new String[] {"swords", "totems", "blocks"}) {
                if (!ResourceLoader.registerBuiltinPack(
                        Identifier.fromNamespaceAndPath("nocturne", theme + "_" + group),
                        mod,
                        Component.literal("Nocturne / " + theme + " " + group),
                        PackActivationType.NORMAL))
                    throw new IllegalStateException("Could not register Nocturne texture pack");
            }
    }
}
