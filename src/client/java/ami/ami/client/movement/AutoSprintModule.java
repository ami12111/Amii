package ami.ami.client.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;

/**
 * Автоматический спринт при движении вперёд.
 *
 * Модуль не трогает саму скорость: он лишь выставляет флаг sprint в текущем вводе игрока,
 * как будто зажата ванильная клавиша спринта. Поэтому все ванильные ограничения остаются
 * в силе — приседание, щит/еда, низкий голод, транспорт, слепота и т.п.
 */
public final class AutoSprintModule {
    /** Включён ли модуль. */
    public static boolean enabled = true;

    /** Спринтовать только стоя на земле (уже начатый спринт в воздухе не сбрасывается). */
    public static boolean onlyOnGround = false;

    private AutoSprintModule() {
    }

    /**
     * Вызывается из ClientPlayerEntity.tickMovement() сразу после чтения клавиш,
     * но до ванильной логики старта спринта.
     */
    public static void onTickMovement(ClientPlayerEntity player) {
        if (!enabled) {
            return;
        }
        if (onlyOnGround && !player.isOnGround()) {
            return;
        }

        PlayerInput input = player.input.playerInput;
        if (input.sprint()) {
            return; // клавиша спринта уже зажата — ничего не добавляем
        }
        if (!player.input.hasForwardMovement()) {
            return; // спринтуем только при движении вперёд
        }

        player.input.playerInput = new PlayerInput(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                input.sneak(),
                true);
    }
}
