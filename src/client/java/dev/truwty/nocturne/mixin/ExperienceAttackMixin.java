package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.experience.Experience;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class ExperienceAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 1)
    private void nocturne$observeAttack(Player player, Entity target, CallbackInfo callback) {
        Experience.attack(target);
    }
}
