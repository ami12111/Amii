package ami.ami.client.triggerbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Регистрация клавиши включения TriggerBot. */
public class TriggerBotClient implements ClientModInitializer {
    public static boolean enabled = false;
    public static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category =
                KeyBinding.Category.create(Identifier.of("ami", "triggerbot"));

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ami.triggerbot_toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (toggleKey.wasPressed()) {
                TriggerBotModule.enabled = !TriggerBotModule.enabled;
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal(
                            "TriggerBot: " + (TriggerBotModule.enabled ? "§aON" : "§cOFF")), true);
                }
            }
        });
    }
}
