package dev.truwty.nocturne.gui;

import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded, defensively copied snapshots shared by visual editors. */
public final class SnapshotHistory {
    private JsonObject current;
    private final Deque<JsonObject> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    public SnapshotHistory(JsonObject initial) {
        current = initial.deepCopy();
    }

    public JsonObject value() {
        return current.deepCopy();
    }

    public void change(JsonObject next) {
        if (current.equals(next)) return;
        undo.addLast(current);
        trim(undo);
        redo.clear();
        current = next.deepCopy();
    }

    private static void trim(Deque<JsonObject> stack) {
        while (stack.size() > 60) stack.removeFirst();
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.addLast(current);
        current = undo.removeLast();
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.addLast(current);
        trim(undo);
        current = redo.removeLast();
        return true;
    }
}
