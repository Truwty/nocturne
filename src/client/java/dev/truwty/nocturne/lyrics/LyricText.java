package dev.truwty.nocturne.lyrics;

import java.text.Bidi;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.resources.language.FormattedBidiReorder;

public final class LyricText {
    private static final Map<Component,FormattedCharSequence> CACHE=new LinkedHashMap<>(128,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Component,FormattedCharSequence> entry){return size()>512;}
    };
    private LyricText() {}
    public static FormattedCharSequence visual(Component text) {
        return CACHE.computeIfAbsent(text,key->{Bidi bidi=new Bidi(key.getString(),Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            return FormattedBidiReorder.reorder(key,!bidi.baseIsLeftToRight());});
    }
    public static List<Integer> order(List<Lyrics.Word> words,List<Integer> indexes) {
        StringBuilder text=new StringBuilder();int[] offsets=new int[indexes.size()];
        for(int i=0;i<indexes.size();i++){offsets[i]=text.length();text.append(words.get(indexes.get(i)).text()).append(' ');}
        if(text.isEmpty())return indexes;
        Bidi bidi=new Bidi(text.toString(),Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        byte[] levels=new byte[indexes.size()];Integer[] visual=indexes.toArray(Integer[]::new);
        for(int i=0;i<levels.length;i++)levels[i]=(byte)bidi.getLevelAt(offsets[i]);
        Bidi.reorderVisually(levels,0,visual,0,visual.length);return List.of(visual);
    }
}
