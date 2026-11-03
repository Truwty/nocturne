package dev.truwty.nocturne.experience;

import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.gui.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

public final class ExperienceScreen extends StudioPanel {
    private final ExperienceSettings.Group group;
    private final List<ExperienceSettings.Option> options;

    public ExperienceScreen(Screen parent, ExperienceSettings.Group group) {
        super(
                parent,
                "Nocturne / "
                        + switch (group) {
                            case WORLD -> "World & sky";
                            case COMBAT -> "Combat";
                            case COSMETICS -> "Cosmetics";
                            case MOVEMENT -> "Movement FX";
                            case INTERFACE -> "Interface";
                            case FUN -> "Textures & fun";
                        });
        this.group = group;
        options =
                Arrays.stream(ExperienceSettings.Option.values())
                        .filter(o -> o.group == group)
                        .toList();
    }

    @Override
    protected int contentHeight() {
        return options.size() * 26
                + (group == ExperienceSettings.Group.FUN
                        ? 258
                        : group == ExperienceSettings.Group.WORLD ? 214 : 108);
    }

    @Override
    protected void build() {
        int row = body + 28;
        for (var option : options) {
            button(
                    x + 16,
                    row,
                    w - 32,
                    option.label + ": " + Experience.SETTINGS.label(option),
                    "Choose the next option. Applies locally; default is off or server settings.",
                    () -> {
                        Experience.SETTINGS.cycle(option);
                        if (option == ExperienceSettings.Option.SEASON && minecraft.level != null) {
                            minecraft.level.clearTintCaches();
                            minecraft.levelRenderer.allChanged();
                        }
                        Experience.save();
                        rebuildWidgets();
                    });
            row += 26;
        }
        if (group == ExperienceSettings.Group.WORLD) {
            range(
                    row,
                    "Custom fog distance",
                    16,
                    512,
                    Experience.SETTINGS.fogDistance(),
                    Experience.SETTINGS::fogDistance);
            row += 26;
            range(
                    row,
                    "Custom cloud height",
                    64,
                    384,
                    Experience.SETTINGS.cloudHeight(),
                    Experience.SETTINGS::cloudHeight);
            row += 26;
            range(
                    row,
                    "Custom fog hue",
                    0,
                    360,
                    Math.round(Experience.SETTINGS.fogHue() * 360),
                    value -> Experience.SETTINGS.fogHue(value / 360f));
        }
        if (group == ExperienceSettings.Group.COSMETICS) {
            var petName =
                    field(x + 16, row, w - 132, "Companion name", Experience.SETTINGS.petName());
            petName.setMaxLength(48);
            button(
                    x + w - 110,
                    row,
                    94,
                    "Name pet",
                    "Save a visible name, up to 24 characters",
                    () -> {
                        Experience.SETTINGS.petName(petName.getValue());
                        Experience.save();
                        notice = "Companion renamed";
                    });
        }
        if (group == ExperienceSettings.Group.COMBAT)
            button(
                    x + 16,
                    row,
                    w - 32,
                    "Watch recent replay",
                    "Requires capture enabled. Local footage plays at 0.4×; the server continues"
                            + " live.",
                    () -> {
                        ClientAccess.setScreen(minecraft, null);
                        KillReplay.preview();
                    });
        if (group == ExperienceSettings.Group.INTERFACE)
            button(
                    x + 16,
                    row,
                    w - 32,
                    "Preview skin intro",
                    "Play the optional intro now; Esc skips",
                    () -> ClientAccess.setScreen(minecraft, new IntroScreen(this)));
        if (group == ExperienceSettings.Group.FUN) {
            button(
                    x + 16,
                    row,
                    w - 32,
                    "Visual block palette / J",
                    "Place local holograms; no collision or server blocks",
                    () -> ClientAccess.setScreen(minecraft, new LocalPaletteScreen(this)));
            row += 26;
            button(
                    x + 16,
                    row,
                    w - 32,
                    "Fake Creative / visual inventory",
                    "Browse items without changing abilities or sending item grants",
                    () -> ClientAccess.setScreen(minecraft, new CreativePreviewScreen(this)));
            row += 26;
            button(
                    x + 16,
                    row,
                    w - 32,
                    "Animated swords, totems & blocks",
                    "Select optional Mint, Cyber or Amber resource packs",
                    () ->
                            ClientAccess.setScreen(
                                    minecraft,
                                    new net.minecraft.client.gui.screens.packs.PackSelectionScreen(
                                            minecraft.getResourcePackRepository(),
                                            repository -> {
                                                minecraft.options.updateResourcePacks(repository);
                                                ClientAccess.setScreen(minecraft, this);
                                            },
                                            minecraft.getResourcePackDirectory(),
                                            net.minecraft.network.chat.Component.literal(
                                                    "Nocturne animated textures"))));
            row += 26;
            button(
                    x + 16,
                    row,
                    w - 32,
                    "Rage quit",
                    "Confirm before leaving your current world",
                    () ->
                            ClientAccess.setScreen(
                                    minecraft,
                                    new net.minecraft.client.gui.screens.ConfirmScreen(
                                            confirmed -> {
                                                if (confirmed)
                                                    minecraft.disconnectFromWorld(
                                                            net.minecraft.network.chat.Component
                                                                    .literal("Rage quit"));
                                                else ClientAccess.setScreen(minecraft, this);
                                            },
                                            net.minecraft.network.chat.Component.literal(
                                                    "Leave this world?"),
                                            net.minecraft.network.chat.Component.literal(
                                                    "Disconnect from the current session."))));
        }
        if (group == ExperienceSettings.Group.FUN) {
            var app =
                    field(
                            x + 16,
                            body + options.size() * 26 + 146,
                            w - 138,
                            "Discord app ID / blank uses Nocturne",
                            Experience.SETTINGS.discordAppId());
            app.setMaxLength(20);
            button(
                    x + w - 116,
                    body + options.size() * 26 + 146,
                    100,
                    "Save app ID",
                    "No setup needed. Leave blank to use Nocturne; optional public ID override.",
                    () -> {
                        Experience.SETTINGS.discordAppId(app.getValue().strip());
                        Experience.save();
                        notice = DiscordPresence.status();
                    });
        }
        button(
                x + 16,
                body
                        + options.size() * 26
                        + (group == ExperienceSettings.Group.FUN
                                ? 210
                                : group == ExperienceSettings.Group.WORLD ? 126 : 62),
                w - 32,
                "Particle budget: " + Experience.SETTINGS.limit(),
                "Cycle the per-tick spawn cap; frame-time adaptation can lower it",
                () -> {
                    Experience.SETTINGS.limit(
                            Experience.SETTINGS.limit() >= 256
                                    ? 32
                                    : Experience.SETTINGS.limit() + 32);
                    Experience.save();
                    rebuildWidgets();
                });
    }

