package dev.truwty.nocturne.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.lyrics.LyricsRenderer;
import dev.truwty.nocturne.platform.LyricFrame;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRenderingMixin {
    @Inject(method="submitEntities", at=@At("HEAD"), require=1)
    private void nocturne$submitLyrics(PoseStack poses, LevelRenderState state,
            SubmitNodeCollector submits, CallbackInfo callback) {
        if(NocturneClient.SETTINGS!=null) {
            LyricsRenderer.render(new LyricFrame(state.cameraRenderState.pos, poses, submits));
        }
    }
}
