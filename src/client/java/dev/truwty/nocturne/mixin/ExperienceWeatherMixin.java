package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.experience.*;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class ExperienceWeatherMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true, require = 1)
    private void nocturne$localRain(float delta, CallbackInfoReturnable<Float> result) {
        if (!((Object) this instanceof ClientLevel)) return;
        int weather = Experience.SETTINGS.get(ExperienceSettings.Option.WEATHER);
        if (weather != 0) result.setReturnValue(weather == 2 ? 1f : 0f);
    }
}
