package dev.truwty.nocturne.compat;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.gui.*;
import java.util.EnumSet;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLStdinc;

public final class GamepadInput {
  private static long gamepad, nextScan;
  private static boolean checked, owned, failed;

  private GamepadInput() {}

  public static net.minecraft.client.input.KeyEvent keyEvent(int key, int modifiers) {
    return new net.minecraft.client.input.KeyEvent(key, org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(key, (short) modifiers, true), modifiers);
  }

  public static GamepadSample poll(long now) {
    if (!checked) {
      checked = true;
      if (SDLInit.SDL_WasInit(SDLInit.SDL_INIT_GAMEPAD) == 0) {
        owned = SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD);
        failed = !owned;
        if (failed)
          NocturneClient.LOG.warn(
              "SDL gamepad subsystem could not start; keyboard navigation remains available");
      }
    }
    if (failed) return null;
    if (gamepad != 0 && !SDLGamepad.SDL_GamepadConnected(gamepad)) {
      SDLGamepad.SDL_CloseGamepad(gamepad);
      gamepad = 0;
    }
    if (gamepad == 0) {
      if (now < nextScan) return null;
      nextScan = now + 1000;
      var devices = SDLGamepad.SDL_GetGamepads();
      if (devices == null) return null;
      try {
        for (int i = devices.position(); i < devices.limit(); i++) {
          gamepad = SDLGamepad.SDL_OpenGamepad(devices.get(i));
          if (gamepad != 0) break;
        }
      } finally {
        SDLStdinc.SDL_free(devices);
      }
      if (gamepad == 0) return null;
    }
    var pressed = EnumSet.noneOf(ControllerRepeater.Action.class);
    int[] buttons = {
      SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP,
      SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN,
      SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT,
      SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT,
      SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH,
      SDLGamepad.SDL_GAMEPAD_BUTTON_EAST,
      SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER,
      SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER,
      SDLGamepad.SDL_GAMEPAD_BUTTON_WEST,
      SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH
    };
    for (int i = 0; i < buttons.length; i++)
      if (SDLGamepad.SDL_GetGamepadButton(gamepad, buttons[i]))
        pressed.add(ControllerRepeater.Action.values()[i]);
    if (axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY) < -.55)
      pressed.add(ControllerRepeater.Action.PREVIOUS);
    if (axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY) > .55) pressed.add(ControllerRepeater.Action.NEXT);
    if (axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX) < -.55) pressed.add(ControllerRepeater.Action.LEFT);
    if (axis(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX) > .55) pressed.add(ControllerRepeater.Action.RIGHT);
    return new GamepadSample(
        pressed,
        axis(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX),
        axis(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY));
  }

  private static double axis(int axis) {
    return GamepadSample.axis(SDLGamepad.SDL_GetGamepadAxis(gamepad, axis) / 32767.0);
  }

  public static void close() {
    if (gamepad != 0) {
      SDLGamepad.SDL_CloseGamepad(gamepad);
      gamepad = 0;
    }
    if (owned) {
      SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_GAMEPAD);
      owned = false;
    }
  }
}
