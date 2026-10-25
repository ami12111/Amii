package ami.ami.client.gui;

/** One primitive in a {@link UiDrawList}. */
public sealed interface UiCommand permits UiCommand.Rect, UiCommand.GradientRect, UiCommand.Outline,
        UiCommand.Circle, UiCommand.Line, UiCommand.Shadow, UiCommand.PushScissor, UiCommand.PopScissor,
        UiCommand.Text, UiCommand.BrandText, UiCommand.Icon {

    enum HorizontalAlign {
        LEFT,
        CENTER,
        RIGHT
    }

    record Rect(float x, float y, float width, float height, float radius, int color) implements UiCommand {
    }

    record GradientRect(float x, float y, float width, float height, float radius,
                        int startColor, int endColor, boolean horizontal) implements UiCommand {
    }

    record Outline(float x, float y, float width, float height, float radius,
                   float thickness, int color) implements UiCommand {
    }

    record Circle(float centerX, float centerY, float radius, int color) implements UiCommand {
    }

    record Line(float x1, float y1, float x2, float y2, float thickness, int color) implements UiCommand {
    }

    /** Мягкая тень: фигура с размытием, рисуется SDF-пайплайном. */
    record Shadow(float x, float y, float width, float height, float radius, float blur,
                  int color) implements UiCommand {
    }

    record PushScissor(float x, float y, float width, float height) implements UiCommand {
    }

    record PopScissor() implements UiCommand {
    }

    record Text(float x, float y, float size, int color, String value,
                HorizontalAlign align, boolean strong) implements UiCommand {
    }

    record BrandText(float x, float y, float size, int color, String value,
                     HorizontalAlign align) implements UiCommand {
    }

    record Icon(float x, float y, float size, int color, int codePoint) implements UiCommand {
    }
}
