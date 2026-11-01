package dev.truwty.nocturne.playback;

import dev.truwty.nocturne.compat.ClientAccess;
import java.util.*;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;

public final class PlaybackKeys {
    private static final Map<String, KeyMapping> KEYS = new LinkedHashMap<>();

    private PlaybackKeys() {}

    public static void register(KeyMapping.Category category) {
        for (String action :
                List.of(
                        "toggle",
                        "previous",
                        "next",
                        "seek_back",
                        "seek_forward",
                        "volume_down",
                        "volume_up",
                        "shuffle",
                        "repeat"))
            KEYS.put(
                    action,
                    KeyMappingHelper.registerKeyMapping(
                            new KeyMapping("key.nocturne." + action, ClientAccess.keyType(), -1, category)));
    }

    public static void tick(Minecraft mc) {
        for (var entry : KEYS.entrySet())
            while (entry.getValue().consumeClick())
                if (mc.level != null && ClientAccess.screen (mc) == null) dispatch(entry.getKey(), mc);
    }

    public static void dispatch(String key, Minecraft mc) {
        String action = key;
        long value = 0;
        var track = Playback.current();
        switch (key) {
            case "seek_back", "seek_forward" -> {
                action = "seek";
                if (track == null) return;
                value = track.positionMs() + (key.equals("seek_back") ? -10000 : 10000);
            }
            case "volume_down", "volume_up" -> {
                action = "volume";
                int volume = Playback.volume();
                if (volume < 0) return;
                value = volume + (key.equals("volume_down") ? -5 : 5);
            }
            case "shuffle" -> value = Playback.shuffle() ? 0 : 1;
            case "repeat" ->
                    value = (List.of("off", "track", "context").indexOf(Playback.repeat()) + 1) % 3;
            default -> {}
        }
        if (Playback.can(action)) Playback.command(action, value);
        else if (mc.player != null)
            ClientAccess.overlay(
          mc, Component.literal("Nocturne: current player does not expose " + action), true);
    }
}
