package dev.truwty.nocturne.experience;

import java.util.*;

/** Tracks only observed health loss after our own attack; removal alone never counts as a kill. */
public final class CombatTracker {
    public record Hit(UUID id, float damage, boolean killed) {}

    private record Target(float health, long expires) {}

    private final Map<UUID, Target> targets = new LinkedHashMap<>();
    private long lastKill = Long.MIN_VALUE;
    private int streak;

    public void attack(UUID id, float health, long tick) {
        if (!Float.isFinite(health) || health <= 0) return;
        if (targets.size() >= 32 && !targets.containsKey(id))
            targets.remove(targets.keySet().iterator().next());
        // Repeated attacks before the next health update must not erase unobserved damage.
        targets.compute(id, (key, old) -> new Target(old == null ? health : old.health, tick + 40));
    }

    public Hit observe(UUID id, float health, long tick) {
        Target old = targets.get(id);
        if (old == null) return null;
        if (tick > old.expires || !Float.isFinite(health)) {
            targets.remove(id);
            return null;
        }
        if (health >= old.health) return null;
        float damage = old.health - Math.max(0, health);
        boolean killed = health <= 0;
        if (killed) targets.remove(id);
        else targets.put(id, new Target(health, old.expires));
        return new Hit(id, damage, killed);
    }

    public int kill(long tick) {
        streak = lastKill != Long.MIN_VALUE && tick - lastKill <= 100 ? streak + 1 : 1;
        lastKill = tick;
        return streak;
    }

    public void expire(long tick) {
        targets.values().removeIf(target -> tick > target.expires);
    }

    public void clear() {
        targets.clear();
        lastKill = Long.MIN_VALUE;
        streak = 0;
    }

    public int size() {
        return targets.size();
    }
}
