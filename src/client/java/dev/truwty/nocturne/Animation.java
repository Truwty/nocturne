package dev.truwty.nocturne;

public final class Animation {
    private double value;
    private long previous;

    public Animation(double initial) { value = initial; }

    public double approach(double target, double speed, boolean enabled) {
        long now = System.nanoTime();
        double elapsed = previous == 0 ? 1.0 / 60 : Math.min(0.1, (now - previous) / 1_000_000_000.0);
        previous = now;
        value = step(value, target, elapsed, speed, enabled);
        return value;
    }

    public void snap(double target) { value = target; previous = System.nanoTime(); }

    static double step(double value, double target, double elapsed, double speed, boolean enabled) {
        if (!enabled) return target;
        return target + (value - target) * Math.exp(-Math.max(0, speed) * Math.max(0, elapsed));
    }
}
