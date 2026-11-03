package dev.truwty.nocturne.mixin;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) { super(title); }

    @org.spongepowered.asm.mixin.Unique private static boolean nocturne$introPlayed;
    @Inject(method="tick",at=@At("TAIL"))
    private void nocturne$intro(CallbackInfo callback) {
        if(!nocturne$introPlayed&&NocturneClient.SETTINGS!=null&&dev.truwty.nocturne.experience.Experience.SETTINGS.on(dev.truwty.nocturne.experience.ExperienceSettings.Option.INTRO)&&dev.truwty.nocturne.accessibility.Accessibility.textMotion()) {nocturne$introPlayed=true;ClientAccess.setScreen(minecraft,new dev.truwty.nocturne.experience.IntroScreen(this));}
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void nocturne$addButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.literal("Nocturne"),
            button -> ClientAccess.setScreen(minecraft, NocturneClient.settingsScreen(this)))
            .bounds(width - 94, 10, 84, 20).build());
    }
}
