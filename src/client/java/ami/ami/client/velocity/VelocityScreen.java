package ami.ami.client.velocity;

import ami.ami.client.velocity.VelocityStats.Result;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/** Минималистичное меню: настройки + индикатор точности. Yarn mappings. */
public class VelocityScreen extends Screen {
    private static final int W = 240;
    private static final int H = 206;

    private static final int COLOR_PANEL = 0xE6101216;
    private static final int COLOR_BORDER = 0xFF2F333B;
    private static final int COLOR_TRACK = 0xFF2A2D33;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF8B919C;

    private int left;
    private int top;
    private int statsTop;

    public VelocityScreen() {
        super(Text.literal("Velocity"));
    }

    @Override
    protected void init() {
        VelocityConfig config = VelocityConfig.get();
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;

        int x = left + 10;
        int w = W - 20;
        int y = top + 28;

        addDrawableChild(ButtonWidget.builder(toggleText("Модуль", config.enabled), b -> {
            config.enabled = !config.enabled;
            b.setMessage(toggleText("Модуль", config.enabled));
        }).dimensions(x, y, w, 20).build());
        y += 24;

        addDrawableChild(new ValueSlider(x, y, w, 20, "Шанс", "%", 0, 100,
                config.chance, v -> config.chance = v));
        y += 24;

        addDrawableChild(new ValueSlider(x, y, w, 20, "Задержка", " тик.", 0, 5,
                config.delayTicks, v -> config.delayTicks = v));
        y += 24;

        addDrawableChild(ButtonWidget.builder(toggleText("Только с земли", config.onlyOnGround), b -> {
            config.onlyOnGround = !config.onlyOnGround;
            b.setMessage(toggleText("Только с земли", config.onlyOnGround));
        }).dimensions(x, y, w, 20).build());
        y += 24;

        statsTop = y + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить статистику"),
                        b -> VelocityStats.reset())
                .dimensions(x, top + H - 26, w, 16).build());
    }

    private static Text toggleText(String name, boolean value) {
        return Text.literal(name + ": " + (value ? "вкл" : "выкл"));
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float deltaTicks) {
        super.renderBackground(g, mouseX, mouseY, deltaTicks);

        // Панель
        g.fill(left, top, left + W, top + H, COLOR_PANEL);
        // Рамка (1 px): нет DrawContext.renderOutline, рисуем линиями
        g.fill(left, top, left + W, top + 1, COLOR_BORDER);
        g.fill(left, top + H - 1, left + W, top + H, COLOR_BORDER);
        g.fill(left, top, left + 1, top + H, COLOR_BORDER);
        g.fill(left + W - 1, top, left + W, top + H, COLOR_BORDER);
        g.drawText(this.textRenderer, "VELOCITY", left + 10, top + 10, COLOR_TEXT, false);

        drawStats(g);
    }

    private void drawStats(DrawContext g) {
        int x = left + 10;
        int w = W - 20;
        int y = statsTop;

        int attempts = VelocityStats.attempts();
        float accuracy = VelocityStats.accuracy();

        String title = attempts == 0
                ? "Точность: нет данных"
                : String.format("Точность: %.0f%%  (%d/%d)", accuracy,
                VelocityStats.count(Result.JUMPED), attempts);
        g.drawText(this.textRenderer, title, x, y, COLOR_TEXT, false);

        // Полоса точности
        int barY = y + 13;
        g.fill(x, barY, x + w, barY + 6, COLOR_TRACK);
        if (attempts > 0) {
            g.fill(x, barY, x + Math.round(w * accuracy / 100f), barY + 6, accuracyColor(accuracy));
        }

        // Последние события: слева самое старое, справа самое новое
        int dotsY = barY + 12;
        int step = w / VelocityStats.WINDOW;
        for (int i = 0; i < VelocityStats.WINDOW; i++) {
            Result r = VelocityStats.recent(i);
            int color = r == null ? COLOR_TRACK : resultColor(r);
            int dx = x + i * step;
            g.fill(dx, dotsY, dx + step - 2, dotsY + 8, color);
        }

        String summary = String.format("ок %d · мимо %d · сбой %d · пропуск %d",
                VelocityStats.count(Result.JUMPED), VelocityStats.count(Result.MISSED),
                VelocityStats.count(Result.FAILED), VelocityStats.count(Result.SKIPPED));
        g.drawText(this.textRenderer, summary, x, dotsY + 13, COLOR_MUTED, false);
    }

    private static int accuracyColor(float accuracy) {
        if (accuracy >= 90f) return 0xFF4CD27A;
        if (accuracy >= 70f) return 0xFFF5B83D;
        return 0xFFE5484D;
    }

    private static int resultColor(Result r) {
        return switch (r) {
            case JUMPED -> 0xFF4CD27A;
            case MISSED -> 0xFFF5B83D;
            case FAILED -> 0xFFE5484D;
            case SKIPPED -> 0xFF5A5F6A;
        };
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        VelocityConfig.save();
    }

    /** Слайдер с целыми значениями. */
    private static final class ValueSlider extends SliderWidget {
        private final String label;
        private final String suffix;
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        ValueSlider(int x, int y, int width, int height, String label, String suffix,
                    int min, int max, int value, IntConsumer onChange) {
            super(x, y, width, height, Text.empty(), (double) (value - min) / (max - min));
            this.label = label;
            this.suffix = suffix;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            if (label == null) {
                return;
            }
            setMessage(Text.literal(label + ": " + current() + suffix));
        }

        @Override
        protected void applyValue() {
            int cur = current();
            this.value = (double) (cur - min) / (max - min); // прилипание к целому значению
            onChange.accept(cur);
        }
    }
}
