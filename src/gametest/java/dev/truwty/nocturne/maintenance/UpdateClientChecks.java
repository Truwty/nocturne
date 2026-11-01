package dev.truwty.nocturne.maintenance;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class UpdateClientChecks {
    private UpdateClientChecks() {}

    public static void run(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            if (UpdateChecker.busy())
                throw new AssertionError("Automatic update request ran without opting in");
            UpdateChecker.applyCatalog(
                    "[{\"tag_name\":\"v999.0.0\",\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"nocturne-999.0.0.jar\"},{\"name\":\"nocturne-mc26.1.2.json\"}]}]",
                    false);
            if (UpdateChecker.available() == null
                    || !UpdateChecker.available().changelog().toString().endsWith("/v999.0.0"))
                throw new AssertionError(
                        "Compatible update notification did not retain its changelog");
            UpdateChecker.tick();
            UpdateChecker.reset();
        });
    }
}
