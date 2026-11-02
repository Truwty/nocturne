package dev.truwty.nocturne.lyrics;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class StableLyrics {
    private StableLyrics() {}
    public static Vec3 anchor(Vec3 camera,Quaternionfc rotation,double distance) {
        Vec3 forward=new Vec3(new Vector3f(0,0,-1).rotate(rotation));
        Vec3 up=new Vec3(new Vector3f(0,1,0).rotate(rotation));
        return camera.add(forward.scale(distance)).add(up.scale(-.35));
    }
}
