package ami.ami.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Процедурный UI-кит в стиле «матовое стекло»: сглаженные скруглённые прямоугольники,
 * тонкие светлые контуры, мягкое свечение, лавандовый акцент. Всё строится из обычных GUI-квадов.
 */
public final class GlassTheme {
    public static final int ACCENT = 0xFF8B7CF6;
    public static final int ACCENT_SOFT = 0xFFB9AEFF;
    public static final int ACCENT_DARK = 0xFF5B4FC2;
    public static final int GREEN = 0xFF5BE3A0;
    public static final int AMBER = 0xFFF2B44C;
    public static final int RED = 0xFFF0626B;

    public static final int TEXT = 0xFFF5F3FF;
    public static final int MUTED = 0xFFE3E1F5;
    public static final int DIM = 0xFFC9C7E0;

    public static final int WINDOW_TOP = 0x78B3B0D6;
    public static final int WINDOW_BOTTOM = 0x689A97C2;
    public static final int WINDOW_BORDER = 0xAAE9E7FF;

    public static final int LINE = 0x1FFFFFFF;
    public static final int CARD = 0x26FFFFFF;
    public static final int CARD_HOVER = 0x36FFFFFF;
    public static final int CARD_BORDER = 0x2EFFFFFF;
    public static final int TRACK = 0x40FFFFFF;

