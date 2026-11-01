package dev.truwty.nocturne.gui;

import java.util.*;

public final class ControllerRepeater {
    public enum Action {
        PREVIOUS,
        NEXT,
        LEFT,
        RIGHT,
        ACCEPT,
        BACK,
        PAGE_UP,
        PAGE_DOWN,
        UNDO,
        REDO
    }

    private final EnumMap<Action, Long> held = new EnumMap<>(Action.class),
            repeat = new EnumMap<>(Action.class);

    public List<Action> sample(Set<Action> pressed, long now) {
        List<Action> output = new ArrayList<>();
        for (Action action : Action.values()) {
            if (!pressed.contains(action)) {
                held.remove(action);
                repeat.remove(action);
                continue;
            }
            if (!held.containsKey(action)) {
                held.put(action, now);
                repeat.put(action, now + 350);
                output.add(action);
            } else if (action != Action.ACCEPT
                    && action != Action.BACK
                    && action != Action.UNDO
                    && action != Action.REDO
                    && now >= repeat.get(action)) {
                repeat.put(action, now + 120);
                output.add(action);
            }
        }
        return List.copyOf(output);
    }

    public void reset() {
        held.clear();
        repeat.clear();
    }
}
