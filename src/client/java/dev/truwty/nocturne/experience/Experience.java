package dev.truwty.nocturne.experience;

import static dev.truwty.nocturne.experience.ExperienceSettings.Option.*;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.Draw;
import dev.truwty.nocturne.platform.LyricFrame;
import dev.truwty.nocturne.platform.ModPlatform;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.*;

import java.io.IOException;
import java.util.*;

public final class Experience {
    public static final ExperienceSettings SETTINGS =
            new ExperienceSettings(
                    ModPlatform.configDirectory().resolve("nocturne-experience.json"));
    private static final CombatTracker COMBAT = new CombatTracker();
    private static final List<Label> labels = new ArrayList<>();
    private static final List<Ring> rings = new ArrayList<>();
    private static final List<KillVisual> kills = new ArrayList<>();
    private static final List<net.minecraft.client.particle.Particle> ownedParticles =
            new ArrayList<>();
    private static final Random random = new Random();
    private static Object level;
    private static long ticks, bannerUntil, emoteUntil, lastDummyHit;
    private static boolean grounded;
    private static int allowance, spawned, bannerCount, observedHits, observedKills;
    private static String banner = "", emote = "";
    private static Vec3 previous, pet, dummy;
    private static double zoom = 1;
    private static KeyMapping orbitKey, emoteKey, blocksKey, zoomKey;

    private record Label(Vec3 at, String text, int color, long until) {}

    private record Ring(Vec3 at, long start, int color, float size) {}

    private record KillVisual(Vec3 at, int kind, long start) {}

    private Experience() {}

    public static void initialize(KeyMapping.Category category) {
        try {
            SETTINGS.load();
        } catch (IOException error) {
            NocturneClient.LOG.warn(
                    "Experience settings could not be loaded; optional effects remain off", error);
        }
        orbitKey = key(category, "orbit", 79);
        emoteKey = key(category, "emotes", 66);
        blocksKey = key(category, "block_palette", 74);
        zoomKey = key(category, "zoom", 67);
        dev.truwty.nocturne.platform.ClientHooks.hud("experience", Experience::hud);
    }

    private static KeyMapping key(KeyMapping.Category category, String name, int key) {
        return dev.truwty.nocturne.platform.ClientHooks.key(
                new KeyMapping("key.nocturne." + name, ClientAccess.keyType(), key, category));
    }

    public static void save() {
        try {
            SETTINGS.save();
        } catch (IOException error) {
            NocturneClient.LOG.error("Could not save experience settings", error);
            throw new IllegalStateException("Could not save experience settings", error);
        }
    }

    public static int color() {
        return switch (SETTINGS.get(WORLD_LOOK)) {
            case 2 -> 0xFF9B70FF;
            case 3 -> 0xFFFFBF70;
            case 4 -> 0xFF9FE8FF;
            case 5 -> 0xFFFF97B2;
            case 6 -> 0xFFD0D5DD;
            default -> 0xFFA8F1D3;
        };
    }

    public static int spawned() {
        return spawned;
    }

    public static int liveParticles() {
        return ownedParticles.size();
    }

    public static int observedHits() {
        return observedHits;
    }

    public static int observedKills() {
        return observedKills;
    }

    public static double zoom() {
        return zoom;
    }

    public static long ticks() {
        return ticks;
    }

    public static String emoteName() {
        return ticks < emoteUntil ? emote : "";
    }

    public static void emote(String name) {
        emote = name;
        emoteUntil = ticks + 80;
    }

    public static void attack(Entity entity) {
        if (entity instanceof LivingEntity living && living.isAlive())
            COMBAT.attack(living.getUUID(), living.getHealth(), ticks);
    }

    private static void clearParticles() {
        for (var particle : ownedParticles) particle.remove();
        ownedParticles.clear();
        dev.truwty.nocturne.performance.RenderPerformance.clientParticles(0);
    }

