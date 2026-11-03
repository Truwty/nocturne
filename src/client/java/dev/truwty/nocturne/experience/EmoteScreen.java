package dev.truwty.nocturne.experience;

import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class EmoteScreen extends StudioPanel {
    public EmoteScreen(Screen parent) {
        super(parent, "Emotes / B");
    }

    @Override
    protected int contentHeight() {
        return 230;
    }

    @Override
    protected void build() {
        String[] names = {"67", "Wave", "Bow", "Celebrate", "T-pose", "Dance", "Heart", "Stop"};
        int cx = x + w / 2, cy = body + 110;
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            double a = -Math.PI / 2 + i * Math.PI / 4;
            button(
                    cx + (int) (Math.cos(a) * Math.min(w * .32, 160)) - 40,
                    cy + (int) (Math.sin(a) * 78) - 10,
                    80,
                    name,
                    "Local third-person emote. Moving cancels the animation.",
                    () -> {
                        Experience.emote(name.equals("Stop") ? "" : name);
                        ClientAccess.setScreen(minecraft, null);
                    });
        }
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        text(
                g,
                "Visible locally in third person. No server emote messages.",
                body + 216,
                0xFFB5C9D6);
    }
}
