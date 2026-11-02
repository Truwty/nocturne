package dev.truwty.nocturne.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

public record LyricFrame(Vec3 camera, PoseStack poses, SubmitNodeCollector submits) {}
