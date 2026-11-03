package dev.truwty.nocturne.experience;

import static dev.truwty.nocturne.experience.ExperienceSettings.Option.*;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.attribute.EnvironmentAttributes;

public final class ExperienceClientChecks {
    private ExperienceClientChecks() {}

    public static void run(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        var original = context.computeOnClient(mc -> Experience.SETTINGS.snapshot());
        try {
            context.runOnClient(
                    mc -> {
                        Experience.SETTINGS.reset();
                        NocturneClient.SETTINGS.photosensitiveMode.set(false);
                        NocturneClient.SETTINGS.reducedMotion.set(false);
                        NocturneClient.SETTINGS.particleEffects.set(true);
                        NocturneClient.SETTINGS.animatedText.set(true);
                    });
            context.runOnClient(
                    mc -> {
                        mc.getResourcePackRepository().reload();
                        var ids = mc.getResourcePackRepository().getAvailableIds();
                        if (ids.stream().filter(id -> id.contains("nocturne:")).count() < 9)
                            throw new AssertionError(
                                    "Animated texture packs were not registered: " + ids);
                        ClientAccess.setScreen(mc, new OrbitScreen(null));
                    });
            context.waitTicks(5);
            context.takeScreenshot("nocturne-orbit");
            for (var group : ExperienceSettings.Group.values()) {
                context.runOnClient(
                        mc -> {
                            ClientAccess.setScreen(mc, new ExperienceScreen(null, group));
                            assertBounds(mc.screen);
                        });
                context.waitTicks(2);
                context.takeScreenshot(
                        "nocturne-modules-" + group.name().toLowerCase(java.util.Locale.ROOT));
            }
            context.runOnClient(mc -> ClientAccess.setScreen(mc, new EmoteScreen(null)));
            context.waitTicks(2);
            context.takeScreenshot("nocturne-emotes");
            context.runOnClient(mc -> ClientAccess.setScreen(mc, new IntroScreen(null)));
            context.waitTicks(6);
            context.takeScreenshot("nocturne-skin-intro");
            context.runOnClient(mc -> ClientAccess.setScreen(mc, new CreativePreviewScreen(null)));
            context.waitTicks(2);
            context.takeScreenshot("nocturne-creative-preview");
            context.runOnClient(
                    mc -> {
                        ClientAccess.setScreen(mc, null);
                        Experience.SETTINGS.set(TIME, 2);
                        Experience.SETTINGS.set(FOG, 2);
                        Experience.SETTINGS.set(CLOUDS, 2);
                        Experience.SETTINGS.set(SKIN, 1);
                        Experience.SETTINGS.set(HOTBAR, 1);
                        Experience.SETTINGS.set(WINGS, 2);
                        Experience.SETTINGS.set(HEAD, 1);
                        Experience.SETTINGS.set(CAPE, 1);
                        Experience.SETTINGS.set(PET, 2);
                        Experience.SETTINGS.set(AURORA, 1);
                        Experience.SETTINGS.set(STARS, 1);
                        Experience.SETTINGS.set(WEATHER, 4);
                        Experience.SETTINGS.set(SEASON, 1);
                        Experience.SETTINGS.set(WIDGETS, 1);
                        Experience.SETTINGS.set(DUMMY, 1);
                        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    });
            context.waitTicks(30);
            context.runOnClient(
                    mc -> {
                        float end =
                                mc.level
                                        .environmentAttributes()
                                        .getValue(
                                                EnvironmentAttributes.FOG_END_DISTANCE,
                                                mc.player.position());
                        if (end != 128f)
                            throw new AssertionError("Local fog override did not apply: " + end);
                        if (Experience.spawned() > Experience.SETTINGS.limit())
                            throw new AssertionError("Particle cap exceeded");
                        if (ExperienceRenderer.submitted() == 0)
                            throw new AssertionError("No experience geometry was submitted");
                    });
            for (int look = 1; look <= 6; look++) {
                final int selected = look;
                context.runOnClient(
                        mc -> {
                            Experience.SETTINGS.set(WORLD_LOOK, selected);
                            Experience.emote("67");
                        });
                context.waitTicks(8);
                context.runOnClient(
                        mc -> {
                            if (mc.gameRenderer.currentPostEffect() == null
                                    || !mc.gameRenderer
                                            .currentPostEffect()
                                            .getNamespace()
                                            .equals("nocturne"))
                                throw new AssertionError("Nocturne shader did not activate");
                        });
                context.takeScreenshot("nocturne-world-look-" + look);
            }
            world.getServer()
                    .runCommand(
                            "summon minecraft:zombie 0 -60 3"
                                + " {NoAI:1b,Health:2f,Tags:[\"nocturne_experience_target\"]}");
            world.getServer()
                    .runCommand(
                            "effect give @e[tag=nocturne_experience_target]"
                                + " minecraft:fire_resistance 100 0 true");
            world.getServer()
                    .runCommand(
                            "item replace entity @p weapon.mainhand with minecraft:diamond_sword");
            context.waitTicks(8);
            int killsBefore = context.computeOnClient(mc -> Experience.observedKills());
            context.runOnClient(
                    mc -> {
                        Experience.SETTINGS.set(HIT, 4);
                        Experience.SETTINGS.set(DAMAGE, 1);
                        Experience.SETTINGS.set(KILL, 1);
                        Experience.SETTINGS.set(BANNERS, 1);
                        net.minecraft.world.entity.Entity target = null;
                        for (var entity : mc.level.entitiesForRendering())
                            if (entity.getType() == net.minecraft.world.entity.EntityType.ZOMBIE) {
                                target = entity;
                                break;
                            }
                        if (target == null) throw new AssertionError("Combat fixture not loaded");
                        mc.gameMode.attack(mc.player, target);
                    });
            context.waitFor(mc -> Experience.observedKills() > killsBefore, 100);
            context.takeScreenshot("nocturne-observed-kill");
            context.runOnClient(
                    mc -> {
                        Experience.SETTINGS.set(WORLD_LOOK, 0);
                        Experience.SETTINGS.set(WEATHER, 0);
                        Experience.SETTINGS.set(TIME, 0);
                    });
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (mc.gameRenderer.currentPostEffect() != null)
                            throw new AssertionError("Disabling the shader did not release it");
                    });
            context.runOnClient(
                    mc -> {
                        Experience.SETTINGS.set(KILLCAM, 1);
                        ClientAccess.setScreen(mc, null);
                    });
            context.waitTicks(50);
            context.runOnClient(
                    mc -> {
                        if (KillReplay.frames() < 2 || KillReplay.frames() > KillReplay.LIMIT)
                            throw new AssertionError(
                                    "Replay recorder did not keep a bounded frame history: "
                                            + KillReplay.frames());
                        KillReplay.preview();
                    });
            context.waitTicks(5);
            context.takeScreenshot("nocturne-kill-replay");
            context.runOnClient(
                    mc -> {
                        ClientAccess.setScreen(mc, null);
                        Experience.SETTINGS.set(KILLCAM, 0);
                    });
            for (int style = 1; style <= 8; style++) {
                final int selected = style;
                context.runOnClient(
                        mc -> {
                            Experience.SETTINGS.set(DEATH, selected);
                            ClientAccess.setScreen(
                                    mc,
                                    new net.minecraft.client.gui.screens.DeathScreen(
                                            net.minecraft.network.chat.Component.literal(
                                                    "Visual preview"),
                                            false,
                                            mc.player));
                        });
                context.waitTicks(2);
                context.takeScreenshot("nocturne-death-" + style);
            }
            context.runOnClient(mc -> ClientAccess.setScreen(mc, null));
            for (int scale : new int[] {1, 2, 3, 4}) {
                context.getInput().resizeWindow(1280, 960);
                context.runOnClient(
                        mc -> {
                            mc.options.guiScale().set(scale);
                            dev.truwty.nocturne.compat.TestAccess.resizeGui(mc);
                            ClientAccess.setScreen(mc, new OrbitScreen(null));
                            assertBounds(mc.screen);
                        });
                for (var group : ExperienceSettings.Group.values())
                    context.runOnClient(
                            mc -> {
                                ClientAccess.setScreen(mc, new ExperienceScreen(null, group));
                                assertBounds(mc.screen);
                                mc.screen.mouseScrolled(0, 0, 0, -100);
                                assertBounds(mc.screen);
                            });
            }
            context.getInput().resizeWindow(854, 480);
            context.runOnClient(
                    mc -> {
                        mc.options.guiScale().set(1);
                        dev.truwty.nocturne.compat.TestAccess.resizeGui(mc);
                        ClientAccess.setScreen(mc, null);
                    });
            var reload =
                    context.computeOnClient(
                            mc -> {
                                var packs =
                                        new java.util.ArrayList<>(
                                                mc.getResourcePackRepository().getSelectedIds());
                                mc.getResourcePackRepository().getAvailableIds().stream()
                                        .filter(
                                                id ->
                                                        id.contains("nocturne:")
                                                                && (id.endsWith("mint_swords")
                                                                        || id.endsWith(
                                                                                "mint_totems")
                                                                        || id.endsWith(
                                                                                "mint_blocks")
                                                                        || id.endsWith(
                                                                                "hd_weather")))
                                        .forEach(packs::add);
                                if (packs.stream().filter(id -> id.contains("nocturne:")).count()
                                        < 4) throw new AssertionError("Texture packs missing");
                                mc.getResourcePackRepository().setSelected(packs);
                                return mc.reloadResourcePacks();
                            });
            context.waitFor(mc -> reload.isDone(), 1000);
            reload.join();
            context.waitTicks(5);
            context.takeScreenshot("nocturne-texture-pack-world");
            context.runOnClient(mc -> NocturneClient.SETTINGS.photosensitiveMode.set(true));
            context.waitTicks(3);
            context.runOnClient(
                    mc -> {
                        if (Experience.spawned() != 0 || Experience.liveParticles() != 0)
                            throw new AssertionError("Photosensitive mode allowed particles");
                    });
            NocturneClient.LOG.info(
                    "EXPERIENCE CLIENT CHECKS PASSED: Orbit, module screens, local geometry, shader"
                        + " looks, caps and accessibility");
        } finally {
            context.runOnClient(
                    mc -> {
                        Experience.SETTINGS.restore(original);
                        Experience.save();
                        NocturneClient.SETTINGS.photosensitiveMode.set(false);
                        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                        ClientAccess.setScreen(mc, null);
                    });
        }
    }

    private static void assertBounds(Screen screen) {
        for (var child : screen.children())
            if (child instanceof AbstractWidget widget
                    && widget.visible
                    && (widget.getX() < 0
                            || widget.getY() < 0
                            || widget.getRight() > screen.width
                            || widget.getBottom() > screen.height))
                throw new AssertionError("Out of bounds: " + widget.getMessage().getString());
    }
}
