package ami.ami.client.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Перемещает, масштабирует и наклоняет предмет (или руку) от первого лица.
 * Трансформация применяется миксином в начале HeldItemRenderer.renderFirstPersonItem().
 *
 * Все значения по умолчанию нейтральные: при включённом модуле и нетронутых настройках
 * картинка не отличается от ванильной.
 */
public final class ViewModelModule {
    /** Включён ли модуль. */
    public static boolean enabled = false;

    /** Сдвиг по экрану: X - вправо, Y - вверх, Z - вперёд (от камеры) в блоках. */
    public static double x = 0.0;
    public static double y = 0.0;
    public static double z = 0.0;

    /** Общий размер и отдельные множители по осям: ширина (X), высота (Y), длина (Z). */
    public static double size = 1.0;
    public static double width = 1.0;
    public static double height = 1.0;
    public static double length = 1.0;

    /** Наклон в градусах вокруг осей X, Y и Z. */
    public static double tiltX = 0.0;
    public static double tiltY = 0.0;
    public static double tiltZ = 0.0;

    /**
     * Точка, где ваниль держит предмет на экране (см. HeldItemRenderer.applyEquipOffset).
     * Наклон и масштаб идут вокруг неё, иначе предмет «летал» бы по орбите вокруг камеры.
     */
    private static final double PIVOT_X = 0.56;
    private static final double PIVOT_Y = -0.52;
    private static final double PIVOT_Z = -0.72;

    private ViewModelModule() {
    }

    /** Возвращает все значения к ванильным. Состояние enabled не меняется. */
    public static void reset() {
        x = 0.0;
        y = 0.0;
        z = 0.0;
        size = 1.0;
        width = 1.0;
        height = 1.0;
        length = 1.0;
        tiltX = 0.0;
        tiltY = 0.0;
        tiltZ = 0.0;
    }

    /**
     * Применяет трансформацию к матрице.
     *
     * @param rightSide true, если предмет в правой руке (точка опоры зеркалится для левой).
     */
    public static void apply(MatrixStack matrices, boolean rightSide) {
        double side = rightSide ? 1.0 : -1.0;
        double px = side * PIVOT_X;

        matrices.translate(x, y, z);

        matrices.translate(px, PIVOT_Y, PIVOT_Z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) tiltX));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) tiltY));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) tiltZ));
        matrices.scale((float) (size * width), (float) (size * height), (float) (size * length));
        matrices.translate(-px, -PIVOT_Y, -PIVOT_Z);
    }
}
