package dev.truwty.nocturne.gui;

import java.util.Set;

public record GamepadSample(Set<ControllerRepeater.Action> pressed, double rightX, double rightY) {
  public GamepadSample {
    pressed = Set.copyOf(pressed);
    rightX = axis(rightX);
    rightY = axis(rightY);
  }

  public static double axis(double value) {
    return Double.isFinite(value) ? Math.clamp(value, -1, 1) : 0;
  }
}
