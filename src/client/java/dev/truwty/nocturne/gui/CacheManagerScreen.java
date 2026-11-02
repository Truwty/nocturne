package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.Studio;
import dev.truwty.nocturne.lyrics.LyricsService;
import dev.truwty.nocturne.performance.CacheInventory;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class CacheManagerScreen extends StudioPanel {
  private static final ExecutorService IO =
      Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "Nocturne cache manager");
        thread.setDaemon(true);
        return thread;
      });
  private final CacheInventory inventory = new CacheInventory(Studio.directory().resolve("cache"));
  private List<CacheInventory.Entry> entries = List.of();
  private final Set<CacheInventory.Entry> selected = new HashSet<>();
  private CompletableFuture<List<CacheInventory.Entry>> pending;
  private int page;
  private boolean confirm;

  public CacheManagerScreen(Screen parent) {
    super(parent, "Cache manager");
    refresh(false);
  }

  private void refresh(boolean delete) {
    var remove = Set.copyOf(selected);
    pending =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                if (delete) {
                  for (var entry : remove) inventory.delete(entry);
                  LyricsService.INSTANCE.invalidateDownloadCaches();
                }
                return inventory.scan();
              } catch (java.io.IOException ex) {
                throw new CompletionException(ex);
              }
            },
            IO);
    notice = delete ? "Removing selected downloads…" : "Reading downloaded lyrics and thumbnails…";
  }

  @Override
  public void tick() {
    if (pending == null || !pending.isDone()) return;
    var finished = pending;
    pending = null;
    try {
      entries = finished.join();
      selected.clear();
      confirm = false;
      notice =
          entries.size()
              + " downloads · "
              + entries.stream().mapToLong(CacheInventory.Entry::bytes).sum() / 1024
              + " KiB";
    } catch (CompletionException ex) {
      notice = "Cache operation failed: " + ex.getCause().getMessage();
    }
    page = Math.min(page, Math.max(0, (entries.size() - 1) / 6));
    rebuildWidgets();
  }

  @Override
  protected int contentHeight() {
    return 264;
  }

  @Override
  protected void build() {
    for (int i = page * 6; i < Math.min(entries.size(), page * 6 + 6); i++) {
      var entry = entries.get(i);
      int yy = body + 20 + (i % 6) * 28;
      var button =
          button(
              x + 16,
              yy,
              w - 32,
              (selected.contains(entry) ? "✓ " : "○ ") + entry.label(),
              "Downloaded "
                  + Instant.ofEpochMilli(entry.modified())
                      .atZone(ZoneId.systemDefault())
                      .toLocalDateTime(),
              () -> {
                if (!selected.remove(entry)) selected.add(entry);
                confirm = false;
                rebuildWidgets();
              });
      button.active = pending == null;
    }
    button(
        x + 16,
        body + 196,
        80,
        "Previous",
        "Previous six downloads",
        () -> {
          page = Math.max(0, page - 1);
          rebuildWidgets();
        });
    button(
        x + 100,
        body + 196,
        80,
        "Next",
        "Next six downloads",
        () -> {
          if ((page + 1) * 6 < entries.size()) page++;
          rebuildWidgets();
        });
    button(
                x + 184,
                body + 196,
                w - 200,
                "Select page",
                "Select visible downloads only",
                () -> {
                  selected.addAll(
                      entries.subList(page * 6, Math.min(entries.size(), page * 6 + 6)));
                  confirm = false;
                  rebuildWidgets();
                })
            .active =
        pending == null;
    button(
                x + 16,
                body + 224,
                w - 32,
                confirm
                    ? "Confirm removal of " + selected.size() + " downloads"
                    : "Remove selected downloads",
                "Local lyric edits, presets and account details are preserved",
                () -> {
                  if (pending != null || selected.isEmpty()) return;
                  if (!confirm) {
                    confirm = true;
                    rebuildWidgets();
                  } else {
                    refresh(true);
                    rebuildWidgets();
                  }
                })
            .active =
        pending == null && !selected.isEmpty();
  }

  @Override
  protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
    text(g, "Downloaded copies only · song corrections and presets are kept", body, 0xffa8b4c7);
  }
}
