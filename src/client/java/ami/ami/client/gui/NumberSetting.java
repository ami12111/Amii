package ami.ami.client.gui;

import net.minecraft.util.math.MathHelper;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Numeric option with a snapped step, rendered as a custom slider. */
public final class NumberSetting extends Setting {
    private final double min;
    private final double max;
    private final double step;
    private final int decimals;
    private final String suffix;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    public NumberSetting(String label, String description, double min, double max, double step,
                         int decimals, String suffix, DoubleSupplier getter, DoubleConsumer setter) {
        super(label, description);
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = decimals;
        this.suffix = suffix;
        this.getter = getter;
        this.setter = setter;
    }

    public double get() {
        return MathHelper.clamp(getter.getAsDouble(), min, max);
    }

    public void set(double value) {
        double snapped = min + Math.round((value - min) / step) * step;
        setter.accept(MathHelper.clamp(snapped, min, max));
    }

    public double normalized() {
        return max - min <= 0.0 ? 0.0 : (get() - min) / (max - min);
    }

    public void setNormalized(double t) {
        set(min + MathHelper.clamp(t, 0.0, 1.0) * (max - min));
    }

    public String display() {
        return String.format(Locale.ROOT, "%." + decimals + "f", get()) + suffix;
    }

    @Override
    public int rowHeight() {
        return 35;
    }
}
