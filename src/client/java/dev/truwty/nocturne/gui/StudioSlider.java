package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class StudioSlider extends AbstractSliderButton {
    protected StudioSlider(int x,int y,int width,int height,Component label,double value) { super(x,y,width,height,label,value);setTooltipDelay(java.time.Duration.ofMillis(450));setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Enter / A: edit slider · Left/right: adjust · Tab / D-pad: next control"))); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        var s=NocturneClient.SETTINGS;
        int text=Draw.alpha(s.uiText.argb(),active ? 1 : .35f);
        Draw.round(g,getX(),getY(),width,height,4,Draw.mix(s.uiBackground.argb(),s.uiText.argb(),.055f));
        int track=getY()+height-4;
        Draw.rect(g,getX()+5,track,width-10,1,Draw.alpha(s.uiText.argb(),.16f));
        Draw.rect(g,getX()+5,track,(int)((width-10)*value),1,Draw.alpha(s.uiAccent.argb(),active ? 1 : .3f));
        Draw.round(g,getX()+3+(int)((width-10)*value),track-2,4,5,2,text);
        if(isFocused()) Draw.roundOutline(g,getX(),getY(),width,height,4,s.uiAccent.argb());
        var font=Minecraft.getInstance().font;
        var label=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(),Math.max(1,width-12)));
        g.text(font,label,getX()+(width-font.width(label))/2,getY()+3,text,false);
    }
}
