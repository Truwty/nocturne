package dev.truwty.nocturne.accessibility;

import java.util.*;

public record ScreenPolicy(
        boolean chat,
        boolean inventory,
        boolean debug,
        boolean pause,
        boolean other,
        boolean hiddenHud,
        Set<String> selected) {
    public ScreenPolicy {
        selected = Set.copyOf(selected);
    }

    public boolean hides(
            String name,
            boolean isChat,
            boolean isInventory,
            boolean isPause,
            boolean debugOpen,
            boolean hudHidden) {
        return (debug && debugOpen)
                || (hiddenHud && hudHidden)
                || (name != null
                        && ((chat && isChat)
                                || (inventory && isInventory)
                                || (pause && isPause)
                                || (other && !isChat && !isInventory && !isPause)
                                || selected.contains(name)));
    }

    public static Set<String> validate(Collection<String> names) {
        if (names.size() > 32) throw new IllegalArgumentException("Use at most 32 screen classes");
        var result = new LinkedHashSet<String>();
        for (String entry : names) {
            String name = entry.strip();
            if (name.isEmpty()) continue;
            if (name.length() > 200
                    || !name.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
                throw new IllegalArgumentException("Enter exact Java screen class names");
            result.add(name);
        }
        return Collections.unmodifiableSet(result);
    }
}
