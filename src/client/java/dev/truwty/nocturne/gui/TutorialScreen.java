package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class TutorialScreen extends StudioPanel {
  private int topic, step;
  private static final String[] TOPICS = {"Lyric timing", "Theme design", "World placement"};
  private static final String[][] STEPS = {
    {
      "Play a song with lyrics, then open the workspace and choose Timing.",
      "Select a line. Seek near its start and compare the audible vocal.",
      "Set line start/end; choose a word to adjust its individual timestamps.",
      "Play the phrase again. Use Undo if needed, then Save lyrics."
    },
    {
      "Open the theme editor; previews apply while the editor is open.",
      "Choose a color role, then change hue, saturation, opacity or hex.",
      "Compare text contrast against the backplate at your normal GUI scale.",
      "Apply your preview, save a named preset, or cancel to restore it."
    },
    {
      "Enter a world, play a song, then open the world placement editor.",
      "Drag the anchor. Change distance and size until the line is readable.",
      "Rotate with the editor controls; use face-you for reliable alignment.",
      "Save placement. Walk and turn to check your chosen follow mode."
    }
  };

  public TutorialScreen(Screen parent) {
    super(parent, "Help & setup");
  }

  @Override
  protected void build() {
    int bw = (w - 40) / 3;
    for (int i = 0; i < TOPICS.length; i++) {
      final int index = i;
      button(
          x + 16 + i * (bw + 4),
          body,
          bw,
          TOPICS[i],
          "Guided practice in the actual editor",
          () -> {
            topic = index;
            step = 0;
            rebuildWidgets();
          });
    }
    button(
        x + 16,
        body + 110,
        w - 32,
        "Practice in " + TOPICS[topic] + " editor",
        "Return here with Back to continue the guide",
        () ->
            ClientAccess.setScreen(
                minecraft,
                switch (topic) {
                  case 0 -> new LyricWorkspaceScreen(this);
                  case 1 -> new ThemeEditorScreen(this);
                  default -> new PlacementScreen(this);
                }));
    button(
        x + 16,
        body + 138,
        (w - 36) / 2,
        "Previous step",
        "Repeat the previous instruction",
        () -> {
          step = Math.max(0, step - 1);
          rebuildWidgets();
        });
    button(
        x + 20 + (w - 36) / 2,
        body + 138,
        (w - 36) / 2,
        step == 3 ? "Start again" : "Next step",
        "Continue after trying the current action",
        () -> {
          step = (step + 1) % 4;
          rebuildWidgets();
        });
    button(
        x + 16,
        body + 178,
        w - 32,
        "Run setup checks again",
        "Live player, lyric and placement checks",
        () -> ClientAccess.setScreen(minecraft, new SetupScreen(this)));
  }

  @Override
  protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
    text(
        g,
        TOPICS[topic] + " · " + (step + 1) + " / 4",
        body + 40,
        NocturneClient.SETTINGS.uiAccent.argb());
    // Split on word boundaries so the guide remains usable at larger GUI scales.
    int yy = body + 60;
    for (var line :
        font.split(net.minecraft.network.chat.Component.literal(STEPS[topic][step]), w - 32)) {
      g.text(font, line, x + 16, yy, NocturneClient.SETTINGS.uiText.argb(), false);
      yy += 12;
    }
  }
}