    public static void tick(Minecraft mc) {
        spawned = 0;
        allowance = 0;
        ownedParticles.removeIf(particle -> !particle.isAlive());
        dev.truwty.nocturne.performance.RenderPerformance.clientParticles(ownedParticles.size());
        if (!Accessibility.particles()) clearParticles();
        DiscordPresence.update(SETTINGS.on(RPC), SETTINGS.discordAppId());
        if (mc.level != level) {
            level = mc.level;
            clearParticles();
            ContainerPreview.reset();
            KillReplay.reset();
            observedHits = observedKills = 0;
            LocalPaletteScreen.clear();
            COMBAT.clear();
            labels.clear();
            rings.clear();
            kills.clear();
            previous = pet = dummy = null;
            grounded = true;
            banner = "";
            emote = "";
            bannerUntil = emoteUntil = 0;
        }
        if (mc.level == null || mc.player == null) {
            while (orbitKey.consumeClick()) {}
            while (emoteKey.consumeClick()) {}
            while (blocksKey.consumeClick()) {}
            zoom = 1;
            return;
        }
        while (orbitKey.consumeClick())
            if (mc.screen == null) ClientAccess.setScreen(mc, new OrbitScreen(null));
        while (emoteKey.consumeClick())
            if (mc.screen == null) ClientAccess.setScreen(mc, new EmoteScreen(null));
        while (blocksKey.consumeClick())
            if (mc.screen == null) ClientAccess.setScreen(mc, new LocalPaletteScreen(null));
        double target = zoomKey.isDown() && mc.screen == null ? .25 : 1;
        zoom = Accessibility.camera() ? zoom + (target - zoom) * .35 : target;
        if (mc.isPaused() || mc.getWindow().isIconified() || !mc.isWindowActive()) return;
        ticks++;
        spawned = 0;
        double frame = dev.truwty.nocturne.performance.RenderPerformance.snapshot().frameMs();
        allowance =
                Accessibility.particles()
                        ? (int) (SETTINGS.limit() * (frame > 40 ? .25 : frame > 24 ? .5 : 1))
                        : 0;
        labels.removeIf(label -> ticks >= label.until);
        rings.removeIf(ring -> ticks - ring.start > 24);
        kills.removeIf(kill -> ticks - kill.start > 30);
        COMBAT.expire(ticks);
        KillReplay.tick();
        ContainerPreview.tick(mc);
        var player = mc.player;
        Vec3 pos = player.position();
        boolean moved =
                previous != null
                        && pos.distanceToSqr(previous) > .008
                        && pos.distanceToSqr(previous) < 25;
        if (moved || mc.player.hurtTime > 0 || mc.player.isUsingItem()) emoteUntil = 0;
        if (moved && SETTINGS.on(FLOOR) && player.onGround())
            particle(mc, style(SETTINGS.get(FLOOR)), pos.add(0, .05, 0), 0, .015, 0);
        if (grounded
                && !player.onGround()
                && player.getDeltaMovement().y > .05
                && SETTINGS.on(JUMP)) ring(pos, 1);
        if (!grounded && player.onGround() && SETTINGS.on(LAND)) ring(pos, 2);
        grounded = player.onGround();
        previous = pos;
        if (SETTINGS.on(PET)) {
            Vec3 desired =
                    pos.add(
                            -Math.cos(Math.toRadians(player.getYRot())) * 1.2,
                            1.2,
                            -Math.sin(Math.toRadians(player.getYRot())) * 1.2);
            pet =
                    pet == null || pet.distanceToSqr(desired) > 100
                            ? desired
                            : pet.lerp(desired, .15);
        }
        if (!SETTINGS.on(DUMMY)) dummy = null;
        if (SETTINGS.on(DUMMY) && dummy == null)
            dummy = pos.add(player.getLookAngle().multiply(3, 0, 3));
        if (dummy != null
                && mc.screen == null
                && mc.options.keyAttack.isDown()
                && ticks - lastDummyHit >= 10) {
            Vec3 eye = player.getEyePosition(), toward = dummy.add(0, 1, 0).subtract(eye);
            if (toward.lengthSqr() < 36 && player.getLookAngle().dot(toward.normalize()) > .965) {
                lastDummyHit = ticks;
                label(dummy.add(0, 2, 0), "Practice hit", color());
                burst(mc, dummy.add(0, 1, 0), ParticleTypes.CRIT, 12);
            }
        }
        if (allowance > 0) environment(mc);
        boolean entities =
                COMBAT.size() > 0
                        || SETTINGS.on(PROJECTILES)
                        || SETTINGS.on(WIND)
                        || SETTINGS.on(ITEM_TIMER);
        if (entities) {
            int visited = 0, trailCount = 0;
            for (var entity : mc.level.entitiesForRendering()) {
                if (++visited > 512) break;
                if (entity.distanceToSqr(player) > 48 * 48) continue;
                if (entity instanceof LivingEntity living) {
                    var hit = COMBAT.observe(entity.getUUID(), living.getHealth(), ticks);
                    if (hit != null) damage(mc, living, hit);
                }
                if (allowance > spawned
                        && entity instanceof Projectile
                        && entity.getDeltaMovement().lengthSqr() > .02
                        && trailCount++ < 32) {
                    boolean wind = entity.getType().toShortString().contains("wind_charge");
                    if (SETTINGS.on(PROJECTILES) || wind && SETTINGS.on(WIND))
                        particle(
                                mc,
                                wind ? ParticleTypes.CLOUD : projectileStyle(),
                                entity.position(),
                                0,
                                0,
                                0);
                }
            }
        }
        if (SETTINGS.on(AURA) && ticks % 3 == 0) {
            double a = ticks * .25;
            particle(
                    mc,
                    SETTINGS.get(AURA) == 2
                            ? ParticleTypes.HEART
                            : SETTINGS.get(AURA) == 3 ? ParticleTypes.SOUL : ParticleTypes.END_ROD,
                    pos.add(Math.cos(a) * .7, .4 + Math.sin(a * .6) * .3, Math.sin(a) * .7),
                    0,
                    .02,
                    0);
        }
    }

