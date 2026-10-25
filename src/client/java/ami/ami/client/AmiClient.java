package ami.ami.client;

import ami.ami.client.bind.ModuleBinds;
import ami.ami.client.config.ModuleSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class AmiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModuleSettings.load();
        ModuleBinds.init();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ModuleSettings.save());
    }
}
