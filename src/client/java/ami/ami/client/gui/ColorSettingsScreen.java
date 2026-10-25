package ami.ami.client.gui;

import ami.ami.client.config.ModuleSettings;
import ami.ami.client.render.JumpCircles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Отдельное окно настройки цветов Jump Circles.
 *
 * Слева - карточки эффектов (takeoff / landing / halo), в центре - HSV-квадрат
 * и полоса оттенка, справа - живой предпросмотр, ниже - палитра пресетов.
 * Цвет пишется прямо в поля JumpCircles, поэтому изменения видны сразу.
 */
public final class ColorSettingsScreen extends Screen {
    private static final int WHITE = 0xFFF1F1F1;
    private static final int TEXT = 0xFFC9C9CB;
    private static final int MUTED = 0xFF73757B;
    private static final int ACCENT = 0xFFECEDEF;
    private static final int PANEL = 0xFA090A0C;
    private static final int CARD = 0xFF101113;
    private static final int CARD_HOVER = 0xFF14161C;
    private static final int CARD_SELECTED = 0xFF171A21;
    private static final int BORDER = 0xFF191A1E;

    private static final String[] EFFECT_NAMES = {"Прыжок", "Приземление", "Кольца"};
    private static final String[] EFFECT_HINTS = {
            "Круг под ногами",
            "Волна в точке приземления",
            "Кольца вдоль тела"
    };
    private static final int[] DEFAULT_COLORS = {0xB06CFF, 0x6C9BFF, 0xE08CFF};
    private static final int[] PRESETS = {
            0xB06CFF, 0xFF6CD9, 0xFF6C6C, 0xFFA86C, 0xE8FF6C,
            0x6CFF9E, 0x6CD9FF, 0x6C8CFF, 0xFFFFFF, 0x9AA0AA
    };

    private final Screen parent;
    private int selected;
    private float hue;
    private float saturation;
    private float value;
    private boolean draggingSv;
    private boolean draggingHue;
    /** Экран открывается кликом, поэтому первый кадр не должен считать это новым нажатием. */
    private boolean previousDown = true;
    private long lastFrame = System.nanoTime();

    public ColorSettingsScreen(Screen parent) {
        super(Text.literal("Настройка цветов"));
        this.parent = parent;
        syncFromModule();
    }

