package dev.truwty.nocturne.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.truwty.nocturne.NocturneClient;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

public final class OnScreenKeyboard extends StudioPanel {
  private final EditBox target;
  private String value;
  private boolean symbols, upper;
  private int cursor;
  private final List<Button> keys = new ArrayList<>();

  public OnScreenKeyboard(Screen parent, EditBox target) {
    super(parent, "Controller keyboard");
    this.target = target;
    value = target.getValue();
  }

  @Override
  protected void build() {
    keys.clear();
    String letters =
        symbols
            ? "1234567890!@#$%&*()[]{}<>:/\\.,?+-=_'\";"
            : "abcdefghijklmnopqrstuvwxyz0123456789.,?!";
    int bw = Math.max(18, (w - 50) / 10);
    for (int i = 0; i < letters.length(); i++) {
      final int keyIndex = i;
      String letter = String.valueOf(letters.charAt(i));
      String entered = upper ? letter.toUpperCase(Locale.ROOT) : letter;
      keys.add(
          button(
              x + 16 + (i % 10) * (bw + 2),
              body + 36 + (i / 10) * 25,
              bw,
              entered,
              "Insert " + entered,
              () -> {
                cursor = keyIndex;
                if (value.length() < 16384) value = value + entered;
                rebuildWidgets();
              }));
    }
    button(
        x + 16,
        body + 146,
        (w - 44) / 4,
        "Space",
        "Insert a space",
        () -> {
          if (value.length() < 16384) value += " ";
          rebuildWidgets();
        });
    button(
        x + 20 + (w - 44) / 4,
        body + 146,
        (w - 44) / 4,
        "Backspace",
        "Delete the last character",
        () -> {
          if (!value.isEmpty())
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
          rebuildWidgets();
        });
    button(
        x + 24 + (w - 44) / 2,
        body + 146,
        (w - 44) / 4,
        "Shift",
        "Toggle uppercase",
        () -> {
          upper = !upper;
          rebuildWidgets();
        });
    button(
        x + 28 + 3 * ((w - 44) / 4),
        body + 146,
        (w - 44) / 4,
        symbols ? "Letters" : "Symbols",
        "Switch character set",
        () -> {
          symbols = !symbols;
          rebuildWidgets();
        });
    cursor = Math.clamp(cursor, 0, keys.size() - 1);
    setFocused(keys.get(cursor));
  }

  @Override
  protected void footer() {
    button(x + 12, y + h - 29, 90, "Cancel", "Discard keyboard edits", this::onClose);
    button(
        x + w - 102,
        y + h - 29,
        90,
        "Use text",
        "Apply text within the original field's length limit",
        () -> {
          target.setValue(value);
          onClose();
        });
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    int delta =
        switch (event.key()) {
          case InputConstants.KEY_LEFT -> -1;
          case InputConstants.KEY_RIGHT -> 1;
          case InputConstants.KEY_UP -> -10;
          case InputConstants.KEY_DOWN -> 10;
          default -> 0;
        };
    if (delta != 0 && keys.contains(getFocused())) {
      cursor = Math.floorMod(keys.indexOf(getFocused()) + delta, keys.size());
      setFocused(keys.get(cursor));
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
    text(
        g,
        value.isEmpty() ? "Choose characters; Tab reaches Space, Shift and Use text" : value,
        body + 8,
        NocturneClient.SETTINGS.uiText.argb());
  }
}
