package ami.ami.client.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Перемещает, масштабирует и наклоняет предмет (или руку) от первого лица.
 * Трансформация применяется миксином в начале отрисовки руки в
 * HeldItemRenderer.renderFirstPersonItem().
 *
 * У основной и второй (offhand) руки свои независимые значения. Все они по умолчанию
 * нейтральные: при включённом модуле и нетронутых настройках картинка не отличается
 * от ванильной.
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

    /** Те же параметры для второй руки (offhand): щит, тотем, блок и т.п. */
    public static double offX = 0.0;
    public static double offY = 0.0;
    public static double offZ = 0.0;
    public static double offSize = 1.0;
    public static double offWidth = 1.0;
    public static double offHeight = 1.0;
    public static double offLength = 1.0;
    public static double offTiltX = 0.0;
    public static double offTiltY = 0.0;
    public static double offTiltZ = 0.0;

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
        offX = 0.0;
        offY = 0.0;
        offZ = 0.0;
        offSize = 1.0;
        offWidth = 1.0;
        offHeight = 1.0;
        offLength = 1.0;
        offTiltX = 0.0;
        offTiltY = 0.0;
        offTiltZ = 0.0;
    }

    /** Копирует все значения основной руки во вторую, чтобы быстро сделать их одинаковыми. */
    public static void copyMainToOffhand() {
        offX = x;
        offY = y;
        offZ = z;
        offSize = size;
        offWidth = width;
        offHeight = height;
        offLength = length;
        offTiltX = tiltX;
        offTiltY = tiltY;
        offTiltZ = tiltZ;
    }

    /**
     * Применяет трансформацию к матрице.
     *
     * @param rightSide true, если рука правая (точка опоры зеркалится для левой).
     * @param offhand   true для второй руки: берутся её отдельные значения.
     */
    public static void apply(MatrixStack matrices, boolean rightSide, boolean offhand) {
        double shiftX = offhand ? offX : x;
        double shiftY = offhand ? offY : y;
        double shiftZ = offhand ? offZ : z;
        double totalSize = offhand ? offSize : size;
        double axisWidth = offhand ? offWidth : width;
        double axisHeight = offhand ? offHeight : height;
        double axisLength = offhand ? offLength : length;
        double rotationX = offhand ? offTiltX : tiltX;
        double rotationY = offhand ? offTiltY : tiltY;
        double rotationZ = offhand ? offTiltZ : tiltZ;

        double side = rightSide ? 1.0 : -1.0;
        double px = side * PIVOT_X;

        matrices.translate(shiftX, shiftY, shiftZ);

        matrices.translate(px, PIVOT_Y, PIVOT_Z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotationX));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotationY));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotationZ));
        matrices.scale((float) (totalSize * axisWidth), (float) (totalSize * axisHeight),
                (float) (totalSize * axisLength));
        matrices.translate(-px, -PIVOT_Y, -PIVOT_Z);
    }
}
