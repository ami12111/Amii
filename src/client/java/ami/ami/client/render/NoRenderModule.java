package ami.ami.client.render;

/**
 * Убирает выбранные визуальные эффекты: частицы в мире, огонь на экране и анимацию тотема.
 * Логика используется миксинами; сами настройки видны в ClickGUI.
 */
public final class NoRenderModule {
    /** Включён ли модуль. */
    public static boolean enabled = false;

    /** Не рисовать частицы в мире. */
    public static boolean particles = true;

    /** Не рисовать огонь на экране, когда игрок горит. */
    public static boolean fireOverlay = true;

    /** Не показывать анимацию тотема бессмертия. */
    public static boolean totemAnimation = true;

    private NoRenderModule() {
    }

    public static boolean hideParticles() {
        return enabled && particles;
    }

    public static boolean hideFireOverlay() {
        return enabled && fireOverlay;
    }

    public static boolean hideTotemAnimation() {
        return enabled && totemAnimation;
    }
}
