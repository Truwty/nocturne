package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.setting.NumberSetting;
import java.util.IdentityHashMap;

/** Keeps sub-step movement until the setting's next quantization boundary. */
public final class PreciseDrag {
    private final IdentityHashMap<NumberSetting,Double> values = new IdentityHashMap<>();
    public void clear() { values.clear(); }
    public void adjust(NumberSetting setting,double delta) {
        if(!Double.isFinite(delta))throw new IllegalArgumentException("Non-finite drag movement");
        double raw=Math.clamp(values.getOrDefault(setting,setting.get())+delta,setting.min,setting.max);
        values.put(setting,raw);
        setting.setFraction((raw-setting.min)/(setting.max-setting.min));
    }
}
