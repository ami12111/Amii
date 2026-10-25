package ami.ami.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/**
 * Procedural Counter-Strike-ish UI kit: chamfered panels, thin borders, orange accents and
 * a few "shader" effects (soft gradients, glow, sheen) built from plain GUI quads.
 */
public final class CsTheme {
    public static final int ACCENT = 0xFFF2A33C;
    public static final int ACCENT_DARK = 0xFF8A5A1D;
    public static final int BLUE = 0xFF5C9BD6;
    public static final int GREEN = 0xFF55D98B;
    public static final int RED = 0xFFE5484D;

    public static final int TEXT = 0xFFF2F5F8;
    public static final int MUTED = 0xFF95A0AF;
    public static final int DIM = 0xFF5B6675;

    public static final int WINDOW = 0xF20B0F15;
    public static final int PANEL = 0xE60E131A;
    public static final int CARD = 0xF0141921;
    public static final int CARD_HOVER = 0xFF191F28;
    public static final int TRACK = 0xFF232B36;
    public static final int BORDER = 0xFF2A3340;
    public static final int BORDER_SOFT = 0xFF1D242D;

    public static final float TEXT_SCALE_SMALL = 0.68f;

    private CsTheme() {
    }

    // ------------------------------------------------------------------ colors

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (MathHelper.clamp(alpha, 0, 255) << 24);
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

