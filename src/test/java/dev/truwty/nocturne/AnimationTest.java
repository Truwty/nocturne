package dev.truwty.nocturne;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {
    @Test void motionDoesNotDependOnFrameRate() {
        double slow = 0, fast = 0;
        for (int i = 0; i < 30; i++) slow = Animation.step(slow, 100, 1.0 / 30, 10, true);
        for (int i = 0; i < 144; i++) fast = Animation.step(fast, 100, 1.0 / 144, 10, true);
        assertEquals(slow, fast, 0.000001);
    }
    @Test void reducedMotionSnapsWithoutOvershoot() {
        assertEquals(100, Animation.step(0, 100, 0.001, 12, false));
        assertEquals(0, Animation.step(100, 0, 0.001, 12, false));
    }
    @Test void animatedMotionConvergesWithoutOvershoot() {
        assertTrue(Animation.step(0, 100, 0.5, 12, true) < 100);
        assertTrue(Animation.step(100, 0, 0.5, 12, true) > 0);
    }
    @Test void unknownSavedThemeHasStableFallback() {
        assertEquals(Theme.GRAPHITE, Theme.fromName(null));
        assertEquals(Theme.GRAPHITE, Theme.fromName("removed-theme"));
        for (Theme theme : Theme.values()) assertEquals(theme, Theme.fromName(theme.name()));
    }
}
