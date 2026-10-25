package ami.ami.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Draws a {@link UiDrawList}. Скругления, круги, обводки и тени идут через один SDF-пайплайн
 * ({@link SdfRenderer}), иконки — простые геометрические фигуры.
 */
public final class UiRenderer {
    public static final int ICON_ALL = 1;
    public static final int ICON_COMBAT = 2;
    public static final int ICON_MOVEMENT = 3;
    public static final int ICON_RENDER = 4;
    public static final int ICON_BRAND = 5;
    public static final int ICON_PLAYER = 6;
    public static final int ICON_SEARCH = 7;
    public static final int ICON_COLUMNS = 8;
    public static final int ICON_ROWS = 9;
    public static final int ICON_CHECK = 10;
    public static final int ICON_CHEVRON_DOWN = 11;
    public static final int ICON_CHEVRON_UP = 12;

    private static final Style UI_FONT =
            Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("ami", "ui")));
    private static final Style SEMIBOLD_FONT =
            Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("ami", "ui_semibold")));
    private static final Style BRAND_FONT =
            Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("ami", "brand")));

    /** Font variants rasterised for each GUI scale: oversample = scale keeps text at native resolution. */
    private static final Style[] SCALED_UI_FONTS = new Style[5];
    private static final Style[] SCALED_SEMIBOLD_FONTS = new Style[5];

    /** Текущая область обрезки в логических координатах GUI для SDF-элементов. */
    private static @Nullable ScreenRect activeScissor;

    private UiRenderer() {
    }

    /** Форсирует инициализацию SDF-пайплайна при старте клиента. */
    public static void init() {
        SdfRenderer.init();
    }

    public static void submit(DrawContext g, UiDrawList ui, TextRenderer tr) {
        Deque<ScreenRect> scissors = new ArrayDeque<>();
        activeScissor = null;
        for (UiCommand command : ui.seal()) {
            switch (command) {
                case UiCommand.Rect rect ->
                        rounded(g, rect.x(), rect.y(), rect.width(), rect.height(), rect.radius(), rect.color());
                case UiCommand.GradientRect rect ->
                        gradientRounded(g, rect.x(), rect.y(), rect.width(), rect.height(), rect.radius(),
                                rect.startColor(), rect.endColor(), rect.horizontal());
                case UiCommand.Outline outline ->
                        outlineRounded(g, outline.x(), outline.y(), outline.width(), outline.height(),
                                outline.radius(), outline.thickness(), outline.color());
                case UiCommand.Circle circle ->
                        circle(g, circle.centerX(), circle.centerY(), circle.radius(), circle.color());
                case UiCommand.Line line ->
                        line(g, line.x1(), line.y1(), line.x2(), line.y2(), line.thickness(), line.color());
                case UiCommand.Shadow shadow ->
                        SdfRenderer.shadow(g, shadow.x(), shadow.y(), shadow.width(), shadow.height(),
                                shadow.radius(), shadow.blur(), shadow.color(), activeScissor);
                case UiCommand.PushScissor scissor -> {
                    int x1 = Math.round(scissor.x());
                    int y1 = Math.round(scissor.y());
                    int x2 = Math.round(scissor.x() + scissor.width());
                    int y2 = Math.round(scissor.y() + scissor.height());
                    ScreenRect rect = new ScreenRect(x1, y1, x2 - x1, y2 - y1)
                            .transform(g.getMatrices());
                    scissors.push(rect);
                    activeScissor = rect;
                    g.enableScissor(x1, y1, x2, y2);
                }
                case UiCommand.PopScissor ignored -> {
                    if (!scissors.isEmpty()) {
                        scissors.pop();
                    }
                    activeScissor = scissors.peek();
                    g.disableScissor();
                }
                case UiCommand.Text text ->
                        drawText(g, tr, text.x(), text.y(), text.size(), text.color(), text.value(),
                                text.align(), text.strong(), false);
                case UiCommand.BrandText text ->
                        drawText(g, tr, text.x(), text.y(), text.size(), text.color(), text.value(),
                                text.align(), false, true);
                case UiCommand.Icon icon ->
                        drawIcon(g, icon.x(), icon.y(), icon.size(), icon.color(), icon.codePoint());
            }
        }
        activeScissor = null;
    }

    // ------------------------------------------------------------------ shapes

    private static void rounded(DrawContext g, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        float r = Math.max(0, Math.min(radius, Math.min(w, h) * 0.5f));
        if (r < 0.5f) {
            int x0 = Math.round(x);
            int y0 = Math.round(y);
            int x1 = Math.round(x + w);
            int y1 = Math.round(y + h);
            if (x1 > x0 && y1 > y0) {
                g.fill(x0, y0, x1, y1, color);
            }
            return;
        }
        SdfRenderer.fill(g, x, y, w, h, r, color, activeScissor);
    }

    private static void gradientRounded(DrawContext g, float x, float y, float w, float h, float radius,
                                        int startColor, int endColor, boolean horizontal) {
        if (w <= 0 || h <= 0 || ((startColor | endColor) >>> 24) == 0) {
            return;
        }
        float r = Math.max(0, Math.min(radius, Math.min(w, h) * 0.5f));
        int y0 = Math.round(y);
        int y1 = Math.round(y + h);
        if (y1 <= y0) {
            return;
        }
        int slices = horizontal ? Math.min(64, Math.max(1, Math.round(w))) : 1;
        for (int row = y0; row < y1; row++) {
            float[] span = rowSpan(x, y, w, h, r, row);
            if (span == null) {
                continue;
            }
            if (!horizontal) {
                float t = Math.max(0f, Math.min(1f, (row + 0.5f - y) / h));
                int left = Math.round(span[0]);
                int right = Math.round(span[1]);
                if (right > left) {
                    g.fill(left, row, right, row + 1, mix(startColor, endColor, t));
                }
                continue;
            }
            float spanWidth = span[1] - span[0];
            for (int i = 0; i < slices; i++) {
                float cx0 = span[0] + spanWidth * i / slices;
                float cx1 = span[0] + spanWidth * (i + 1) / slices;
                float t = Math.max(0f, Math.min(1f, ((cx0 + cx1) * 0.5f - x) / w));
                int left = Math.round(cx0);
                int right = Math.round(cx1);
                if (right > left) {
                    g.fill(left, row, right, row + 1, mix(startColor, endColor, t));
                }
            }
        }
    }

    private static void outlineRounded(DrawContext g, float x, float y, float w, float h, float radius,
                                       float thickness, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        float t = Math.min(thickness, Math.min(w, h) * 0.5f);
        if (t <= 0) {
            return;
        }
        float r = Math.max(0, Math.min(radius, Math.min(w, h) * 0.5f));
        SdfRenderer.stroke(g, x, y, w, h, r, t, color, activeScissor);
    }

    private static void circle(DrawContext g, float cx, float cy, float radius, int color) {
        if (radius <= 0 || (color >>> 24) == 0) {
            return;
        }
        if (radius < 0.75f) {
            int left = Math.round(cx - 0.5f);
            int top = Math.round(cy - 0.5f);
            g.fill(left, top, left + 1, top + 1, color);
            return;
        }
        SdfRenderer.circle(g, cx, cy, radius, color, activeScissor);
    }

    private static void line(DrawContext g, float x1, float y1, float x2, float y2,
                             float thickness, int color) {
        if (thickness <= 0 || (color >>> 24) == 0) {
            return;
        }
        if (Math.abs(x2 - x1) < 0.01f && Math.abs(y2 - y1) < 0.01f) {
            return;
        }
        if (Math.abs(y2 - y1) < 0.01f) {
            rounded(g, Math.min(x1, x2), y1 - thickness * 0.5f, Math.abs(x2 - x1), thickness,
                    thickness * 0.5f, color);
            return;
        }
        if (Math.abs(x2 - x1) < 0.01f) {
            rounded(g, x1 - thickness * 0.5f, Math.min(y1, y2), thickness, Math.abs(y2 - y1),
                    thickness * 0.5f, color);
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) Math.ceil(length / Math.max(0.35f, thickness * 0.5f)));
        for (int i = 0; i <= steps; i++) {
            circle(g, x1 + dx * i / steps, y1 + dy * i / steps, thickness * 0.5f, color);
        }
    }

    /** Span of a rounded rectangle at the given scanline, or null outside it. */
    private static float[] rowSpan(float x, float y, float w, float h, float r, int row) {
        float cy = row + 0.5f;
        if (cy < y || cy > y + h) {
            return null;
        }
        float inset = 0;
        if (r > 0 && cy < y + r) {
            float dy = (y + r) - cy;
            inset = r - (float) Math.sqrt(Math.max(0.0, r * r - dy * dy));
        } else if (r > 0 && cy > y + h - r) {
            float dy = cy - (y + h - r);
            inset = r - (float) Math.sqrt(Math.max(0.0, r * r - dy * dy));
        }
        return new float[]{x + inset, x + w - inset};
    }

    // ------------------------------------------------------------------ text

    private static void drawText(DrawContext g, TextRenderer tr, float x, float y, float size,
                                 int color, String value, UiCommand.HorizontalAlign align,
                                 boolean strong, boolean brand) {
        if (value == null || value.isEmpty() || (color >>> 24) == 0 || size <= 0) {
            return;
        }
        Style style = brand ? BRAND_FONT : strong ? semiboldFont() : uiFont();
        Text text = Text.literal(value).setStyle(style);
        float scale = size / (float) tr.fontHeight;
        float textWidth = tr.getWidth(text) * scale;
        float drawX = switch (align) {
            case LEFT -> x;
            case CENTER -> x - textWidth * 0.5f;
            case RIGHT -> x - textWidth;
        };
        float drawY = y - tr.fontHeight * scale * 0.5f;
        // Целые физические пиксели: при дробном сдвиге стебли букв выходят разной толщины.
        drawX = Math.round(drawX);
        drawY = Math.round(drawY);
        Matrix3x2fStack matrices = g.getMatrices();
        matrices.pushMatrix();
        matrices.translate(drawX, drawY);
        matrices.scale(scale, scale);
        g.drawText(tr, text, 0, 0, color, false);
        matrices.popMatrix();
    }

    /** Выбирает вариант шрифта с oversample, равным текущему GUI-масштабу. */
    private static Style uiFont() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int guiScale = mc == null || mc.getWindow() == null ? 1 : mc.getWindow().getScaleFactor();
        if (guiScale <= 1) {
            return UI_FONT;
        }
        int index = Math.min(4, guiScale);
        Style cached = SCALED_UI_FONTS[index];
        if (cached == null) {
            cached = Style.EMPTY.withFont(new StyleSpriteSource.Font(
                    Identifier.of("ami", "ui_x" + index)));
            SCALED_UI_FONTS[index] = cached;
        }
        return cached;
    }

    /** SemiBold-начертание для заголовков: не синтетический bold, а отдельный TTF. */
    private static Style semiboldFont() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int guiScale = mc == null || mc.getWindow() == null ? 1 : mc.getWindow().getScaleFactor();
        if (guiScale <= 1) {
            return SEMIBOLD_FONT;
        }
        int index = Math.min(4, guiScale);
        Style cached = SCALED_SEMIBOLD_FONTS[index];
        if (cached == null) {
            cached = Style.EMPTY.withFont(new StyleSpriteSource.Font(
                    Identifier.of("ami", "ui_semibold_x" + index)));
            SCALED_SEMIBOLD_FONTS[index] = cached;
        }
        return cached;
    }

    // ------------------------------------------------------------------ icons

    private static void drawIcon(DrawContext g, float cx, float cy, float size, int color, int code) {
        float s = Math.max(2f, size);
        float half = s * 0.5f;
        float stroke = Math.max(0.6f, s * 0.09f);
        switch (code) {
            case ICON_ALL -> {
                float cell = s * 0.36f;
                float gap = s * 0.1f;
                float x0 = cx - half;
                float y0 = cy - half;
                rounded(g, x0, y0, cell, cell, cell * 0.25f, color);
                rounded(g, x0 + cell + gap, y0, cell, cell, cell * 0.25f, color);
                rounded(g, x0, y0 + cell + gap, cell, cell, cell * 0.25f, color);
                rounded(g, x0 + cell + gap, y0 + cell + gap, cell, cell, cell * 0.25f, color);
            }
            case ICON_COMBAT -> {
                line(g, cx - half * 0.75f, cy - half * 0.75f, cx + half * 0.75f, cy + half * 0.75f,
                        stroke, color);
                line(g, cx + half * 0.75f, cy - half * 0.75f, cx - half * 0.75f, cy + half * 0.75f,
                        stroke, color);
            }
            case ICON_MOVEMENT -> {
                line(g, cx, cy + half * 0.8f, cx, cy - half * 0.75f, stroke, color);
                line(g, cx, cy - half * 0.75f, cx - half * 0.55f, cy - half * 0.1f, stroke, color);
                line(g, cx, cy - half * 0.75f, cx + half * 0.55f, cy - half * 0.1f, stroke, color);
            }
            case ICON_RENDER -> {
                outlineCircle(g, cx, cy, half * 0.85f, stroke, color);
                circle(g, cx, cy, s * 0.13f, color);
            }
            case ICON_BRAND -> {
                outlineRounded(g, cx - s * 0.36f, cy - s * 0.36f, s * 0.72f, s * 0.72f,
                        s * 0.22f, Math.max(0.7f, stroke * 1.3f), color);
                rounded(g, cx - s * 0.13f, cy - s * 0.13f, s * 0.26f, s * 0.26f, s * 0.06f, color);
            }
            case ICON_PLAYER -> {
                circle(g, cx, cy - half * 0.42f, s * 0.2f, color);
                rounded(g, cx - half * 0.55f, cy + half * 0.05f, s * 0.55f, s * 0.34f,
                        s * 0.16f, color);
            }
            case ICON_SEARCH -> {
                outlineCircle(g, cx - s * 0.06f, cy - s * 0.06f, s * 0.27f, stroke, color);
                line(g, cx + s * 0.16f, cy + s * 0.16f, cx + s * 0.36f, cy + s * 0.36f,
                        stroke * 1.1f, color);
            }
            case ICON_COLUMNS -> {
                float columnWidth = s * 0.34f;
                float columnHeight = s * 0.62f;
                rounded(g, cx - columnWidth - s * 0.06f, cy - columnHeight * 0.5f,
                        columnWidth, columnHeight, columnWidth * 0.18f, color);
                rounded(g, cx + s * 0.06f, cy - columnHeight * 0.5f,
                        columnWidth, columnHeight, columnWidth * 0.18f, color);
            }
            case ICON_ROWS -> rounded(g, cx - s * 0.32f, cy - s * 0.32f, s * 0.64f, s * 0.64f,
                    s * 0.12f, color);
            case ICON_CHECK -> {
                line(g, cx - half * 0.7f, cy, cx - half * 0.1f, cy + half * 0.5f, stroke, color);
                line(g, cx - half * 0.1f, cy + half * 0.5f, cx + half * 0.75f, cy - half * 0.55f,
                        stroke, color);
            }
            case ICON_CHEVRON_DOWN -> {
                line(g, cx - half * 0.55f, cy - half * 0.22f, cx, cy + half * 0.3f, stroke, color);
                line(g, cx, cy + half * 0.3f, cx + half * 0.55f, cy - half * 0.22f, stroke, color);
            }
            case ICON_CHEVRON_UP -> {
                line(g, cx - half * 0.55f, cy + half * 0.22f, cx, cy - half * 0.3f, stroke, color);
                line(g, cx, cy - half * 0.3f, cx + half * 0.55f, cy + half * 0.22f, stroke, color);
            }
            default -> {
            }
        }
    }

    private static void outlineCircle(DrawContext g, float cx, float cy, float radius,
                                      float thickness, int color) {
        if (radius <= 0 || thickness <= 0 || (color >>> 24) == 0) {
            return;
        }
        float inner = Math.max(0, radius - thickness);
        int y0 = Math.round(cy - radius);
        int y1 = Math.round(cy + radius);
        for (int row = y0; row < y1; row++) {
            float dy = (row + 0.5f) - cy;
            float outerHalf = (float) Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
            float innerHalf = Math.abs(dy) < inner
                    ? (float) Math.sqrt(Math.max(0.0, inner * inner - dy * dy)) : 0;
            int outerLeft = Math.round(cx - outerHalf);
            int outerRight = Math.round(cx + outerHalf);
            int innerLeft = Math.round(cx - innerHalf);
            int innerRight = Math.round(cx + innerHalf);
            if (innerLeft > outerLeft) {
                g.fill(outerLeft, row, Math.min(innerLeft, outerRight), row + 1, color);
            }
            if (outerRight > innerRight) {
                g.fill(Math.max(innerRight, outerLeft), row, outerRight, row + 1, color);
            }
        }
    }

    // ------------------------------------------------------------------ colors

    private static int mix(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gr = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }
}
