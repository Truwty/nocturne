package dev.truwty.nocturne.experience;

import static dev.truwty.nocturne.experience.ExperienceSettings.Option.*;

import dev.truwty.nocturne.accessibility.Accessibility;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.Draw;
import dev.truwty.nocturne.platform.LyricFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ExperienceRenderer {
    public record Wave(Vec3 at, float age, int color, float size) {}

    public record Kill(Vec3 at, int kind, float age) {}

    public record Caption(Vec3 at, String text, int color) {}

    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {}

    private static int submitted;

    private ExperienceRenderer() {}

    public static int submitted() {
        return submitted;
    }

    public static void render(
            LyricFrame frame,
            Vec3 pet,
            Vec3 dummy,
            List<Wave> waves,
            List<Caption> captions,
            String emote,
            List<Kill> kills) {
        var mc = Minecraft.getInstance();
        submitted = 0;
        if (mc.level == null
                || mc.player == null
                || mc.options.hideGui
                || mc.getWindow().isIconified()) return;
        var settings = Experience.SETTINGS;
        var geometry = new ArrayList<Quad>();
        int color = Experience.color();
        double time = Experience.ticks() * .05;
        if (Accessibility.particles()) {
            for (var kill : kills) kill(geometry, kill);
            for (var wave : waves)
                ring(
                        geometry,
                        wave.at,
                        wave.size * (.1 + wave.age),
                        .035,
                        Draw.alpha(wave.color, 1 - wave.age));
            if (settings.on(AURORA) && mc.level.canSeeSky(mc.player.blockPosition())) {
                Vec3 origin = frame.camera().add(-30, 35, 35);
                for (int i = 0; i < 24; i++) {
                    double x = i * 2.5, y = Math.sin(i * .32 + time * .2) * 4;
                    quad(
                            geometry,
                            origin.add(x, y, 0),
                            origin.add(x + 2.5, Math.sin((i + 1) * .32 + time * .2) * 4, 0),
                            origin.add(x + 2.5, 14 + y, 0),
                            origin.add(x, 14 + y, 0),
                            Draw.withAlpha(Draw.hsv((float) (.4 + i * .008), .6f, .9f), 55));
                }
            }
            if (settings.on(STARS)
                    && Experience.ticks() % 180 < 18
                    && mc.level.canSeeSky(mc.player.blockPosition())) {
                double travel = (Experience.ticks() % 180) * 2;
                Vec3 a = frame.camera().add(-25 + travel, 35 - travel * .15, 45);
                quad(
                        geometry,
                        a,
                        a.add(.15, .15, 0),
                        a.add(-6, 2, 0),
                        a.add(-6, 1.85, 0),
                        0xBBDDF5FF);
            }
        }
        if (pet != null && settings.on(PET)) {
            double bob = Accessibility.textMotion() ? Math.sin(time * 2) * .1 : 0;
            cube(geometry, pet.add(-.12, bob, -.12), .24, .24, .24, Draw.withAlpha(color, 210));
            if (settings.get(PET) == 2) {
                cube(geometry, pet.add(-.32, bob, -.03), .2, .06, .18, 0x99FFFFFF);
                cube(geometry, pet.add(.12, bob, -.03), .2, .06, .18, 0x99FFFFFF);
            }
            if (settings.get(PET) == 3) ring(geometry, pet.add(0, bob + .1, 0), .3, .025, color);
            caption(frame, pet.add(0, bob + .4, 0), settings.petName(), color);
        }
        if (dummy != null) {
            cube(geometry, dummy.add(-.3, 0, -.15), .6, 1.5, .3, 0x6647DDBA);
            cube(geometry, dummy.add(-.2, 1.5, -.2), .4, .4, .4, 0xAAACF6E1);
            caption(frame, dummy.add(0, 2.2, 0), "PRACTICE / visual target", color);
        }
        boolean thirdPerson = !mc.options.getCameraType().isFirstPerson();
        if (thirdPerson) cosmetics(geometry, mc.player.position(), mc.player.getYRot(), time);
        if (!emote.isEmpty()) caption(frame, mc.player.position().add(0, 2.6, 0), emote, color);
        if (settings.on(OUTLINE) && mc.hitResult instanceof BlockHitResult hit) {
            var at = Vec3.atLowerCornerOf(hit.getBlockPos());
            int ink =
                    switch (settings.get(OUTLINE)) {
                        case 2 -> 0xFFFF9AB5;
                        case 3 -> 0xFFFFD271;
                        default -> 0xFFA8F1D3;
                    };
            // Wire edges, never an opaque box over the selected block.
            double e = .012;
            for (int i = 0; i < 2; i++)
                for (int j = 0; j < 2; j++) {
                    cube(geometry, at.add(-e, i - e, j - e), 1 + 2 * e, 2 * e, 2 * e, ink);
                    cube(geometry, at.add(i - e, -e, j - e), 2 * e, 1 + 2 * e, 2 * e, ink);
                    cube(geometry, at.add(i - e, j - e, -e), 2 * e, 2 * e, 1 + 2 * e, ink);
                }
        }
        for (var ghost : LocalPaletteScreen.ghosts())
            cube(geometry, ghost.at(), 1, 1, 1, ghost.color());
        for (var label : captions) caption(frame, label.at, label.text, label.color);
        if (settings.on(ITEM_TIMER)) {
            int count = 0, visited = 0;
            for (var entity : mc.level.entitiesForRendering()) {
                if (++visited > 512 || count >= 16) break;
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity item
                        && item.distanceToSqr(mc.player) < 16 * 16
                        && count < 16) {
                    count++;
                    int seconds = Math.max(0, (6000 - item.getAge()) / 20);
                    caption(
                            frame,
                            item.position().add(0, .7, 0),
                            "~"
                                    + seconds / 60
                                    + ":"
                                    + String.format(Locale.ROOT, "%02d", seconds % 60)
                                    + " observed",
                            0xFFD9E5ED);
                }
            }
        }
        if (geometry.isEmpty()) return;
        var poses = frame.poses();
        poses.pushPose();
        Vec3 camera = frame.camera();
        List<Quad> frozen = List.copyOf(geometry);
        submitted = frozen.size() * 8;
        dev.truwty.nocturne.performance.RenderPerformance.mesh(submitted);
        frame.submits()
                .submitCustomGeometry(
                        poses,
                        RenderTypes.debugFilledBox(),
                        (pose, buffer) -> {
                            for (var q : frozen) {
                                buffer.addVertex(
                                                pose,
                                                (float) (q.a.x - camera.x),
                                                (float) (q.a.y - camera.y),
                                                (float) (q.a.z - camera.z))
                                        .setColor(q.color);
                                buffer.addVertex(
                                                pose,
                                                (float) (q.b.x - camera.x),
                                                (float) (q.b.y - camera.y),
                                                (float) (q.b.z - camera.z))
                                        .setColor(q.color);
                                buffer.addVertex(
                                                pose,
                                                (float) (q.c.x - camera.x),
                                                (float) (q.c.y - camera.y),
                                                (float) (q.c.z - camera.z))
                                        .setColor(q.color);
                                buffer.addVertex(
                                                pose,
                                                (float) (q.d.x - camera.x),
                                                (float) (q.d.y - camera.y),
                                                (float) (q.d.z - camera.z))
                                        .setColor(q.color);
                                buffer.addVertex(
                                                pose,
                                                (float) (q.d.x - camera.x),
                                                (float) (q.d.y - camera.y),
                                                (float) (q.d.z - camera.z))
                                        .setColor(q.color);
                                buffer.addVertex(
                                                pose,
                                                (float) (q.c.x - camera.x),
                                                (float) (q.c.y - camera.y),
                                                (float) (q.c.z - camera.z))
                                        .setColor(q.color);
                                buffer.addVertex(
                                                pose,
                                                (float) (q.b.x - camera.x),
                                                (float) (q.b.y - camera.y),
                                                (float) (q.b.z - camera.z))
                                        .setColor(q.color);
                                buffer.addVertex(
                                                pose,
                                                (float) (q.a.x - camera.x),
                                                (float) (q.a.y - camera.y),
                                                (float) (q.a.z - camera.z))
                                        .setColor(q.color);
                            }
                        });
        poses.popPose();
    }

    private static void caption(LyricFrame frame, Vec3 at, String text, int color) {
        var mc = Minecraft.getInstance();
        if (at.distanceToSqr(frame.camera()) > 48 * 48) return;
        var poses = frame.poses();
        poses.pushPose();
        poses.translate(at.x - frame.camera().x, at.y - frame.camera().y, at.z - frame.camera().z);
        poses.mulPose(ClientAccess.camera(mc).rotation());
        poses.scale(.025f, -.025f, .025f);
        frame.submits()
                .submitText(
                        poses,
                        -mc.font.width(text) / 2f,
                        0,
                        Component.literal(text).getVisualOrderText(),
                        true,
                        Font.DisplayMode.NORMAL,
                        15728880,
                        color,
                        0x77101820,
                        0);
        poses.popPose();
    }

    private static void cosmetics(List<Quad> geometry, Vec3 origin, float yaw, double time) {
        var settings = Experience.SETTINGS;
        int color = Experience.color();
        var local = new ArrayList<Quad>();
        if (settings.on(CAPE)) {
            int tint =
                    switch (settings.get(CAPE)) {
                        case 2 -> 0xCC9B70FF;
                        case 3 -> 0xCCFFAE77;
                        default -> 0xCCA8F1D3;
                    };
            double sway = Accessibility.textMotion() ? Math.sin(time * 2) * .08 : 0;
            quad(
                    local,
                    new Vec3(-.3, 1.45, .22),
                    new Vec3(.3, 1.45, .22),
                    new Vec3(.4, .25, .38 + sway),
                    new Vec3(-.4, .25, .38 + sway),
                    tint);
        }
        if (settings.on(WINGS)) {
            double flap = Accessibility.textMotion() ? Math.sin(time * 2) * .25 : 0;
            for (int side : new int[] {-1, 1}) {
                Vec3 root = new Vec3(side * .18, 1.4, .2),
                        tip = new Vec3(side * 1.35, 1.8, .25 + flap),
                        low = new Vec3(side * .8, .55, .35);
                quad(local, root, tip, low, root, Draw.withAlpha(color, 170));
                for (int i = 0; i < 4; i++)
                    cube(
                            local,
                            new Vec3(side * (.4 + i * .2) - .025, .8 + i * .15, .3 + flap * i / 4),
                            .05,
                            .45,
                            .05,
                            0xBBFFFFFF);
            }
        }
        switch (settings.get(HEAD)) {
            case 1 -> ring(local, new Vec3(0, 2.06, 0), .32, .045, 0xFFFFDB85);
            case 2 -> {
                ring(local, new Vec3(0, 1.86, 0), .27, .06, 0xFFFFDB85);
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5;
                    cube(
                            local,
                            new Vec3(Math.cos(a) * .23 - .03, 1.86, Math.sin(a) * .23 - .03),
                            .06,
                            .17,
                            .06,
                            0xFFFFDB85);
                }
            }
            case 3 -> {
                cube(local, new Vec3(-.3, 1.83, 0), .1, .34, .1, color);
                cube(local, new Vec3(.2, 1.83, 0), .1, .34, .1, color);
            }
            case 4 -> {
                cube(local, new Vec3(-.4, 1.83, -.4), .8, .05, .8, 0xEE202331);
                cube(local, new Vec3(-.24, 1.88, -.24), .48, .45, .48, 0xEE202331);
            }
            default -> {}
        }
        double angle = Math.toRadians(-yaw), c = Math.cos(angle), s = Math.sin(angle);
        for (var q : local)
            quad(
                    geometry,
                    transform(q.a, origin, c, s),
                    transform(q.b, origin, c, s),
                    transform(q.c, origin, c, s),
                    transform(q.d, origin, c, s),
                    q.color);
    }

    private static void kill(List<Quad> geometry, Kill kill) {
        Vec3 p = kill.at;
        int color = Draw.alpha(kill.kind == 1 ? 0xFFA6774E : Experience.color(), 1 - kill.age);
        if (kill.kind == 1) {
            // Original wooden mascot and mallet; entirely local submitted geometry.
            cube(geometry, p.add(-.2, 0, -.15), .14, .55, .2, color);
            cube(geometry, p.add(.06, 0, -.15), .14, .55, .2, color);
            cube(geometry, p.add(-.24, .45, -.2), .48, 1, .4, color);
            cube(geometry, p.add(-.15, 1.15, -.215), .09, .13, .025, 0xFFF1EAD4);
            cube(geometry, p.add(.06, 1.15, -.215), .09, .13, .025, 0xFFF1EAD4);
            double drop = Math.sin(Math.min(1, kill.age * 2) * Math.PI) * .8;
            cube(geometry, p.add(.4, .7 + drop, 0), .07, .7, .07, color);
            cube(geometry, p.add(.15, 1.3 + drop, -.12), .55, .25, .3, color);
        } else {
            double angle = kill.age * Math.PI * 2;
            for (int i = 0; i < 6; i++) {
                double a = angle + i * Math.PI / 3;
                cube(
                        geometry,
                        p.add(
                                Math.cos(a) * kill.age * 1.8,
                                .4 + Math.sin(kill.age * Math.PI) * 1.8,
                                Math.sin(a) * kill.age * 1.8),
                        .12,
                        .12,
                        .12,
                        color);
            }
        }
    }

    private static Vec3 transform(Vec3 p, Vec3 origin, double c, double s) {
        return origin.add(p.x * c - p.z * s, p.y, p.x * s + p.z * c);
    }

    private static void quad(List<Quad> target, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        if (target.size() < 768) target.add(new Quad(a, b, c, d, color));
    }

    private static void ring(List<Quad> target, Vec3 p, double radius, double thick, int color) {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16, b = (i + 1) * Math.PI / 16;
            quad(
                    target,
                    p.add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    p.add(Math.cos(b) * radius, 0, Math.sin(b) * radius),
                    p.add(Math.cos(b) * (radius + thick), 0, Math.sin(b) * (radius + thick)),
                    p.add(Math.cos(a) * (radius + thick), 0, Math.sin(a) * (radius + thick)),
                    color);
        }
    }

    private static void cube(List<Quad> target, Vec3 p, double x, double y, double z, int color) {
        Vec3 a = p,
                b = p.add(x, 0, 0),
                c = p.add(x, y, 0),
                d = p.add(0, y, 0),
                e = p.add(0, 0, z),
                f = p.add(x, 0, z),
                g = p.add(x, y, z),
                h = p.add(0, y, z);
        quad(target, a, b, c, d, color);
        quad(target, e, h, g, f, color);
        quad(target, a, e, f, b, color);
        quad(target, d, c, g, h, color);
        quad(target, a, d, h, e, color);
        quad(target, b, f, g, c, color);
    }
}
