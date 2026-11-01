package dev.truwty.nocturne.performance;

import com.google.gson.*;

import java.util.*;

/** Fixed-workload gates; elapsed CPU time is a coarse guard, never a GPU/FPS benchmark. */
public final class RegressionBudget {
    private final List<RenderPerformance.Snapshot> samples = new ArrayList<>();
    private long previous;

    public void add(RenderPerformance.Snapshot sample) {
        if (sample.sampled() != 0 && sample.sampled() != previous) {
            previous = sample.sampled();
            samples.add(sample);
        }
    }

    public JsonObject report(String scenario, JsonObject limits) {
        if (samples.size() < 30)
            throw new IllegalStateException("Too few performance samples: " + samples.size());
        double[] times =
                samples.stream().mapToDouble(RenderPerformance.Snapshot::cpuMs).sorted().toArray();
        if (Arrays.stream(times).anyMatch(v -> !Double.isFinite(v) || v < 0))
            throw new IllegalStateException("Invalid CPU timing");
        var out = new JsonObject();
        out.addProperty("scenario", scenario);
        out.addProperty("samples", samples.size());
        out.addProperty("cpuP50Ms", times[times.length / 2]);
        double p95 = times[(int) Math.ceil(times.length * .95) - 1];
        out.addProperty("cpuP95Ms", p95);
        if (p95 > limits.get("cpuP95Ms").getAsDouble())
            throw new IllegalStateException(scenario + " CPU budget exceeded: " + p95);
        int minText = samples.stream().mapToInt(RenderPerformance.Snapshot::text).min().orElse(0);
        if (minText < limits.get("minText").getAsInt())
            throw new IllegalStateException(scenario + " lost lyric submissions");
        var maxima = new LinkedHashMap<String, Integer>();
        maxima.put(
                "text",
                samples.stream().mapToInt(RenderPerformance.Snapshot::text).max().orElse(0));
        maxima.put(
                "vertices",
                samples.stream().mapToInt(RenderPerformance.Snapshot::vertices).max().orElse(0));
        maxima.put(
                "particles",
                samples.stream().mapToInt(RenderPerformance.Snapshot::particles).max().orElse(0));
        maxima.put(
                "glow",
                samples.stream().mapToInt(RenderPerformance.Snapshot::glow).max().orElse(0));
        maxima.put(
                "cloud",
                samples.stream().mapToInt(RenderPerformance.Snapshot::cloud).max().orElse(0));
        maxima.put(
                "trails",
                samples.stream().mapToInt(RenderPerformance.Snapshot::trails).max().orElse(0));
        for (var entry : maxima.entrySet()) {
            out.addProperty(entry.getKey() + "Max", entry.getValue());
            if (entry.getValue() > limits.get(entry.getKey()).getAsInt())
                throw new IllegalStateException(
                        scenario + " " + entry.getKey() + " budget exceeded: " + entry.getValue());
        }
        return out;
    }
}
