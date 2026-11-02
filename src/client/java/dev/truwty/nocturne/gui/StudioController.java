package dev.truwty.nocturne.gui;

import com.mojang.blaze3d.platform.InputConstants;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.compat.GamepadInput;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

public final class StudioController {
    private static final ControllerRepeater REPEATER = new ControllerRepeater();
    private static Screen previous;

    private StudioController() {}

    private static boolean supported(Screen screen) {
        return screen != null
                && screen.getClass().getPackageName().equals("dev.truwty.nocturne.gui");
    }

    public static void tick(Minecraft minecraft) {
        Screen screen = ClientAccess.screen(minecraft);
        if (!NocturneClient.SETTINGS.controllerNavigation.on()
                || !supported(screen)
                || dev.truwty.nocturne.platform.ModPlatform.loaded("controlify")
                || dev.truwty.nocturne.platform.ModPlatform.loaded("midnightcontrols")) {
            REPEATER.reset();
            previous = screen;
            return;
        }
        long now = System.nanoTime() / 1_000_000;
        var state = GamepadInput.poll(now);
        if (state == null) {
            REPEATER.reset();
            return;
        }
        var pressed = state.pressed();
        if (previous != screen) {
            REPEATER.reset();
            REPEATER.sample(pressed, now);
            previous = screen;
            return;
        }
        for (var action : REPEATER.sample(pressed, now)) {
            dispatch(screen, action);
            if (ClientAccess.screen(minecraft) != screen) return;
        }
        double dx = state.rightX(), dy = state.rightY();
        if (Math.hypot(dx, dy) > .35) {
            if (screen instanceof HudEditorScreen editor) editor.nudge(dx * 3, dy * 3);
            else if (screen instanceof PlacementScreen editor)
                editor.controllerNudge(dx * 3, dy * 3);
        }
    }

    public static void dispatch(Screen screen, ControllerRepeater.Action action) {
        if (action == ControllerRepeater.Action.PAGE_UP
                || action == ControllerRepeater.Action.PAGE_DOWN) {
            screen.mouseScrolled(0, 0, 0, action == ControllerRepeater.Action.PAGE_UP ? 5 : -5);
            return;
        }
        int key, modifiers = 0;
        switch (action) {
            case PREVIOUS -> {
                key = InputConstants.KEY_TAB;
                modifiers = InputConstants.MOD_SHIFT;
            }
            case NEXT -> key = InputConstants.KEY_TAB;
            case LEFT -> key = InputConstants.KEY_LEFT;
            case RIGHT -> key = InputConstants.KEY_RIGHT;
            case ACCEPT -> key = InputConstants.KEY_RETURN;
            case BACK -> key = InputConstants.KEY_ESCAPE;
            case PAGE_UP -> key = InputConstants.KEY_PAGEUP;
            case PAGE_DOWN -> key = InputConstants.KEY_PAGEDOWN;
            case UNDO -> {
                key = InputConstants.KEY_Z;
                modifiers = InputConstants.MOD_CONTROL;
            }
            case REDO -> {
                key = InputConstants.KEY_Y;
                modifiers = InputConstants.MOD_CONTROL;
            }
            default -> throw new IllegalArgumentException("Unknown controller action");
        }
        screen.keyPressed(GamepadInput.keyEvent(key, modifiers));
    }
}
