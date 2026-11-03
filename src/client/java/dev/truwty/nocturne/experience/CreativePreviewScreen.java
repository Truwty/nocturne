package dev.truwty.nocturne.experience;

import dev.truwty.nocturne.gui.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class CreativePreviewScreen extends StudioPanel {
    private String query = "";
    private int page;
    private List<ItemStack> shown = List.of();
    private boolean pending;

    public CreativePreviewScreen(Screen parent) {
        super(parent, "Fake Creative / visual catalog only");
    }

    @Override
    protected void build() {
        var search = field(x + 16, body, w - 32, "Find an item", query);
        search.setMaxLength(80);
        search.setResponder(
                value -> {
                    query = value;
                    page = 0;
                    pending = true;
                });
        shown =
                BuiltInRegistries.ITEM.stream()
                        .map(ItemStack::new)
                        .filter(
                                stack ->
                                        !stack.isEmpty()
                                                && stack.getHoverName()
                                                        .getString()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query.toLowerCase(Locale.ROOT)))
                        .toList();
        page = Math.clamp(page, 0, Math.max(0, (shown.size() - 1) / 48));
        button(
                x + 16,
                body + 190,
                90,
                "Previous",
                "Previous 48 items",
                () -> {
                    page = Math.max(0, page - 1);
                    rebuildWidgets();
                });
        button(
                x + 112,
                body + 190,
                90,
                "Next",
                "Next 48 items",
                () -> {
                    if ((page + 1) * 48 < shown.size()) page++;
                    rebuildWidgets();
                });
    }

    @Override
    public void tick() {
        if (pending) {
            pending = false;
            rebuildWidgets();
            for (var child : children())
                if (child instanceof net.minecraft.client.gui.components.EditBox field) {
                    setFocused(field);
                    field.setCursorPosition(field.getValue().length());
                    break;
                }
        }
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        for (int i = page * 48; i < Math.min(shown.size(), page * 48 + 48); i++) {
            int cell = i % 48,
                    xx = x + 20 + (cell % 8) * Math.max(24, (w - 40) / 8),
                    yy = body + 34 + (cell / 8) * 24;
            g.item(shown.get(i), xx, yy);
        }
        text(g, "Browse only / no item grants / page " + (page + 1), body + 225, 0xFFB5C9D6);
    }
}
