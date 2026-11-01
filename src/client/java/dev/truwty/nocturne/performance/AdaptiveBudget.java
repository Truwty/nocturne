package dev.truwty.nocturne.performance;

/** Frame-time controller with a fast decrease and a deliberately slower recovery. */
public final class AdaptiveBudget {
    private double average = 16.667, scale = 1, pressure, recovery;
    private boolean wasIdle;

    public double update(double frameMs, double targetMs, boolean adaptive, boolean idle) {
        if (!Double.isFinite(frameMs) || frameMs <= 0) return idle ? 0 : scale;
        targetMs = Math.clamp(Double.isFinite(targetMs) ? targetMs : 16.667, 4, 100);
        frameMs = Math.min(frameMs, 250);
        if (idle) {
            wasIdle = true;
            pressure = recovery = 0;
            return 0;
        }
        if (wasIdle) {
            wasIdle = false;
            average = targetMs;
            pressure = recovery = 0;
        }
        average += (frameMs - average) * (1 - Math.exp(-frameMs / 350));
        if (!adaptive) {
            scale = 1;
            pressure = recovery = 0;
            return idle ? 0 : 1;
        }
        if (average > targetMs * 1.12) {
            pressure += frameMs;
            recovery = 0;
            if (pressure >= 350) {
                scale = Math.max(.2, scale - .15);
                pressure = 0;
            }
        } else if (average < targetMs * 1.03) {
            recovery += frameMs;
            pressure = 0;
            if (recovery >= 3000) {
                scale = Math.min(1, scale + .05);
                recovery = 0;
            }
        } else {
            pressure = recovery = 0;
        }
        return scale;
    }

    public double scale() {
        return scale;
    }

    public double frameMs() {
        return average;
    }

    public static double distanceDetail(double distance, double near, double far) {
        if (!Double.isFinite(distance) || distance < 0) return 0;
        far = Math.max(near + 1, far);
        double t = Math.clamp((distance - near) / (far - near), 0, 1);
        return 1 - .85 * t * t * (3 - 2 * t);
    }

    public static int limit(int maximum, double scale) {
        return (int)
                Math.floor(
                        Math.max(0, maximum)
                                * Math.clamp(Double.isFinite(scale) ? scale : 0, 0, 1));
    }
}
