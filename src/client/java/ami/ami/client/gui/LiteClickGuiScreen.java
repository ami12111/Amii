package ami.ami.client.gui;

import ami.ami.client.bind.ModuleBinds;
import ami.ami.client.config.ModuleSettings;
import ami.ami.client.velocity.VelocityClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * Compact dark click GUI: sidebar categories, module cards with animated toggles and inline sliders.
 * The supplied Lite Project screen ported to Ami's module registry and Yarn mappings.
 */
public final class LiteClickGuiScreen extends Screen {
    private static final int WHITE = 0xFFF1F1F1;
    private static final int TEXT = 0xFFC9C9CB;
    private static final int MUTED = 0xFF73757B;
    private static final int ACCENT = 0xFFECEDEF;
    private static final int PANEL = 0xFA090A0C;
    private static final int SIDE = 0xFC0D0E10;
    private static final int CARD = 0xFF101113;
    private static final int BORDER = 0xFF191A1E;

    private static int rememberedCategory;

    private final List<ClickGuiModel.Category> categories = ClickGuiModel.create();
    private final AnimatedFloat nav;
    private int categoryIndex;
    private ClickGuiModel.Module expanded;
    private ClickGuiModel.Slider dragging;
    /** Module id whose key row is waiting for the next key press, or null. */
    private String bindingModule;
    private boolean previousDown;
    private boolean previousRightDown;
    private boolean searchFocused;
    private boolean twoColumns = true;
    private String search = "";
    private float scrollOffset;
    private float scrollTarget;
    private float maxScroll;
    private long lastFrame = System.nanoTime();

    public LiteClickGuiScreen() {
        super(Text.literal("Ami Click GUI"));
        categoryIndex = Math.min(Math.max(rememberedCategory, 0), categories.size() - 1);
        nav = new AnimatedFloat(categoryIndex);
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float tickDelta) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        MinecraftClient mc = MinecraftClient.getInstance();
        long handle = mc.getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean pressed = down && !previousDown;
        boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean rightPressed = rightDown && !previousRightDown;

        // Рисуем в реальных пикселях фреймбуфера: обратная шкала матрицей отменяет
        // GUI-масштаб, поэтому текст и скругления не размываются при апскейле.
        int fbWidth = mc.getWindow().getFramebufferWidth();
        int fbHeight = mc.getWindow().getFramebufferHeight();
        float guiScale = (float) mc.getWindow().getScaleFactor();
        int mx = Math.round((float) (mouseX * (double) fbWidth / Math.max(1, width)));
        int my = Math.round((float) (mouseY * (double) fbHeight / Math.max(1, height)));

        Layout layout = Layout.create(width, height, fbWidth, fbHeight, guiScale);
        UiDrawList ui = new UiDrawList();
        Matrix3x2fStack matrices = g.getMatrices();
        matrices.pushMatrix();
        matrices.scale(1f / guiScale, 1f / guiScale);

        backdrop(ui, layout, fbWidth, fbHeight, guiScale);
        sidebar(ui, layout, mx, my, pressed, dt);
        ClickGuiModel.Category category = categories.get(categoryIndex);
        header(ui, layout, mx, my, pressed);
        moduleGrid(ui, layout, category, mx, my, down, pressed, rightPressed, dt);
        if (bindingModule != null && (expanded == null || !expanded.id.equals(bindingModule))) {
            bindingModule = null;
        }
        UiRenderer.submit(g, ui, textRenderer);
        matrices.popMatrix();

