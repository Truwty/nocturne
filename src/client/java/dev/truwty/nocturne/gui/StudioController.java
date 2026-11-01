package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;

import java.util.*;

public final class StudioController {
    private static final ControllerRepeater REPEATER = new ControllerRepeater();
    private static Screen previous;
    private static int joystick = -1;
    private static long nextScan;

    private StudioController() {}

    private static boolean supported(Screen screen) {
        return screen != null
                && screen.getClass().getPackageName().equals("dev.truwty.nocturne.gui");
    }

    public static void tick(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        if (!NocturneClient.SETTINGS.controllerNavigation.on()
                || !supported(screen)
                || FabricLoader.getInstance().isModLoaded("controlify")
                || FabricLoader.getInstance().isModLoaded("midnightcontrols")) {
            REPEATER.reset();
            previous = screen;
            return;
        }
        long now = System.nanoTime() / 1_000_000;
        if (joystick < 0 || !GLFW.glfwJoystickPresent(joystick)) {
            if (now < nextScan) return;
            nextScan = now + 1000;
            joystick = -1;
            for (int id = GLFW.GLFW_JOYSTICK_1; id <= GLFW.GLFW_JOYSTICK_LAST; id++)
                if (GLFW.glfwJoystickIsGamepad(id)) {
                    joystick = id;
                    break;
                }
            if (joystick < 0) {
                REPEATER.reset();
                return;
            }
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWGamepadState state = GLFWGamepadState.malloc(stack);
            if (!GLFW.glfwGetGamepadState(joystick, state)) {
                joystick = -1;
                REPEATER.reset();
                return;
            }
            EnumSet<ControllerRepeater.Action> pressed =
                    EnumSet.noneOf(ControllerRepeater.Action.class);
            int[] buttons = {
                GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP,
                GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN,
                GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT,
                GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT,
                GLFW.GLFW_GAMEPAD_BUTTON_A,
                GLFW.GLFW_GAMEPAD_BUTTON_B,
                GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER,
                GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER,
                GLFW.GLFW_GAMEPAD_BUTTON_X,
                GLFW.GLFW_GAMEPAD_BUTTON_Y
            };
            for (int i = 0; i < buttons.length; i++)
                if (state.buttons(buttons[i]) == GLFW.GLFW_PRESS)
                    pressed.add(ControllerRepeater.Action.values()[i]);
            if (state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y) < -.55)
                pressed.add(ControllerRepeater.Action.PREVIOUS);
            if (state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y) > .55)
                pressed.add(ControllerRepeater.Action.NEXT);
            if (state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X) < -.55)
                pressed.add(ControllerRepeater.Action.LEFT);
            if (state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X) > .55)
                pressed.add(ControllerRepeater.Action.RIGHT);
            if (previous != screen) {
                REPEATER.reset();
                REPEATER.sample(pressed, now);
                previous = screen;
                return;
            }
            for (var action : REPEATER.sample(pressed, now)) {
                dispatch(screen, action);
                if (minecraft.screen != screen) return;
            }
            double dx = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X),
                    dy = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
            if (Math.hypot(dx, dy) > .35) {
                if (screen instanceof HudEditorScreen editor) editor.nudge(dx * 3, dy * 3);
                else if (screen instanceof PlacementScreen editor)
                    editor.controllerNudge(dx * 3, dy * 3);
            }
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
                key = GLFW.GLFW_KEY_TAB;
                modifiers = GLFW.GLFW_MOD_SHIFT;
            }
            case NEXT -> key = GLFW.GLFW_KEY_TAB;
            case LEFT -> key = GLFW.GLFW_KEY_LEFT;
            case RIGHT -> key = GLFW.GLFW_KEY_RIGHT;
            case ACCEPT -> key = GLFW.GLFW_KEY_ENTER;
            case BACK -> key = GLFW.GLFW_KEY_ESCAPE;
            case PAGE_UP -> key = GLFW.GLFW_KEY_PAGE_UP;
            case PAGE_DOWN -> key = GLFW.GLFW_KEY_PAGE_DOWN;
            case UNDO -> {
                key = GLFW.GLFW_KEY_Z;
                modifiers = GLFW.GLFW_MOD_CONTROL;
            }
            case REDO -> {
                key = GLFW.GLFW_KEY_Y;
                modifiers = GLFW.GLFW_MOD_CONTROL;
            }
            default -> throw new IllegalArgumentException("Unknown controller action");
        }
        screen.keyPressed(new KeyEvent(key, 0, modifiers));
    }
}