    private static ParticleOptions hitStyle() {
        return SETTINGS.get(HIT) == 4
                ? new DustParticleOptions(Draw.hsv((float) (ticks * .01 % 1), .8f, 1), 1)
                : style(SETTINGS.get(HIT));
    }

    private static ParticleOptions projectileStyle() {
        return switch (SETTINGS.get(PROJECTILES)) {
            case 2 -> ParticleTypes.SOUL;
            case 3 -> new DustParticleOptions(Draw.hsv((float) (ticks * .01 % 1), .8f, 1), .8f);
            default -> ParticleTypes.END_ROD;
        };
    }

    public static void previewKill(Vec3 at, int kind) {
        if (kills.size() >= 8) kills.removeFirst();
        kills.add(new KillVisual(at, kind, ticks));
    }

    private static SimpleParticleType style(int index) {
        return switch (index) {
            case 2 -> ParticleTypes.HEART;
            case 3 -> ParticleTypes.SOUL;
            case 4 -> ParticleTypes.CHERRY_LEAVES;
            default -> ParticleTypes.END_ROD;
        };
    }

    private static void particle(
            Minecraft mc, ParticleOptions type, Vec3 at, double vx, double vy, double vz) {
        if (spawned >= allowance) return;
        while (ownedParticles.size() >= SETTINGS.limit() * 4) ownedParticles.removeFirst().remove();
        var particle = mc.particleEngine.createParticle(type, at.x, at.y, at.z, vx, vy, vz);
        if (particle != null) {
            spawned++;
            ownedParticles.add(particle);
            dev.truwty.nocturne.performance.RenderPerformance.clientParticles(
                    ownedParticles.size());
        }
    }

    private static void burst(Minecraft mc, Vec3 at, ParticleOptions type, int count) {
        for (int i = 0; i < count && spawned < allowance; i++)
            particle(
                    mc,
                    type,
                    at,
                    (random.nextDouble() - .5) * .2,
                    random.nextDouble() * .2,
                    (random.nextDouble() - .5) * .2);
    }

    private static void ring(Vec3 at, float size) {
        if (!Accessibility.particles()) return;
        if (rings.size() >= 16) rings.removeFirst();
        rings.add(new Ring(at.add(0, .025, 0), ticks, color(), size));
    }

    private static void label(Vec3 at, String text, int color) {
        if (labels.size() >= 32) labels.removeFirst();
        labels.add(new Label(at, text, color, ticks + 30));
    }

