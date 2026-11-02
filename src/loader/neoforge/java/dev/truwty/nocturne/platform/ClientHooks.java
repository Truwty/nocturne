package dev.truwty.nocturne.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientHooks {
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static final Map<String,BiConsumer<GuiGraphics,DeltaTracker>> HUD = new LinkedHashMap<>();
    private ClientHooks() {}
    public static void bind(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            KEYS.stream().map(KeyMapping::getCategory).distinct().forEach(event::registerCategory);
            KEYS.forEach(event::register);
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> HUD.forEach((name, action) ->
                event.registerBelowAll(Identifier.fromNamespaceAndPath("nocturne", name), action::accept)));
    }
    public static KeyMapping.Category category(Identifier id) { return new KeyMapping.Category(id); }
    public static KeyMapping key(KeyMapping mapping) { KEYS.add(mapping); return mapping; }
    public static void tick(Consumer<Minecraft> action) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> action.accept(Minecraft.getInstance()));
    }
    public static void stop(Consumer<Minecraft> action) {
        NeoForge.EVENT_BUS.addListener((ClientStoppingEvent event) -> action.accept(event.getClient()));
    }
    public static void hud(String name, BiConsumer<GuiGraphics,DeltaTracker> action) { HUD.put(name, action); }
}
