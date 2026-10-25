package ami.ami.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Live view-model for the click GUI: categories and modules are read from {@link ModuleRegistry},
 * every control reads and writes the real module state through the existing {@link Setting} classes.
 */
public final class ClickGuiModel {

    public record Category(String name, int icon, List<Module> modules) {
    }

    public static final class Module {
        public final String id;
        public final String name;
        public final String description;
        public final List<Control> controls;
        public final AnimatedFloat animation;
        public boolean enabled;
        private final ModuleEntry backing;

        Module(ModuleEntry backing, List<Control> controls) {
            this.backing = backing;
            this.id = backing.id();
            this.name = backing.name();
            this.description = backing.description();
            this.controls = List.copyOf(controls);
            this.enabled = backing.isEnabled();
            this.animation = new AnimatedFloat(enabled ? 1 : 0);
        }

        public void toggle() {
            backing.toggle();
            enabled = backing.isEnabled();
            animation.setTarget(enabled ? 1 : 0);
        }
    }

    public sealed interface Control permits Toggle, Slider, Action, Key {
        String name();

        String hint();
    }

    public static final class Toggle implements Control {
        private final BoolSetting backing;
        public final AnimatedFloat animation;
        public boolean value;

        Toggle(BoolSetting backing) {
            this.backing = backing;
            this.value = backing.get();
            this.animation = new AnimatedFloat(value ? 1 : 0);
        }

        public String name() {
            return backing.label();
        }

        public String hint() {
            return backing.description();
        }

        public void toggle() {
            backing.toggle();
            value = backing.get();
            animation.setTarget(value ? 1 : 0);
        }
    }

    public static final class Slider implements Control {
        private final NumberSetting backing;
        public final SliderModel model;

        Slider(NumberSetting backing) {
            this.backing = backing;
            this.model = new SliderModel(0f, 1f, (float) backing.normalized());
        }

        public String name() {
            return backing.label();
        }

        public String hint() {
            return backing.description();
        }

        public String value() {
            return backing.display();
        }

        public void sync() {
            backing.setNormalized(model.value());
        }

        /** Re-reads the module value, e.g. after the module changed it itself. */
        public void refresh() {
            model.setValue((float) backing.normalized());
        }
    }

    public static final class Action implements Control {
        private final ActionSetting backing;

        Action(ActionSetting backing) {
            this.backing = backing;
        }

        public String name() {
            return backing.label();
        }

        public String hint() {
            return backing.description();
        }

        public String button() {
            return backing.buttonLabel();
        }

        public void run() {
            backing.run();
        }
    }

    public record Key(String name, String hint) implements Control {
    }

    public static List<Category> create() {
        // One Module instance per entry, shared by the "All" bucket and the entry's own category,
        // so a toggle made in one view is visible in the other.
        Map<ModuleEntry, Module> modules = new LinkedHashMap<>();
        for (ModuleEntry entry : ModuleRegistry.all()) {
            modules.put(entry, live(entry));
        }
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("All", UiRenderer.ICON_ALL, modules(null, modules)));
        for (ModuleEntry.Category category : ModuleEntry.Category.values()) {
            categories.add(new Category(label(category), icon(category), modules(category, modules)));
        }
        return List.copyOf(categories);
    }

    private static List<Module> modules(ModuleEntry.Category category, Map<ModuleEntry, Module> modules) {
        List<Module> result = new ArrayList<>();
        for (ModuleEntry entry : ModuleRegistry.filtered(category)) {
            result.add(modules.get(entry));
        }
        return List.copyOf(result);
    }

    private static Module live(ModuleEntry entry) {
        List<Control> controls = new ArrayList<>();
        for (Setting setting : entry.settings()) {
            if (setting instanceof BoolSetting bool) {
                controls.add(new Toggle(bool));
            } else if (setting instanceof NumberSetting number) {
                controls.add(new Slider(number));
            } else if (setting instanceof ActionSetting action) {
                controls.add(new Action(action));
            }
        }
        controls.add(new Key("Keybind", "Click to bind, right-click to clear"));
        return new Module(entry, controls);
    }

    private static String label(ModuleEntry.Category category) {
        String raw = category.name();
        return raw.charAt(0) + raw.substring(1).toLowerCase(Locale.ROOT);
    }

    private static int icon(ModuleEntry.Category category) {
        return switch (category) {
            case COMBAT -> UiRenderer.ICON_COMBAT;
            case MOVEMENT -> UiRenderer.ICON_MOVEMENT;
            case RENDER -> UiRenderer.ICON_RENDER;
        };
    }

    private ClickGuiModel() {
    }
}
