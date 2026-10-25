package ami.ami.client.gui;

import ami.ami.client.velocity.VelocityClient;
import ami.ami.client.velocity.VelocityConfig;
import ami.ami.client.velocity.VelocityStats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Click GUI в стиле «матовое стекло»: скруглённое полупрозрачное окно, боковое меню с группой,
 * поиск сверху, карточки модулей с чипом категории и чекбоксом, раскрывающиеся настройки.
 */
public class AmiClickGui extends Screen {
    /** true: оставить ванильный блюр мира за окном (как на референсе). */
    private static final boolean WORLD_BLUR = true;

    private static final int HEADER_H = 46;
    private static final int SIDEBAR_W = 150;
    private static final int PAD = 14;
    private static final int RADIUS = 20;
    private static final int CARD_R = 12;
    private static final int CARD_HEADER = 46;
    private static final int CARD_GAP = 8;
    private static final int SETTINGS_PAD_TOP = 7;
    private static final int TELEMETRY_H = 50;
    private static final int ITEM_H = 22;
    private static final int ITEM_GAP = 2;
    private static final int SEARCH_H = 24;
    private static final int ITEMS_TOP = 90;

    private static int savedShiftX;
    private static int savedShiftY;
    private static String expandedId = "triggerbot";

    private int winW;
    private int winH;
    private int winX;
    private int winY;

    private int searchX;
    private int searchY;
    private int searchW;
    private boolean showUser;

    private int centerX;
    private int centerW;
    private int listTop;
    private int listBottom;

    private TextFieldWidget searchField;

    private ModuleEntry.Category filter;
    private double scroll;
    private double targetScroll;

    private boolean draggingWindow;
    private double dragAnchorX;
    private double dragAnchorY;
    private boolean draggingSlider;
    private NumberSetting dragSlider;
    private int dragSliderX;
    private int dragSliderW;

    private final Map<String, Float> toggleAnim = new HashMap<>();
    private final Map<String, Float> hoverAnim = new HashMap<>();
    private final Map<String, Float> expandAnim = new HashMap<>();
    private float hoverFactor = 0.25f;
    private long lastFrameMs;

    private String toast;
    private int toastColor = GlassTheme.TEXT;
    private long toastUntil;

    public AmiClickGui() {
        super(Text.literal("AMI // CLICKGUI"));
        for (ModuleEntry module : ModuleRegistry.all()) {
            expandAnim.put(module.id(), module.id().equals(expandedId) ? 1.0f : 0.0f);
        }
    }

    @Override
    protected void init() {
        winW = Math.min(640, this.width - 24);
        winH = Math.min(390, this.height - 24);
        winX = (this.width - winW) / 2 + savedShiftX;
        winY = (this.height - winH) / 2 + savedShiftY;
        clampWindow();

        String previous = searchField == null ? "" : searchField.getText();
        searchField = new TextFieldWidget(this.textRenderer, 0, 0, 100, 10, Text.literal("Search"));
        searchField.setDrawsBackground(false);
        searchField.setMaxLength(24);
        searchField.setEditableColor(GlassTheme.TEXT);
        searchField.setPlaceholder(Text.literal("Search modules...").setStyle(GlassTheme.uiFont()));
        searchField.addFormatter((text, firstCharacterIndex) ->
                OrderedText.styledForwardsVisitedString(text, GlassTheme.uiFont()));
        searchField.setText(previous);
        searchField.setChangedListener(text -> {
            scroll = 0.0;
            targetScroll = 0.0;
        });
        addDrawableChild(searchField);

        layout();
        lastFrameMs = System.currentTimeMillis();
    }

    private void clampWindow() {
        int minX = 4;
        int minY = 4;
        int maxX = Math.max(minX, this.width - winW - 4);
        int maxY = Math.max(minY, this.height - winH - 4);
        winX = MathHelper.clamp(winX, minX, maxX);
        winY = MathHelper.clamp(winY, minY, maxY);
        savedShiftX = winX - (this.width - winW) / 2;
        savedShiftY = winY - (this.height - winH) / 2;
    }

    private void layout() {
        centerX = winX + SIDEBAR_W + PAD;
        centerW = winW - SIDEBAR_W - PAD * 2;

        showUser = centerW >= 340;
        searchX = centerX;
        searchY = winY + 11;
        searchW = Math.max(110, Math.min(240, centerW - (showUser ? 160 : 0)));

        listTop = winY + HEADER_H + 8;
        listBottom = winY + winH - 12;

        if (searchField != null) {
            searchField.setX(searchX + 27);
            searchField.setY(searchY + (SEARCH_H - 8) / 2);
            searchField.setWidth(searchW - 38);
        }
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float deltaTicks) {
        layout();
        updateAnimations();

        renderWindow(g);
        renderSidebar(g, mouseX, mouseY);
        renderHeader(g, mouseX, mouseY);
        renderModuleList(g, mouseX, mouseY);
        renderSheen(g);

        // рисует поле поиска поверх всего остального
        super.render(g, mouseX, mouseY, deltaTicks);
    }