    private static void damage(Minecraft mc, LivingEntity entity, CombatTracker.Hit hit) {
        observedHits++;
        Vec3 at = entity.position().add(0, entity.getBbHeight() * .7, 0);
        if (SETTINGS.on(DAMAGE))
            label(at, String.format(Locale.ROOT, "−%.1f", hit.damage()), color());
        if (SETTINGS.on(HIT)) burst(mc, at, hitStyle(), 12);
        if (SETTINGS.on(CRIT) && mc.player.getDeltaMovement().y < 0 && !mc.player.onGround())
            burst(mc, at, ParticleTypes.CRIT, 18);
        if (SETTINGS.on(MACE) && mc.player.getMainHandItem().is(Items.MACE)) {
            ring(entity.position(), 3);
            burst(mc, at, ParticleTypes.GUST, 6);
        }
        if (!hit.killed()) return;
        observedKills++;
        KillReplay.markKill();
        int streak = COMBAT.kill(ticks);
        int effect = SETTINGS.get(KILL);
        if (effect == 6) effect = 1 + random.nextInt(5);
        if (effect != 0) {
            previewKill(entity.position(), effect);
            burst(
                    mc,
                    at,
                    switch (effect) {
                        case 3 -> ParticleTypes.HEART;
                        case 4 -> ParticleTypes.CHERRY_LEAVES;
                        case 5 -> ParticleTypes.SOUL;
                        default -> ParticleTypes.CRIT;
                    },
                    30);
            label(
                    at.add(0, 1, 0),
                    switch (effect) {
                        case 1 -> "BONK";
                        case 2 -> "67";
                        case 3 -> "<3";
                        case 4 -> "BLOOM";
                        default -> "SOUL";
                    },
                    color());
            ring(entity.position(), 2);
        }
        if (SETTINGS.on(BANNERS)) {
            banner = streak > 1 ? streak + " × STREAK" : "ELIMINATION";
            bannerCount = streak;
            bannerUntil = ticks + 50;
        }
        if (SETTINGS.on(SOUND))
            mc.level.playLocalSound(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    switch (SETTINGS.get(SOUND)) {
                        case 1 -> SoundEvents.NOTE_BLOCK_BELL.value();
                        case 2 -> SoundEvents.NOTE_BLOCK_CHIME.value();
                        default -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
                    },
                    SoundSource.PLAYERS,
                    .5f,
                    1.2f,
                    false);
    }

    private static void environment(Minecraft mc) {
        Vec3 pos = mc.player.position();
        int weather = SETTINGS.get(WEATHER), season = SETTINGS.get(SEASON);
        int count = weather == 4 ? 12 : weather >= 2 ? 5 : 0;
        for (int i = 0; i < count; i++) {
            Vec3 at =
                    pos.add(
                            (random.nextDouble() - .5) * 24,
                            3 + random.nextDouble() * 8,
                            (random.nextDouble() - .5) * 24);
            if (!mc.level.canSeeSky(BlockPos.containing(at))) continue;
            ParticleOptions type =
                    switch (weather) {
                        case 2 -> ParticleTypes.RAIN;
                        case 3, 4 -> ParticleTypes.SNOWFLAKE;
                        case 5 -> ParticleTypes.DUST_PLUME;
                        case 6 -> ParticleTypes.CHERRY_LEAVES;
                        default -> ParticleTypes.WHITE_ASH;
                    };
            particle(
                    mc,
                    type,
                    at,
                    weather == 4 || weather == 5 ? .15 : 0,
                    weather == 2 ? -.7 : -.03,
                    0);
        }
        if (ticks % 4 == 0 && (season > 0 || SETTINGS.on(AMBIENT))) {
            Vec3 at =
                    pos.add(
                            (random.nextDouble() - .5) * 14,
                            random.nextDouble() * 4,
                            (random.nextDouble() - .5) * 14);
            ParticleOptions type =
                    SETTINGS.on(AMBIENT)
                            ? switch (SETTINGS.get(AMBIENT)) {
                                case 1 -> ParticleTypes.FIREFLY;
                                case 2 -> ParticleTypes.ENCHANT;
                                default -> ParticleTypes.CHERRY_LEAVES;
                            }
                            : switch (season) {
                                case 1 -> ParticleTypes.CHERRY_LEAVES;
                                case 2 -> ParticleTypes.FIREFLY;
                                case 3 -> ParticleTypes.PALE_OAK_LEAVES;
                                default -> ParticleTypes.SNOWFLAKE;
                            };
            particle(mc, type, at, 0, .01, 0);
        }
    }

