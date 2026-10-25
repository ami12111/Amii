package ami.ami.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** On/off option bound directly to a module field. */
public final class BoolSetting extends Setting {
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public BoolSetting(String label, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        super(label, description);
        this.getter = getter;
        this.setter = setter;
    }

    public boolean get() {
        return getter.getAsBoolean();
    }

    public void set(boolean value) {
        setter.accept(value);
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public int rowHeight() {
        return 26;
    }
}
