package dev.truwty.nocturne;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FrameMotionTest {
    @Test void smoothingIsIndependentOfFrameRate() {
        float slow = 0, fast = 0;
        for (int i = 0; i < 30; i++) slow = FrameMotion.approach(slow, 1, 5, 1f / 30);
        for (int i = 0; i < 144; i++) fast = FrameMotion.approach(fast, 1, 5, 1f / 144);
        assertEquals(slow, fast, 0.00001);
    }
    @Test void rotationsTakeShortestPathAcrossWraparound() {
        float next = FrameMotion.angle((float)Math.toRadians(179), (float)Math.toRadians(-179), 5, 0.02f);
        assertTrue(next > Math.toRadians(179)); assertTrue(next < Math.toRadians(181));
    }
    @Test void invalidDeltaDoesNotPoisonAnimation() {
        assertEquals(0.5f, FrameMotion.approach(0.5f, 1, 5, Float.NaN));
        assertEquals(0.5f, FrameMotion.approach(0.5f, 1, 5, -1));
    }
}