        if (!down) {
            dragging = null;
        }
        previousDown = down;
        previousRightDown = rightDown;
    }

    private void backdrop(UiDrawList ui, Layout l, int fbWidth, int fbHeight, float guiScale) {
        ui.rect(0, 0, fbWidth, fbHeight, 0xC9000000);
        for (int i = 0; i < 90; i++) {
            float px = i * 83 % Math.max(1, fbWidth);
            float py = i * 47 % Math.max(1, fbHeight);
            ui.circle(px, py, 0.45f * guiScale, 0x16FFFFFF);
        }
        ui.shadow(l.x - 3 * l.s, l.y + 2 * l.s, l.w + 6 * l.s, l.h + 6 * l.s,
                11 * l.s, 24 * l.s, 0x50000000);
        ui.roundedRect(l.x, l.y, l.w, l.h, 8 * l.s, PANEL)
                .roundedRect(l.x, l.y, l.side, l.h, 8 * l.s, SIDE)
                .rect(l.x + l.side - l.s, l.y + 10 * l.s, l.s, l.h - 20 * l.s, BORDER)
                .outlineRoundedRect(l.x, l.y, l.w, l.h, 8 * l.s,
                        Math.max(0.55f * guiScale, l.s * 0.7f), 0xFF202126);
    }

    private void sidebar(UiDrawList ui, Layout l, int mx, int my, boolean pressed, float dt) {
        float bx = l.x + 15 * l.s;
        float brandY = l.y + 24 * l.s;
        ui.icon(bx + 8 * l.s, brandY, 16 * l.s, WHITE, UiRenderer.ICON_BRAND)
                .brandText(bx + 22 * l.s, brandY, 9.5f * l.s, WHITE, "AMI");

        float h = 27 * l.s;
        float gap = 2 * l.s;
        float x = l.x + 9 * l.s;
        float w = l.side - 18 * l.s;
        for (int i = 0; i < categories.size(); i++) {
            float iy = categoryY(l, i, h, gap);
            if (pressed && hit(mx, my, x, iy, w, h)) {
                categoryIndex = i;
                rememberedCategory = i;
                expanded = null;
                dragging = null;
                scrollOffset = scrollTarget = 0;
                nav.setTarget(i);
            }
        }
        nav.setTarget(categoryIndex);
        float animated = nav.update(dt, 18);
        float indicatorY = categoryY(l, animated, h, gap);
        ui.roundedRect(x, indicatorY, w, h, 6 * l.s, 0xFF171920)
                .roundedRect(x, indicatorY + 7 * l.s, 2 * l.s, h - 14 * l.s, l.s, ACCENT);
        for (int i = 0; i < categories.size(); i++) {
            ClickGuiModel.Category category = categories.get(i);
            float iy = categoryY(l, i, h, gap);
            int color = i == categoryIndex ? WHITE : MUTED;
            ui.icon(x + 14 * l.s, iy + h / 2, 11 * l.s, color, category.icon())
                    .text(x + 28 * l.s, iy + h / 2, 8.5f * l.s, color, category.name());
        }

        float fy = l.y + l.h - 35 * l.s;
        ui.line(l.x + 11 * l.s, fy - 10 * l.s, l.x + l.side - 11 * l.s, fy - 10 * l.s,
                        Math.max(0.5f, l.s * 0.6f), BORDER)
                .circle(l.x + 22 * l.s, fy + 2 * l.s, 8 * l.s, 0xFF1D1F27)
                .icon(l.x + 22 * l.s, fy + 2 * l.s, 10 * l.s, WHITE, UiRenderer.ICON_PLAYER)
                .strongText(l.x + 36 * l.s, fy - 2 * l.s, 7.8f * l.s, WHITE, "Ami")
                .text(l.x + 36 * l.s, fy + 8 * l.s, 6.5f * l.s, MUTED, "Клик-меню");
    }

    private void header(UiDrawList ui, Layout l, int mx, int my, boolean pressed) {
        float y = l.y + 23 * l.s;
        float sx = l.mainX + l.mainW - 116 * l.s;
        float sw = 88 * l.s;
        boolean searchOver = hit(mx, my, sx, y - 10 * l.s, sw, 20 * l.s);
        if (pressed) {
            searchFocused = searchOver;
        }
        ui.roundedRect(sx, y - 10 * l.s, sw, 20 * l.s, 5 * l.s,
                        searchFocused ? 0xFF181A1D : 0xFF111214)
                .outlineRoundedRect(sx, y - 10 * l.s, sw, 20 * l.s, 5 * l.s,
                        Math.max(0.5f, l.s * 0.6f), searchFocused ? 0xFF3A3B40 : BORDER)
                .icon(sx + sw - 10 * l.s, y, 9 * l.s, MUTED, UiRenderer.ICON_SEARCH);
        String shown = search.isEmpty() ? "Поиск модулей..." : search;
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            shown += "_";
        }
        ui.text(sx + 7 * l.s, y, 7 * l.s, search.isEmpty() ? MUTED : TEXT, shown);

        float gx = l.mainX + l.mainW - 18 * l.s;
        if (pressed && hit(mx, my, gx - 8 * l.s, y - 9 * l.s, 17 * l.s, 18 * l.s)) {
            twoColumns = !twoColumns;
        }
        ui.icon(gx, y, 10 * l.s, twoColumns ? WHITE : MUTED,
                        twoColumns ? UiRenderer.ICON_COLUMNS : UiRenderer.ICON_ROWS)
                .line(l.mainX, l.y + 42 * l.s, l.mainX + l.mainW, l.y + 42 * l.s,
                        Math.max(0.5f, l.s * 0.6f), BORDER);
    }

    private void moduleGrid(UiDrawList ui, Layout l, ClickGuiModel.Category category,
                            int mx, int my, boolean down, boolean pressed, boolean rightPressed,
                            float dt) {
        List<ClickGuiModel.Module> visible = new ArrayList<>();
        String query = search.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            visible.addAll(category.modules());
        } else {
            // Skip the "All" bucket so a module is not listed twice.
            for (int c = 1; c < categories.size(); c++) {
                for (ClickGuiModel.Module module : categories.get(c).modules()) {
                    if (module.name.toLowerCase(Locale.ROOT).contains(query)
                            || module.description.toLowerCase(Locale.ROOT).contains(query)) {
                        visible.add(module);
                    }
                }
            }
        }

        float gap = 13 * l.s;
        int columns = twoColumns ? 2 : 1;
        float columnW = (l.mainW - gap * (columns - 1)) / columns;
        scrollOffset += (scrollTarget - scrollOffset) * Math.min(1, dt * 18);

        float viewportTop = l.y + 44 * l.s;
        float viewportBottom = l.y + l.h - 10 * l.s;
        float[] ys = new float[columns];
        for (int i = 0; i < columns; i++) {
            ys[i] = l.y + 49 * l.s - scrollOffset;
        }

        ui.pushScissor(l.mainX, l.y + 44 * l.s, l.mainW, l.h - 54 * l.s);
        for (int i = 0; i < visible.size(); i++) {
            ClickGuiModel.Module module = visible.get(i);
            int column = columns == 1 ? 0 : i % 2;
            float x = l.mainX + column * (columnW + gap);
            float y = ys[column];
            float baseH = 31 * l.s;
            float settingsH = expanded == module ? settingsHeight(module, l.s) : 0;
            boolean pointerInView = hit(mx, my, l.mainX, viewportTop, l.mainW,
                    viewportBottom - viewportTop);
            boolean over = pointerInView && hit(mx, my, x, y, columnW, baseH);
            float toggleX = x + columnW - 25 * l.s;

            if (pressed && over) {
                module.toggle();
            }
            if (rightPressed && over) {
                expanded = expanded == module ? null : module;
            }

            ui.roundedRect(x, y, columnW, baseH + settingsH, 5 * l.s,
                            expanded == module ? 0xFF12141A : over ? 0xFF111319 : CARD)
                    .strongText(x + 9 * l.s, y + 15 * l.s, 8.2f * l.s,
                            expanded == module ? WHITE : TEXT, module.name);

            toggle(ui, toggleX, y + 9 * l.s, 21 * l.s, 13 * l.s, module.animation.update(dt, 20), l.s);
            if (expanded == module) {
                drawSettings(ui, l, module, x + 8 * l.s, y + 35 * l.s, columnW - 16 * l.s,
                        pointerInView ? mx : -100000, pointerInView ? my : -100000,
                        down && pointerInView, pressed && pointerInView,
                        rightPressed && pointerInView, dt);
            }
            ys[column] += baseH + settingsH + 5 * l.s;
        }

        float contentBottom = ys[0];
        for (int i = 1; i < columns; i++) {
            contentBottom = Math.max(contentBottom, ys[i]);
        }
        maxScroll = Math.max(0, contentBottom + scrollOffset - viewportBottom);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        ui.popScissor();

        // Полоса прокрутки живёт в правом отступе и не перекрывает карточки.
        if (maxScroll > 1) {
            float trackH = viewportBottom - viewportTop;
            float thumbH = Math.max(18 * l.s, trackH * trackH / (trackH + maxScroll));
            float thumbY = viewportTop + (trackH - thumbH) * (scrollOffset / maxScroll);
            ui.roundedRect(l.mainX + l.mainW + 5 * l.s, thumbY, 2 * l.s, thumbH,
                    l.s, 0x6673757B);
        }
        if (visible.isEmpty()) {
            ui.centeredText(l.mainX + l.mainW / 2, l.y + l.h / 2, 9 * l.s, MUTED,
                    "Модули не найдены", false);
        }
    }

    private void drawSettings(UiDrawList ui, Layout l, ClickGuiModel.Module module, float x, float y,
                              float w, int mx, int my, boolean down, boolean pressed,
                              boolean rightPressed, float dt) {
        for (ClickGuiModel.Control control : module.controls) {
            if (control instanceof ClickGuiModel.Toggle toggle) {
                ui.strongText(x, y + 7 * l.s, 7.2f * l.s, TEXT, toggle.name())
                        .text(x, y + 17 * l.s, 6 * l.s, MUTED, toggle.hint());
                if (pressed && hit(mx, my, x, y, w, 23 * l.s)) {
                    toggle.toggle();
                }
                float p = toggle.animation.update(dt, 20);
                ui.roundedRect(x + w - 11 * l.s, y + 4 * l.s, 8 * l.s, 8 * l.s, 2 * l.s,
                        p > 0.5f ? ACCENT : 0xFF24262E);
                if (p > 0.5f) {
                    ui.icon(x + w - 7 * l.s, y + 8 * l.s, 7 * l.s, PANEL, UiRenderer.ICON_CHECK);
                }
                y += 25 * l.s;
            } else if (control instanceof ClickGuiModel.Slider slider) {
                ui.strongText(x, y + 6 * l.s, 7.2f * l.s, TEXT, slider.name())
                        .rightText(x + w, y + 6 * l.s, 6.8f * l.s, TEXT, slider.value(), true)
                        .text(x, y + 15 * l.s, 6 * l.s, MUTED, slider.hint());
                float tx = x + w * 0.48f;
                float tw = w * 0.37f;
                float ty = y + 11 * l.s;
                if (pressed && hit(mx, my, tx, ty - 5 * l.s, tw, 12 * l.s)) {
                    dragging = slider;
                }
                if (down && dragging == slider) {
                    slider.model.setFromMouse(mx, tx, tw);
                    slider.sync();
                }
                float p = slider.model.update(dt);
                ui.roundedRect(tx, ty, tw, 2 * l.s, l.s, 0xFF292C35)
                        .roundedRect(tx, ty, Math.max(l.s, tw * p), 2 * l.s, l.s, ACCENT)
                        .circle(tx + tw * p, ty + l.s, 2.6f * l.s, WHITE);
                y += 27 * l.s;
            } else if (control instanceof ClickGuiModel.Action action) {
                ui.strongText(x, y + 7 * l.s, 7.2f * l.s, TEXT, action.name())
                        .text(x, y + 16 * l.s, 6 * l.s, MUTED, action.hint());
                float bw = Math.min(74 * l.s, w * 0.44f);
                float bx = x + w - bw;
                boolean over = pressed && hit(mx, my, x, y, w, 20 * l.s);
                if (over) {
                    action.run();
                }
                ui.roundedRect(bx, y, bw, 20 * l.s, 4 * l.s, over ? 0xFF1C2133 : 0xFF191B22)
                        .centeredText(bx + bw / 2, y + 10 * l.s, 6.2f * l.s, WHITE,
                                action.button(), true);
                y += 24 * l.s;
            } else if (control instanceof ClickGuiModel.Key key) {
                boolean listening = module.id.equals(bindingModule);
                String label = listening ? "Нажмите клавишу" : ModuleBinds.label(module.id);
                ui.strongText(x, y + 7 * l.s, 7.2f * l.s, TEXT, key.name())
                        .text(x, y + 16 * l.s, 6 * l.s, MUTED, key.hint());
                float kw = Math.min(74 * l.s, w * 0.44f);
                float kx = x + w - kw;
                boolean over = hit(mx, my, x, y, w, 20 * l.s);
                if (pressed && over) {
                    bindingModule = module.id;
                    searchFocused = false;
                } else if (rightPressed && over) {
                    ModuleBinds.clear(module.id);
                    bindingModule = null;
                }
                ui.roundedRect(kx, y, kw, 20 * l.s, 4 * l.s,
                                listening ? 0xFF1C2133 : 0xFF191B22)
                        .centeredText(kx + kw / 2, y + 10 * l.s, 6.2f * l.s,
                                listening ? WHITE : TEXT, label, true);
                y += 24 * l.s;
            }
        }
    }

    private static float categoryY(Layout l, float index, float height, float gap) {
        return l.y + 48 * l.s + index * (height + gap);
    }

    private static float settingsHeight(ClickGuiModel.Module module, float s) {
        float h = 9 * s;
        for (ClickGuiModel.Control control : module.controls) {
            h += (control instanceof ClickGuiModel.Slider ? 27
                    : control instanceof ClickGuiModel.Toggle ? 25 : 24) * s;
        }
        return h;
    }

    private static void toggle(UiDrawList ui, float x, float y, float w, float h, float p, float s) {
        ui.roundedRect(x, y, w, h, h / 2, mix(0xFF292C34, ACCENT, p))
                .circle(x + h / 2 + (w - h) * p, y + h / 2, h / 2 - 2 * s,
                        mix(WHITE, 0xFF111216, p));
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = Math.round((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return aa << 24 | r << 16 | g << 8 | bl;
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused && input.isValidChar() && search.length() < 28) {
            search += input.asString();
            scrollOffset = scrollTarget = 0;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (bindingModule != null) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                bindingModule = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                ModuleBinds.clear(bindingModule);
                bindingModule = null;
                return true;
            }
            if (key > 0) {
                ModuleBinds.set(bindingModule, key);
            }
            bindingModule = null;
            return true;
        }
        if (searchFocused) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) {
                    search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
                }
                scrollOffset = scrollTarget = 0;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
        }
        if (VelocityClient.openMenu != null && VelocityClient.openMenu.matchesKey(input)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int fbWidth = mc.getWindow().getFramebufferWidth();
        int fbHeight = mc.getWindow().getFramebufferHeight();
        float guiScale = (float) mc.getWindow().getScaleFactor();
        float mx = (float) (mouseX * (double) fbWidth / Math.max(1, width));
        float my = (float) (mouseY * (double) fbHeight / Math.max(1, height));
        Layout l = Layout.create(width, height, fbWidth, fbHeight, guiScale);
        if (hit(mx, my, l.mainX, l.y + 44 * l.s, l.mainW, l.h - 54 * l.s)) {
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) verticalAmount * 36 * l.s));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        ModuleSettings.save();
    }

    private record Layout(float x, float y, float w, float h, float s, float side,
                          float mainX, float mainW) {
        static Layout create(int guiW, int guiH, int fbW, int fbH, float guiScale) {
            float designW = 600;
            float designH = 340;
            float margin = 18;
            float fitScale = Math.min((guiW - margin * 2) / designW, (guiH - margin * 2) / designH);
            float logical = Math.max(0.55f, Math.min(0.78f, fitScale));
            float s = logical * guiScale;
            float w = designW * s;
            float h = designH * s;
            float x = (fbW - w) / 2f;
            float y = (fbH - h) / 2f;
            float side = 130 * s;
            float mainX = x + side + 14 * s;
            return new Layout(x, y, w, h, s, side, mainX, x + w - mainX - 14 * s);
        }
    }
}
