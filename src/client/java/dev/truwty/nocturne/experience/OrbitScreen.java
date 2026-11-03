package dev.truwty.nocturne.experience;

import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public final class OrbitScreen extends Screen {
    private final Screen parent;

    public OrbitScreen(Screen parent) {
        super(Component.literal("Nocturne Orbit"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int cx = width / 2,
                cy = height / 2,
                bw = Math.clamp(width / 4, 70, 132),
                radius = Math.min(135, width / 3);
        var groups = ExperienceSettings.Group.values();
        String[] names = {
            "World & sky", "Combat", "Cosmetics", "Movement FX", "Interface", "Textures & fun"
        };
        for (int i = 0; i < groups.length; i++) {
            var group = groups[i];
            double a = -Math.PI / 2 + i * Math.PI / 3;
            int bx = cx + (int) (Math.cos(a) * radius) - bw / 2,
                    by = cy + (int) (Math.sin(a) * Math.min(86, height * .28)) - 10;
            addRenderableWidget(
                    StudioButton.create(
                                    Draw.comp(names[i]),
                                    b ->
                                            ClientAccess.setScreen(
                                                    minecraft, new ExperienceScreen(this, group)))
                            .bounds(bx, by, bw, 20)
                            .build());
        }
        addRenderableWidget(
                StudioButton.create(
                                Draw.comp("Music & lyrics"),
                                b -> ClientAccess.setScreen(minecraft, new StudioScreen(this)))
                        .bounds(12, height - 30, 130, 20)
                        .build());
        addRenderableWidget(
                StudioButton.create(
                                Draw.comp("HUD editor"),
                                b -> ClientAccess.setScreen(minecraft, new HudEditorScreen(this)))
                        .bounds(148, height - 30, 100, 20)
                        .build());
        addRenderableWidget(
                StudioButton.create(Draw.comp("Done"), b -> onClose())
                        .bounds(width - 76, height - 30, 64, 20)
                        .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xCB080E18);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int cx = width / 2, cy = height / 2;
        Draw.text(
                g,
                font,
                "N O C T U R N E  /  O R B I T",
                cx - font.width("N O C T U R N E  /  O R B I T") / 2,
                14,
                Experience.color(),
                false);
        Draw.ring(g, cx, cy, Math.min(65, height * .21f), 0x446BE6BB, 1);
        if (minecraft.player != null)
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    g, cx - 36, cy - 56, cx + 36, cy + 58, 45, .0625f, mx, my, minecraft.player);
        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public void onClose() {
        Experience.save();
        ClientAccess.setScreen(minecraft, parent);
    }
}
