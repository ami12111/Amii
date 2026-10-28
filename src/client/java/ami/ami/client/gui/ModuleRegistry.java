package ami.ami.client.gui;

import ami.ami.client.movement.AutoSprintModule;
import ami.ami.client.render.HitParticle;
import ami.ami.client.render.JumpCircles;
import ami.ami.client.render.NoHurtCamModule;
import ami.ami.client.render.NoRenderModule;
import ami.ami.client.render.ViewModelModule;
import ami.ami.client.triggerbot.TriggerBotModule;
import ami.ami.client.velocity.VelocityConfig;
import ami.ami.client.velocity.VelocityStats;

import java.util.List;

/** Every module that exists in the client, exposed to the click GUI. */
public final class ModuleRegistry {
    private static final List<ModuleEntry> MODULES = List.of(
            new ModuleEntry(
                    "triggerbot", "Triggerbot", "TB", ModuleEntry.Category.COMBAT,
                    "Автоматически бьёт цель под прицелом. Типы целей настраиваются; удары "
                            + "приостанавливаются во время еды и блока щитом, щит цели ломается топором, "
                            + "атака учитывает ванильный кулдаун и выдерживается до крита в падении, "
                            + "включая падение после отбрасывания.",
                    () -> TriggerBotModule.enabled,
                    value -> TriggerBotModule.enabled = value,
                    List.of(
                            new BoolSetting("Не бить во время еды", "Не бить, пока вы едите",
                                    () -> TriggerBotModule.noHitWhileEating,
                                    value -> TriggerBotModule.noHitWhileEating = value),
                            new BoolSetting("Не бить при блоке", "Не бить, пока вы закрываетесь щитом",
                                    () -> TriggerBotModule.noHitWhileBlocking,
                                    value -> TriggerBotModule.noHitWhileBlocking = value),
                            new BoolSetting("Ломать щит", "Если цель закрылась щитом, взять топор из хотбара, ударить с задержкой и вернуть слот",
                                    () -> TriggerBotModule.breakShield,
                                    value -> TriggerBotModule.breakShield = value),
                            new NumberSetting("Задержка топора", "Тиков между выбором топора и ударом",
                                    1, 3, 1, 0, "т",
                                    () -> TriggerBotModule.shieldBreakDelay,
                                    value -> TriggerBotModule.shieldBreakDelay = (int) Math.round(value)),
                            new NumberSetting("Держать топор", "Сколько тиков держать топор после удара",
                                    1, 5, 1, 0, "т",
                                    () -> TriggerBotModule.shieldBreakHold,
                                    value -> TriggerBotModule.shieldBreakHold = (int) Math.round(value)),
                            new BoolSetting("Бить игроков", "Игроки хотя бы с одной частью брони",
                                    () -> TriggerBotModule.targetPlayers,
                                    value -> TriggerBotModule.targetPlayers = value),
                            new BoolSetting("Бить голых игроков", "Игроки без брони",
                                    () -> TriggerBotModule.targetNakedPlayers,
                                    value -> TriggerBotModule.targetNakedPlayers = value),
                            new BoolSetting("Бить животных", "Коровы, свиньи, овцы и другие мирные животные",
                                    () -> TriggerBotModule.targetAnimals,
                                    value -> TriggerBotModule.targetAnimals = value),
                            new BoolSetting("Бить монстров", "Враждебные мобы: зомби, скелеты и т.п.",
                                    () -> TriggerBotModule.targetMonsters,
                                    value -> TriggerBotModule.targetMonsters = value),
                            new BoolSetting("Бить невидимых", "Разрешить удары по невидимым целям",
                                    () -> TriggerBotModule.targetInvisible,
                                    value -> TriggerBotModule.targetInvisible = value),
                            new BoolSetting("Только криты", "Бить только в падении",
                                    () -> TriggerBotModule.onlyCrits,
                                    value -> TriggerBotModule.onlyCrits = value),
                            new BoolSetting("Сброс спринта", "Снимать спринт перед критом и возвращать после удара",
                                    () -> TriggerBotModule.sprintReset,
                                    value -> TriggerBotModule.sprintReset = value),
                            new BoolSetting("Крит от отдачи", "Удерживать удар, пока вас подбрасывает, и бить на падении",
                                    () -> TriggerBotModule.recoilCrits,
                                    value -> TriggerBotModule.recoilCrits = value),
                            new NumberSetting("Заряд атаки", "Минимальный ванильный кулдаун перед ударом",
                                    50, 100, 1, 0, "%",
                                    () -> TriggerBotModule.minCooldown * 100.0,
                                    value -> TriggerBotModule.minCooldown = (float) (value / 100.0)),
                            new NumberSetting("Дистанция", "Максимальное расстояние от глаз до хитбокса цели",
                                    2.5, 4.0, 0.1, 1, "м",
                                    () -> TriggerBotModule.reach,
                                    value -> TriggerBotModule.reach = value)
                    )),
            new ModuleEntry(
                    "velocity", "Velocity", "VL", ModuleEntry.Category.MOVEMENT,
                    "Сброс отбрасывания прыжком. После удара планирует прыжок, чтобы ослабить "
                            + "следующий отброс; ограничено шансом, задержкой и кулдауном.",
                    () -> VelocityConfig.get().enabled,
                    value -> VelocityConfig.get().enabled = value,
                    List.of(
                            new BoolSetting("Только с земли", "Прыгать только с твёрдой земли",
                                    () -> VelocityConfig.get().onlyOnGround,
                                    value -> VelocityConfig.get().onlyOnGround = value),
                            new NumberSetting("Шанс срабатывания", "Вероятность среагировать на удар",
                                    0, 100, 1, 0, "%",
                                    () -> VelocityConfig.get().chance,
                                    value -> VelocityConfig.get().chance = (int) Math.round(value)),
                            new NumberSetting("Задержка прыжка", "Сколько тиков ждать перед прыжком",
                                    0, 5, 1, 0, "т",
                                    () -> VelocityConfig.get().delayTicks,
                                    value -> VelocityConfig.get().delayTicks = (int) Math.round(value)),
                            new NumberSetting("Кулдаун прыжка", "Минимум тиков между прыжками модуля",
                                    10, 40, 1, 0, "т",
                                    () -> VelocityConfig.get().cooldownTicks,
                                    value -> VelocityConfig.get().cooldownTicks = (int) Math.round(value)),
                            new ActionSetting("Статистика", "Сбросить статистику прыжков", "Сбросить",
                                    VelocityStats::reset)
                    )),
            new ModuleEntry(
                    "autosprint", "Auto Sprint", "AS", ModuleEntry.Category.MOVEMENT,
                    "Удерживает спринт при движении вперёд — клавишу спринта держать не нужно. "
                            + "Ванильные ограничения сохраняются: приседание, блок щитом, еда и низкий "
                            + "голод останавливают спринт.",
                    () -> AutoSprintModule.enabled,
                    value -> AutoSprintModule.enabled = value,
                    List.of(
                            new BoolSetting("Только с земли", "Начинать спринт только с твёрдой земли",
                                    () -> AutoSprintModule.onlyOnGround,
                                    value -> AutoSprintModule.onlyOnGround = value)
                    )),
            new ModuleEntry(
                    "nohurtcam", "No Hurt Cam", "NH", ModuleEntry.Category.RENDER,
                    "Убирает наклон камеры при получении урона — обзор остаётся ровным в бою.",
                    () -> NoHurtCamModule.enabled,
                    value -> NoHurtCamModule.enabled = value,
                    List.of()),
            new ModuleEntry(
                    "norender", "No Render", "NR", ModuleEntry.Category.RENDER,
                    "Скрывает выбранные визуальные эффекты: частицы в мире, огонь на экране при "
                            + "горении и анимацию тотема бессмертия.",
                    () -> NoRenderModule.enabled,
                    value -> NoRenderModule.enabled = value,
                    List.of(
                            new BoolSetting("Частицы", "Скрывать частицы в мире",
                                    () -> NoRenderModule.particles,
                                    value -> NoRenderModule.particles = value),
                            new BoolSetting("Огонь на экране", "Скрывать огонь на экране, пока вы горите",
                                    () -> NoRenderModule.fireOverlay,
                                    value -> NoRenderModule.fireOverlay = value),
                            new BoolSetting("Тотем", "Скрывать анимацию тотема бессмертия",
                                    () -> NoRenderModule.totemAnimation,
                                    value -> NoRenderModule.totemAnimation = value)
                    )),
            new ModuleEntry(
                    "jumpcircles", "Jump Circles", "JC", ModuleEntry.Category.RENDER,
                    "Магические круги на шейдерах: несколько стилей и отдельные цвета для каждого "
                            + "эффекта. При прыжке — рунический или спиральный круг с поднимающимися "
                            + "кольцами, при приземлении — ударная волна, звёздная вспышка или "
                            + "рунический разлом; масштаб зависит от высоты падения.",
                    () -> JumpCircles.enabled,
                    value -> JumpCircles.enabled = value,
                    List.of(
                            new BoolSetting("Показывать другим", "Рисовать круги и для других игроков",
                                    () -> JumpCircles.showForOthers,
                                    value -> JumpCircles.showForOthers = value),
                            new BoolSetting("Эффект приземления", "Эффект при приземлении после падения",
                                    () -> JumpCircles.landingBurst,
                                    value -> JumpCircles.landingBurst = value),
                            new BoolSetting("Поднимающиеся кольца", "Кольца, поднимающиеся по телу при прыжке",
                                    () -> JumpCircles.risingHalos,
                                    value -> JumpCircles.risingHalos = value),
                            new BoolSetting("Случайные стили", "Случайно выбирать один из дополнительных стилей",
                                    () -> JumpCircles.randomStyles,
                                    value -> JumpCircles.randomStyles = value),
                            new BoolSetting("Радуга", "Плавно менять оттенок вместо фиксированных цветов",
                                    () -> JumpCircles.rainbow,
                                    value -> JumpCircles.rainbow = value),
                            new ActionSetting("Меню цветов", "Открыть отдельный редактор цветов",
                                    "Настроить", ColorSettingsScreen::open),
                            new NumberSetting("Размер", "Общий множитель размера", 0.25, 3.0, 0.05, 2, "x",
                                    () -> JumpCircles.scale,
                                    value -> JumpCircles.scale = (float) value)
                    )),
            new ModuleEntry(
                    "hitparticle", "Hit Particle", "HP", ModuleEntry.Category.RENDER,
                    "Шейдерные частицы при ударе: ослепительная вспышка, призматическая ударная "
                            + "волна, искры-кометы, граненые кристаллические осколки, тлеющие огоньки "
                            + "и энергетический дым. Криты и добивания получают усиленный взрыв со "
                            + "вращающейся звездой. Эффект появляется только когда удар нанёс урон.",
                    () -> HitParticle.enabled,
                    value -> HitParticle.enabled = value,
                    List.of(
                            new BoolSetting("Игроки", "Частицы при ударе по игрокам",
                                    () -> HitParticle.targetPlayers,
                                    value -> HitParticle.targetPlayers = value),
                            new BoolSetting("Монстры", "Частицы при ударе по враждебным мобам",
                                    () -> HitParticle.targetMonsters,
                                    value -> HitParticle.targetMonsters = value),
                            new BoolSetting("Животные и прочие", "Частицы при ударе по мирным существам",
                                    () -> HitParticle.targetAnimals,
                                    value -> HitParticle.targetAnimals = value),
                            new BoolSetting("Усиление крита", "Звезда и большая волна при крите и добивании",
                                    () -> HitParticle.critBurst,
                                    value -> HitParticle.critBurst = value),
                            new BoolSetting("Ударная волна", "Кольцо, расходящееся от точки удара",
                                    () -> HitParticle.shockwave,
                                    value -> HitParticle.shockwave = value),
                            new BoolSetting("Осколки", "Кристаллические осколки, разлетающиеся при ударе",
                                    () -> HitParticle.shards,
                                    value -> HitParticle.shards = value),
                            new BoolSetting("Радуга", "Плавно менять оттенок вместо фиксированных цветов",
                                    () -> HitParticle.rainbow,
                                    value -> HitParticle.rainbow = value),
                            new NumberSetting("Оттенок: основной", "Вспышка, волна и искры",
                                    0, 359, 1, 0, "°",
                                    () -> HitParticle.hueOf(HitParticle.primaryColor),
                                    value -> HitParticle.primaryColor = HitParticle.colorFromHue(value)),
                            new NumberSetting("Оттенок: акцент", "Осколки, дым и часть искр",
                                    0, 359, 1, 0, "°",
                                    () -> HitParticle.hueOf(HitParticle.accentColor),
                                    value -> HitParticle.accentColor = HitParticle.colorFromHue(value)),
                            new NumberSetting("Оттенок: крит", "Звезда и волна при крите",
                                    0, 359, 1, 0, "°",
                                    () -> HitParticle.hueOf(HitParticle.critColor),
                                    value -> HitParticle.critColor = HitParticle.colorFromHue(value)),
                            new NumberSetting("Количество", "Множитель количества частиц",
                                    0.25, 2.0, 0.05, 2, "x",
                                    () -> HitParticle.density,
                                    value -> HitParticle.density = (float) value),
                            new NumberSetting("Размер", "Общий множитель размера", 0.25, 3.0, 0.05, 2, "x",
                                    () -> HitParticle.scale,
                                    value -> HitParticle.scale = (float) value),
                            new NumberSetting("Длительность", "Множитель длительности эффекта",
                                    0.4, 2.0, 0.05, 2, "x",
                                    () -> HitParticle.duration,
                                    value -> HitParticle.duration = (float) value),
                            new ActionSetting("Предпросмотр", "Показать эффект перед собой (крит и обычный по очереди)",
                                    "Показать", HitParticle::spawnPreview),
                            new ActionSetting("Сброс цветов", "Вернуть цвета по умолчанию", "Сбросить",
                                    () -> {
                                        HitParticle.primaryColor = HitParticle.DEFAULT_PRIMARY;
                                        HitParticle.accentColor = HitParticle.DEFAULT_ACCENT;
                                        HitParticle.critColor = HitParticle.DEFAULT_CRIT;
                                    })
                    )),
            new ModuleEntry(
                    "viewmodel", "ViewModel", "VM", ModuleEntry.Category.RENDER,
                    "Сдвигает, масштабирует и наклоняет предмет или руку от первого лица. "
                            + "У второй руки (offhand) свои значения. Все значения по умолчанию "
                            + "нейтральные — картинка меняется только после изменения ползунков.",
                    () -> ViewModelModule.enabled,
                    value -> ViewModelModule.enabled = value,
                    List.of(
                            new NumberSetting("X", "Сдвиг влево-вправо", -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.x,
                                    value -> ViewModelModule.x = value),
                            new NumberSetting("Y", "Сдвиг вниз-вверх", -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.y,
                                    value -> ViewModelModule.y = value),
                            new NumberSetting("Z", "Сдвиг от камеры и к ней", -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.z,
                                    value -> ViewModelModule.z = value),
                            new NumberSetting("Размер", "Масштаб всей модели", 0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.size,
                                    value -> ViewModelModule.size = value),
                            new NumberSetting("Ширина", "Растяжение по оси X", 0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.width,
                                    value -> ViewModelModule.width = value),
                            new NumberSetting("Высота", "Растяжение по оси Y", 0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.height,
                                    value -> ViewModelModule.height = value),
                            new NumberSetting("Длина", "Растяжение по оси Z, от камеры",
                                    0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.length,
                                    value -> ViewModelModule.length = value),
                            new NumberSetting("Наклон X", "Поворот вокруг оси X (тангаж)",
                                    -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.tiltX,
                                    value -> ViewModelModule.tiltX = value),
                            new NumberSetting("Наклон Y", "Поворот вокруг оси Y (рыскание)",
                                    -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.tiltY,
                                    value -> ViewModelModule.tiltY = value),
                            new NumberSetting("Наклон Z", "Поворот вокруг оси Z (крен)",
                                    -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.tiltZ,
                                    value -> ViewModelModule.tiltZ = value),
                            new NumberSetting("Вторая: X", "Сдвиг второй руки влево-вправо",
                                    -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.offX,
                                    value -> ViewModelModule.offX = value),
                            new NumberSetting("Вторая: Y", "Сдвиг второй руки вниз-вверх",
                                    -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.offY,
                                    value -> ViewModelModule.offY = value),
                            new NumberSetting("Вторая: Z", "Сдвиг второй руки от камеры и к ней",
                                    -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.offZ,
                                    value -> ViewModelModule.offZ = value),
                            new NumberSetting("Вторая: размер", "Масштаб модели второй руки",
                                    0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.offSize,
                                    value -> ViewModelModule.offSize = value),
                            new NumberSetting("Вторая: ширина", "Растяжение второй руки по оси X",
                                    0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.offWidth,
                                    value -> ViewModelModule.offWidth = value),
                            new NumberSetting("Вторая: высота", "Растяжение второй руки по оси Y",
                                    0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.offHeight,
                                    value -> ViewModelModule.offHeight = value),
                            new NumberSetting("Вторая: длина", "Растяжение второй руки по оси Z",
                                    0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.offLength,
                                    value -> ViewModelModule.offLength = value),
                            new NumberSetting("Вторая: наклон X", "Поворот второй руки вокруг оси X (тангаж)",
                                    -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.offTiltX,
                                    value -> ViewModelModule.offTiltX = value),
                            new NumberSetting("Вторая: наклон Y", "Поворот второй руки вокруг оси Y (рыскание)",
                                    -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.offTiltY,
                                    value -> ViewModelModule.offTiltY = value),
                            new NumberSetting("Вторая: наклон Z", "Поворот второй руки вокруг оси Z (крен)",
                                    -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.offTiltZ,
                                    value -> ViewModelModule.offTiltZ = value),
                            new ActionSetting("Копировать основную", "Скопировать все значения основной руки во вторую",
                                    "Копировать", ViewModelModule::copyMainToOffhand),
                            new ActionSetting("Сброс", "Вернуть все значения к ванильным", "Сбросить",
                                    ViewModelModule::reset)
                    ))
    );

    private ModuleRegistry() {
    }

    public static List<ModuleEntry> all() {
        return MODULES;
    }

    public static List<ModuleEntry> filtered(ModuleEntry.Category category) {
        if (category == null) {
            return MODULES;
        }
        return MODULES.stream().filter(module -> module.category() == category).toList();
    }

    public static ModuleEntry byId(String id) {
        for (ModuleEntry module : MODULES) {
            if (module.id().equals(id)) {
                return module;
            }
        }
        return null;
    }

    public static int indexOf(ModuleEntry entry) {
        return MODULES.indexOf(entry);
    }

    public static int activeCount() {
        int count = 0;
        for (ModuleEntry module : MODULES) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    public static int count(ModuleEntry.Category category) {
        return filtered(category).size();
    }
}
