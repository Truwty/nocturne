package dev.truwty.nocturne.platform;

import dev.truwty.nocturne.NocturneClient;
import net.fabricmc.api.ClientModInitializer;

public final class FabricEntrypoint implements ClientModInitializer {
    @Override public void onInitializeClient() { new NocturneClient().onInitializeClient(); }
}
