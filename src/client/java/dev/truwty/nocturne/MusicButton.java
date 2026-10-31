package dev.truwty.nocturne;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class MusicButton extends Button {
    private final boolean accent;
    private final Animation hover = new Animation(0);
    private boolean selected;

    public MusicButton selected(boolean value) { selected = value; return this; }

    public MusicButton(int x, int y, int width, int height, String text, boolean accent, OnPress action) {
        super(x, y, width, height, Component.literal(text), action, DEFAULT_NARRATION);
        this.accent = accent;
    }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme theme = Theme.current();
        double focus = hover.approach(active && isHoveredOrFocused() ? 1 : 0, 18, NocturneClient.preferences == null || NocturneClient.preferences.animations);
        int background = accent ? Theme.blend(theme.highlight(), 0xFFFFFFFF, focus * 0.12)
                : Theme.blend(selected ? theme.elevated : theme.surface, theme.border, focus * 0.65);
        MusicVisuals.panel(g, getX(), getY(), getWidth(), getHeight(), background);
        if (selected) g.fill(getX() + 9, getY() + getHeight() - 2, getX() + getWidth() - 9, getY() + getHeight() - 1, theme.highlight());
        if (isFocused()) MusicVisuals.outline(g, getX(), getY(), getWidth(), getHeight(), theme.highlight());
        var font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int color = !active ? Theme.blend(theme.surface, theme.muted, 0.55) : accent ? theme.onAccent : theme.text;
        if (label.equals("Play") || label.equals("Pause") || label.equals("Previous") || label.equals("Next")) {
            MusicVisuals.transportIcon(g, label, getX() + getWidth() / 2, getY() + getHeight() / 2, color);
            return;
        }
        g.text(font, label, getX() + (getWidth() - font.width(label)) / 2,
                getY() + (getHeight() - 8) / 2, color, false);
    }
}
