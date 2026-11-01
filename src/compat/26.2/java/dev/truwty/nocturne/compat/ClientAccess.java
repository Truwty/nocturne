package dev.truwty.nocturne.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;

/** Compile-time bindings for Minecraft 26.2; no reflective fallback or version spoofing. */
public final class ClientAccess {
  private ClientAccess() {}

  public static com.mojang.blaze3d.pipeline.RenderTarget renderTarget(Minecraft mc) {
    return mc.gameRenderer.mainRenderTarget();
  }

  public static Screen screen(Minecraft mc) {
    return mc.gui.screen();
  }

  public static void setScreen(Minecraft mc, Screen screen) {
    mc.gui.setScreen(screen);
  }

  public static Camera camera(Minecraft mc) {
    return mc.gameRenderer.mainCamera();
  }

  public static boolean hiddenHud(Minecraft mc) {
    return mc.gui.hud.isHidden();
  }

  public static ToastManager toasts(Minecraft mc) {
    return mc.gui.toastManager();
  }

  public static ChatListener chat(Minecraft mc) {
    return mc.gui.chatListener();
  }

  public static void overlay(Minecraft mc, Component text, boolean animate) {
    mc.gui.hud.setOverlayMessage(text, animate);
  }

  public static void openUri(java.net.URI uri) {
    net.minecraft.util.Util.getPlatform().openUri(uri);
  }

  public static boolean swinging(net.minecraft.client.player.LocalPlayer player) {
    return player.swinging;
  }

  public static com.mojang.blaze3d.platform.InputConstants.Type keyType() {
    return com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM;
  }

  public static void rotate(
      com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Quaternionfc rotation) {
    pose.mulPose(rotation);
  }

  public static Integer cloudColor(
      net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.phys.Vec3 camera) {
    return level
        .environmentAttributes()
        .getValue(net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_COLOR, camera);
  }

  public static Integer sunColor(
      net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.phys.Vec3 camera) {
    return level
        .environmentAttributes()
        .getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUNRISE_SUNSET_COLOR, camera);
  }

  public static Integer skyColor(
      net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.phys.Vec3 camera) {
    return level
        .environmentAttributes()
        .getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_COLOR, camera);
  }
}
