package dev.truwty.nocturne.experience;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class CombatTrackerTest {
    @Test
    void untrackedDamageDoesNotCountAsOurAttack() {
        assertNull(new CombatTracker().observe(UUID.randomUUID(), 0, 1));
    }

    @Test
    void repeatedAttackDoesNotErasePendingHealthLoss() {
        var tracker = new CombatTracker();
        var id = UUID.randomUUID();
        tracker.attack(id, 20, 0);
        tracker.attack(id, 15, 1);
        var hit = tracker.observe(id, 15, 2);
        assertEquals(5, hit.damage());
        assertFalse(hit.killed());
        assertNull(tracker.observe(id, 15, 3));
        assertTrue(tracker.observe(id, 0, 4).killed());
        assertNull(tracker.observe(id, 0, 5));
    }

    @Test
    void timeoutAndRemovalNeverInventKills() {
        var tracker = new CombatTracker();
        var id = UUID.randomUUID();
        tracker.attack(id, 20, 0);
        tracker.expire(41);
        assertNull(tracker.observe(id, 0, 42));
        assertEquals(1, tracker.kill(10));
        assertEquals(2, tracker.kill(50));
        assertEquals(1, tracker.kill(151));
    }

    @Test
    void adversarialTargetsAreBoundedAndDisconnectResetsTheStreak() {
        var tracker = new CombatTracker();
        for (int i = 0; i < 100; i++) tracker.attack(UUID.randomUUID(), 20, 0);
        assertEquals(32, tracker.size());
        tracker.kill(1);
        tracker.clear();
        assertEquals(0, tracker.size());
        assertEquals(1, tracker.kill(2));
    }
}
