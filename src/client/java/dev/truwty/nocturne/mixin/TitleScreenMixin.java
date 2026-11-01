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

    @Inject(method = "init", at = @At("TAIL"))
    private void nocturne$addButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.literal("Nocturne"),
            button -> ClientAccess.setScreen(minecraft, NocturneClient.settingsScreen(this)))
            .bounds(width - 94, 10, 84, 20).build());
    }
}
