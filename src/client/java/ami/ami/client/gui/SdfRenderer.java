package ami.ami.client.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Один SDF-пайплайн для скруглённых плашек, кругов, обводок и теней.
 * Геометрия идёт через очередь GUI (GuiRenderState), поэтому порядок
 * с текстом и scissor остаётся правильным.
 */
final class SdfRenderer {
    /** Запас вокруг фигуры, чтобы сглаживание не обрезалось краем квада. */
    private static final float AA_PADDING = 1.5f;

    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of("ami", "pipeline/gui_sdf"))
                    .withVertexShader(Identifier.of("ami", "core/gui_sdf"))
                    .withFragmentShader(Identifier.of("ami", "core/gui_sdf"))
                    .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private SdfRenderer() {
    }

    /** Форсирует регистрацию пайплайна при старте клиента, до первого кадра GUI. */
    static void init() {
    }

    /** Заливка со скруглением. */
    static void fill(DrawContext g, float x, float y, float w, float h, float radius,
                     int color, @Nullable ScreenRect scissor) {
        submit(g, x, y, w, h, radius, 0f, 0f, color, scissor);
    }

    /** Обводка по внутреннему краю фигуры. */
    static void stroke(DrawContext g, float x, float y, float w, float h, float radius,
                       float thickness, int color, @Nullable ScreenRect scissor) {
        submit(g, x, y, w, h, radius, Math.max(0.1f, thickness), 0f, color, scissor);
    }

    /** Мягкая тень: та же фигура с размытием наружу. */
    static void shadow(DrawContext g, float x, float y, float w, float h, float radius,
                       float blur, int color, @Nullable ScreenRect scissor) {
        submit(g, x, y, w, h, radius, 0f, Math.max(0.01f, blur), color, scissor);
    }

    /** Круг — частный случай скруглённого прямоугольника. */
    static void circle(DrawContext g, float cx, float cy, float radius,
                       int color, @Nullable ScreenRect scissor) {
        float diameter = radius * 2f;
        submit(g, cx - radius, cy - radius, diameter, diameter, radius, 0f, 0f, color, scissor);
    }

    private static void submit(DrawContext g, float x, float y, float w, float h,
                               float radius, float stroke, float blur, int color,
                               @Nullable ScreenRect scissor) {
        if (w <= 0f || h <= 0f || (color >>> 24) == 0) {
            return;
        }

        float halfWidth = w * 0.5f;
        float halfHeight = h * 0.5f;
        float clampedRadius = Math.max(0f, Math.min(radius, Math.min(halfWidth, halfHeight)));
        float pad = AA_PADDING + stroke + blur;
        float centerX = x + halfWidth;
        float centerY = y + halfHeight;

        g.state.addSimpleElement(new SdfGuiElementRenderState(
                PIPELINE,
                new Matrix3x2f(g.getMatrices()),
                centerX - halfWidth - pad, centerY - halfHeight - pad,
                centerX + halfWidth + pad, centerY + halfHeight + pad,
                halfWidth, halfHeight, clampedRadius, stroke, blur, color, scissor));
    }
}
