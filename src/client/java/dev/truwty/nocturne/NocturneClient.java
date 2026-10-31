package dev.truwty.nocturne;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NocturneClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("nocturne");
    public static final MediaBridge MEDIA = new MediaBridge();
    public static Preferences preferences;

    @Override public void onInitializeClient() {
        preferences = Preferences.load();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nocturne", "music"));
        var open = key("open", GLFW.GLFW_KEY_N, category);
        var toggle = key("toggle", GLFW.GLFW_KEY_UNKNOWN, category);
        var next = key("next", GLFW.GLFW_KEY_UNKNOWN, category);
        var previous = key("previous", GLFW.GLFW_KEY_UNKNOWN, category);
        var hud = key("hud", GLFW.GLFW_KEY_UNKNOWN, category);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) client.setScreen(new MusicScreen(client.screen));
            while (toggle.consumeClick()) if (MEDIA.state().canToggle()) MEDIA.command("toggle");
            while (next.consumeClick()) if (MEDIA.state().canNext()) MEDIA.command("next");
            while (previous.consumeClick()) if (MEDIA.state().canPrevious()) MEDIA.command("previous");
            while (hud.consumeClick()) { preferences.hud = !preferences.hud; preferences.save(); }
            Artwork.update(MEDIA.state().art());
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("nocturne", "player"), MusicVisuals::hud);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { MEDIA.close(); Artwork.close(); });
        MEDIA.start();
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nocturne." + name,
                InputConstants.Type.KEYSYM, code, category));
    }
}
