package dev.truwty.nocturne.compat;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public final class TestAccess {
  private TestAccess() {}

  public static void waitForChunks(TestSingleplayerContext world) {
    world.getClientWorld().waitForChunksRender();
  }
  public static void resizeGui(net.minecraft.client.Minecraft mc) { mc.resizeDisplay(); }
  public static void debugOverlay(net.minecraft.client.Minecraft mc, boolean visible) { mc.debugEntries.setOverlayVisible(visible); }
}
