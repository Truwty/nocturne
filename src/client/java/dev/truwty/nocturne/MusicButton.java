package dev.truwty.nocturne;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class MusicButton extends Button {
    private final boolean accent;

    public MusicButton(int x, int y, int width, int height, String text, boolean accent, OnPress action) {
        super(x, y, width, height, Component.literal(text), action, DEFAULT_NARRATION);
        this.accent = accent;
    }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean focused = isHoveredOrFocused();
        int background = accent ? (focused ? 0xFFA0FFE8 : MusicVisuals.CYAN) : (focused ? 0xFF303A54 : 0xFF1A2235);
        MusicVisuals.panel(g, getX(), getY(), getWidth(), getHeight(), background);
        if (focused) MusicVisuals.outline(g, getX(), getY(), getWidth(), getHeight(), MusicVisuals.VIOLET);
        var font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int color = !active ? 0xFF596078 : accent ? 0xFF102829 : MusicVisuals.WHITE;
        g.text(font, label, getX() + (getWidth() - font.width(label)) / 2,
                getY() + (getHeight() - 8) / 2, color, false);
    }
}