    public static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        KillReplay.capture(mc);
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null)
            return;
        int width = mc.getWindow().getGuiScaledWidth(),
                height = mc.getWindow().getGuiScaledHeight();
        if (SETTINGS.on(WIDGETS)
                && dev.truwty.nocturne.gui.HudLayouts.current().node("Session").visible()) {
            try (var scope =
                    dev.truwty.nocturne.gui.HudLayouts.begin(
                            g, "Session", width, height, 184, 38)) {
                Draw.round(g, 0, 0, 184, 38, 6, 0xCF101820);
                Draw.text(g, mc.font, "NOCTURNE / SESSION", 8, 7, color(), false);
                Draw.text(
                        g,
                        mc.font,
                        "XYZ "
                                + mc.player.getBlockX()
                                + " / "
                                + mc.player.getBlockY()
                                + " / "
                                + mc.player.getBlockZ(),
                        8,
                        21,
                        0xFFE4EBEF,
                        false);
            }
        }
        if (SETTINGS.on(CONTAINER)
                && dev.truwty.nocturne.gui.HudLayouts.current().node("Container").visible()) {
            var preview = ContainerPreview.view(mc);
            if (preview != null) {
                var items = preview.items();
                int hh = 24 + Math.max(1, (items.size() + 8) / 9) * 18;
                try (var scope =
                        dev.truwty.nocturne.gui.HudLayouts.begin(
                                g, "Container", width, height, 176, hh)) {
                    Draw.round(g, 0, 0, 176, hh, 6, 0xE8101820);
                    Draw.text(g, mc.font, preview.label(), 6, 5, color(), false);
                    for (int i = 0; i < items.size(); i++)
                        g.item(items.get(i), 7 + (i % 9) * 18, 19 + (i / 9) * 18);
                }
            }
        }
        if (ticks < bannerUntil) {
            String text = banner;
            int size = mc.font.width(text);
            Draw.round(g, (width - size) / 2 - 12, 42, size + 24, 25, 6, 0xD9101820);
            Draw.text(g, mc.font, text, (width - size) / 2, 50, color(), false);
        }
        if (ticks < emoteUntil) {
            String text = "Emote / " + emote;
            Draw.text(
                    g,
                    mc.font,
                    text,
                    (width - mc.font.width(text)) / 2,
                    height - 68,
                    color(),
                    true);
        }
    }

    public static void render(LyricFrame frame) {
        if (kills.isEmpty()
                && rings.isEmpty()
                && labels.isEmpty()
                && pet == null
                && dummy == null
                && LocalPaletteScreen.ghosts().isEmpty()
                && emoteName().isEmpty()
                && !SETTINGS.on(AURORA)
                && !SETTINGS.on(STARS)
                && !SETTINGS.on(CAPE)
                && !SETTINGS.on(WINGS)
                && !SETTINGS.on(HEAD)
                && !SETTINGS.on(OUTLINE)
                && !SETTINGS.on(ITEM_TIMER)) return;
        dev.truwty.nocturne.performance.RenderPerformance.hudBegin();
        long started = System.nanoTime();
        try {
            ExperienceRenderer.render(
                    frame,
                    pet,
                    dummy,
                    rings.stream()
                            .map(
                                    r ->
                                            new ExperienceRenderer.Wave(
                                                    r.at, (ticks - r.start) / 24f, r.color, r.size))
                            .toList(),
                    labels.stream()
                            .map(
                                    l ->
                                            new ExperienceRenderer.Caption(
                                                    l.at.add(0, (30 - (l.until - ticks)) * .015, 0),
                                                    l.text,
                                                    l.color))
                            .toList(),
                    ticks < emoteUntil ? emote : "",
                    kills.stream()
                            .map(
                                    k ->
                                            new ExperienceRenderer.Kill(
                                                    k.at, k.kind, (ticks - k.start) / 30f))
                            .toList());
        } finally {
            dev.truwty.nocturne.performance.RenderPerformance.addTime(started);
        }
    }
}
