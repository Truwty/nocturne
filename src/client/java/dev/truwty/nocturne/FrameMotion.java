package dev.truwty.nocturne;

public final class FrameMotion {
    private FrameMotion() {}
    public static float approach(float value, float target, float rate, float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0) return value;
        return value + (target - value) * (float)-Math.expm1(-Math.max(0, rate) * Math.min(deltaSeconds, 1));
    }
    public static float angle(float value, float target, float rate, float deltaSeconds) {
        float difference = (float)Math.atan2(Math.sin(target - value), Math.cos(target - value));
        return value + approach(0, difference, rate, deltaSeconds);
    }
}
