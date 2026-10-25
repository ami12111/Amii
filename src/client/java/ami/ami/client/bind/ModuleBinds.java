package ami.ami.client.bind;

import ami.ami.client.gui.ModuleEntry;
import ami.ami.client.gui.ModuleRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Key binds for every module: one raw GLFW key code per module id, persisted in config/ami-binds.json.
 * Keys are polled once per client tick and toggle the bound module when no screen is open.
 */
public final class ModuleBinds {
    public static final int NONE = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ami-binds.json");
    private static final Map<String, Integer> BINDS = new LinkedHashMap<>();
    private static final Map<Integer, Boolean> KEY_STATE = new HashMap<>();

    private static boolean initialized;

    private ModuleBinds() {
    }

    /** Registers the tick handler and loads the saved binds. Safe to call more than once. */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        load();
        ClientTickEvents.END_CLIENT_TICK.register(ModuleBinds::tick);
    }

    public static int key(String moduleId) {
        return BINDS.getOrDefault(moduleId, NONE);
    }

    public static void set(String moduleId, int keyCode) {
        if (keyCode == NONE) {
            clear(moduleId);
            return;
        }
        // A key can only belong to one module: steal it from any previous owner.
        BINDS.entrySet().removeIf(bind -> !bind.getKey().equals(moduleId) && bind.getValue() == keyCode);
        BINDS.put(moduleId, keyCode);
        save();
    }

    public static void clear(String moduleId) {
        if (BINDS.remove(moduleId) != null) {
            save();
        }
    }

    /** Human-readable key name for the click GUI, or "None". */
    public static String label(String moduleId) {
        return label(key(moduleId));
    }

    public static String label(int keyCode) {
        if (keyCode == NONE) {
            return "Нет";
        }
        String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
        if (glfwName != null) {
            return glfwName.toUpperCase(Locale.ROOT);
        }
        return InputUtil.Type.KEYSYM.createFromCode(keyCode).getLocalizedText().getString();
    }

    private static void tick(MinecraftClient mc) {
        long handle = mc.getWindow().getHandle();
        boolean active = mc.currentScreen == null && mc.isWindowFocused();
        Map<Integer, Boolean> next = new HashMap<>();
        for (Map.Entry<String, Integer> bind : BINDS.entrySet()) {
            int code = bind.getValue();
            if (code == NONE) {
                continue;
            }
            boolean down = GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
            boolean wasDown = KEY_STATE.getOrDefault(code, false);
            next.put(code, down);
            if (!active || !down || wasDown) {
                continue;
            }
            ModuleEntry entry = ModuleRegistry.byId(bind.getKey());
            if (entry == null) {
                continue;
            }
            entry.toggle();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(entry.name() + ": "
                        + (entry.isEnabled() ? "§aВКЛ" : "§cВЫКЛ")), true);
            }
        }
        KEY_STATE.clear();
        KEY_STATE.putAll(next);
    }

    private static void load() {
        BINDS.clear();
        if (Files.exists(PATH)) {
            try {
                Map<String, Integer> loaded = GSON.fromJson(Files.readString(PATH),
                        new TypeToken<Map<String, Integer>>() {
                        }.getType());
                if (loaded != null) {
                    for (Map.Entry<String, Integer> entry : loaded.entrySet()) {
                        if (entry.getValue() != null && entry.getValue() > 0) {
                            BINDS.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                return;
            } catch (Exception ignored) {
                BINDS.clear();
            }
        }
        // First run: keep the historical TriggerBot default.
        BINDS.put("triggerbot", GLFW.GLFW_KEY_R);
        save();
    }

    private static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(BINDS));
        } catch (Exception ignored) {
        }
    }
}
