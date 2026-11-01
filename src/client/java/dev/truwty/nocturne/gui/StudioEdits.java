package dev.truwty.nocturne.gui;

import com.google.gson.JsonObject;

import dev.truwty.nocturne.Studio;

import java.util.HashMap;
import java.util.Map;

public final class StudioEdits {
    private static final Map<String, SnapshotHistory> HISTORY = new HashMap<>();

    private StudioEdits() {}

    public static void record(String group, JsonObject before, JsonObject after) {
        group = group.startsWith("placement") ? "placement" : group;
        SnapshotHistory history =
                HISTORY.computeIfAbsent(group, key -> new SnapshotHistory(before));
        if (!history.value().equals(before)) history = new SnapshotHistory(before);
        history.change(after);
        HISTORY.put(group, history);
    }

    public static boolean undo(String group) {
        return move(group, false);
    }

    public static boolean redo(String group) {
        return move(group, true);
    }

    private static boolean move(String group, boolean forward) {
        group = group.startsWith("placement") ? "placement" : group;
        SnapshotHistory history = HISTORY.get(group);
        if (history == null) return false;
        if (!Studio.snapshot(group).equals(history.value())) {
            HISTORY.remove(group);
            return false;
        }
        if (!(forward ? history.redo() : history.undo())) return false;
        Studio.apply(group, history.value());
        return true;
    }
}