    /** Открывает редактор поверх текущего экрана (обычно ClickGUI). */
    public static void open() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof ColorSettingsScreen) {
            return;
        }
        mc.setScreen(new ColorSettingsScreen(mc.currentScreen));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        ModuleSettings.save();
    }

    // ---------------------------------------------------------------- render
    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float tickDelta) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean pressed = down && !previousDown;

        Layout l = Layout.create(width, height);
        UiDrawList ui = new UiDrawList();
        backdrop(ui, l);
        header(ui, l, mouseX, mouseY, pressed);
        cards(ui, l, mouseX, mouseY, pressed);
        picker(ui, l, mouseX, mouseY, down, pressed, now);
        presets(ui, l, mouseX, mouseY, pressed);
        footer(ui, l);

        UiRenderer.submit(g, ui, textRenderer);

        if (!down) {
            draggingSv = false;
            draggingHue = false;
        }
        previousDown = down;
    }

    private void backdrop(UiDrawList ui, Layout l) {
        ui.rect(0, 0, width, height, 0xC9000000);
        for (int i = 0; i < 90; i++) {
            float px = i * 97 % Math.max(1, width);
            float py = i * 53 % Math.max(1, height);
            ui.circle(px, py, 0.45f, 0x16FFFFFF);
        }
        for (int i = 5; i > 0; i--) {
            float p = i * 3.5f * l.s;
            ui.roundedRect(l.x - p, l.y - p + 4 * l.s, l.w + p * 2, l.h + p * 2,
                    14 * l.s, 0x08000000);
        }
        ui.roundedRect(l.x, l.y, l.w, l.h, 10 * l.s, PANEL)
                .outlineRoundedRect(l.x, l.y, l.w, l.h, 10 * l.s,
                        Math.max(0.6f, l.s * 0.8f), BORDER);
    }

    private void header(UiDrawList ui, Layout l, int mx, int my, boolean pressed) {
        ui.strongText(l.x + 20 * l.s, l.y + 15 * l.s, 10 * l.s, WHITE, "ЦВЕТА");
        ui.text(l.x + 20 * l.s, l.y + 30 * l.s, 6.2f * l.s, MUTED,
                "Jump Circles · выберите цвет для каждого эффекта");

        float cs = 20 * l.s;
        float cx = l.x + l.w - 20 * l.s - cs;
        float cy = l.y + 14 * l.s;
        boolean over = hit(mx, my, cx, cy, cs, cs);
        if (pressed && over) {
            close();
        }
        ui.roundedRect(cx, cy, cs, cs, 4 * l.s, over ? 0xFF1C2133 : 0xFF14161C);
        ui.line(cx + 6 * l.s, cy + 6 * l.s, cx + cs - 6 * l.s, cy + cs - 6 * l.s,
                1.2f * l.s, TEXT);
        ui.line(cx + cs - 6 * l.s, cy + 6 * l.s, cx + 6 * l.s, cy + cs - 6 * l.s,
                1.2f * l.s, TEXT);
    }

    private void cards(UiDrawList ui, Layout l, int mx, int my, boolean pressed) {
        float cardY = l.y + 48 * l.s;
        float gap = 10 * l.s;
        float cardW = (l.w - 40 * l.s - 2 * gap) / 3f;
        float cardH = 50 * l.s;

        for (int i = 0; i < 3; i++) {
            float cx = l.x + 20 * l.s + i * (cardW + gap);
            boolean over = hit(mx, my, cx, cardY, cardW, cardH);
            if (pressed && over && selected != i) {
                selected = i;
                syncFromModule();
            }
            boolean sel = selected == i;
            int bg = sel ? CARD_SELECTED : over ? CARD_HOVER : CARD;
            ui.roundedRect(cx, cardY, cardW, cardH, 6 * l.s, bg);
            if (sel) {
                ui.outlineRoundedRect(cx, cardY, cardW, cardH, 6 * l.s,
                        1.2f * l.s, 0xFF3B4150);
            }

            int color = sel ? currentColor() : colorOf(i);
            ui.circle(cx + 17 * l.s, cardY + cardH / 2f, 9 * l.s, 0xFF000000 | color)
                    .outlineRoundedRect(cx + 17 * l.s - 9 * l.s, cardY + cardH / 2f - 9 * l.s,
                            18 * l.s, 18 * l.s, 9 * l.s, 1, 0xFF2A2D36);
            ui.strongText(cx + 32 * l.s, cardY + 13 * l.s, 7 * l.s, TEXT, EFFECT_NAMES[i]);
            ui.text(cx + 32 * l.s, cardY + 24 * l.s, 5.8f * l.s, MUTED,
                    String.format("#%06X", color));
            ui.text(cx + 32 * l.s, cardY + 34 * l.s, 5.4f * l.s, MUTED, EFFECT_HINTS[i]);
        }
    }

    private void picker(UiDrawList ui, Layout l, int mx, int my, boolean down, boolean pressed, long now) {
        float svX = l.x + 20 * l.s;
        float svY = l.y + 110 * l.s;
        float sv = 168 * l.s;
        float hueW = 16 * l.s;
        float hueX = svX + sv + 12 * l.s;

        // --- saturation/value square, drawn as a small grid of solid cells
        int cells = 24;
        float cell = sv / cells;
        for (int xi = 0; xi < cells; xi++) {
            float sat = (xi + 0.5f) / cells;
            for (int yi = 0; yi < cells; yi++) {
                float val = 1f - (yi + 0.5f) / cells;
                ui.rect(svX + xi * cell, svY + yi * cell, cell + 0.5f, cell + 0.5f,
                        0xFF000000 | hsvToRgb(hue, sat, val));
            }
        }
        ui.outlineRoundedRect(svX, svY, sv, sv, 4 * l.s,
                Math.max(0.6f, l.s * 0.8f), 0xFF2A2D36);

        // --- hue bar
        for (int i = 0; i < 36; i++) {
            float t = i / 36f;
            ui.rect(hueX, svY + sv * i / 36f, hueW, sv / 36f + 0.6f,
                    0xFF000000 | hsvToRgb(t, 1f, 1f));
        }
        ui.outlineRoundedRect(hueX, svY, hueW, sv, hueW * 0.5f,
                Math.max(0.6f, l.s * 0.8f), 0xFF2A2D36);

        // --- cursors
        float curX = svX + saturation * sv;
        float curY = svY + (1f - value) * sv;
        ui.circle(curX, curY, 5.2f * l.s, 0xFF0B0C10)
                .circle(curX, curY, 3.8f * l.s, WHITE);
        float hueCursorY = svY + hue * sv;
        ui.circle(hueX + hueW / 2f, hueCursorY, 5 * l.s, 0xFF0B0C10)
                .circle(hueX + hueW / 2f, hueCursorY, 3.6f * l.s, WHITE);

        // --- input
        if (pressed && hit(mx, my, svX, svY, sv, sv)) {
            draggingSv = true;
        }
        if (down && draggingSv) {
            saturation = clamp01((mx - svX) / sv);
            value = 1f - clamp01((my - svY) / sv);
            applyColor();
        }
        if (pressed && hit(mx, my, hueX, svY, hueW, sv)) {
            draggingHue = true;
        }
        if (down && draggingHue) {
            hue = clamp01((my - svY) / sv);
            applyColor();
        }

        // --- preview + details
        float previewX = hueX + hueW + 20 * l.s;
        float previewW = 130 * l.s;
        float previewH = sv;
        preview(ui, previewX, svY, previewW, previewH, now, l.s);

        float infoX = previewX + previewW + 18 * l.s;
        float infoW = l.x + l.w - 20 * l.s - infoX;
        int rgb = currentColor();
        ui.strongText(infoX, svY + 8 * l.s, 9 * l.s, WHITE, String.format("#%06X", rgb));
        ui.text(infoX, svY + 22 * l.s, 6 * l.s, MUTED,
                "R " + ((rgb >> 16) & 255) + "   G " + ((rgb >> 8) & 255)
                        + "   B " + (rgb & 255));

        // rainbow switch
        float ry = svY + 48 * l.s;
        boolean rainbowOver = hit(mx, my, infoX, ry, infoW, 20 * l.s);
        if (pressed && rainbowOver) {
            JumpCircles.rainbow = !JumpCircles.rainbow;
        }
        ui.strongText(infoX, ry + 2 * l.s, 6.8f * l.s, TEXT, "Радуга");
        ui.text(infoX, ry + 12 * l.s, 5.5f * l.s, MUTED, "Плавная смена оттенка");
        float tw = 22 * l.s;
        float th = 11 * l.s;
        float tx = infoX + infoW - tw;
        float ty = ry + 2 * l.s;
        ui.roundedRect(tx, ty, tw, th, th / 2f, JumpCircles.rainbow ? ACCENT : 0xFF24262E);
        ui.circle(tx + th / 2f + (tw - th) * (JumpCircles.rainbow ? 1f : 0f),
                ty + th / 2f, th / 2f - 2 * l.s, JumpCircles.rainbow ? 0xFF111216 : WHITE);

        // reset button
        float bw = 96 * l.s;
        float bh = 24 * l.s;
        float bx = infoX;
        float by = svY + sv - bh;
        boolean resetOver = hit(mx, my, bx, by, bw, bh);
        if (pressed && resetOver) {
            setColor(DEFAULT_COLORS[selected]);
        }
        ui.roundedRect(bx, by, bw, bh, 4 * l.s, resetOver ? 0xFF1C2133 : 0xFF191B22)
                .centeredText(bx + bw / 2f, by + bh / 2f, 6.2f * l.s, WHITE,
                        "Сбросить цвет", true);
    }

    private void preview(UiDrawList ui, float x, float y, float w, float h, long now, float s) {
        ui.roundedRect(x, y, w, h, 6 * s, 0xFF07080B)
                .outlineRoundedRect(x, y, w, h, 6 * s, 1, 0xFF191B22);

        int rgb = currentColor();
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 1_000_000_000.0 * 2.6);

        for (int i = 0; i < 4; i++) {
            float rr = (16f + i * 13f + pulse * 3f) * s;
            ui.circle(cx, cy, rr, withAlpha(rgb, 6 + i * 4));
        }
        float[] radii = {18f, 34f, 52f};
        int[] alphas = {235, 160, 95};
        for (int i = 0; i < radii.length; i++) {
            float rr = radii[i] * s * (1f + 0.035f * (float) Math.sin(now / 1_000_000_000.0 * 3.0 + i));
            ui.outlineRoundedRect(cx - rr, cy - rr, rr * 2, rr * 2, rr,
                    Math.max(1f, 1.6f * s), withAlpha(rgb, alphas[i]));
        }
        ui.circle(cx, cy, 5 * s, withAlpha(rgb, 255));
    }

    private void presets(UiDrawList ui, Layout l, int mx, int my, boolean pressed) {
        float radius = 9 * l.s;
        float gap = 10 * l.s;
        float total = PRESETS.length * radius * 2 + (PRESETS.length - 1) * gap;
        float x0 = l.x + (l.w - total) / 2f;
        float cy = l.y + 296 * l.s;

        ui.text(l.x + 20 * l.s, cy - 15 * l.s, 5.6f * l.s, MUTED, "ПРЕСЕТЫ");

        int current = currentColor();
        for (int i = 0; i < PRESETS.length; i++) {
            float cx = x0 + radius + i * (radius * 2 + gap);
            boolean over = distance(mx, my, cx, cy) <= radius + 3 * l.s;
            if (pressed && over) {
                setColor(PRESETS[i]);
                current = PRESETS[i];
            }
            if (PRESETS[i] == current) {
                ui.outlineRoundedRect(cx - radius - 3 * l.s, cy - radius - 3 * l.s,
                        (radius + 3 * l.s) * 2, (radius + 3 * l.s) * 2,
                        radius + 3 * l.s, 1.4f * l.s, ACCENT);
            } else if (over) {
                ui.outlineRoundedRect(cx - radius - 2 * l.s, cy - radius - 2 * l.s,
                        (radius + 2 * l.s) * 2, (radius + 2 * l.s) * 2,
                        radius + 2 * l.s, 1.1f * l.s, 0xFF3B4150);
            }
            ui.circle(cx, cy, radius, 0xFF000000 | PRESETS[i]);
        }
    }

    private void footer(UiDrawList ui, Layout l) {
        ui.text(l.x + 20 * l.s, l.y + l.h - 17 * l.s, 5.6f * l.s, MUTED,
                "Тяните в квадрате или на полосе оттенка · Esc или X — закрыть");
        ui.rightText(l.x + l.w - 20 * l.s, l.y + l.h - 17 * l.s, 5.6f * l.s, MUTED,
                "Редактируется: " + EFFECT_NAMES[selected], true);
    }

    // ------------------------------------------------------------------ data
    private int colorOf(int slot) {
        return switch (slot) {
            case 0 -> JumpCircles.takeoffColor;
            case 1 -> JumpCircles.landingColor;
            default -> JumpCircles.haloColor;
        };
    }

    private int currentColor() {
        return colorOf(selected);
    }

    private void writeColor(int rgb) {
        rgb &= 0xFFFFFF;
        switch (selected) {
            case 0 -> JumpCircles.takeoffColor = rgb;
            case 1 -> JumpCircles.landingColor = rgb;
            default -> JumpCircles.haloColor = rgb;
        }
    }

    /** Подхватывает текущий цвет выбранного эффекта из модуля. */
    private void syncFromModule() {
        float[] hsv = rgbToHsv(currentColor());
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    /** Записывает точный цвет (пресеты, сброс) и обновляет позицию курсоров. */
    private void setColor(int rgb) {
        writeColor(rgb);
        syncFromModule();
    }

    /** Записывает цвет, собранный из HSV-курсоров. */
    private void applyColor() {
        writeColor(hsvToRgb(hue, saturation, value));
    }

    // --------------------------------------------------------------- helpers
    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h = 0f;
        if (delta > 1e-6f) {
            if (max == r) {
                h = ((g - b) / delta + 6f) % 6f;
            } else if (max == g) {
                h = (b - r) / delta + 2f;
            } else {
                h = (r - g) / delta + 4f;
            }
            h /= 6f;
        }
        float s = max <= 1e-6f ? 0f : delta / max;
        return new float[]{h, s, max};
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;
        float r;
        float g;
        float b;
        int sector = (int) (h * 6f);
        switch (sector) {
            case 0 -> {
                r = c;
                g = x;
                b = 0f;
            }
            case 1 -> {
                r = x;
                g = c;
                b = 0f;
            }
            case 2 -> {
                r = 0f;
                g = c;
                b = x;
            }
            case 3 -> {
                r = 0f;
                g = x;
                b = c;
            }
            case 4 -> {
                r = x;
                g = 0f;
                b = c;
            }
            default -> {
                r = c;
                g = 0f;
                b = x;
            }
        }
        int ri = Math.round((r + m) * 255f);
        int gi = Math.round((g + m) * 255f);
        int bi = Math.round((b + m) * 255f);
        return ri << 16 | gi << 8 | bi;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float distance(float mx, float my, float cx, float cy) {
        float dx = mx - cx;
        float dy = my - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private record Layout(float x, float y, float w, float h, float s) {
        static Layout create(int sw, int sh) {
            float designW = 560f;
            float designH = 340f;
            float margin = 18f;
            float fit = Math.min((sw - margin * 2) / designW, (sh - margin * 2) / designH);
            float s = Math.max(0.55f, Math.min(0.9f, fit));
            float w = designW * s;
            float h = designH * s;
            return new Layout((sw - w) / 2f, (sh - h) / 2f, w, h, s);
        }
    }
}
