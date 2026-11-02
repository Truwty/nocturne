package dev.truwty.nocturne.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.truwty.nocturne.lyrics.LyricsRain;
import java.util.List;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherRenderingMixin {
  @Inject(method = "render", at = @At("HEAD"), require = 1)
  private void nocturne$rainFrame(Vec3 cam, WeatherRenderState state, CallbackInfo ci) {
    LyricsRain.frame(state.rainColumns);
  }

  @Inject(method = "renderInstances", at = @At("HEAD"), require = 1)
  private void nocturne$rainOrSnow(
      VertexConsumer consumer,
      List<?> pieces,
      Vec3 cam,
      float a,
      int radius,
      float intensity,
      CallbackInfo ci) {
    LyricsRain.which(pieces);
  }

  @ModifyExpressionValue(
      method = "renderInstances",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"),
      require = 1)
  private int nocturne$rainColour(int original) {
    return LyricsRain.tint(original);
  }
}
