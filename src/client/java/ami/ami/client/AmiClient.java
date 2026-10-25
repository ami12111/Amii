package ami.ami.client;

import ami.ami.client.bind.ModuleBinds;
import net.fabricmc.api.ClientModInitializer;

public class AmiClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModuleBinds.init();
    }
}
