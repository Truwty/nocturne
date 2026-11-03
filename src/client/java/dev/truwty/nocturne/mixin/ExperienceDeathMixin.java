package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.experience.*;
import dev.truwty.nocturne.gui.Draw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public abstract class ExperienceDeathMixin {
    private static int style() {
        return Experience.SETTINGS.get(ExperienceSettings.Option.DEATH);
    }

    private static int ink() {
        return switch (style()) {
            case 1, 2 -> 0xFFFF696D;
            case 3 -> 0xFFFFD374;
            case 5 -> 0xFFB8A6FF;
            case 6 -> 0xFF17222B;
            case 8 -> 0xFFFFABC8;
            default -> Experience.color();
        };
    }

    @ModifyArg(
            method = "visitText",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/network/chat/Component;)V",
                            ordinal = 0),
            index = 3,
            require = 1)
    private Component nocturne$title(Component original) {
        if (style() == 0) return original;
        String title =
                switch (style()) {
                    case 1 -> "DETERMINATION";
                    case 2 -> "WASTED";
                    case 3 -> "GAME OVER";
                    case 4 -> "THE LAST FRAME";
                    case 5 -> "AMONG THE STARS";
                    case 6 -> "Try again.";
                    case 7 -> "SIGNAL LOST";
                    default -> "BLOOM AGAIN";
                };
        return Component.literal(title).withStyle(s -> s.withColor(ink() & 0xFFFFFF));
    }

    @ModifyArg(
            method = "visitText",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/network/chat/Component;)V",
                            ordinal = 1),
            index = 3,
            require = 1)
    private Component nocturne$cause(Component original) {
        return style() == 6 ? original.copy().withStyle(s -> s.withColor(0x17222B)) : original;
    }

    @ModifyArg(
            method = "visitText",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/network/chat/Component;)V",
                            ordinal = 2),
            index = 3,
            require = 1)
    private Component nocturne$score(Component original) {
        return style() == 6 ? original.copy().withStyle(s -> s.withColor(0x17222B)) : original;
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 1)
    private void nocturne$deathStyle(
            GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo callback) {
        int style = style();
        if (style == 0) return;
        callback.cancel();
        var mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth(),
                h = mc.getWindow().getGuiScaledHeight(),
                color = ink();
        Draw.rect(g, 0, 0, w, h, style == 6 ? 0xFFF1EEE8 : style == 1 ? 0xFF000000 : 0xF00B1019);
        switch (style) {
            case 1 -> {
                int cx = w / 2;
                Draw.rect(g, cx - 7, 22, 5, 4, color);
                Draw.rect(g, cx + 2, 22, 5, 4, color);
                Draw.rect(g, cx - 8, 26, 16, 4, color);
                Draw.rect(g, cx - 5, 30, 10, 3, color);
                Draw.rect(g, cx - 2, 33, 4, 3, color);
            }
            case 2 -> Draw.rect(g, 0, 48, w, 30, 0x662A0B15);
            case 3 -> {
                Draw.rect(g, 8, 8, w - 16, 2, color);
                Draw.rect(g, 8, h - 10, w - 16, 2, color);
                Draw.rect(g, 8, 8, 2, h - 16, color);
                Draw.rect(g, w - 10, 8, 2, h - 16, color);
                for (int yy = 12; yy < h; yy += 4) Draw.rect(g, 12, yy, w - 24, 1, 0x0AFFFFFF);
            }
            case 4 -> {
                Draw.rect(g, 0, 0, w, 23, 0xFF000000);
                Draw.rect(g, 0, h - 23, w, 23, 0xFF000000);
                Draw.rect(g, w / 2 - 70, 30, 140, 1, color);
            }
            case 5 -> {
                for (int i = 0; i < 48; i++) {
                    int xx = Math.floorMod(i * 97 + 17, Math.max(1, w)),
                            yy = Math.floorMod(i * 61 + 29, Math.max(1, h));
                    Draw.rect(
                            g, xx, yy, i % 4 == 0 ? 2 : 1, 1, Draw.withAlpha(color, 80 + i % 120));
                }
            }
            case 6 -> Draw.rect(g, w / 2 - 28, 36, 56, 1, 0xFF17222B);
            case 7 -> {
                for (int i = 0; i < 9; i++)
                    Draw.rect(
                            g,
                            (i * 71) % Math.max(1, w),
                            15 + i * 17,
                            20 + i * 3,
                            2,
                            Draw.withAlpha(color, 30 + i * 5));
                Draw.rect(g, 0, h - 18, w, 1, color);
            }
            case 8 -> {
                for (int i = 0; i < 14; i++) {
                    int xx = i % 2 == 0 ? 18 : w - 18, yy = 18 + i * h / 16;
                    Draw.round(g, xx - 4, yy - 2, 8, 4, 2, 0x88FFABC8);
                    Draw.round(g, xx - 2, yy - 4, 4, 8, 2, 0x88FFABC8);
                }
            }
            default -> {}
        }
        // Vanilla narration, death cause, score, respawn rules and button cooldowns stay intact.
    }
}
