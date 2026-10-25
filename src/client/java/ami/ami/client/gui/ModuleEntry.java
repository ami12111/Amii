package ami.ami.client.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Describes one module for the click GUI. State is read and written through lambdas. */
public final class ModuleEntry {
    public enum Category {
        COMBAT,
        MOVEMENT,
        RENDER;

        public String label() {
            return name();
        }
    }

    private final String id;
    private final String name;
    private final String glyph;
    private final Category category;
    private final String description;
    private final BooleanSupplier enabled;
    private final Consumer<Boolean> setEnabled;
    private final List<Setting> settings;

    public ModuleEntry(String id, String name, String glyph, Category category, String description,
                       BooleanSupplier enabled, Consumer<Boolean> setEnabled, List<Setting> settings) {
        this.id = id;
        this.name = name;
        this.glyph = glyph;
        this.category = category;
        this.description = description;
        this.enabled = enabled;
        this.setEnabled = setEnabled;
        this.settings = List.copyOf(settings);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String glyph() {
        return glyph;
    }

    public Category category() {
        return category;
    }

    public String description() {
        return description;
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    public void setEnabled(boolean value) {
        setEnabled.accept(value);
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    public List<Setting> settings() {
        return settings;
    }

    public int settingsHeight() {
        if (settings.isEmpty()) {
            return 0;
        }
        int height = 7 + 7;
        for (Setting setting : settings) {
            height += setting.rowHeight();
        }
        return height;
    }
}
