package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.Fx;

public final class Anim {
   private static float dt = 0.016666668F;
   private static double time;

   private Anim() {
   }

   public static void tick(float delta) {
      dt = delta;
      time += delta;
   }

   public static float dt() {
      return dt;
   }

   public static float time() {
      return (float)time;
   }

   public static boolean on() {
      return Fx.animations();
   }

   public static float k(float rate) {
      return !on() ? 1.0F : 1.0F - (float)Math.exp(-rate * dt * Fx.animationSpeed());
   }

   public static float to(float current, float target, float rate) {
      float n = current + (target - current) * k(rate);
      return Math.abs(target - n) < 5.0E-4F ? target : n;
   }

   public static float clamp01(float v) {
      return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
   }

   public static float easeOutCubic(float t) {
      t = clamp01(t);
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   public static float easeInOutCubic(float t) {
      t = clamp01(t);
      return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
   }

   public static float easeOutBack(float t) {
      t = clamp01(t);
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      return 1.0F + c3 * (float)Math.pow(t - 1.0F, 3.0) + c1 * (float)Math.pow(t - 1.0F, 2.0);
   }

   public static final class Spring {
      public float value;
      public float velocity;
      public float target;
      private final float stiffness;
      private final float damping;

      public Spring(float start, float stiffness, float damping) {
         this.value = this.target = start;
         this.stiffness = stiffness;
         this.damping = damping;
      }

      public Spring(float start) {
         this(start, 170.0F, 22.0F);
      }

      public float update() {
         if (!Anim.on()) {
            this.value = this.target;
            this.velocity = 0.0F;
            return this.value;
         }

         float d = Anim.dt * Fx.animationSpeed();
         int steps = Math.max(1, (int)Math.ceil(d / 0.008F));
         float h = d / steps;

         for (int i = 0; i < steps; i++) {
            float force = (this.target - this.value) * this.stiffness - this.velocity * this.damping;
            this.velocity += force * h;
            this.value = this.value + this.velocity * h;
         }

         if (Math.abs(this.target - this.value) < 0.01F && Math.abs(this.velocity) < 0.01F) {
            this.value = this.target;
            this.velocity = 0.0F;
         }

         return this.value;
      }

      public void snap(float v) {
         this.value = this.target = v;
         this.velocity = 0.0F;
      }

      public boolean settled() {
         return this.value == this.target && this.velocity == 0.0F;
      }
   }
}
