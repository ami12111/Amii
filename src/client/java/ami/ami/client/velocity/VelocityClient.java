package ami.ami.client.velocity;

import ami.ami.client.gui.LiteClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Yarn mappings. */
public class VelocityClient implements ClientModInitializer {
    public static KeyBinding openMenu;

    @Override
    public void onInitializeClient() {
        VelocityConfig.load();

        KeyBinding.Category category =
                KeyBinding.Category.create(Identifier.of("ami", "main"));

        // Клавиша открытия клик-меню: Настройки -> Управление -> Ami
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ami.open_clickgui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openMenu.wasPressed()) {
                if (mc.currentScreen == null) {
                    mc.setScreen(new LiteClickGuiScreen());
                }
            }
        });
    }
}
