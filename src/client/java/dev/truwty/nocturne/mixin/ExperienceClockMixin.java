package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.experience.*;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public abstract class ExperienceClockMixin {
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true, require = 1)
    private void nocturne$localTime(Holder<WorldClock> clock, CallbackInfoReturnable<Long> result) {
        int setting = Experience.SETTINGS.get(ExperienceSettings.Option.TIME);
        if (setting != 0 && clock.is(WorldClocks.OVERWORLD)) {
            long time =
                    switch (setting) {
                        case 1 -> 23000;
                        case 2 -> 6000;
                        case 3 -> 12500;
                        default -> 18000;
                    };
            result.setReturnValue(Math.floorDiv(result.getReturnValue(), 24000) * 24000 + time);
        }
    }
}
