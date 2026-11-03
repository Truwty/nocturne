package dev.truwty.nocturne.experience;

import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.Draw;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.PlayerSkin;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Supplier;

public final class IntroScreen extends Screen {
    private final Screen parent;
    private long start;
    private Supplier<PlayerSkin> skin;

    public IntroScreen(Screen parent) {
        super(Component.literal("Nocturne intro / Esc to skip"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (start == 0) start = System.nanoTime();
        if (skin == null)
            skin = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
    }

    @Override
    public void tick() {
        if (System.nanoTime() - start > 1_800_000_000L || !Accessibility.textMotion()) onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Draw.rect(g, 0, 0, width, height, 0xFF0B1019);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        float t = Math.clamp((System.nanoTime() - start) / 1_800_000_000f, 0, 1);
        var avatar = new AvatarRenderState();
        avatar.entityType = EntityType.PLAYER;
        avatar.skin = skin.get();
        avatar.boundingBoxHeight = 1.8f;
        avatar.boundingBoxWidth = .6f;
        avatar.scale = avatar.ageScale = 1;
        avatar.mainArm = avatar.attackArm = HumanoidArm.RIGHT;
        avatar.attackTime = Math.clamp((t - .15f) * 1.5f, 0, 1);
        avatar.swingAnimationType = net.minecraft.world.item.SwingAnimationType.WHACK;
        avatar.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        avatar.rightHandItemStack =
                new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.DIAMOND_SWORD);
        minecraft
                .getItemModelResolver()
                .updateForTopItem(
                        avatar.rightHandItemState,
                        avatar.rightHandItemStack,
                        net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        minecraft.level,
                        null,
                        0);
        avatar.bodyRot = 150;
        avatar.yRot = 20;
        avatar.id = -1;
        int size = Math.max(24, Math.min(100, height / 3));
        g.entity(
                avatar,
                size,
                new Vector3f(0, .95f, 0),
                new Quaternionf().rotateZ((float) Math.PI),
                new Quaternionf(),
                width / 2 - 100,
                10,
                width / 2 + 100,
                height - 45);
        if (t > .32f && t < .75f) {
            float cut = (t - .32f) / .43f;
            g.pose().pushMatrix();
            g.pose().translate(width * .5f, height * .45f);
            g.pose().rotate(-.42f);
            Draw.rect(g, -width * cut, -2, width * cut * 2, 3, 0xFFA8F1D3);
            g.pose().popMatrix();
        }
        String name = "N O C T U R N E";
        Draw.text(g, font, name, (width - font.width(name)) / 2, height - 38, 0xFFA8F1D3, false);
        Draw.text(g, font, "Esc to skip", 12, height - 17, 0xFF8195A8, false);
    }

    @Override
    public void onClose() {
        ClientAccess.setScreen(minecraft, parent);
    }
}
