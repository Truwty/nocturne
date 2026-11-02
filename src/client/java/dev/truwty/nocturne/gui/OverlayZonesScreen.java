package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

public final class OverlayZonesScreen extends StudioPanel {
  private final List<HudLayout.Box> zones = new ArrayList<>(OverlayZones.current());
  private int selected;

  public OverlayZonesScreen(Screen parent) {
    super(parent, "Reserve space for other overlays");
  }

  @Override
  protected int contentHeight() {
    return 290;
  }

  @Override
  protected void build() {
    button(
        x + 16,
        body + 84,
        (w - 40) / 3,
        "Previous",
        "Select previous reserved region",
        () -> {
          selected = Math.max(0, selected - 1);
          rebuildWidgets();
        });
    button(
        x + 20 + (w - 40) / 3,
        body + 84,
        (w - 40) / 3,
        "Next",
        "Select next reserved region",
        () -> {
          selected = Math.min(zones.size() - 1, selected + 1);
          rebuildWidgets();
        });
    button(
                x + 24 + 2 * ((w - 40) / 3),
                body + 84,
                (w - 40) / 3,
                "Add zone",
                "Reserve up to eight normalized screen regions",
                () -> {
                  if (zones.size() < 8) {
                    zones.add(new HudLayout.Box(.75, .05, .2, .25));
                    selected = zones.size() - 1;
                    rebuildWidgets();
                  }
                })
            .active =
        zones.size() < 8;
    if (zones.isEmpty()) return;
    selected = Math.clamp(selected, 0, zones.size() - 1);
    var zone = zones.get(selected);
    EditBox[] fields = new EditBox[4];
    String[] labels = {"Left %", "Top %", "Width %", "Height %"};
    double[] values = {zone.x(), zone.y(), zone.width(), zone.height()};
    int bw = (w - 44) / 4;
    for (int i = 0; i < 4; i++) {
      fields[i] =
          field(
              x + 16 + i * (bw + 4),
              body + 136,
              bw,
              labels[i],
              String.format(Locale.ROOT, "%.1f", values[i] * 100));
      fields[i].setMaxLength(8);
    }
    button(
        x + 16,
        body + 164,
        (w - 36) / 2,
        "Update region",
        "Use percentages: left/top start at zero; dimensions must fit on screen",
        () -> {
          double[] v =
              Arrays.stream(fields)
                  .mapToDouble(f -> Double.parseDouble(f.getValue().trim()) / 100)
                  .toArray();
          var candidate = new ArrayList<>(zones);
          candidate.set(selected, new HudLayout.Box(v[0], v[1], v[2], v[3]));
          OverlayZones.save(candidate);
          zones.clear();
          zones.addAll(candidate);
          notice = "Region saved";
          rebuildWidgets();
        });
    button(
        x + 20 + (w - 36) / 2,
        body + 164,
        (w - 36) / 2,
        "Remove region",
        "Remove this exclusion region; no files are deleted",
        () -> {
          zones.remove(selected);
          OverlayZones.save(zones);
          rebuildWidgets();
        });
    button(
        x + 16,
        body + 196,
        w - 32,
        "Save all regions",
        "HUD elements move away from these regions during gameplay",
        () -> {
          OverlayZones.save(zones);
          notice = "Reserved regions saved";
        });
  }

  @Override
  protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
    int ink = NocturneClient.SETTINGS.uiText.argb();
    text(g, "Hotbar, subtitles and sidebar are handled automatically.", body, ink);
    text(
        g,
        "Reserve a region for minimaps or overlays without a Nocturne provider.",
        body + 18,
        ink);
    text(
        g,
        "Selected region " + (zones.isEmpty() ? "none" : selected + 1) + " / " + zones.size(),
        body + 112,
        ink);
    int left = x + 16, top = body + 230, width = w - 32, height = 48;
    g.fill(left, top, left + width, top + height, 0x99101820);
    for (int i = 0; i < zones.size(); i++) {
      var z = zones.get(i);
      g.fill(
          left + (int) (z.x() * width),
          top + (int) (z.y() * height),
          left + (int) ((z.x() + z.width()) * width),
          top + (int) ((z.y() + z.height()) * height),
          i == selected ? 0xaaa78bfa : 0x88667788);
    }
  }
}
