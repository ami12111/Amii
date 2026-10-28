package ami.ami.client.config;

import ami.ami.client.movement.AutoSprintModule;
import ami.ami.client.render.HitParticle;
import ami.ami.client.render.JumpCircles;
import ami.ami.client.render.NoHurtCamModule;
import ami.ami.client.render.NoRenderModule;
import ami.ami.client.render.ViewModelModule;
import ami.ami.client.triggerbot.TriggerBotModule;
import ami.ami.client.velocity.VelocityConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Сохраняет настройки модулей в config/ami-modules.json при выходе из игры
 * (и при закрытии ClickGUI) и загружает их обратно при запуске.
 *
 * Настройки модулей — это public static поля без final. Пишем их по имени поля,
 * поэтому новые настройки подхватываются автоматически, а отсутствующие в файле
 * остаются со значениями по умолчанию. Velocity хранит настройки в экземпляре
 * VelocityConfig, поэтому для неё вызываем существующий save().
 */
public final class ModuleSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ami-modules.json");

    private static final List<Class<?>> MODULE_CLASSES = List.of(
            TriggerBotModule.class,
            ViewModelModule.class,
            NoHurtCamModule.class,
            NoRenderModule.class,
            AutoSprintModule.class,
            JumpCircles.class,
            HitParticle.class);

    private ModuleSettings() {
    }

    /** Читает сохранённые значения. Битый или отсутствующий файл просто оставляет значения по умолчанию. */
    public static void load() {
        if (!Files.exists(PATH)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(PATH));
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            for (Class<?> moduleClass : MODULE_CLASSES) {
                JsonElement sectionElement = root.get(key(moduleClass));
                if (sectionElement == null || !sectionElement.isJsonObject()) {
                    continue;
                }
                JsonObject section = sectionElement.getAsJsonObject();
                for (Field field : moduleClass.getDeclaredFields()) {
                    if (!isSettingField(field)) {
                        continue;
                    }
                    JsonElement value = section.get(field.getName());
                    if (value == null || !value.isJsonPrimitive()) {
                        continue;
                    }
                    try {
                        apply(field, value);
                    } catch (Exception ignored) {
                        // Одно битое значение не должно мешать загрузке остальных.
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Пишет настройки всех модулей. Вызывается при закрытии ClickGUI и при выходе из игры. */
    public static void save() {
        JsonObject root = new JsonObject();
        for (Class<?> moduleClass : MODULE_CLASSES) {
            JsonObject section = new JsonObject();
            for (Field field : moduleClass.getDeclaredFields()) {
                if (!isSettingField(field)) {
                    continue;
                }
                try {
                    section.add(field.getName(), GSON.toJsonTree(field.get(null)));
                } catch (Exception ignored) {
                }
            }
            root.add(key(moduleClass), section);
        }
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (Exception ignored) {
        }

        VelocityConfig.save();
    }

    /** Public static не-final поле простого типа — это настройка модуля. */
    private static boolean isSettingField(Field field) {
        int modifiers = field.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
                || Modifier.isFinal(modifiers) || field.isSynthetic()) {
            return false;
        }
        Class<?> type = field.getType();
        return type == boolean.class || type == int.class || type == long.class
                || type == float.class || type == double.class || type == String.class;
    }

    private static void apply(Field field, JsonElement value) throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == boolean.class) {
            field.setBoolean(null, value.getAsBoolean());
        } else if (type == int.class) {
            field.setInt(null, value.getAsInt());
        } else if (type == long.class) {
            field.setLong(null, value.getAsLong());
        } else if (type == float.class) {
            field.setFloat(null, value.getAsFloat());
        } else if (type == double.class) {
            field.setDouble(null, value.getAsDouble());
        } else if (type == String.class) {
            field.set(null, value.getAsString());
        }
    }

    /** TriggerBotModule -> triggerbot, ViewModelModule -> viewmodel. */
    private static String key(Class<?> moduleClass) {
        String name = moduleClass.getSimpleName();
        if (name.endsWith("Module")) {
            name = name.substring(0, name.length() - "Module".length());
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
