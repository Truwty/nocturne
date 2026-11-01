package dev.truwty.nocturne.accessibility;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.truwty.nocturne.setting.Setting;

import java.util.*;
import java.util.function.Predicate;

/** A nestable, exception-safe render override. Saved preferences never use this layer. */
public final class SettingLayer implements AutoCloseable {
    private final List<Runnable> restore = new ArrayList<>();
    private boolean closed;

    private SettingLayer() {}

    public static SettingLayer apply(
            Collection<Setting<?>> settings, JsonObject values, Predicate<String> allowed) {
        var layer = new SettingLayer();
        try {
            if (values != null)
                for (var setting : settings)
                    if (allowed.test(setting.name) && values.has(setting.name))
                        layer.capture(setting, values.get(setting.name));
            return layer;
        } catch (RuntimeException ex) {
            layer.close();
            throw ex;
        }
    }

    private <T> void capture(Setting<T> setting, JsonElement value) {
        T previous = setting.get();
        restore.add(() -> setting.set(previous));
        setting.load(value);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (int i = restore.size() - 1; i >= 0; i--) restore.get(i).run();
    }
}
