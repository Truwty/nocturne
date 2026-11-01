package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native keyboard focus, narration and click behavior with Nocturne styling. */
public final class StudioButton extends Button {
    private float hover;
    private long lastFrame;
    private StudioButton(int x, int y, int width, int height, Component label, OnPress action) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
        setTooltipDelay(java.time.Duration.ofMillis(450));
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Enter / A: activate · Tab / D-pad: focus · Esc / B: back")));
    }
    public static Factory create(Component label, OnPress action) { return new Factory(label, action); }
    public static final class Factory {
        private final Component label;
        private final OnPress action;
        private int x, y, width = 150, height = 20;
        private Factory(Component label, OnPress action) { this.label = label; this.action = action; }
        public Factory bounds(int x, int y, int width, int height) { this.x=x; this.y=y; this.width=width; this.height=height; return this; }
        public Factory width(int width) { this.width=width; return this; }
        public Button build() { return new StudioButton(x,y,width,height,label,action); }
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var s = NocturneClient.SETTINGS;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now-lastFrame)/1e9f); lastFrame=now;
        float target = active && isHoveredOrFocused() ? 1 : 0;
        hover = s.reducedMotion.on() ? target : hover + (target-hover) * (float)-Math.expm1(-dt*18);
        int accent=s.uiAccent.argb(), text=s.uiText.argb();
        int surface=Draw.mix(s.uiBackground.argb(), text, 0.065f);
        Draw.round(g,getX(),getY(),width,height,4,Draw.mix(surface,accent,hover*0.21f));
        Draw.roundOutline(g,getX(),getY(),width,height,4,Draw.alpha(accent,active ? 0.22f+hover*0.55f : 0.08f));
        if (isFocused()) Draw.rect(g,getX()+1,getY()+5,2,height-10,accent);
        var font=Minecraft.getInstance().font;
        var label=dev.truwty.nocturne.lyrics.LyricText.visual(Component.literal(font.substrByWidth(getMessage(),Math.max(1,width-12)).getString()).withStyle(getMessage().getStyle()));
        g.text(font,label,getX()+(width-font.width(label))/2,getY()+(height-8)/2,Draw.alpha(text,active ? 1 : 0.35f),false);
    }
}
