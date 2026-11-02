package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;

/**
 * Overlay providers use scaled GUI coordinates. Registrations are bounded and replaceable by ID.
 */
public final class HudAvoidance {
  private static final Map<String, BiFunction<Integer, Integer, HudLayout.Box>> PROVIDERS =
      new LinkedHashMap<>();

  private record Key(HudLayout.Box box, int width, int height, List<HudLayout.Box> regions) {}

  private static final Map<Key, HudLayout.Box> SOLUTIONS = new LinkedHashMap<>();
  private static HudLayout.Box sidebar;
  private static long nextSidebar;
  private static int sidebarWidth, sidebarHeight;

  private HudAvoidance() {}

  public static synchronized void register(
      String id, BiFunction<Integer, Integer, HudLayout.Box> provider) {
    if (id == null || id.isBlank() || id.length() > 100 || provider == null)
      throw new IllegalArgumentException("Invalid overlay provider");
    if (!PROVIDERS.containsKey(id) && PROVIDERS.size() >= 16)
      throw new IllegalStateException("Overlay provider limit reached");
    PROVIDERS.put(id, provider);
  }

  public static synchronized void unregister(String id) {
    PROVIDERS.remove(id);
  }

  public static HudLayout.Box place(HudLayout.Box box, int sw, int sh) {
    var mc = Minecraft.getInstance();
    if (!NocturneClient.SETTINGS.hudAvoidance.on()
        || mc.player == null
        || dev.truwty.nocturne.compat.ClientAccess.screen(mc) != null) return box;
    var blocked = new ArrayList<HudLayout.Box>();
    blocked.add(new HudLayout.Box(sw / 2.0 - 105, sh - 65, 210, 65));
    for (var zone : OverlayZones.current())
      blocked.add(
          new HudLayout.Box(zone.x() * sw, zone.y() * sh, zone.width() * sw, zone.height() * sh));
    if (mc.options.showSubtitles().get())
      blocked.add(new HudLayout.Box(Math.max(0, sw - 210), sh - 130, 210, 100));
    var sidebar = sidebar(mc, sw, sh);
    if (sidebar != null) blocked.add(sidebar);
    synchronized (HudAvoidance.class) {
      for (var provider : PROVIDERS.values()) {
        try {
          var region = provider.apply(sw, sh);
          if (valid(region)) blocked.add(region);
        } catch (RuntimeException ex) {
          NocturneClient.LOG.debug("HUD exclusion provider failed", ex);
        }
      }
    }
    var key = new Key(box, sw, sh, List.copyOf(blocked));
    var saved = SOLUTIONS.get(key);
    if (saved != null) return saved;
    var solution = solve(box, sw, sh, blocked);
    if (SOLUTIONS.size() >= 64) SOLUTIONS.remove(SOLUTIONS.keySet().iterator().next());
    SOLUTIONS.put(key, solution);
    return solution;
  }

  private static HudLayout.Box sidebar(Minecraft mc, int sw, int sh) {
    long now = System.nanoTime();
    if (now < nextSidebar && sw == sidebarWidth && sh == sidebarHeight) return sidebar;
    nextSidebar = now + 250_000_000L;
    sidebarWidth = sw;
    sidebarHeight = sh;
    sidebar = null;
    if (mc.level == null) return null;
    var scoreboard = mc.level.getScoreboard();
    var team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
    var slot = dev.truwty.nocturne.compat.ClientAccess.teamSidebarSlot(team);
    var objective = slot == null ? null : scoreboard.getDisplayObjective(slot);
    if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    if (objective == null) return null;
    int width = mc.font.width(objective.getDisplayName()), count = 0;
    var format =
        objective.numberFormatOrDefault(
            net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT);
    // Reserve the widest of all visible entries, so server ordering changes remain safe.
    for (var score : scoreboard.listPlayerScores(objective)) {
      if (score.isHidden()) continue;
      if (count >= 512) break;
      count++;
      var name =
          net.minecraft.world.scores.PlayerTeam.formatNameForTeam(
              scoreboard.getPlayersTeam(score.owner()), score.ownerName());
      int scoreWidth = mc.font.width(score.formatValue(format));
      width =
          Math.max(
              width,
              mc.font.width(name) + (scoreWidth == 0 ? 0 : mc.font.width(": ") + scoreWidth));
    }
    int height = Math.min(15, count) * 9, bottom = sh / 2 + height / 3;
    sidebar =
        new HudLayout.Box(
            Math.max(0, sw - width - 5),
            bottom - height - 10,
            Math.min(sw, width + 5),
            height + 10);
    return sidebar;
  }

  private static boolean valid(HudLayout.Box b) {
    return b != null
        && Double.isFinite(b.x())
        && Double.isFinite(b.y())
        && Double.isFinite(b.width())
        && Double.isFinite(b.height())
        && b.width() > 0
        && b.height() > 0;
  }

  private static double overlap(HudLayout.Box a, HudLayout.Box b) {
    return Math.max(
            0, Math.min(a.x() + a.width(), b.x() + b.width() + 4) - Math.max(a.x(), b.x() - 4))
        * Math.max(
            0, Math.min(a.y() + a.height(), b.y() + b.height() + 4) - Math.max(a.y(), b.y() - 4));
  }

  public static HudLayout.Box solve(
      HudLayout.Box box, int sw, int sh, List<HudLayout.Box> regions) {
    var blocked = regions.stream().filter(HudAvoidance::valid).limit(32).toList();
    if (blocked.stream().noneMatch(b -> overlap(box, b) > 0)) return box;
    var xs = new ArrayList<Double>();
    var ys = new ArrayList<Double>();
    xs.add(box.x());
    ys.add(box.y());
    for (var b : blocked) {
      xs.add(b.x() - box.width() - 4);
      xs.add(b.x() + b.width() + 4);
      ys.add(b.y() - box.height() - 4);
      ys.add(b.y() + b.height() + 4);
    }
    HudLayout.Box best = box;
    double bestOverlap = Double.POSITIVE_INFINITY, bestDistance = Double.POSITIVE_INFINITY;
    for (double xx : xs)
      for (double yy : ys) {
        var candidate =
            new HudLayout.Box(
                Math.clamp(xx, 8, Math.max(8, sw - 8 - box.width())),
                Math.clamp(yy, 8, Math.max(8, sh - 8 - box.height())),
                box.width(),
                box.height());
        double area = blocked.stream().mapToDouble(b -> overlap(candidate, b)).sum();
        double distance =
            Math.pow(candidate.x() - box.x(), 2) + Math.pow(candidate.y() - box.y(), 2);
        if (area < bestOverlap || area == bestOverlap && distance < bestDistance) {
          best = candidate;
          bestOverlap = area;
          bestDistance = distance;
        }
      }
    return best;
  }
}
