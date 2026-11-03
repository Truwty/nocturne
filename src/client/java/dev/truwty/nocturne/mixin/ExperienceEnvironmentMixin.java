package dev.truwty.nocturne.mixin;

import static dev.truwty.nocturne.experience.ExperienceSettings.Option.*;

import dev.truwty.nocturne.experience.Experience;

import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.*;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnvironmentAttributeSystem.class)
public abstract class ExperienceEnvironmentMixin {
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, require = 1)
    private void nocturne$environment(
            EnvironmentAttribute<?> attribute,
            Vec3 at,
            SpatialAttributeInterpolator interpolation,
            CallbackInfoReturnable<Object> result) {
        var mc = Minecraft.getInstance();
        // The integrated server also uses this class. Never modify its attribute queries.
        if (!mc.isSameThread()
                || mc.level == null
                || mc.level.environmentAttributes() != (Object) this) return;
        var settings = Experience.SETTINGS;
        if (settings.on(WORLD_LOOK)) {
            int color = Experience.color() & 0xFFFFFF;
            if (attribute == EnvironmentAttributes.SKY_COLOR)
                result.setReturnValue(
                        dev.truwty.nocturne.gui.Draw.mix(
                                        0xFF000000 | ((Number) result.getReturnValue()).intValue(),
                                        0xFF000000 | color,
                                        .38f)
                                & 0xFFFFFF);
            if (attribute == EnvironmentAttributes.CLOUD_COLOR)
                result.setReturnValue(0xDD000000 | color);
            if (attribute == EnvironmentAttributes.FOG_COLOR)
                result.setReturnValue(
                        dev.truwty.nocturne.gui.Draw.mix(
                                        0xFF000000 | ((Number) result.getReturnValue()).intValue(),
                                        0xFF000000 | color,
                                        .2f)
                                & 0xFFFFFF);
        }
        if (settings.on(FOG)) {
            if (attribute == EnvironmentAttributes.FOG_START_DISTANCE)
                result.setReturnValue(
                        switch (settings.get(FOG)) {
                            case 1 -> 8f;
                            case 2 -> 32f;
                            case 3 -> 96f;
                            default -> settings.fogDistance() * .25f;
                        });
            if (attribute == EnvironmentAttributes.FOG_END_DISTANCE)
                result.setReturnValue(
                        switch (settings.get(FOG)) {
                            case 1 -> 48f;
                            case 2 -> 128f;
                            case 3 -> 512f;
                            default -> (float) settings.fogDistance();
                        });
        }
        if (settings.get(FOG) == 4 && attribute == EnvironmentAttributes.FOG_COLOR)
            result.setReturnValue(
                    dev.truwty.nocturne.gui.Draw.hsv(settings.fogHue(), .35f, .8f) & 0xFFFFFF);
        if (settings.on(CLOUDS) && attribute == EnvironmentAttributes.CLOUD_HEIGHT)
            result.setReturnValue(
                    switch (settings.get(CLOUDS)) {
                        case 1 -> 100f;
                        case 2 -> 240f;
                        case 3 -> Float.NaN;
                        default -> (float) settings.cloudHeight();
                    });
    }
}
