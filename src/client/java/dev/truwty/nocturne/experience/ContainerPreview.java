package dev.truwty.nocturne.experience;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.*;

/** Only records inventory data a chest screen already received from the server. */
public final class ContainerPreview {
    public record View(String label, List<ItemStack> items) {}

    private static final LinkedHashMap<BlockPos, List<ItemStack>> chests =
            new LinkedHashMap<>(8, .75f, true);
    private static BlockPos opened;
    private static Object screen;
    private static int ticks;

    private ContainerPreview() {}

    public static void reset() {
        chests.clear();
        opened = null;
        screen = null;
    }

    public static void tick(Minecraft mc) {
        if (!Experience.SETTINGS.on(ExperienceSettings.Option.CONTAINER) || mc.level == null)
            return;
        if (mc.screen != screen) {
            screen = mc.screen;
            opened = null;
            if (screen instanceof ContainerScreen
                    && mc.hitResult instanceof BlockHitResult hit
                    && mc.level.getBlockEntity(hit.getBlockPos()) instanceof ChestBlockEntity)
                opened = hit.getBlockPos().immutable();
        }
        if (opened != null && mc.screen instanceof ContainerScreen container && ++ticks % 5 == 0) {
            var inventory = container.getMenu().getContainer();
            var items = new ArrayList<ItemStack>();
            for (int i = 0; i < Math.min(54, inventory.getContainerSize()); i++)
                items.add(inventory.getItem(i).copy());
            chests.put(opened, List.copyOf(items));
            while (chests.size() > 8) chests.remove(chests.keySet().iterator().next());
        }
    }

    public static View view(Minecraft mc) {
        int mode = Experience.SETTINGS.get(ExperienceSettings.Option.CONTAINER);
        if (mode == 0 || mc.player == null) return null;
        if (mode >= 2 && mc.hitResult instanceof BlockHitResult hit) {
            var items = chests.get(hit.getBlockPos());
            if (items != null) return new View("CHEST / last seen", items);
        }
        if (mode == 1 || mode == 3) {
            var contents =
                    mc.player
                            .getMainHandItem()
                            .get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (contents != null)
                return new View(
                        "CONTAINER / held item", contents.allItemsCopyStream().limit(54).toList());
        }
        return null;
    }
}
