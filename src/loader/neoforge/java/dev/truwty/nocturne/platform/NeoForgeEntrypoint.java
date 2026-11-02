package dev.truwty.nocturne.platform;

import dev.truwty.nocturne.NocturneClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value=NocturneClient.ID, dist=Dist.CLIENT)
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint(IEventBus modBus, ModContainer container) {
        ClientHooks.bind(modBus);
        new NocturneClient().onInitializeClient();
        IConfigScreenFactory screens = (mod, parent) -> NocturneClient.settingsScreen(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, screens);
    }
}