    private void range(
            int row,
            String label,
            int min,
            int max,
            int current,
            java.util.function.IntConsumer update) {
        addRenderableWidget(
                new StudioSlider(
                        x + 16,
                        row,
                        w - 32,
                        20,
                        net.minecraft.network.chat.Component.empty(),
                        (double) (current - min) / (max - min)) {
                    {
                        updateMessage();
                        setTooltip(
                                net.minecraft.client.gui.components.Tooltip.create(
                                        net.minecraft.network.chat.Component.literal(
                                                "Select Custom in the corresponding mode above."
                                                        + " Saved when you leave this screen.")));
                    }

                    @Override
                    protected void updateMessage() {
                        setMessage(Draw.comp(label + ": " + Math.round(min + value * (max - min))));
                    }

                    @Override
                    protected void applyValue() {
                        update.accept((int) Math.round(min + value * (max - min)));
                        updateMessage();
                    }
                });
    }

    @Override
    public void onClose() {
        Experience.save();
        super.onClose();
    }

    @Override
    protected void footer() {
        super.footer();
        button(
                x + 110,
                y + h - 29,
                130,
                "Reset category",
                "Turn this category back to its default settings",
                () -> {
                    for (var option : options) Experience.SETTINGS.set(option, 0);
                    if (group == ExperienceSettings.Group.WORLD && minecraft.level != null) {
                        minecraft.level.clearTintCaches();
                        minecraft.levelRenderer.allChanged();
                    }
                    Experience.save();
                    rebuildWidgets();
                });
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        String note =
                switch (group) {
                    case WORLD -> "Six color-grading shaders / existing Iris packs take priority.";
                    case COMBAT ->
                            "Effects use observed health changes; some servers hide that data.";
                    case COSMETICS -> "Local procedural cosmetics. Other players do not see them.";
                    case MOVEMENT ->
                            "Particle effects respect reduced motion and your effect budget.";
                    case INTERFACE ->
                            "C: hold zoom. Item timers are estimates from client-observed age.";
                    case FUN ->
                            "Visual toys only. Server blocks, abilities and inventories stay"
                                    + " intact.";
                };
        if (group == ExperienceSettings.Group.FUN)
            text(
                    g,
                    "Discord: " + DiscordPresence.status(),
                    body + options.size() * 26 + 178,
                    0xFFB5C9D6);
        text(g, note, body + 5, 0xFFB5C9D6);
    }
}