    /** Vector UI font loaded from assets/ami/font/ui.json (Inter). */
    private static final Style UI_FONT =
            Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("ami", "ui")));

    private GlassTheme() {
    }

    public static Style uiFont() {
        return UI_FONT;
    }

    public static Text styled(String value) {
        return Text.literal(value).setStyle(UI_FONT);
    }

    // ------------------------------------------------------------------ colors

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (MathHelper.clamp(alpha, 0, 255) << 24);
    }

    /** Multiplies the alpha channel of a colour by a factor. */
    public static int scaleAlpha(int color, float factor) {
        return withAlpha(color, Math.round((color >>> 24) * MathHelper.clamp(factor, 0.0f, 1.0f)));
    }

    public static int mix(int from, int to, float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        int aa = (from >>> 24) & 0xFF;
        int ar = (from >> 16) & 0xFF;
        int ag = (from >> 8) & 0xFF;
        int ab = from & 0xFF;
        int ba = (to >>> 24) & 0xFF;
        int br = (to >> 16) & 0xFF;
        int bg = (to >> 8) & 0xFF;
        int bb = to & 0xFF;
        int a = Math.round(aa + (ba - aa) * t);
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b = Math.round(ab + (bb - ab) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ shapes

    /** Filled rounded rectangle with anti-aliased corners. */
    public static void round(DrawContext g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        if (h > 2 * r) {
            g.fill(x, y + r, x + w, y + h - r, color);
        }
        for (int i = 0; i < r; i++) {
            cornerRows(g, x, y, w, h, r, i, color, color);
        }
    }

    /** Rounded rectangle with a vertical two-colour gradient. */
    public static void roundGradient(DrawContext g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        if (h > 2 * r) {
            g.fillGradient(x, y + r, x + w, y + h - r,
                    mix(top, bottom, (float) r / h), mix(top, bottom, (float) (h - r) / h));
        }
        for (int i = 0; i < r; i++) {
            int upper = mix(top, bottom, (i + 0.5f) / h);
            int lower = mix(top, bottom, (h - i - 0.5f) / h);
            cornerRows(g, x, y, w, h, r, i, upper, lower);
        }
    }

    private static void cornerRows(DrawContext g, int x, int y, int w, int h, int r, int i,
                                   int upperColor, int lowerColor) {
        double dy = r - i - 0.5;
        double inset = r - Math.sqrt(r * (double) r - dy * dy);
        int edge = (int) Math.ceil(inset);
        double coverage = edge - inset;
        cornerRow(g, x, y + i, w, edge, coverage, upperColor);
        cornerRow(g, x, y + h - i - 1, w, edge, coverage, lowerColor);
    }

    private static void cornerRow(DrawContext g, int x, int py, int w, int edge, double coverage, int color) {
        int alpha = color >>> 24;
        if (alpha == 0) {
            return;
        }
        if (x + edge < x + w - edge) {
            g.fill(x + edge, py, x + w - edge, py + 1, color);
        }
        if (edge > 0 && coverage > 0.05) {
            int aa = withAlpha(color, (int) Math.round(alpha * coverage));
            g.fill(x + edge - 1, py, x + edge, py + 1, aa);
            g.fill(x + w - edge, py, x + w - edge + 1, py + 1, aa);
        }
    }

    /** 1 px rounded outline (does not touch the interior, so it is safe on translucent fills). */
    public static void outline(DrawContext g, int x, int y, int w, int h, int r, int color) {
        int alpha = color >>> 24;
        if (w <= 1 || h <= 1 || alpha == 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        if (h > 2 * r) {
            g.fill(x, y + r, x + 1, y + h - r, color);
            g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double outer = r - Math.sqrt(r * (double) r - dy * dy);
            double inner = dy <= r - 1
                    ? r - Math.sqrt((r - 1.0) * (r - 1.0) - dy * dy)
                    : r;
            ringRun(g, x, w, y + i, outer, inner, color);
            ringRun(g, x, w, y + h - 1 - i, outer, inner, color);
        }
    }

    private static void ringRun(DrawContext g, int x, int w, int py, double from, double to, int color) {
        int alpha = color >>> 24;
        int c0 = (int) Math.floor(from);
        int c1 = (int) Math.ceil(to);
        int fullStart = -1;
        for (int c = c0; c < c1; c++) {
            double coverage = Math.min(c + 1, to) - Math.max(c, from);
            if (coverage >= 0.97) {
                if (fullStart < 0) {
                    fullStart = c;
                }
                continue;
            }
            if (fullStart >= 0) {
                span(g, x, w, py, fullStart, c, color);
                fullStart = -1;
            }
            if (coverage > 0.04) {
                span(g, x, w, py, c, c + 1, withAlpha(color, (int) Math.round(alpha * coverage)));
            }
        }
        if (fullStart >= 0) {
            span(g, x, w, py, fullStart, c1, color);
        }
    }

    private static void span(DrawContext g, int x, int w, int py, int from, int to, int color) {
        g.fill(x + from, py, x + to, py + 1, color);
        g.fill(x + w - to, py, x + w - from, py + 1, color);
    }

    /** Translucent rounded fill plus a thin outline. */
    public static void panel(DrawContext g, int x, int y, int w, int h, int r, int fill, int border) {
        round(g, x, y, w, h, r, fill);
        outline(g, x, y, w, h, r, border);
    }

    /** Cheap bloom: a few translucent rounded layers around a rectangle. */
    public static void glow(DrawContext g, int x, int y, int w, int h, int r, int color, int spread, float intensity) {
        if (intensity <= 0.01f) {
            return;
        }
        for (int i = spread; i >= 1; i--) {
            int alpha = Math.max(2, Math.round(20.0f / i * intensity));
            round(g, x - i, y - i, w + i * 2, h + i * 2, r + i, withAlpha(color, alpha));
        }
    }

    // ------------------------------------------------------------------ text

    public static void text(DrawContext g, TextRenderer tr, String value, int x, int y, float scale, int color, boolean shadow) {
        if (value == null || value.isEmpty() || (color >>> 24) == 0) {
            return;
        }
        if (Math.abs(scale - 1.0f) < 0.001f) {
            g.drawText(tr, styled(value), x, y, color, shadow);
            return;
        }
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(x, y);
        g.getMatrices().scale(scale, scale);
        g.drawText(tr, styled(value), 0, 0, color, shadow);
        g.getMatrices().popMatrix();
    }

    public static void textCentered(DrawContext g, TextRenderer tr, String value, int centerX, int y, float scale, int color, boolean shadow) {
        text(g, tr, value, centerX - textWidth(tr, value, scale) / 2, y, scale, color, shadow);
    }

    public static void textRight(DrawContext g, TextRenderer tr, String value, int rightX, int y, float scale, int color, boolean shadow) {
        text(g, tr, value, rightX - textWidth(tr, value, scale), y, scale, color, shadow);
    }

    public static int textWidth(TextRenderer tr, String value, float scale) {
        return value == null ? 0 : Math.round(tr.getWidth(styled(value)) * scale);
    }

    /** Draws text letter by letter with extra spacing (used for the logotype). */
    public static void spaced(DrawContext g, TextRenderer tr, String value, int x, int y, float scale, int spacing, int color) {
        int cursor = x;
        for (int i = 0; i < value.length(); i++) {
            String ch = String.valueOf(value.charAt(i));
            text(g, tr, ch, cursor, y, scale, color, false);
            cursor += textWidth(tr, ch, scale) + spacing;
        }
    }

    /** Trims text to a pixel width at the given scale, adding "..." when it was cut. */
    public static String fit(TextRenderer tr, String value, int maxWidth, float scale) {
        if (value == null || textWidth(tr, value, scale) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int ellipsisWidth = textWidth(tr, ellipsis, scale);
        int end = value.length();
        while (end > 0 && textWidth(tr, value.substring(0, end), scale) + ellipsisWidth > maxWidth) {
            end--;
        }
        return value.substring(0, end) + ellipsis;
    }

    // ------------------------------------------------------------------ icons

    /** 2x2 grid icon, centred on (cx, cy). */
    public static void iconGrid(DrawContext g, int cx, int cy, int color) {
        int x0 = cx - 3;
        int y0 = cy - 3;
        g.fill(x0, y0, x0 + 3, y0 + 3, color);
        g.fill(x0 + 4, y0, x0 + 7, y0 + 3, color);
        g.fill(x0, y0 + 4, x0 + 3, y0 + 7, color);
        g.fill(x0 + 4, y0 + 4, x0 + 7, y0 + 7, color);
    }

    public static void iconCrosshair(DrawContext g, int cx, int cy, int color) {
        outline(g, cx - 3, cy - 3, 7, 7, 3, color);
        g.fill(cx - 5, cy, cx - 2, cy + 1, color);
        g.fill(cx + 3, cy, cx + 6, cy + 1, color);
        g.fill(cx, cy - 5, cx + 1, cy - 2, color);
        g.fill(cx, cy + 3, cx + 1, cy + 6, color);
    }

    public static void iconMove(DrawContext g, int cx, int cy, int color) {
        for (int pass = 0; pass < 2; pass++) {
            int x0 = cx - 4 + pass * 3;
            for (int k = 0; k < 4; k++) {
                g.fill(x0 + k, cy - 3 + k, x0 + k + 1, cy - 2 + k, color);
                g.fill(x0 + k, cy + 3 - k, x0 + k + 1, cy + 4 - k, color);
            }
        }
    }

    public static void iconEye(DrawContext g, int cx, int cy, int color) {
        for (int dy = -4; dy <= 4; dy++) {
            int hx = 4 - Math.abs(dy);
            g.fill(cx - hx, cy + dy, cx - hx + 1, cy + dy + 1, color);
            g.fill(cx + hx, cy + dy, cx + hx + 1, cy + dy + 1, color);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
    }

    public static void categoryIcon(DrawContext g, ModuleEntry.Category category, int cx, int cy, int color) {
        if (category == null) {
            iconGrid(g, cx, cy, color);
            return;
        }
        switch (category) {
            case COMBAT -> iconCrosshair(g, cx, cy, color);
            case MOVEMENT -> iconMove(g, cx, cy, color);
            case RENDER -> iconEye(g, cx, cy, color);
        }
    }

    public static void iconSearch(DrawContext g, int x, int y, int color) {
        outline(g, x, y, 8, 8, 4, color);
        for (int k = 0; k < 3; k++) {
            g.fill(x + 7 + k, y + 7 + k, x + 8 + k, y + 8 + k, color);
        }
    }

    public static void chevron(DrawContext g, int cx, int cy, boolean up, int color) {
        for (int k = 0; k < 4; k++) {
            int dy = up ? 3 - k : k;
            g.fill(cx - 3 + k, cy + dy, cx - 2 + k, cy + dy + 1, color);
            g.fill(cx + 2 - k, cy + dy, cx + 3 - k, cy + dy + 1, color);
        }
    }

    // ------------------------------------------------------------------ widgets

    /** Rounded square checkbox with a glowing lavender fill and a white core. */
    public static void checkbox(DrawContext g, int x, int y, int size, float progress, boolean hovered) {
        float p = MathHelper.clamp(progress, 0.0f, 1.0f);
        glow(g, x, y, size, size, 5, ACCENT, 3, p);
        round(g, x, y, size, size, 5, mix(hovered ? 0x30FFFFFF : 0x22FFFFFF, ACCENT, p));
        outline(g, x, y, size, size, 5,
                mix(hovered ? 0x66FFFFFF : 0x38FFFFFF, withAlpha(ACCENT_SOFT, 0xD0), p));
        int core = 6;
        round(g, x + (size - core) / 2, y + (size - core) / 2, core, core, 2,
                withAlpha(0xFFFFFF, Math.round(235 * p)));
    }

    /** Small pill switch for boolean settings. */
    public static void pillSwitch(DrawContext g, int x, int y, float progress, boolean hovered) {
        float p = MathHelper.clamp(progress, 0.0f, 1.0f);
        int w = 26;
        int h = 12;
        glow(g, x, y, w, h, 6, ACCENT, 2, p * 0.8f);
        round(g, x, y, w, h, 6, mix(hovered ? 0x38FFFFFF : 0x2AFFFFFF, ACCENT, p));
        outline(g, x, y, w, h, 6, mix(0x38FFFFFF, withAlpha(ACCENT_SOFT, 0xB0), p));
        int knobX = x + 2 + Math.round(14 * p);
        round(g, knobX, y + 2, 8, 8, 4, mix(0xFFC9C6DE, 0xFFFFFFFF, p));
    }

    public static int keycapWidth(TextRenderer tr, String label) {
        return Math.max(20, textWidth(tr, label, 0.62f) + 12);
    }

    public static void keycap(DrawContext g, TextRenderer tr, String label, int x, int y) {
        int width = keycapWidth(tr, label);
        round(g, x, y, width, 14, 5, 0x22FFFFFF);
        outline(g, x, y, width, 14, 5, 0x30FFFFFF);
        textCentered(g, tr, label, x + width / 2, y + 5, 0.62f, MUTED, false);
    }

    /** Small pulsing dot used as an online indicator. */
    public static void statusDot(DrawContext g, int x, int y, int color, long timeMs) {
        float pulse = 0.55f + 0.45f * (float) Math.sin(timeMs * 0.004);
        int alpha = 90 + Math.round(140 * pulse);
        round(g, x - 2, y - 2, 8, 8, 4, withAlpha(color, alpha / 4));
        round(g, x, y, 4, 4, 2, withAlpha(color, alpha));
    }
}
