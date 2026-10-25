package ami.ami.client.gui;

import ami.ami.client.render.NoHurtCamModule;
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
                    "Swings automatically at the target under your crosshair. Choose which targets are "
                            + "allowed, pause while eating or blocking, respect the vanilla attack cooldown "
                            + "and hold the swing for a falling critical hit, including the fall after knockback.",
                    () -> TriggerBotModule.enabled,
                    value -> TriggerBotModule.enabled = value,
                    List.of(
                            new BoolSetting("Don't hit while eating", "Pause swings while you are eating food",
                                    () -> TriggerBotModule.noHitWhileEating,
                                    value -> TriggerBotModule.noHitWhileEating = value),
                            new BoolSetting("Don't hit while blocking", "Pause swings while you are blocking with a shield",
                                    () -> TriggerBotModule.noHitWhileBlocking,
                                    value -> TriggerBotModule.noHitWhileBlocking = value),
                            new BoolSetting("Target players", "Players wearing at least one piece of armor",
                                    () -> TriggerBotModule.targetPlayers,
                                    value -> TriggerBotModule.targetPlayers = value),
                            new BoolSetting("Target naked players", "Players with no armor on at all",
                                    () -> TriggerBotModule.targetNakedPlayers,
                                    value -> TriggerBotModule.targetNakedPlayers = value),
                            new BoolSetting("Target animals", "Cows, pigs, sheep and other passive animals",
                                    () -> TriggerBotModule.targetAnimals,
                                    value -> TriggerBotModule.targetAnimals = value),
                            new BoolSetting("Target monsters", "Hostile mobs such as zombies and skeletons",
                                    () -> TriggerBotModule.targetMonsters,
                                    value -> TriggerBotModule.targetMonsters = value),
                            new BoolSetting("Target invisible", "Allow swings at invisible targets; off skips them",
                                    () -> TriggerBotModule.targetInvisible,
                                    value -> TriggerBotModule.targetInvisible = value),
                            new BoolSetting("Only crits", "Never swing unless falling",
                                    () -> TriggerBotModule.onlyCrits,
                                    value -> TriggerBotModule.onlyCrits = value),
                            new BoolSetting("Sprint reset", "Drop sprint before a crit, restore it after the swing",
                                    () -> TriggerBotModule.sprintReset,
                                    value -> TriggerBotModule.sprintReset = value),
                            new BoolSetting("Recoil crits", "Hold the swing while knockback launches you, crit on the fall",
                                    () -> TriggerBotModule.recoilCrits,
                                    value -> TriggerBotModule.recoilCrits = value),
                            new NumberSetting("Attack charge", "Minimum vanilla cooldown before a swing",
                                    50, 100, 1, 0, "%",
                                    () -> TriggerBotModule.minCooldown * 100.0,
                                    value -> TriggerBotModule.minCooldown = (float) (value / 100.0)),
                            new NumberSetting("Reach", "Maximum distance from eyes to the target hitbox",
                                    2.5, 4.0, 0.1, 1, "m",
                                    () -> TriggerBotModule.reach,
                                    value -> TriggerBotModule.reach = value)
                    )),
            new ModuleEntry(
                    "velocity", "Velocity", "VL", ModuleEntry.Category.MOVEMENT,
                    "Jump-resets on knockback. Schedules a jump after taking a hit so the next knockback "
                            + "is reduced, limited by chance, delay and cooldown.",
                    () -> VelocityConfig.get().enabled,
                    value -> VelocityConfig.get().enabled = value,
                    List.of(
                            new BoolSetting("Ground only", "Jump only from solid ground",
                                    () -> VelocityConfig.get().onlyOnGround,
                                    value -> VelocityConfig.get().onlyOnGround = value),
                            new NumberSetting("Trigger chance", "Chance to react to a hit",
                                    0, 100, 1, 0, "%",
                                    () -> VelocityConfig.get().chance,
                                    value -> VelocityConfig.get().chance = (int) Math.round(value)),
                            new NumberSetting("Jump delay", "Ticks to wait before the jump",
                                    0, 5, 1, 0, "t",
                                    () -> VelocityConfig.get().delayTicks,
                                    value -> VelocityConfig.get().delayTicks = (int) Math.round(value)),
                            new NumberSetting("Jump cooldown", "Minimum ticks between module jumps",
                                    10, 40, 1, 0, "t",
                                    () -> VelocityConfig.get().cooldownTicks,
                                    value -> VelocityConfig.get().cooldownTicks = (int) Math.round(value)),
                            new ActionSetting("Telemetry", "Clear jump statistics", "Reset telemetry",
                                    VelocityStats::reset)
                    )),
            new ModuleEntry(
                    "nohurtcam", "No Hurt Cam", "NH", ModuleEntry.Category.RENDER,
                    "Suppresses the camera tilt applied when you take damage, keeping your view steady "
                            + "while fighting.",
                    () -> NoHurtCamModule.enabled,
                    value -> NoHurtCamModule.enabled = value,
                    List.of()),
            new ModuleEntry(
                    "viewmodel", "ViewModel", "VM", ModuleEntry.Category.RENDER,
                    "Moves, resizes and tilts the item or hand you hold in first person. Every value "
                            + "starts neutral, so nothing changes until you drag a slider.",
                    () -> ViewModelModule.enabled,
                    value -> ViewModelModule.enabled = value,
                    List.of(
                            new NumberSetting("X", "Move left and right", -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.x,
                                    value -> ViewModelModule.x = value),
                            new NumberSetting("Y", "Move down and up", -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.y,
                                    value -> ViewModelModule.y = value),
                            new NumberSetting("Z", "Move away from and toward the camera", -2.0, 2.0, 0.01, 2, "",
                                    () -> ViewModelModule.z,
                                    value -> ViewModelModule.z = value),
                            new NumberSetting("Size", "Scale of the whole model", 0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.size,
                                    value -> ViewModelModule.size = value),
                            new NumberSetting("Width", "Stretch along the X axis", 0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.width,
                                    value -> ViewModelModule.width = value),
                            new NumberSetting("Height", "Stretch along the Y axis", 0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.height,
                                    value -> ViewModelModule.height = value),
                            new NumberSetting("Length", "Stretch along the Z axis, away from the camera",
                                    0.1, 3.0, 0.05, 2, "x",
                                    () -> ViewModelModule.length,
                                    value -> ViewModelModule.length = value),
                            new NumberSetting("Tilt X", "Rotate around the X axis (pitch)", -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.tiltX,
                                    value -> ViewModelModule.tiltX = value),
                            new NumberSetting("Tilt Y", "Rotate around the Y axis (yaw)", -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.tiltY,
                                    value -> ViewModelModule.tiltY = value),
                            new NumberSetting("Tilt Z", "Rotate around the Z axis (roll)", -180, 180, 1, 0, "°",
                                    () -> ViewModelModule.tiltZ,
                                    value -> ViewModelModule.tiltZ = value),
                            new ActionSetting("Reset", "Put every value back to vanilla", "Reset values",
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
