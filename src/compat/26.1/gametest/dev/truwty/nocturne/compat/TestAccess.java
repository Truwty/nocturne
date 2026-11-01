package dev.truwty.nocturne.compat;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

public final class TestAccess {
  private TestAccess() {}

  public static void waitForChunks(TestSingleplayerContext world) {
    world.getClientLevel().waitForChunksRender();
  }
}
