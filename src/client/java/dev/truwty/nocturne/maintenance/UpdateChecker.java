package dev.truwty.nocturne.maintenance;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.playback.BoundedBody;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public final class UpdateChecker {
    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(10000);
    private static CompletableFuture<?> pending;
    private static long nextAutomatic, nextManual;
    private static int generation;
    private static String notified = "", chatNotified = "";
    private static volatile String status = "Not checked; automatic checks are off by default";
    private static volatile ReleaseCatalog.Release available;

    private UpdateChecker() {}

    public static String version() {
        return dev.truwty.nocturne.platform.ModPlatform.version("nocturne");
    }

    public static String status() {
        return status;
    }

    public static ReleaseCatalog.Release available() {
        return available;
    }

    public static boolean busy() {
        return pending != null && !pending.isDone();
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (available != null && mc.player != null && !available.tag().equals(chatNotified)) {
            chatNotified = available.tag();
            ClientAccess.chat(mc)
                    .handleSystemMessage(
                            Component.literal("Nocturne " + available.tag() + " available · ")
                                    .append(
                                            Component.literal("Changelog / download")
                                                    .withStyle(style ->
                                                            style.withColor(
                                                                            net.minecraft
                                                                                    .ChatFormatting
                                                                                    .AQUA)
                                                                    .withUnderlined(true)
                                                                    .withClickEvent(
                                                                            new net.minecraft
                                                                                    .network.chat
                                                                                    .ClickEvent
                                                                                    .OpenUrl(
                                                                                    available
                                                                                            .changelog())))),
                            false);
        }
        if (NocturneClient.SETTINGS.updateChecks.on()
                && System.nanoTime() >= nextAutomatic
                && !busy()) check(false);
    }

    public static void reset() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null;
        available = null;
        nextAutomatic = 0;
        status = "Update preferences changed";
    }

    public static void close() {
        reset();
        HTTP.close();
    }

    static void applyCatalog(String catalog, boolean previews) {
        available = ReleaseCatalog.newest(catalog, version(), previews, NocturneClient.minecraftVersion(),
                dev.truwty.nocturne.platform.ModPlatform.loader()).orElse(null);
        status =
                available == null
                        ? "No newer compatible release in the latest 20 releases"
                        : "Available: " + available.tag();
        if (available != null && !available.tag().equals(notified)) {
            notified = available.tag();
            SystemToast.add(
                    ClientAccess.toasts(Minecraft.getInstance()),
                    TOAST,
                    Component.literal("Nocturne " + available.tag() + " available"),
                    Component.literal("N → Health → Releases & diagnostics"));
        }
    }

    public static void check(boolean manual) {
        long now = System.nanoTime();
        if (busy() || (manual && now < nextManual)) return;
        nextManual = now + TimeUnit.MINUTES.toNanos(1);
        nextAutomatic = now + TimeUnit.HOURS.toNanos(24);
        int request = ++generation;
        boolean previews = NocturneClient.SETTINGS.previewUpdates.on();
        status = "Checking GitHub releases…";
        var call =
                HttpRequest.newBuilder(
                                URI.create(
                                        "https://api.github.com/repos/Truwty/nocturne/releases?per_page=20"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Nocturne/" + version())
                        .GET()
                        .build();
        var exchange = HTTP.sendAsync(call, BoundedBody.handler(262144));
        pending = exchange;
        exchange.whenComplete((response, error) ->
                Minecraft.getInstance()
                        .execute(() -> {
                            if (request != generation) return;
                            if (error != null) {
                                status = "Update check unavailable; retry later";
                                return;
                            }
                            if (response.statusCode() != 200) {
                                status =
                                        response.statusCode() == 403 || response.statusCode() == 429
                                                ? "GitHub rate limit; retry later"
                                                : "GitHub release check failed (HTTP "
                                                        + response.statusCode()
                                                        + ")";
                                return;
                            }
                            try {
                                applyCatalog(
                                        new String(response.body(), StandardCharsets.UTF_8),
                                        previews);
                            } catch (RuntimeException ex) {
                                status = "GitHub returned an invalid release catalog";
                                NocturneClient.LOG.debug("Update catalog rejected", ex);
                            }
                        }));
    }
}
