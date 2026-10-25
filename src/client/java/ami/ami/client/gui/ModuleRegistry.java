package ami.ami.client.gui;

import ami.ami.client.render.NoHurtCamModule;
import ami.ami.client.triggerbot.TriggerBotClient;
import ami.ami.client.triggerbot.TriggerBotModule;
import ami.ami.client.velocity.VelocityConfig;
import ami.ami.client.velocity.VelocityStats;

import java.util.List;

/** Every module that exists in the client, exposed to the click GUI. */
public final class ModuleRegistry {
    private static final List<ModuleEntry> MODULES = List.of(
            new ModuleEntry(
                    "triggerbot", "TRIGGERBOT", "TB", ModuleEntry.Category.COMBAT,
                    "Swings automatically at the player under your crosshair. Respects the vanilla attack "
                            + "cooldown and can hold the swing for a falling critical hit.",
                    () -> TriggerBotModule.enabled,
                    value -> TriggerBotModule.enabled = value,
                    ModuleRegistry::triggerBotKey,
                    List.of(
                            new BoolSetting("Only crits", "Never swing unless falling",
                                    () -> TriggerBotModule.onlyCrits,
                                    value -> TriggerBotModule.onlyCrits = value),
                            new NumberSetting("Attack charge", "Minimum vanilla cooldown before a swing",
                                    50, 100, 1, 0, "%",
                                    () -> TriggerBotModule.minCooldown * 100.0,
                                    value -> TriggerBotModule.minCooldown = (float) (value / 100.0)),
                            new NumberSetting("Reach", "Maximum attack distance",
                                    2.5, 6.0, 0.1, 1, "m",
                                    () -> TriggerBotModule.reach,
                                    value -> TriggerBotModule.reach = value)
                    )),
            new ModuleEntry(
                    "velocity", "VELOCITY", "VL", ModuleEntry.Category.MOVEMENT,
                    "Jump-resets on knockback. Schedules a jump after taking a hit so the next knockback "
                            + "is reduced, limited by chance, delay and cooldown.",
                    () -> VelocityConfig.get().enabled,
                    value -> VelocityConfig.get().enabled = value,
                    null,
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
                            new ActionSetting("Telemetry", "Clear jump statistics", "RESET TELEMETRY",
                                    VelocityStats::reset)
                    )),
            new ModuleEntry(
                    "nohurtcam", "NO HURT CAM", "NH", ModuleEntry.Category.RENDER,
                    "Suppresses the camera tilt applied when you take damage, keeping your view steady "
                            + "while fighting.",
                    () -> NoHurtCamModule.enabled,
                    value -> NoHurtCamModule.enabled = value,
                    null,
                    List.of())
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

    private static String triggerBotKey() {
        return TriggerBotClient.toggleKey == null
                ? "R"
                : TriggerBotClient.toggleKey.getBoundKeyLocalizedText().getString();
    }
}
