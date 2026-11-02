package dev.truwty.nocturne.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class ClientHooks {
    private ClientHooks() {}
    public static KeyMapping.Category category(Identifier id) { return KeyMapping.Category.register(id); }
    public static KeyMapping key(KeyMapping mapping) { return KeyMappingHelper.registerKeyMapping(mapping); }
    public static void tick(Consumer<Minecraft> action) { ClientTickEvents.END_CLIENT_TICK.register(action::accept); }
    public static void stop(Consumer<Minecraft> action) { ClientLifecycleEvents.CLIENT_STOPPING.register(action::accept); }
    public static void hud(String name, BiConsumer<GuiGraphicsExtractor, DeltaTracker> action) {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("nocturne", name), action::accept);
    }
}
