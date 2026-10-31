package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.lyrics.LyricsRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
   @ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"), require = 1)
   private float nocturne$beatZoom(float original) {
      return original * LyricsRenderer.fovKick();
   }
}
