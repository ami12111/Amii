package ami.ami.client.velocity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки модуля, хранятся в config/velocity.json. */
public final class VelocityConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("velocity.json");
    private static VelocityConfig instance = new VelocityConfig();

    public boolean enabled = true;
    public boolean onlyOnGround = true;
    /** Шанс срабатывания, 0..100 %. */
    public int chance = 100;
    /** Задержка прыжка после удара в тиках, 0..5. */
    public int delayTicks = 0;
    /** Минимальный интервал между прыжками модуля в тиках (не меньше ванильных 10). */
    public int cooldownTicks = 10;

    public static VelocityConfig get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                VelocityConfig loaded = GSON.fromJson(Files.readString(PATH), VelocityConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            }
        } catch (Exception ignored) {
            instance = new VelocityConfig();
        }
        instance.chance = Math.max(0, Math.min(100, instance.chance));
        instance.delayTicks = Math.max(0, Math.min(5, instance.delayTicks));
        instance.cooldownTicks = Math.max(10, Math.min(40, instance.cooldownTicks));
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(instance));
        } catch (IOException ignored) {
        }
    }
}
