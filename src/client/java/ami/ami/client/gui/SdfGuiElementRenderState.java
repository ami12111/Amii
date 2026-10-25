package ami.ami.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * Один SDF-квад в очереди GUI.
 *
 * Геометрия расширена на запас для сглаживания/тени, а локальные координаты
 * считаются в физических пикселях относительно центра фигуры. Параметры
 * (полуразмер, радиус, обводка) пакуются в стандартные short-атрибуты UV1/UV2
 * с множителем 4, размытие тени едет в LineWidth.
 */
final class SdfGuiElementRenderState implements SimpleGuiElementRenderState {
    /** Сколько значений помещается в short при множителе 4. */
    private static final float PACK = 4f;

    private final RenderPipeline pipeline;
    private final Matrix3x2f pose;
    private final float x0;
    private final float y0;
    private final float x1;
    private final float y1;
    private final float halfWidth;
    private final float halfHeight;
    private final float radius;
    private final float stroke;
    private final float blur;
    private final int color;
    private final @Nullable ScreenRect scissorArea;
    private final @Nullable ScreenRect bounds;

    SdfGuiElementRenderState(RenderPipeline pipeline, Matrix3x2f pose,
                             float x0, float y0, float x1, float y1,
                             float halfWidth, float halfHeight,
                             float radius, float stroke, float blur,
                             int color, @Nullable ScreenRect scissorArea) {
        this.pipeline = pipeline;
        this.pose = pose;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.radius = radius;
        this.stroke = stroke;
        this.blur = blur;
        this.color = color;
        this.scissorArea = scissorArea;
        this.bounds = createBounds(x0, y0, x1, y1, pose, scissorArea);
    }

    @Override
    public void setupVertices(VertexConsumer vertices) {
        int packedHalfWidth = Math.round(halfWidth * PACK);
        int packedHalfHeight = Math.round(halfHeight * PACK);
        int packedRadius = Math.round(radius * PACK);
        int packedStroke = Math.round(stroke * PACK);

        float halfQuadWidth = (x1 - x0) * 0.5f;
        float halfQuadHeight = (y1 - y0) * 0.5f;

        vertex(vertices, x0, y0, -halfQuadWidth, -halfQuadHeight,
                packedHalfWidth, packedHalfHeight, packedRadius, packedStroke);
        vertex(vertices, x0, y1, -halfQuadWidth, halfQuadHeight,
                packedHalfWidth, packedHalfHeight, packedRadius, packedStroke);
        vertex(vertices, x1, y1, halfQuadWidth, halfQuadHeight,
                packedHalfWidth, packedHalfHeight, packedRadius, packedStroke);
        vertex(vertices, x1, y0, halfQuadWidth, -halfQuadHeight,
                packedHalfWidth, packedHalfHeight, packedRadius, packedStroke);
    }

    private void vertex(VertexConsumer vertices, float x, float y, float localX, float localY,
                        int packedHalfWidth, int packedHalfHeight, int packedRadius, int packedStroke) {
        vertices.vertex(pose, x, y)
                .color(color)
                .texture(localX, localY)
                .overlay(packedHalfWidth, packedHalfHeight)
                .light(packedRadius, packedStroke)
                .lineWidth(blur);
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.empty();
    }

    @Override
    public @Nullable ScreenRect scissorArea() {
        return scissorArea;
    }

    @Override
    public @Nullable ScreenRect bounds() {
        return bounds;
    }

    private static @Nullable ScreenRect createBounds(float x0, float y0, float x1, float y1,
                                                     Matrix3x2fc pose, @Nullable ScreenRect scissorArea) {
        ScreenRect rect = new ScreenRect(Math.round(x0), Math.round(y0),
                Math.round(x1 - x0), Math.round(y1 - y0)).transformEachVertex(pose);
        return scissorArea != null ? scissorArea.intersection(rect) : rect;
    }
}
