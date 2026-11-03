package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.experience.*;

import net.minecraft.client.gui.Gui;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Gui.class)
public abstract class ExperienceHotbarMixin {
    @Shadow @Final private static Identifier HOTBAR_SELECTION_SPRITE;
    @Unique private double nocturne$x;
    @Unique private long nocturne$frame;

    @ModifyArgs(
            method = "extractItemHotbar",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            require = 1)
    private void nocturne$smoothSelection(Args args) {
        if (!HOTBAR_SELECTION_SPRITE.equals(args.get(1))) return;
        int target = args.get(2);
        long now = System.nanoTime();
        double elapsed = (now - nocturne$frame) / 1e9;
        if (!Experience.SETTINGS.on(ExperienceSettings.Option.HOTBAR)
                || !Accessibility.textMotion()
                || elapsed > .25
                || nocturne$frame == 0) nocturne$x = target;
        else nocturne$x += (target - nocturne$x) * (1 - Math.exp(-Math.max(0, elapsed) * 22));
        nocturne$frame = now;
        args.set(2, (int) Math.round(nocturne$x));
    }
}
