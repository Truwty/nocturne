package dev.truwty.nocturne.lyrics;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StableLyricsTest {
 @Test void cameraRelativeAnchorRemainsConstantAcrossMovementYawAndPitch() {
  for(int i=0;i<180;i++) {
   var camera=new Vec3(i*.7,60+i*.02,-i*.4);
   var rotation=new Quaternionf().rotationYXZ((float)Math.toRadians(i*7),(float)Math.toRadians(i%120-60),0);
   Vec3 relative=StableLyrics.anchor(camera,rotation,3.5).subtract(camera);
   var local=new Vector3f((float)relative.x,(float)relative.y,(float)relative.z).rotate(new Quaternionf(rotation).conjugate());
   assertEquals(0,local.x,.00001);assertEquals(-.35,local.y,.00001);assertEquals(-3.5,local.z,.00001);
  }
 }
}
