package dev.truwty.nocturne.maintenance;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class UpdateClientChecks {
    private UpdateClientChecks() {}

    public static void run(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if (UpdateChecker.busy())
                throw new AssertionError("Automatic update request ran without opting in");
            String minecraft = dev.truwty.nocturne.NocturneClient.minecraftVersion();
            var release = new com.google.gson.JsonObject();
            release.addProperty("tag_name", "v999.0.0");
            release.addProperty("draft", false);
            release.addProperty("prerelease", false);
            var assets = new com.google.gson.JsonArray();
            for (String name : java.util.List.of("nocturne-999.0.0-mc" + minecraft + ".jar",
                    "nocturne-mc" + minecraft + ".json")) {
                var asset = new com.google.gson.JsonObject();
                asset.addProperty("name", name);
                assets.add(asset);
            }
            release.add("assets", assets);
            UpdateChecker.applyCatalog("[" + release + "]", false);
            if (UpdateChecker.available() == null
                    || !UpdateChecker.available().changelog().toString().endsWith("/v999.0.0"))
                throw new AssertionError(
                        "Compatible update notification did not retain its changelog");
            UpdateChecker.tick();
            UpdateChecker.reset();
        });
    }
}
