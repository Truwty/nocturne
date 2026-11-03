package dev.truwty.nocturne.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import dev.truwty.nocturne.experience.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.rendertype.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class ExperienceSkinMixin {
    private static boolean local(LivingEntityRenderState state) {
        var player = Minecraft.getInstance().player;
        return player != null
                && state instanceof AvatarRenderState avatar
                && avatar.id == player.getId()
                && !state.isInvisible;
    }

    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"), require = 1)
    private RenderType nocturne$glass(
            RenderType original,
            LivingEntityRenderState state,
            boolean visible,
            boolean translucent,
            boolean glowing) {
        if (original == null
                || !local(state)
                || !Experience.SETTINGS.on(ExperienceSettings.Option.SKIN)) return original;
        return RenderTypes.entityTranslucent(((AvatarRenderState) state).skin.body().texturePath());
    }

    @ModifyReturnValue(method = "getModelTint", at = @At("RETURN"), require = 1)
    private int nocturne$opacity(int original, LivingEntityRenderState state) {
        if (!local(state) || !Experience.SETTINGS.on(ExperienceSettings.Option.SKIN))
            return original;
        return (original & 0xFFFFFF)
                | (Experience.SETTINGS.get(ExperienceSettings.Option.SKIN) == 1
                        ? 0xA0000000
                        : 0x60000000);
    }
}