    public static int brighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF, amount);
    }

    // ------------------------------------------------------------------ shapes

    /** Filled rectangle with 45-degree cut corners. */
    public static void chamfer(DrawContext g, int x, int y, int w, int h, int cut, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        cut = Math.min(cut, Math.min(w / 2, h / 2));
        if (cut <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + cut, y, x + w - cut, y + h, color);
        g.fill(x, y + cut, x + cut, y + h - cut, color);
        g.fill(x + w - cut, y + cut, x + w, y + h - cut, color);
        for (int i = 0; i < cut; i++) {
            int inset = cut - i;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** Chamfered panel with a 1 px border. */
    public static void panel(DrawContext g, int x, int y, int w, int h, int cut, int fill, int border) {
        chamfer(g, x, y, w, h, cut, border);
        chamfer(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, cut - 1), fill);
    }

    /** Cheap bloom: a few translucent chamfered layers around a rectangle. */
    public static void glow(DrawContext g, int x, int y, int w, int h, int cut, int color, int spread) {
        for (int i = spread; i >= 1; i--) {
            int alpha = Math.max(2, 18 / i);
            chamfer(g, x - i, y - i, w + i * 2, h + i * 2, cut + i, withAlpha(color, alpha));
        }
    }

    /** Vertical or horizontal two-colour gradient. Horizontal is split into thin quads. */
    public static void smoothGradient(DrawContext g, int x1, int y1, int x2, int y2, int from, int to, boolean horizontal) {
        if (horizontal) {
            int width = x2 - x1;
            if (width <= 0) {
                return;
            }
            int steps = Math.min(48, width);
            for (int i = 0; i < steps; i++) {
                int sx = x1 + width * i / steps;
                int ex = x1 + width * (i + 1) / steps;
                g.fill(sx, y1, ex, y2, mix(from, to, (i + 0.5f) / steps));
            }
        } else {
            g.fillGradient(x1, y1, x2, y2, from, to);
        }
    }

    /** Thin HUD-style corner brackets. */
    public static void bracket(DrawContext g, int x, int y, int w, int h, int length, int color) {
        g.fill(x, y, x + length, y + 1, color);
        g.fill(x, y, x + 1, y + length, color);
        g.fill(x + w - length, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + length, color);
        g.fill(x, y + h - 1, x + length, y + h, color);
        g.fill(x, y + h - length, x + 1, y + h, color);
        g.fill(x + w - length, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - length, x + w, y + h, color);
    }

    // ------------------------------------------------------------------ text

    public static void text(DrawContext g, TextRenderer tr, String value, int x, int y, float scale, int color, boolean shadow) {
        if (value == null || value.isEmpty() || (color >>> 24) == 0) {
            return;
        }
        if (Math.abs(scale - 1.0f) < 0.001f) {
            g.drawText(tr, value, x, y, color, shadow);
            return;
        }
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(x, y);
        g.getMatrices().scale(scale, scale);
        g.drawText(tr, value, 0, 0, color, shadow);
        g.getMatrices().popMatrix();
    }

    public static void textCentered(DrawContext g, TextRenderer tr, String value, int centerX, int y, float scale, int color, boolean shadow) {
        text(g, tr, value, centerX - textWidth(tr, value, scale) / 2, y, scale, color, shadow);
    }

    public static void textRight(DrawContext g, TextRenderer tr, String value, int rightX, int y, float scale, int color, boolean shadow) {
        text(g, tr, value, rightX - textWidth(tr, value, scale), y, scale, color, shadow);
    }

    public static int textWidth(TextRenderer tr, String value, float scale) {
        return value == null ? 0 : Math.round(tr.getWidth(value) * scale);
    }

    /** Draws word-wrapped text and returns the used height in pixels. */
    public static int wrapped(DrawContext g, TextRenderer tr, String value, int x, int y, int maxWidth, float scale, int color, int lineGap) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int lineHeight = Math.round(tr.fontHeight * scale) + lineGap;
        StringBuilder line = new StringBuilder();
        int cursorY = y;
        for (String word : value.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && textWidth(tr, candidate, scale) > maxWidth) {
                text(g, tr, line.toString(), x, cursorY, scale, color, false);
                cursorY += lineHeight;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) {
            text(g, tr, line.toString(), x, cursorY, scale, color, false);
            cursorY += lineHeight;
        }
        return cursorY - y;
    }

    // ------------------------------------------------------------------ widgets

    public static void iconTile(DrawContext g, TextRenderer tr, int x, int y, int size, String glyph, int color, boolean active) {
        if (active) {
            glow(g, x, y, size, size, 3, color, 3);
        }
        panel(g, x, y, size, size, 3, 0xFF0E131A, active ? withAlpha(color, 150) : BORDER_SOFT);
        textCentered(g, tr, glyph, x + size / 2, y + (size - Math.round(tr.fontHeight * 0.62f)) / 2, 0.62f,
                active ? color : MUTED, false);
    }

    public static void toggle(DrawContext g, int x, int y, boolean on, float progress, boolean hovered) {
        int w = 28;
        int h = 12;
        int trackBorder = on ? withAlpha(ACCENT, hovered ? 230 : 180) : (hovered ? BORDER : BORDER_SOFT);
        if (on) {
            glow(g, x, y, w, h, 3, ACCENT, 2);
        }
        chamfer(g, x, y, w, h, 3, trackBorder);
        chamfer(g, x + 1, y + 1, w - 2, h - 2, 2, 0xFF151B23);

        int fill = Math.round((w - 2) * MathHelper.clamp(progress, 0.0f, 1.0f));
        if (fill > 0) {
            chamfer(g, x + 1, y + 1, fill, h - 2, 2, mix(ACCENT_DARK, ACCENT, progress));
        }

        int knobX = x + 1 + Math.round((w - 8) * MathHelper.clamp(progress, 0.0f, 1.0f));
        g.fill(knobX, y + 2, knobX + 6, y + h - 2, on ? 0xFFFFFFFF : 0xFF8C97A6);
    }

    public static int keycapWidth(TextRenderer tr, String label, int maxWidth) {
        String text = label == null ? "NONE" : label.toUpperCase(Locale.ROOT);
        int width = textWidth(tr, text, 0.68f) + 10;
        return Math.max(24, Math.min(maxWidth, width));
    }

    public static void keycap(DrawContext g, TextRenderer tr, String label, int x, int y, int maxWidth) {
        String text = label == null || label.isBlank() ? "NONE" : label.toUpperCase(Locale.ROOT);
        int width = keycapWidth(tr, text, maxWidth);
        int height = 13;
        chamfer(g, x, y, width, height, 3, BORDER);
        chamfer(g, x + 1, y + 1, width - 2, height - 2, 2, 0xFF171E27);
        text = tr.trimToWidth(text, Math.max(4, (int) ((width - 8) / 0.68f)));
        textCentered(g, tr, text, x + width / 2, y + 3, 0.68f, TEXT, false);
    }

    /** Small pulsing square used as an online indicator. */
    public static void statusDot(DrawContext g, int x, int y, int color, long timeMs) {
        float pulse = 0.55f + 0.45f * (float) Math.sin(timeMs * 0.004);
        int alpha = 90 + Math.round(140 * pulse);
        g.fill(x - 1, y - 1, x + 3, y + 3, withAlpha(color, alpha / 3));
        g.fill(x, y, x + 2, y + 2, withAlpha(color, alpha));
    }
}
