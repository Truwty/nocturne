package dev.truwty.nocturne.gui;

import com.google.gson.JsonObject;
import dev.truwty.nocturne.*;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.lyrics.LyricsService;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class LyricPresetsScreen extends StudioPanel {
    private static final List<String> CATEGORIES=java.util.stream.Stream.concat(java.util.stream.Stream.of("All"),LyricLooks.all().stream().map(LyricLooks.Look::category).distinct()).toList();
    private String query="",selected="Perfection",saveName="";
    private int category,page;
    private boolean favorites,pending;
    private List<String> names=List.of();
    private JsonObject imported;
    public LyricPresetsScreen(Screen parent) {super(parent,"Lyric presets / 57 built-in looks");}
    @Override protected int contentHeight() {return 365;}
    private boolean builtin(String name) {return LyricLooks.all().stream().anyMatch(p->p.name().equals(name));}
    private JsonObject values() {
        if(imported!=null)return imported.deepCopy();
        if(builtin(selected))return LyricLooks.named(selected).values();
        var saved=Studio.library().preset(LyricLooks.GROUP,selected);
        if(saved==null)throw new IllegalArgumentException("Select a lyric preset first");return saved;
    }
    private List<String> available() {
        var result=new ArrayList<String>();
        for(var look:LyricLooks.search(query,CATEGORIES.get(category)))result.add(look.name());
        if(category==0)for(String name:Studio.library().presets(LyricLooks.GROUP))
            if(!result.contains(name)&&name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))result.add(name);
        if(favorites)result.removeIf(name->!Studio.library().favorite(LyricLooks.GROUP,name));
        return List.copyOf(result);
    }
    @Override protected void build() {
        var search=field(x+16,body,w-170,"Search lyric presets",query);search.setMaxLength(80);
        search.setResponder(value->{query=value;page=0;pending=true;});
        button(x+w-148,body,132,CATEGORIES.get(category),"Filter by look family",()->{category=(category+1)%CATEGORIES.size();page=0;rebuildWidgets();});
        names=available();page=Math.clamp(page,0,Math.max(0,(names.size()-1)/6));
        for(int i=page*6;i<Math.min(names.size(),page*6+6);i++) {
            String name=names.get(i);int cell=i%6,col=cell%2,by=body+28+(cell/2)*24,bw=(w-36)/2;
            String label=(selected.equals(name)?"> ":"")+name;
            button(x+16+col*(bw+4),by,bw,font.plainSubstrByWidth(label,bw-12),builtin(name)?LyricLooks.named(name).description():"Saved custom lyric look",()->{selected=name;imported=null;rebuildWidgets();});
        }
        button(x+16,body+106,60,"Prev","Previous six presets",()->{page=Math.max(0,page-1);rebuildWidgets();});
        button(x+80,body+106,60,"Next","Next six presets",()->{if((page+1)*6<names.size())page++;rebuildWidgets();});
        button(x+144,body+106,100,favorites?"Favorites only":"All looks","Toggle favorites filter",()->{favorites=!favorites;page=0;rebuildWidgets();});
        button(x+16,body+240,(w-36)/2,Studio.library().favorite(LyricLooks.GROUP,selected)?"Unfavorite":"Favorite","Remember this look in your favorites",()->{if(!selected.isBlank())Studio.library().favorite(LyricLooks.GROUP,selected,!Studio.library().favorite(LyricLooks.GROUP,selected));rebuildWidgets();});
        button(x+20+(w-36)/2,body+240,(w-36)/2,LyricsService.INSTANCE.demoPlaying()?"Stop demo":"Visual demo","Preview applied settings with the built-in visual demo; no audio",()->{if(LyricsService.INSTANCE.demoPlaying())LyricsService.INSTANCE.stopDemo();else LyricsService.INSTANCE.startDemo();rebuildWidgets();});
        button(x+16,body+266,(w-36)/2,"Export selected","Copy this look as portable JSON",()->{minecraft.keyboardHandler.setClipboard(PresetCodec.encode(LyricLooks.GROUP,selected,values()));notice="Selected lyric look copied as JSON";});
        button(x+20+(w-36)/2,body+266,(w-36)/2,"Import JSON","Read a validated lyric look from your clipboard",()->{var decoded=PresetCodec.decode(LyricLooks.GROUP,minecraft.keyboardHandler.getClipboard());imported=Studio.snapshot(LyricLooks.GROUP);decoded.entrySet().forEach(entry->imported.add(entry.getKey(),entry.getValue().deepCopy()));selected="Imported look";rebuildWidgets();notice="Imported for preview; Apply to save";});
        var save=field(x+16,body+294,w-136,"Name your current look",saveName);save.setMaxLength(48);save.setResponder(value->saveName=value);
        button(x+w-116,body+294,100,"Save current","Save the applied appearance as a custom look",()->{if(builtin(saveName))throw new IllegalArgumentException("Choose a custom name; built-in looks are protected");Studio.library().preset(LyricLooks.GROUP,saveName,Studio.snapshot(LyricLooks.GROUP));selected=saveName;imported=null;rebuildWidgets();notice="Saved custom lyric look";});
        button(x+16,body+322,(w-36)/2,"Undo","Undo the last applied lyric preset",()->{undo();notice="Lyric preset undone";});
        button(x+20+(w-36)/2,body+322,(w-36)/2,"Redo","Redo the last applied lyric preset",()->{redo();notice="Lyric preset restored";});
    }
    @Override protected void footer() {
        super.footer();
        button(x+106,y+h-29,104,"Apply preset","Apply and save appearance; timing and player settings stay unchanged",()->{LyricLooks.apply(values());notice="Applied "+selected;});
        button(x+216,y+h-29,Math.max(40,w-228),"Apply & view","Apply this look and close Studio to view it in the world",()->{LyricLooks.apply(values());ClientAccess.setScreen(minecraft,null);});
    }
    @Override public void tick() {
        if(pending){pending=false;rebuildWidgets();for(var child:children())if(child instanceof net.minecraft.client.gui.components.EditBox field){setFocused(field);field.setCursorPosition(field.getValue().length());break;}}
    }
    @Override protected boolean undo(){return StudioEdits.undo(LyricLooks.GROUP);}
    @Override protected boolean redo(){return StudioEdits.redo(LyricLooks.GROUP);}
    @Override protected void content(GuiGraphicsExtractor g,int mx,int my,float delta) {
        int color=NocturneClient.SETTINGS.uiText.argb();
        text(g,(names.isEmpty()?"No matching looks":names.size()+" matching looks / page "+(page+1)+" of "+Math.max(1,(names.size()+5)/6)),body+134,color);
        if(selected.isBlank())return;
        var look=values();int primary=(int)Long.parseLong(look.get("Colour").getAsString().substring(1),16);
        Draw.round(g,x+16,body+154,w-32,76,6,0xD910141B);
        text(g,selected+" / "+look.get("Placement").getAsString()+" / "+look.get("Animation").getAsString(),body+163,color);
        int fontIndex=List.of(Draw.LYRIC_FONTS).indexOf(look.get("Lyrics font").getAsString());
        g.text(font,Draw.lyric("Your world. Your soundtrack.",false,Math.max(0,fontIndex)),x+28,body+187,primary,false);
        text(g,"Style sample / Apply & view uses the actual in-world renderer",body+213,Draw.alpha(color,.6f));
    }
}