    // ------------------------------------------------------------------ backdrop

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float deltaTicks) {
        if (WORLD_BLUR) {
            super.renderBackground(g, mouseX, mouseY, deltaTicks);
        }
        g.fill(0, 0, this.width, this.height, 0x14000000);
    }

    // ------------------------------------------------------------------ window chrome

    private void renderWindow(DrawContext g) {
        for (int i = 4; i >= 1; i--) {
            GlassTheme.round(g, winX - i, winY - i + 2, winW + i * 2, winH + i * 2, RADIUS + i,
                    GlassTheme.withAlpha(0x000000, 16 - i * 3));
        }
        GlassTheme.roundGradient(g, winX, winY, winW, winH, RADIUS, GlassTheme.WINDOW_TOP, GlassTheme.WINDOW_BOTTOM);
        GlassTheme.roundGradient(g, winX, winY, winW, 120, RADIUS, 0x38FFFFFF, 0x00FFFFFF);
        GlassTheme.outline(g, winX, winY, winW, winH, RADIUS, GlassTheme.WINDOW_BORDER);
        GlassTheme.outline(g, winX + 1, winY + 1, winW - 2, winH - 2, RADIUS - 1, 0x28FFFFFF);

        int dividerX = winX + SIDEBAR_W;
        int top = winY + 16;
        int bottom = winY + winH - 16;
        int mid = (top + bottom) / 2;
        g.fillGradient(dividerX, top, dividerX + 1, mid, 0x00FFFFFF, 0x2AFFFFFF);
        g.fillGradient(dividerX, mid, dividerX + 1, bottom, 0x2AFFFFFF, 0x00FFFFFF);
    }

    private void renderSheen(DrawContext g) {
        long now = System.currentTimeMillis();
        int period = 9000;
        float phase = (now % period) / (float) period;
        int bx = winX - 160 + (int) ((winW + 320) * phase);

        g.enableScissor(winX + 8, winY + 8, winX + winW - 8, winY + winH - 8);
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(bx, winY + winH / 2);
        g.getMatrices().rotate(-0.35f);
        for (int i = 0; i < 3; i++) {
            int half = 10 + i * 14;
            int alpha = 9 - i * 3;
            int tint = GlassTheme.withAlpha(0xFFFFFF, alpha);
            g.fillGradient(-half, -winH, half, 0, 0x00000000, tint);
            g.fillGradient(-half, 0, half, winH, tint, 0x00000000);
        }
        g.getMatrices().popMatrix();
        g.disableScissor();
    }

    // ------------------------------------------------------------------ header

    private void renderHeader(DrawContext g, int mouseX, int mouseY) {
        boolean searchHover = inside(mouseX, mouseY, searchX, searchY, searchW, SEARCH_H);
        boolean focused = searchField != null && searchField.isFocused();
        GlassTheme.round(g, searchX, searchY, searchW, SEARCH_H, 12,
                focused ? 0x26FFFFFF : (searchHover ? 0x1EFFFFFF : 0x16FFFFFF));
        GlassTheme.outline(g, searchX, searchY, searchW, SEARCH_H, 12,
                focused ? GlassTheme.withAlpha(GlassTheme.ACCENT_SOFT, 0xA0) : 0x2CFFFFFF);
        GlassTheme.iconSearch(g, searchX + 10, searchY + 8, focused ? GlassTheme.ACCENT_SOFT : GlassTheme.DIM);

        if (searchHover) {
            g.setCursor(StandardCursors.POINTING_HAND);
        } else if (inside(mouseX, mouseY, winX, winY, winW, HEADER_H)) {
            g.setCursor(StandardCursors.RESIZE_ALL);
        }

        if (showUser) {
            int right = winX + winW - PAD;
            int avatar = 26;
            int ax = right - avatar;
            int ay = winY + 10;
            GlassTheme.glow(g, ax, ay, avatar, avatar, 13, GlassTheme.ACCENT, 3, 0.7f);
            GlassTheme.roundGradient(g, ax, ay, avatar, avatar, 13, 0xFFA79BFF, 0xFF6C5CE7);
            GlassTheme.outline(g, ax, ay, avatar, avatar, 13, 0x88FFFFFF);
            String name = username();
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            GlassTheme.textCentered(g, textRenderer, initial, ax + avatar / 2, ay + 9, 0.95f, 0xFFFFFFFF, false);

            String shown = GlassTheme.fit(textRenderer, name, 110, 0.82f);
            GlassTheme.textRight(g, textRenderer, shown, ax - 8, ay + 3, 0.82f, GlassTheme.TEXT, false);
            GlassTheme.textRight(g, textRenderer, "MC " + gameVersion(), ax - 8, ay + 15, 0.58f,
                    GlassTheme.DIM, false);
        }
    }

    // ------------------------------------------------------------------ sidebar

    private int itemY(int index) {
        return winY + ITEMS_TOP + index * (ITEM_H + ITEM_GAP);
    }

    private int itemX() {
        return winX + 30;
    }

    private int itemW() {
        return SIDEBAR_W - 30 - 12;
    }

    private void renderSidebar(DrawContext g, int mouseX, int mouseY) {
        // логотип
        int lx = winX + 18;
        int ly = winY + 12;
        GlassTheme.glow(g, lx, ly, 24, 24, 8, GlassTheme.ACCENT, 3, 0.8f);
        GlassTheme.roundGradient(g, lx, ly, 24, 24, 8, 0xFFA79BFF, 0xFF6C5CE7);
        GlassTheme.outline(g, lx, ly, 24, 24, 8, 0x77FFFFFF);
        GlassTheme.textCentered(g, textRenderer, "A", lx + 12, ly + 8, 1.0f, 0xFFFFFFFF, false);
        GlassTheme.spaced(g, textRenderer, "AMI", lx + 33, ly + 3, 1.2f, 2, GlassTheme.TEXT);
        GlassTheme.text(g, textRenderer, "CLICKGUI", lx + 33, ly + 15, 0.55f, GlassTheme.DIM, false);

        // заголовок группы
        int gy = winY + 62;
        int gx = winX + 16;
        GlassTheme.round(g, gx, gy, 16, 16, 5, GlassTheme.withAlpha(GlassTheme.ACCENT, 0x40));
        GlassTheme.iconGrid(g, gx + 8, gy + 8, GlassTheme.ACCENT_SOFT);
        GlassTheme.text(g, textRenderer, "Modules", gx + 24, gy + 4, 1.05f, GlassTheme.TEXT, false);

        // направляющая линия подпунктов
        int count = ModuleEntry.Category.values().length + 1;
        g.fill(winX + 24, itemY(0) + 2, winX + 25, itemY(count - 1) + ITEM_H - 2, 0x18FFFFFF);

        renderCategory(g, mouseX, mouseY, 0, "All", null, ModuleRegistry.all().size());
        for (ModuleEntry.Category category : ModuleEntry.Category.values()) {
            renderCategory(g, mouseX, mouseY, category.ordinal() + 1, capitalize(category.label()), category,
                    ModuleRegistry.count(category));
        }

        // нижний блок
        if (winH >= 300) {
            long now = System.currentTimeMillis();
            int by = winY + winH - 58;
            g.fill(winX + 18, by, winX + SIDEBAR_W - 18, by + 1, GlassTheme.LINE);
            GlassTheme.text(g, textRenderer, "RMB TOGGLE  /  WHEEL SCROLL", winX + 18, by + 8, 0.52f,
                    GlassTheme.DIM, false);
            GlassTheme.statusDot(g, winX + 19, by + 22, GlassTheme.GREEN, now);
            GlassTheme.text(g, textRenderer, "ONLINE", winX + 28, by + 21, 0.62f, GlassTheme.GREEN, false);
            String active = String.format(Locale.ROOT, "%02d / %02d ACTIVE",
                    ModuleRegistry.activeCount(), ModuleRegistry.all().size());
            GlassTheme.textRight(g, textRenderer, active, winX + SIDEBAR_W - 18, by + 21, 0.58f,
                    GlassTheme.ACCENT_SOFT, false);
            GlassTheme.text(g, textRenderer, modVersion() + "  //  " + gameVersion(), winX + 18, by + 34, 0.52f,
                    GlassTheme.DIM, false);
        }
    }

    private void renderCategory(DrawContext g, int mouseX, int mouseY, int index, String label,
                                ModuleEntry.Category category, int count) {
        int x = itemX();
        int y = itemY(index);
        int w = itemW();
        boolean active = filter == category;
        boolean hovered = inside(mouseX, mouseY, x, y, w, ITEM_H);
        float hover = smoothHover("cat:" + label, hovered);

        if (active) {
            GlassTheme.round(g, x, y, w, ITEM_H, 7, GlassTheme.withAlpha(GlassTheme.ACCENT, 0x2C));
            g.fill(x + 3, y + 6, x + 5, y + ITEM_H - 6, GlassTheme.ACCENT_SOFT);
        } else if (hover > 0.01f) {
            GlassTheme.round(g, x, y, w, ITEM_H, 7, GlassTheme.withAlpha(0xFFFFFF, Math.round(14 * hover)));
        }
        if (hovered) {
            g.setCursor(StandardCursors.POINTING_HAND);
        }

        int iconColor = active ? GlassTheme.ACCENT_SOFT : GlassTheme.mix(GlassTheme.DIM, GlassTheme.MUTED, hover);
        GlassTheme.categoryIcon(g, category, x + 15, y + ITEM_H / 2, iconColor);
        GlassTheme.text(g, textRenderer, label, x + 28, y + 7, 0.95f,
                active ? GlassTheme.TEXT : GlassTheme.mix(GlassTheme.MUTED, GlassTheme.TEXT, hover * 0.7f), false);
        GlassTheme.textRight(g, textRenderer, String.valueOf(count), x + w - 9, y + 8, 0.64f,
                active ? GlassTheme.ACCENT_SOFT : GlassTheme.DIM, false);
    }

    // ------------------------------------------------------------------ module list

    private void renderModuleList(DrawContext g, int mouseX, int mouseY) {
        List<ModuleEntry> modules = filtered();

        g.enableScissor(centerX - 3, listTop, centerX + centerW + 3, listBottom);
        if (modules.isEmpty()) {
            GlassTheme.textCentered(g, textRenderer, "NO MODULES FOUND", centerX + centerW / 2, listTop + 34, 0.8f,
                    GlassTheme.DIM, false);
        }
        int cardY = listTop - (int) Math.round(scroll);
        for (ModuleEntry module : modules) {
            int height = cardHeight(module);
            if (cardY + height >= listTop - 2 && cardY <= listBottom + 2) {
                renderCard(g, module, cardY, height, mouseX, mouseY);
            }
            cardY += height + CARD_GAP;
        }
        g.disableScissor();

        renderScrollbar(g);
        renderToast(g);
    }

    private void renderCard(DrawContext g, ModuleEntry module, int y, int height, int mouseX, int mouseY) {
        int x = centerX;
        int w = centerW;
        boolean enabled = module.isEnabled();
        boolean expanded = isExpanded(module);
        float expand = expandProgress(module);
        float progress = toggleAnim.getOrDefault("module:" + module.id(), enabled ? 1.0f : 0.0f);
        boolean hovered = inList(mouseX, mouseY) && inside(mouseX, mouseY, x, y, w, Math.min(height, CARD_HEADER));
        float hover = smoothHover("card:" + module.id(), hovered);

        int fill = GlassTheme.mix(GlassTheme.CARD, GlassTheme.CARD_HOVER, hover);
        fill = GlassTheme.mix(fill, GlassTheme.withAlpha(GlassTheme.ACCENT, 0x1C), progress * 0.6f);
        int border = expanded
                ? 0x55FFFFFF
                : GlassTheme.mix(GlassTheme.CARD_BORDER, GlassTheme.withAlpha(GlassTheme.ACCENT_SOFT, 0x40), progress * 0.5f);
        GlassTheme.panel(g, x, y, w, height, CARD_R, fill, border);

        g.enableScissor(x + 1, y + 1, x + w - 1, y + height - 1);

        // правая часть: чекбокс, бинд, шеврон
        int boxX = x + w - 28;
        int boxY = y + 15;
        boolean boxHover = inList(mouseX, mouseY) && inside(mouseX, mouseY, boxX - 4, boxY - 4, 24, 24);
        if (boxHover) {
            g.setCursor(StandardCursors.POINTING_HAND);
        } else if (hovered) {
            g.setCursor(StandardCursors.POINTING_HAND);
        }
        GlassTheme.checkbox(g, boxX, boxY, 16, progress, boxHover);

        int rightEdge = boxX - 12;
        String bind = module.keybind();
        if (bind != null && !bind.isBlank()) {
            String label = bind.toUpperCase(Locale.ROOT);
            int capW = GlassTheme.keycapWidth(textRenderer, label);
            GlassTheme.keycap(g, textRenderer, label, rightEdge - capW, y + 16);
            rightEdge -= capW + 10;
        }
        if (!module.settings().isEmpty()) {
            GlassTheme.chevron(g, rightEdge - 4, y + 21, expanded,
                    hovered ? GlassTheme.MUTED : GlassTheme.DIM);
            rightEdge -= 16;
        }

        // левая часть: чип категории, название, описание
        String tag = module.category().label();
        int tagW = GlassTheme.textWidth(textRenderer, tag, 0.68f) + 10;
        GlassTheme.round(g, x + 12, y + 10, tagW, 12, 4, 0x24FFFFFF);
        GlassTheme.textCentered(g, textRenderer, tag, x + 12 + tagW / 2, y + 13, 0.68f, GlassTheme.MUTED, false);
        GlassTheme.text(g, textRenderer, module.name(), x + 12 + tagW + 7, y + 12, 1.05f,
                enabled ? GlassTheme.TEXT : 0xFFC9C6DE, false);

        int descWidth = Math.max(40, rightEdge - (x + 12));
        GlassTheme.text(g, textRenderer, GlassTheme.fit(textRenderer, module.description(), descWidth, 0.76f),
                x + 12, y + 28, 0.76f, GlassTheme.DIM, false);

        // настройки
        if (expand > 0.01f) {
            g.fill(x + 12, y + CARD_HEADER - 1, x + w - 12, y + CARD_HEADER, GlassTheme.LINE);
            int end = renderSettings(g, module, x, y + CARD_HEADER + SETTINGS_PAD_TOP, w, mouseX, mouseY);
            if (hasTelemetry(module)) {
                renderTelemetry(g, x + 16, x + w - 16, end + 2);
            }
        }
        g.disableScissor();
    }

    private int renderSettings(DrawContext g, ModuleEntry module, int cardX, int top, int cardW,
                               int mouseX, int mouseY) {
        int left = cardX + 16;
        int right = cardX + cardW - 16;
        int y = top;

        for (Setting setting : module.settings()) {
            if (setting instanceof BoolSetting bool) {
                renderBool(g, module, bool, left, right, y, mouseX, mouseY);
            } else if (setting instanceof NumberSetting number) {
                renderNumber(g, number, left, right, y, mouseX, mouseY);
            } else if (setting instanceof ActionSetting action) {
                renderAction(g, action, left, right, y, mouseX, mouseY);
            }
            y += setting.rowHeight();
        }
        return y;
    }

    private void renderBool(DrawContext g, ModuleEntry module, BoolSetting setting, int left, int right,
                            int y, int mouseX, int mouseY) {
        boolean hovered = inList(mouseX, mouseY) && inside(mouseX, mouseY, left, y, right - left, setting.rowHeight());
        if (hovered) {
            g.setCursor(StandardCursors.POINTING_HAND);
        }
        GlassTheme.text(g, textRenderer, setting.label(), left, y + 9, 0.86f,
                hovered ? GlassTheme.TEXT : GlassTheme.MUTED, false);

        boolean on = setting.get();
        float progress = toggleAnim.getOrDefault("bool:" + module.id() + ":" + setting.label(), on ? 1.0f : 0.0f);
        GlassTheme.pillSwitch(g, right - 26, y + 7, progress, hovered);
    }

    private void renderNumber(DrawContext g, NumberSetting setting, int left, int right,
                              int y, int mouseX, int mouseY) {
        boolean hovered = inList(mouseX, mouseY) && inside(mouseX, mouseY, left, y, right - left, setting.rowHeight());
        boolean active = draggingSlider && dragSlider == setting;
        if (hovered || active) {
            g.setCursor(StandardCursors.RESIZE_EW);
        }

        GlassTheme.text(g, textRenderer, setting.label(), left, y + 5, 0.86f,
                hovered || active ? GlassTheme.TEXT : GlassTheme.MUTED, false);
        GlassTheme.textRight(g, textRenderer, setting.display(), right, y + 4, 0.82f, GlassTheme.ACCENT_SOFT, false);

        int trackY = y + 22;
        int width = right - left;
        GlassTheme.round(g, left, trackY, width, 4, 2, GlassTheme.TRACK);

        double t = setting.normalized();
        int fill = (int) Math.round(width * t);
        if (fill > 0) {
            GlassTheme.round(g, left, trackY, Math.max(4, fill), 4, 2, GlassTheme.ACCENT);
        }

        int handleX = left + (int) Math.round((width - 10) * t);
        if (hovered || active) {
            GlassTheme.glow(g, handleX, trackY - 3, 10, 10, 5, GlassTheme.ACCENT_SOFT, 3, 1.0f);
        }
        GlassTheme.round(g, handleX, trackY - 3, 10, 10, 5, 0xFFFFFFFF);
        GlassTheme.round(g, handleX + 3, trackY, 4, 4, 2, GlassTheme.withAlpha(GlassTheme.ACCENT, 0xFF));
    }

    private void renderAction(DrawContext g, ActionSetting setting, int left, int right, int y,
                              int mouseX, int mouseY) {
        int width = right - left;
        boolean hovered = inList(mouseX, mouseY) && inside(mouseX, mouseY, left, y + 2, width, 20);
        if (hovered) {
            g.setCursor(StandardCursors.POINTING_HAND);
        }
        GlassTheme.round(g, left, y + 2, width, 20, 7,
                hovered ? GlassTheme.withAlpha(GlassTheme.ACCENT, 0x44) : 0x1CFFFFFF);
        GlassTheme.outline(g, left, y + 2, width, 20, 7,
                hovered ? GlassTheme.withAlpha(GlassTheme.ACCENT_SOFT, 0xA0) : 0x30FFFFFF);
        GlassTheme.textCentered(g, textRenderer, setting.buttonLabel(), left + width / 2, y + 9, 0.64f,
                hovered ? GlassTheme.TEXT : GlassTheme.MUTED, false);
    }

    private void renderTelemetry(DrawContext g, int x, int right, int y) {
        int width = right - x;
        GlassTheme.text(g, textRenderer, "TELEMETRY", x, y + 1, 0.58f, GlassTheme.DIM, false);

        int attempts = VelocityStats.attempts();
        if (attempts == 0) {
            GlassTheme.text(g, textRenderer, "NO DATA YET  //  JUMP ON KNOCKBACK", x, y + 15, 0.62f,
                    GlassTheme.MUTED, false);
            return;
        }

        float accuracy = VelocityStats.accuracy();
        int accuracyColor = accuracyColor(accuracy);
        GlassTheme.textRight(g, textRenderer, String.format(Locale.ROOT, "%.0f%%", accuracy), right, y - 1,
                1.0f, accuracyColor, false);

        int barY = y + 13;
        GlassTheme.round(g, x, barY, width, 4, 2, GlassTheme.TRACK);
        int fill = Math.round(width * accuracy / 100.0f);
        if (fill > 0) {
            GlassTheme.round(g, x, barY, Math.max(4, fill), 4, 2, accuracyColor);
        }

        int dots = VelocityStats.WINDOW;
        int gap = 2;
        int dotWidth = Math.max(2, (width - (dots - 1) * gap) / dots);
        for (int i = 0; i < dots; i++) {
            VelocityStats.Result result = VelocityStats.recent(i);
            int color = result == null ? GlassTheme.TRACK : resultColor(result);
            int dx = x + i * (dotWidth + gap);
            GlassTheme.round(g, dx, y + 23, dotWidth, 6, 2, color);
        }

        String counts = String.format(Locale.ROOT, "%d OK  %d LATE  %d FAIL",
                VelocityStats.count(VelocityStats.Result.JUMPED),
                VelocityStats.count(VelocityStats.Result.MISSED),
                VelocityStats.count(VelocityStats.Result.FAILED));
        GlassTheme.text(g, textRenderer, counts, x, y + 34, 0.58f, GlassTheme.DIM, false);
    }

    private static int accuracyColor(float accuracy) {
        if (accuracy >= 90.0f) {
            return GlassTheme.GREEN;
        }
        if (accuracy >= 70.0f) {
            return GlassTheme.AMBER;
        }
        return GlassTheme.RED;
    }

    private static int resultColor(VelocityStats.Result result) {
        return switch (result) {
            case JUMPED -> GlassTheme.GREEN;
            case MISSED -> GlassTheme.AMBER;
            case FAILED -> GlassTheme.RED;
            case SKIPPED -> GlassTheme.DIM;
        };
    }

    private void renderScrollbar(DrawContext g) {
        int content = contentHeight();
        int viewport = listBottom - listTop;
        if (content <= viewport) {
            return;
        }
        int barX = winX + winW - 9;
        int barTop = listTop + 2;
        int barHeight = viewport - 4;
        GlassTheme.round(g, barX, barTop, 3, barHeight, 1, 0x16FFFFFF);
        int thumbHeight = Math.max(18, Math.round(barHeight * viewport / (float) content));
        int thumbY = barTop + Math.round((barHeight - thumbHeight) * (float) (scroll / maxScroll()));
        GlassTheme.round(g, barX, thumbY, 3, thumbHeight, 1, GlassTheme.withAlpha(GlassTheme.ACCENT_SOFT, 0xB0));
    }

    private void renderToast(DrawContext g) {
        long now = System.currentTimeMillis();
        if (toast == null || now >= toastUntil) {
            return;
        }
        float fade = MathHelper.clamp((toastUntil - now) / 300.0f, 0.0f, 1.0f);
        int textW = GlassTheme.textWidth(textRenderer, toast, 0.7f);
        int w = textW + 26;
        int h = 22;
        int x = centerX + (centerW - w) / 2;
        int y = listBottom - h - 6;
        GlassTheme.round(g, x, y, w, h, 11, GlassTheme.scaleAlpha(0xF01B1930, fade));
        GlassTheme.outline(g, x, y, w, h, 11, GlassTheme.scaleAlpha(GlassTheme.withAlpha(GlassTheme.ACCENT_SOFT, 0x90), fade));
        GlassTheme.textCentered(g, textRenderer, toast, x + w / 2, y + 8, 0.7f,
                GlassTheme.scaleAlpha(toastColor, fade), false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        layout();
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // поиск
        if (button == 0 && inside(mouseX, mouseY, searchX, searchY, searchW, SEARCH_H)) {
            setFocused(searchField);
            searchField.setFocused(true);
            super.mouseClicked(click, doubled);
            return true;
        }
        if (searchField.isFocused()) {
            searchField.setFocused(false);
            setFocused(null);
        }

        // перетаскивание окна за шапку
        if (button == 0 && inside(mouseX, mouseY, winX, winY, winW, HEADER_H)) {
            draggingWindow = true;
            dragAnchorX = mouseX - winX;
            dragAnchorY = mouseY - winY;
            return true;
        }

        // категории
        if (button == 0) {
            int count = ModuleEntry.Category.values().length + 1;
            for (int i = 0; i < count; i++) {
                if (inside(mouseX, mouseY, itemX(), itemY(i), itemW(), ITEM_H)) {
                    setFilter(i == 0 ? null : ModuleEntry.Category.values()[i - 1]);
                    playClick(1.05f);
                    return true;
                }
            }
        }

        // карточки
        if (inside(mouseX, mouseY, centerX, listTop, centerW, listBottom - listTop)) {
            int cardY = listTop - (int) Math.round(scroll);
            for (ModuleEntry module : filtered()) {
                int height = cardHeight(module);
                if (mouseY >= cardY && mouseY <= cardY + height) {
                    int boxX = centerX + centerW - 28;
                    if (button == 0 && inside(mouseX, mouseY, boxX - 4, cardY + 11, 24, 24)) {
                        toggleModule(module);
                        return true;
                    }

                    boolean open = isExpanded(module) && expandProgress(module) > 0.95f;
                    if (button == 0 && open && mouseY > cardY + CARD_HEADER) {
                        int left = centerX + 16;
                        int right = centerX + centerW - 16;
                        int settingsY = cardY + CARD_HEADER + SETTINGS_PAD_TOP;
                        for (Setting setting : module.settings()) {
                            int rowHeight = setting.rowHeight();
                            if (mouseY >= settingsY && mouseY < settingsY + rowHeight) {
                                if (setting instanceof BoolSetting bool) {
                                    bool.toggle();
                                    playClick(bool.get() ? 1.12f : 0.92f);
                                    return true;
                                }
                                if (setting instanceof NumberSetting number) {
                                    draggingSlider = true;
                                    dragSlider = number;
                                    dragSliderX = left;
                                    dragSliderW = Math.max(1, right - left);
                                    number.setNormalized((mouseX - left) / (double) dragSliderW);
                                    playClick(1.0f);
                                    return true;
                                }
                                if (setting instanceof ActionSetting action) {
                                    action.run();
                                    playClick(1.18f);
                                    showToast(action.label().toUpperCase(Locale.ROOT) + " // DONE", GlassTheme.ACCENT_SOFT);
                                    return true;
                                }
                            }
                            settingsY += rowHeight;
                        }
                        return true;
                    }

                    if (mouseY <= cardY + CARD_HEADER) {
                        if (button == 1) {
                            toggleModule(module);
                            return true;
                        }
                        if (button == 0) {
                            if (module.settings().isEmpty()) {
                                toggleModule(module);
                            } else {
                                expandedId = isExpanded(module) ? null : module.id();
                                playClick(0.95f);
                            }
                        }
                    }
                    return true;
                }
                cardY += height + CARD_GAP;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        super.mouseReleased(click);
        draggingWindow = false;
        if (draggingSlider) {
            draggingSlider = false;
            dragSlider = null;
            VelocityConfig.save();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingWindow) {
            winX = (int) Math.round(click.x() - dragAnchorX);
            winY = (int) Math.round(click.y() - dragAnchorY);
            clampWindow();
            layout();
            return true;
        }
        if (draggingSlider && dragSlider != null) {
            double t = (click.x() - dragSliderX) / (double) dragSliderW;
            dragSlider.setNormalized(t);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        layout();
        if (inside(mouseX, mouseY, centerX, listTop, centerW, listBottom - listTop)) {
            targetScroll = MathHelper.clamp(targetScroll - verticalAmount * 30.0, 0.0, maxScroll());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (searchField != null && searchField.isFocused()) {
            if (input.isEscape()) {
                searchField.setFocused(false);
                setFocused(null);
                return true;
            }
            super.keyPressed(input);
            return true;
        }
        if (VelocityClient.openMenu != null && VelocityClient.openMenu.matchesKey(input)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        VelocityConfig.save();
    }

    // ------------------------------------------------------------------ helpers

    private void updateAnimations() {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;
        if (dt <= 0.0f) {
            dt = 0.016f;
        }
        hoverFactor = 1.0f - (float) Math.exp(-dt * 15.0f);
        float toggleFactor = 1.0f - (float) Math.exp(-dt * 18.0f);
        float expandFactor = 1.0f - (float) Math.exp(-dt * 14.0f);

        for (ModuleEntry module : ModuleRegistry.all()) {
            updateToggle("module:" + module.id(), module.isEnabled(), toggleFactor);
            for (Setting setting : module.settings()) {
                if (setting instanceof BoolSetting bool) {
                    updateToggle("bool:" + module.id() + ":" + setting.label(), bool.get(), toggleFactor);
                }
            }

            float target = isExpanded(module) ? 1.0f : 0.0f;
            float current = expandAnim.getOrDefault(module.id(), target);
            current += (target - current) * expandFactor;
            if (Math.abs(target - current) < 0.002f) {
                current = target;
            }
            expandAnim.put(module.id(), current);
        }

        scroll += (targetScroll - scroll) * (1.0f - (float) Math.exp(-dt * 18.0f));
        if (Math.abs(targetScroll - scroll) < 0.05) {
            scroll = targetScroll;
        }
        targetScroll = MathHelper.clamp(targetScroll, 0.0, maxScroll());
    }

    private void updateToggle(String key, boolean enabled, float factor) {
        float current = toggleAnim.getOrDefault(key, enabled ? 1.0f : 0.0f);
        current += ((enabled ? 1.0f : 0.0f) - current) * factor;
        if (Math.abs((enabled ? 1.0f : 0.0f) - current) < 0.001f) {
            current = enabled ? 1.0f : 0.0f;
        }
        toggleAnim.put(key, current);
    }

    private float smoothHover(String key, boolean hovered) {
        float current = hoverAnim.getOrDefault(key, 0.0f);
        current += ((hovered ? 1.0f : 0.0f) - current) * hoverFactor;
        if (Math.abs((hovered ? 1.0f : 0.0f) - current) < 0.002f) {
            current = hovered ? 1.0f : 0.0f;
        }
        hoverAnim.put(key, current);
        return current;
    }

    private List<ModuleEntry> filtered() {
        List<ModuleEntry> base = ModuleRegistry.filtered(filter);
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return base;
        }
        return base.stream()
                .filter(module -> module.name().toLowerCase(Locale.ROOT).contains(query)
                        || module.category().label().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private boolean isExpanded(ModuleEntry module) {
        return module.id().equals(expandedId) && !module.settings().isEmpty();
    }

    private static boolean hasTelemetry(ModuleEntry module) {
        return "velocity".equals(module.id());
    }

    /** Eased 0..1 progress of the settings drawer. */
    private float expandProgress(ModuleEntry module) {
        float t = MathHelper.clamp(expandAnim.getOrDefault(module.id(), 0.0f), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private int fullExtra(ModuleEntry module) {
        if (module.settings().isEmpty()) {
            return 0;
        }
        return module.settingsHeight() + (hasTelemetry(module) ? TELEMETRY_H : 0);
    }

    private int cardHeight(ModuleEntry module) {
        return CARD_HEADER + Math.round(fullExtra(module) * expandProgress(module));
    }

    private int contentHeight() {
        int height = 0;
        for (ModuleEntry module : filtered()) {
            height += cardHeight(module) + CARD_GAP;
        }
        return Math.max(0, height - CARD_GAP);
    }

    private double maxScroll() {
        return Math.max(0.0, contentHeight() - (listBottom - listTop));
    }

    private void setFilter(ModuleEntry.Category category) {
        filter = category;
        scroll = 0.0;
        targetScroll = 0.0;
    }

    private void toggleModule(ModuleEntry module) {
        boolean enabled = !module.isEnabled();
        module.setEnabled(enabled);
        if ("velocity".equals(module.id())) {
            VelocityConfig.save();
        }
        showToast(module.name() + " // " + (enabled ? "ENGAGED" : "DISENGAGED"),
                enabled ? GlassTheme.ACCENT_SOFT : GlassTheme.MUTED);
        playClick(enabled ? 1.16f : 0.9f);
    }

    private void showToast(String text, int color) {
        toast = text;
        toastColor = color;
        toastUntil = System.currentTimeMillis() + 1600L;
    }

    private void playClick(float pitch) {
        if (client == null) {
            return;
        }
        client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private boolean inList(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, centerX, listTop, centerW, listBottom - listTop);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String username() {
        if (client != null && client.getSession() != null) {
            String name = client.getSession().getUsername();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return "Player";
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer("ami")
                .map(container -> "v" + container.getMetadata().getVersion().getFriendlyString())
                .orElse("vdev");
    }

    private static String gameVersion() {
        return SharedConstants.getGameVersion().id();
    }
}
