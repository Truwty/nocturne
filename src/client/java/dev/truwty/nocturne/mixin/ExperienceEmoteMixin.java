package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.experience.Experience;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class ExperienceEmoteMixin {
    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"),
            require = 1)
    private void nocturne$emote(AvatarRenderState state, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        String emote = Experience.emoteName();
        if (player == null
                || state.id != player.getId()
                || emote.isEmpty()
                || !Accessibility.textMotion()) return;
        var model = (PlayerModel) (Object) this;
        float wave = (float) Math.sin(state.ageInTicks * .35);
        switch (emote) {
            case "67" -> {
                model.leftArm.xRot = -1.2f + wave * .45f;
                model.rightArm.xRot = -1.2f - wave * .45f;
                model.leftArm.zRot = -.55f;
                model.rightArm.zRot = .55f;
            }
            case "Wave" -> {
                model.rightArm.xRot = -2.7f;
                model.rightArm.zRot = .35f + wave * .35f;
            }
            case "Bow" -> {
                model.body.xRot = .6f;
                model.head.xRot = .4f;
                model.leftArm.xRot = .6f;
                model.rightArm.xRot = .6f;
            }
            case "Celebrate" -> {
                model.leftArm.zRot = -2.6f + wave * .15f;
                model.rightArm.zRot = 2.6f - wave * .15f;
            }
            case "T-pose" -> {
                model.leftArm.zRot = -1.57f;
                model.rightArm.zRot = 1.57f;
                model.leftArm.xRot = model.rightArm.xRot = 0;
            }
            case "Dance" -> {
                model.body.zRot = wave * .12f;
                model.leftArm.xRot = wave;
                model.rightArm.xRot = -wave;
                model.leftLeg.xRot = -wave * .4f;
                model.rightLeg.xRot = wave * .4f;
            }
            case "Heart" -> {
                model.leftArm.xRot = model.rightArm.xRot = -1.4f;
                model.leftArm.zRot = .5f;
                model.rightArm.zRot = -.5f;
            }
            default -> {}
        }
        model.leftSleeve.xRot = model.leftArm.xRot;
        model.leftSleeve.yRot = model.leftArm.yRot;
        model.leftSleeve.zRot = model.leftArm.zRot;
        model.rightSleeve.xRot = model.rightArm.xRot;
        model.rightSleeve.yRot = model.rightArm.yRot;
        model.rightSleeve.zRot = model.rightArm.zRot;
        model.jacket.xRot = model.body.xRot;
        model.jacket.zRot = model.body.zRot;
        model.leftPants.xRot = model.leftLeg.xRot;
        model.rightPants.xRot = model.rightLeg.xRot;
    }
}
