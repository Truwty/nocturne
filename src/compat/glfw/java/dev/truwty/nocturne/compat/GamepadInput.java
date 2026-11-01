package dev.truwty.nocturne.compat;

import dev.truwty.nocturne.gui.*;
import java.util.EnumSet;
import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;

public final class GamepadInput {
  private static int joystick = -1;
  private static long nextScan;

  private GamepadInput() {}

  public static net.minecraft.client.input.KeyEvent keyEvent(int key, int modifiers) {
    return new net.minecraft.client.input.KeyEvent(key, 0, modifiers);
  }

  public static GamepadSample poll(long now) {
    if (joystick < 0 || !GLFW.glfwJoystickPresent(joystick)) {
      if (now < nextScan) return null;
      nextScan = now + 1000;
      joystick = -1;
      for (int id = GLFW.GLFW_JOYSTICK_1; id <= GLFW.GLFW_JOYSTICK_LAST; id++)
        if (GLFW.glfwJoystickIsGamepad(id)) {
          joystick = id;
          break;
        }
      if (joystick < 0) return null;
    }
    try (var stack = MemoryStack.stackPush()) {
      var state = GLFWGamepadState.malloc(stack);
      if (!GLFW.glfwGetGamepadState(joystick, state)) {
        joystick = -1;
        return null;
      }
      var pressed = EnumSet.noneOf(ControllerRepeater.Action.class);
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
      return new GamepadSample(
          pressed,
          state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X),
          state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y));
    }
  }

  public static void close() {
    joystick = -1;
  }
}
