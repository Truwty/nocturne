package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.experience.*;
import dev.truwty.nocturne.lyrics.ShaderCompat;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ExperiencePostEffectMixin {
    @Shadow private Identifier postEffectId;
    @Shadow private boolean effectActive;
    @Unique private Identifier nocturne$owned;

    @Unique
    private static final Identifier[] nocturne$looks = {
        null,
        Identifier.fromNamespaceAndPath("nocturne", "nocturne"),
        Identifier.fromNamespaceAndPath("nocturne", "cyberspace"),
        Identifier.fromNamespaceAndPath("nocturne", "golden_hour"),
        Identifier.fromNamespaceAndPath("nocturne", "glacier"),
        Identifier.fromNamespaceAndPath("nocturne", "rosewood"),
        Identifier.fromNamespaceAndPath("nocturne", "monochrome")
    };

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 1)
    private void nocturne$look(DeltaTracker delta, CallbackInfo callback) {
        int look = Experience.SETTINGS.get(ExperienceSettings.Option.WORLD_LOOK);
        if (look == 0 || Accessibility.photosensitive() || ShaderCompat.shaderPackOn()) {
            if (nocturne$owned != null && nocturne$owned.equals(postEffectId)) postEffectId = null;
            nocturne$owned = null;
            return;
        }
        // Preserve spectator effects and any post effect another mod owns.
        if (postEffectId != null && !postEffectId.equals(nocturne$owned)) return;
        nocturne$owned = nocturne$looks[look];
        postEffectId = nocturne$owned;
        effectActive = true;
    }
}
