package dev.truwty.nocturne.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import dev.truwty.nocturne.experience.*;
import dev.truwty.nocturne.gui.Draw;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ExperienceSeasonMixin {
    @ModifyReturnValue(method = "getBlockTint", at = @At("RETURN"), require = 1)
    private int nocturne$season(int original, BlockPos position, ColorResolver resolver) {
        int season = Experience.SETTINGS.get(ExperienceSettings.Option.SEASON);
        if (season == 0
                || (resolver != BiomeColors.FOLIAGE_COLOR_RESOLVER
                        && resolver != BiomeColors.GRASS_COLOR_RESOLVER
                        && resolver != BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER)) return original;
        int color =
                switch (season) {
                    case 1 -> 0xFF95C779;
                    case 2 -> 0xFF58A04C;
                    case 3 -> 0xFFD99353;
                    default -> 0xFFCEDBD9;
                };
        return Draw.mix(0xFF000000 | original, color, .55f) & 0xFFFFFF;
    }
}
