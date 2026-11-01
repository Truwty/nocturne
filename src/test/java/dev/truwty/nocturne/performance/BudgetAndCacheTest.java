package dev.truwty.nocturne.performance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BudgetAndCacheTest {
    @Test
    void sustainedSlowFramesReduceDetailWithoutHidingCore() {
        var b = new AdaptiveBudget();
        for (int i = 0; i < 120; i++) b.update(40, 16.7, true, false);
        assertEquals(.2, b.scale(), .001);
    }

    @Test
    void shortSpikeDoesNotCauseImmediateQualityChange() {
        var b = new AdaptiveBudget();
        b.update(60, 16.7, true, false);
        assertEquals(1, b.scale());
    }

    @Test
    void sustainedVerySlowFramesAreCappedForSamplingAndStillDegrade() {
        var b = new AdaptiveBudget();
        for (int i = 0; i < 12; i++) b.update(1000, 16.7, true, false);
        assertEquals(.2, b.scale(), .001);
    }

    @Test
    void recoveryIsSlowerThanDegradation() {
        var b = new AdaptiveBudget();
        for (int i = 0; i < 100; i++) b.update(40, 16.7, true, false);
        double low = b.scale();
        for (int i = 0; i < 100; i++) b.update(10, 16.7, true, false);
        assertEquals(low, b.scale(), .001);
        for (int i = 0; i < 500; i++) b.update(10, 16.7, true, false);
        assertTrue(b.scale() > low);
        assertTrue(b.scale() < 1);
    }

    @Test
    void disablingAdaptationRestoresConfiguredLimits() {
        var b = new AdaptiveBudget();
        for (int i = 0; i < 100; i++) b.update(40, 16.7, true, false);
        assertEquals(1, b.update(40, 16.7, false, false));
    }

    @Test
    void idleDisablesDecorationsAndDoesNotPenalizeForegroundResume() {
        var b = new AdaptiveBudget();
        assertEquals(0, b.update(200, 16.7, true, true));
        assertEquals(1, b.update(16.7, 16.7, true, false));
    }

    @Test
    void invalidSamplesDoNotPoisonController() {
        var b = new AdaptiveBudget();
        b.update(Double.NaN, 16.7, true, false);
        b.update(-1, 16.7, true, false);
        assertEquals(1, b.scale());
        assertTrue(Double.isFinite(b.frameMs()));
    }

    @Test
    void limitsAreHardBoundsIncludingZero() {
        assertEquals(0, AdaptiveBudget.limit(0, 1));
        assertEquals(6, AdaptiveBudget.limit(6, 10));
        assertEquals(1, AdaptiveBudget.limit(6, .2));
        assertEquals(0, AdaptiveBudget.limit(6, Double.NaN));
    }

    @Test
    void distanceDetailIsSmoothAndMonotonic() {
        double previous = 1;
        for (int i = 0; i < 100; i++) {
            double d = AdaptiveBudget.distanceDetail(i, 12, 64);
            assertTrue(d <= previous + .0001);
            assertTrue(d >= .15 - .0001);
            previous = d;
        }
        assertEquals(1, AdaptiveBudget.distanceDetail(12, 12, 64));
        assertEquals(.15, AdaptiveBudget.distanceDetail(64, 12, 64), .0001);
    }

    private static ArtworkCache.Entry entry(int size, long time) {
        return new ArtworkCache.Entry(new byte[size], 123, time);
    }

    @Test
    void artworkLruEvictsLeastRecentlyUsed() {
        var c = new ArtworkCache(2, 100, 1000);
        c.put("a", entry(4, 10));
        c.put("b", entry(4, 10));
        assertNotNull(c.get("a", 11));
        c.put("c", entry(4, 10));
        assertNull(c.get("b", 11));
        assertEquals(2, c.size());
    }

    @Test
    void artworkByteLimitIsIndependentOfEntryCount() {
        var c = new ArtworkCache(20, 10, 1000);
        c.put("a", entry(6, 10));
        c.put("b", entry(6, 10));
        assertEquals(1, c.size());
        assertEquals(6, c.bytes());
    }

    @Test
    void artworkExpiresAndRejectsFutureClockEntries() {
        var c = new ArtworkCache(2, 100, 1000);
        c.put("a", entry(4, 10));
        assertNull(c.get("a", 1010));
        c.put("b", entry(4, 10));
        assertNull(c.get("b", 9));
        assertEquals(0, c.bytes());
    }

    @Test
    void replacingAndOversizedEntriesKeepAccountingCorrect() {
        var c = new ArtworkCache(2, 10, 1000);
        c.put("a", entry(4, 10));
        c.put("a", entry(8, 10));
        assertEquals(8, c.bytes());
        c.put("a", entry(11, 10));
        assertEquals(0, c.bytes());
        assertEquals(0, c.size());
    }

    @Test
    void artworkPayloadIsDefensive() {
        byte[] bytes = {1, 2};
        var e = new ArtworkCache.Entry(bytes, 1, 1);
        bytes[0] = 7;
        e.png()[0] = 9;
        assertEquals(1, e.png()[0]);
    }
}
